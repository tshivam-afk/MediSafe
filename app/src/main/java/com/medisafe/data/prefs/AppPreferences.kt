package com.medisafe.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    var installedReleaseTag: String?
        get() = prefs.getString(KEY_INSTALLED_RELEASE, null)
        set(value) {
            prefs.edit().putString(KEY_INSTALLED_RELEASE, value).apply()
        }

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    var vacationUntilMillis: Long
        get() = prefs.getLong(KEY_VACATION_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_VACATION_UNTIL, value).apply()

    val isOnVacation: Boolean
        get() = vacationUntilMillis > System.currentTimeMillis()

    var homeSort: String
        get() = prefs.getString(KEY_HOME_SORT, "NEXT_DUE") ?: "NEXT_DUE"
        set(value) = prefs.edit().putString(KEY_HOME_SORT, value).apply()

    /**
     * Master switch for the whole alarm-reliability feature. While off, reminders only
     * notify normally (no full-screen ringing alarms, no re-ring escalation) and the
     * per-reminder "Ring like an alarm" option stays unavailable. Off by default.
     */
    var alarmReliabilityEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALARM_RELIABILITY, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_RELIABILITY, value).apply()

    /** Minutes an unanswered alarm keeps ringing before it gives up and escalates. */
    var alarmTimeoutMinutes: Int
        get() = prefs.getInt(KEY_ALARM_TIMEOUT, 5).coerceIn(1, 30)
        set(value) = prefs.edit().putInt(KEY_ALARM_TIMEOUT, value.coerceIn(1, 30)).apply()

    /** Re-ring an unanswered alarm after this many minutes. 0 (off) by default. */
    var escalationMinutes: Int
        get() = prefs.getInt(KEY_ESCALATION_MINUTES, 0).coerceIn(0, 120)
        set(value) = prefs.edit().putInt(KEY_ESCALATION_MINUTES, value.coerceIn(0, 120)).apply()

    /** How many times an unanswered alarm re-rings before it stops nagging. */
    var escalationMaxAttempts: Int
        get() = prefs.getInt(KEY_ESCALATION_ATTEMPTS, 3).coerceIn(0, 10)
        set(value) = prefs.edit().putInt(KEY_ESCALATION_ATTEMPTS, value.coerceIn(0, 10)).apply()

    /** Temporarily raise the alarm stream to full volume so a silenced phone still wakes you. */
    var forceAlarmVolume: Boolean
        get() = prefs.getBoolean(KEY_FORCE_ALARM_VOLUME, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_ALARM_VOLUME, value).apply()

    /** Fade the alarm in over a few seconds instead of blasting at full volume instantly. */
    var gradualAlarmVolume: Boolean
        get() = prefs.getBoolean(KEY_GRADUAL_ALARM_VOLUME, false)
        set(value) = prefs.edit().putBoolean(KEY_GRADUAL_ALARM_VOLUME, value).apply()

    val hasPin: Boolean
        get() = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.toHex())
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        return hashPin(pin, saltHex.fromHex()) == stored
    }

    companion object {
        private const val PREFS_NAME = "medisafe_prefs"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_INSTALLED_RELEASE = "installed_release_tag"
        private const val KEY_AUTO_UPDATE = "auto_update_enabled"
        private const val KEY_VACATION_UNTIL = "vacation_until_millis"
        private const val KEY_HOME_SORT = "home_sort"
        private const val KEY_ALARM_RELIABILITY = "alarm_reliability_enabled"
        private const val KEY_ALARM_TIMEOUT = "alarm_timeout_minutes"
        private const val KEY_ESCALATION_MINUTES = "alarm_escalation_minutes"
        private const val KEY_ESCALATION_ATTEMPTS = "alarm_escalation_attempts"
        private const val KEY_FORCE_ALARM_VOLUME = "force_alarm_volume"
        private const val KEY_GRADUAL_ALARM_VOLUME = "gradual_alarm_volume"

        private fun hashPin(pin: String, salt: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            return digest.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray {
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
