package com.medisafe.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medisafe.R
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.prefs.AppPreferences
import com.medisafe.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns a ringing alarm.
 *
 * Why a service and not just [AlarmRingActivity]: from Android 10 onward an app cannot
 * reliably launch an Activity from the background, and with the screen off that is exactly
 * the situation an alarm fires in. A foreground service, started from the exact-alarm
 * broadcast, is allowed to run and to play audio immediately. The full-screen intent on its
 * notification is what brings the UI up over the lock screen when the system permits it —
 * but the sound and vibration no longer depend on that happening.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    /**
     * Set the moment a stop is requested. The Room lookup above finishes on IO and then
     * posts to the main handler; without this guard that late post could start ringing
     * AFTER the user (or an ACTION_STOP) already silenced the alarm — with nothing left
     * running that would ever stop it.
     */
    @Volatile
    private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val reminderId = intent?.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId == -1L) {
            stopEverything()
            return START_NOT_STICKY
        }
        stopRequested = false
        // Post a placeholder notification within the 5s ANR window, then enrich it from the DB.
        startForegroundCompat(buildNotification(null, reminderId))

        scope.launch {
            val item = runCatching {
                AppDatabase.getInstance(applicationContext).reminderDao().getReminderById(reminderId)
            }.getOrNull()

            handler.post {
                if (stopRequested) return@post
                startForegroundCompat(buildNotification(item, reminderId))
                AlarmPlayer.start(
                    context = applicationContext,
                    sound = item?.notificationSound ?: true,
                    vibrate = item?.vibrate ?: true,
                    critical = item?.isCritical == true
                )
                scheduleAutoStop(item)
            }
        }
        // Not sticky: a restarted alarm with no intent would be meaningless, and the
        // scheduled AlarmManager trigger is the real source of truth.
        return START_NOT_STICKY
    }

    /**
     * An alarm nobody answers should not ring forever and flatten the battery. After the
     * configured timeout we stop the noise and hand off to the escalation re-ring.
     */
    private fun scheduleAutoStop(item: ReminderItem?) {
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        val minutes = AppPreferences(applicationContext).alarmTimeoutMinutes
        val runnable = Runnable {
            if (item != null) {
                AlarmScheduler.scheduleEscalation(applicationContext, item, attempt = 1)
            }
            stopEverything()
        }
        autoStopRunnable = runnable
        handler.postDelayed(runnable, minutes * 60_000L)
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.e(TAG, "startForeground failed; falling back to a plain notification", it)
            runCatching {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(item: ReminderItem?, reminderId: Long): Notification {
        NotificationHelper.ensureChannel(this)

        val title = item?.title ?: "Reminder"
        val isMed = item?.categoryEnum == ReminderCategory.MEDICATION
        val headline = when {
            item == null -> "Reminder"
            isMed -> "Time for: $title"
            else -> "Reminder: $title"
        }
        val body = buildString {
            val dose = item?.doseLabel.orEmpty()
            append(dose.ifBlank { "It's time for your scheduled reminder." })
            val food = item?.foodTimingEnum?.displayName
            if (!food.isNullOrBlank() && food != "No food rule") append(" · $food")
        }

        val fullScreen = PendingIntent.getActivity(
            this,
            AlarmScheduler.requestCode(reminderId, 5),
            Intent(this, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun broadcast(action: String, salt: Int): PendingIntent = PendingIntent.getBroadcast(
            this,
            AlarmScheduler.requestCode(reminderId, salt),
            Intent(this, ReminderAlarmReceiver::class.java).apply {
                this.action = action
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALARM_ID_SILENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headline)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$body\nDue ${DateTimeUtils.formatTime(System.currentTimeMillis())}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreen)
            // The heart of "ring with the screen off": ask the system to launch the alarm UI.
            .setFullScreenIntent(fullScreen, true)
            .addAction(0, if (isMed) "Take now" else "Mark done", broadcast(AlarmScheduler.ACTION_MARK_DONE, 2))
            .addAction(0, "Snooze 15m", broadcast(AlarmScheduler.ACTION_SNOOZE, 3))
            .addAction(0, "Skip", broadcast(AlarmScheduler.ACTION_SKIP, 4))
            .build()
    }

    private fun stopEverything() {
        stopRequested = true
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        autoStopRunnable = null
        AlarmPlayer.stop(applicationContext)
        // Critical for battery: AlarmRingActivity holds FLAG_KEEP_SCREEN_ON, so if it is
        // left open after the alarm stops it keeps the display awake indefinitely.
        runCatching {
            sendBroadcast(
                Intent(AlarmRingActivity.ACTION_STOP_RINGING).setPackage(packageName)
            )
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopRequested = true
        autoStopRunnable?.let { handler.removeCallbacks(it) }
        AlarmPlayer.stop(applicationContext)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        const val NOTIFICATION_ID = 90210
        const val ACTION_START = "com.medisafe.app.ALARM_SERVICE_START"
        const val ACTION_STOP = "com.medisafe.app.ALARM_SERVICE_STOP"

        /**
         * Starts the ringing service.
         *
         * @return true if the service was started. On false the caller must post its own
         * full-screen alarm notification so the user is never left in silence (can happen
         * with ForegroundServiceStartNotAllowedException on locked-down OEM ROMs).
         */
        fun start(context: Context, reminderId: Long): Boolean {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrElse {
                Log.e(TAG, "Could not start AlarmService", it)
                false
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply { action = ACTION_STOP }
            // Prefer a graceful ACTION_STOP, but startService() is illegal from the
            // background on O+ when the service isn't already foreground, so always
            // follow up with stopService() which is safe from anywhere.
            runCatching { context.startService(intent) }
            runCatching { context.stopService(intent) }
            // Belt and braces: kill the audio even if the service was never running.
            AlarmPlayer.stop(context)
        }
    }
}
