package com.medisafe.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.medisafe.data.prefs.AppPreferences

/**
 * Plays the looping alarm tone and vibration.
 *
 * This is deliberately independent of any Activity: with the screen off Android may never
 * let [AlarmRingActivity] start, so the audio has to be owned by the foreground
 * [AlarmService] instead. A partial wake lock is held while ringing so the CPU keeps
 * running the media/vibrator loop on a sleeping device.
 */
object AlarmPlayer {

    private const val TAG = "AlarmPlayer"
    private const val WAKELOCK_TAG = "MediSafe::AlarmPlayer"
    private const val RAMP_STEPS = 8
    private const val RAMP_STEP_MS = 700L

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampRunnable: Runnable? = null

    /** Alarm stream volume before we raised it, so we can politely put it back. */
    private var previousAlarmVolume: Int? = null

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Synchronized
    fun start(
        context: Context,
        sound: Boolean,
        vibrate: Boolean,
        critical: Boolean = false
    ) {
        stop(context)
        isPlaying = true
        acquireWakeLock(context)

        if (sound) {
            val prefs = AppPreferences(context)
            val reliabilityOn = prefs.alarmReliabilityEnabled
            if ((reliabilityOn && prefs.forceAlarmVolume) || critical) {
                raiseAlarmVolume(context, critical)
            }
            startTone(context, gradual = reliabilityOn && prefs.gradualAlarmVolume && !critical)
        }

        if (vibrate) {
            startVibration(context)
        }
    }

    private fun startTone(context: Context, gradual: Boolean) {
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // USAGE_ALARM is what lets the tone play through silent mode and,
                        // with the right DND policy, through Do Not Disturb.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                val startVolume = if (gradual) 0.15f else 1f
                setVolume(startVolume, startVolume)
                prepare()
                start()
            }
        }.onFailure { Log.e(TAG, "Unable to start alarm tone", it) }

        if (gradual && player != null) rampVolumeUp()
    }

    /** Fades the tone in so it wakes you without being a heart attack. */
    private fun rampVolumeUp() {
        var step = 1
        val runnable = object : Runnable {
            override fun run() {
                val active = player ?: return
                val volume = (0.15f + (0.85f * step / RAMP_STEPS)).coerceAtMost(1f)
                runCatching { active.setVolume(volume, volume) }
                if (step < RAMP_STEPS && isPlaying) {
                    step++
                    handler.postDelayed(this, RAMP_STEP_MS)
                }
            }
        }
        rampRunnable = runnable
        handler.postDelayed(runnable, RAMP_STEP_MS)
    }

    private fun startVibration(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 700, 400, 700, 400)
        runCatching {
            when {
                // API 33+ lets us tag the vibration as an alarm so it survives DND/silent.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val attrs = android.os.VibrationAttributes.Builder()
                        .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                        .build()
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        }.onFailure { Log.e(TAG, "Unable to vibrate", it) }
    }

    /**
     * Raises the ALARM stream so a phone left on silent still rings. The user's original
     * level is restored in [stop].
     */
    private fun raiseAlarmVolume(context: Context, critical: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = if (critical) max else (max * 0.8f).toInt().coerceAtLeast(1)
            if (current < target) {
                previousAlarmVolume = current
                audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        }.onFailure { Log.w(TAG, "Could not adjust alarm volume", it) }
    }

    private fun restoreAlarmVolume(context: Context?) {
        val original = previousAlarmVolume ?: return
        previousAlarmVolume = null
        val audio = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, original, 0) }
    }

    private fun acquireWakeLock(context: Context) {
        if (wakeLock?.isHeld == true) return
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        // Bound the lock to how long the alarm can actually ring (plus a small margin)
        // rather than a fixed 10 minutes, so we never hold the CPU longer than needed.
        val ringMinutes = AppPreferences(context).alarmTimeoutMinutes
        val timeoutMs = (ringMinutes * 60_000L) + 30_000L
        runCatching {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                // Safety net: never leak the lock even if stop() is somehow missed.
                acquire(timeoutMs)
            }
        }.onFailure { Log.e(TAG, "Unable to acquire wake lock", it) }
    }

    @JvmOverloads
    @Synchronized
    fun stop(context: Context? = null) {
        isPlaying = false
        rampRunnable?.let { handler.removeCallbacks(it) }
        rampRunnable = null

        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }.onFailure { Log.w(TAG, "Error stopping player", it) }
        player = null

        runCatching { vibrator?.cancel() }
        vibrator = null

        restoreAlarmVolume(context)

        runCatching {
            wakeLock?.let { if (it.isHeld) it.release() }
        }
        wakeLock = null
    }
}
