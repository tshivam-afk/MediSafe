package com.medisafe.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.prefs.AppPreferences
import com.medisafe.util.DateTimeUtils
import com.medisafe.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_TRIGGER_REMINDER = "com.medisafe.app.ACTION_TRIGGER"
    const val ACTION_MARK_DONE = "com.medisafe.app.ACTION_MARK_DONE"
    const val ACTION_SNOOZE = "com.medisafe.app.ACTION_SNOOZE"
    const val ACTION_SKIP = "com.medisafe.app.ACTION_SKIP"
    const val ACTION_DAILY_RECAP = "com.medisafe.app.ACTION_DAILY_RECAP"
    const val ACTION_WIDGET_REFRESH = "com.medisafe.app.ACTION_WIDGET_REFRESH"
    const val ACTION_WIDGET_TAKE = "com.medisafe.app.ACTION_WIDGET_TAKE"
    const val ACTION_VACATION_END = "com.medisafe.app.ACTION_VACATION_END"
    const val ACTION_ESCALATE = "com.medisafe.app.ACTION_ESCALATE"

    const val EXTRA_ATTEMPT = "extra_attempt"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DETAILS = "extra_details"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_SOUND = "extra_sound"
    const val EXTRA_VIBRATE = "extra_vibrate"
    const val EXTRA_AS_ALARM = "extra_as_alarm"

    private const val TAG = "AlarmScheduler"
    private const val RECAP_REQUEST = 88001
    private const val WIDGET_REQUEST = 88002
    private const val VACATION_REQUEST = 88003
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Background work failed", error)
        }
    )

    fun requestCode(reminderId: Long, salt: Int = 0): Int {
        val mixed = reminderId xor (salt.toLong() shl 16)
        return (mixed and 0x7fffffffL).toInt()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun scheduleReminderAlarm(context: Context, reminder: ReminderItem) {
        if (!reminder.shouldAlert) return
        if (AppPreferences(context).isOnVacation) return
        if (reminder.isCourseOver()) return

        val now = System.currentTimeMillis()
        var triggerTime = reminder.effectiveTriggerTimeMillis
        if (triggerTime < now - 10_000L) {
            if (reminder.recurrenceEnum == RecurrenceType.ONCE) {
                Log.d(TAG, "Skipping one-time alarm already in the past: ${reminder.title}")
                return
            }
            triggerTime = DateTimeUtils.computeNextOccurrence(
                currentScheduledMillis = reminder.scheduledTimeMillis,
                recurrenceType = reminder.recurrenceEnum,
                customIntervalHours = reminder.customIntervalHours,
                fromTimeMillis = now,
                doseTimes = reminder.parsedDoseTimes,
                weekdaysMask = reminder.weekdaysMask
            )
        }
        // A course that ends before the next occurrence is due is finished — never
        // schedule beyond it, whichever path computed the trigger time.
        if (reminder.courseEndMillis != null && triggerTime > reminder.courseEndMillis) return

        setWakeup(context, triggerTime, triggerPendingIntent(context, reminder))
        Log.d(TAG, "Scheduled alarm for '${reminder.title}' at $triggerTime")
    }

    /**
     * @param includeEscalations clearing escalations costs one PendingIntent round-trip per
     * possible attempt, so bulk rescheduling passes false. Rescheduling is not a dismissal:
     * an in-flight escalation should survive the app simply being opened.
     */
    fun cancelReminderAlarm(
        context: Context,
        reminderId: Long,
        includeEscalations: Boolean = true
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        if (includeEscalations) cancelEscalations(context, reminderId)
    }

    fun scheduleDailyRecap(context: Context) {
        val trigger = nextRecapTime()
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_RECAP
        }
        val pending = PendingIntent.getBroadcast(
            context,
            RECAP_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, trigger, pending)
        }
    }

    /**
     * Re-rings an alarm that was never answered. Only applies to items opted into ringing
     * as alarms via the per-reminder switch, and only while Alarm Reliability is on in
     * Settings, so ordinary reminders don't nag.
     */
    fun scheduleEscalation(context: Context, reminder: ReminderItem, attempt: Int) {
        if (!reminder.ringsAsAlarm) return
        val prefs = AppPreferences(context)
        if (!prefs.alarmReliabilityEnabled) return
        val gap = prefs.escalationMinutes
        if (gap <= 0 || attempt > prefs.escalationMaxAttempts) return
        if (prefs.isOnVacation) return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_ESCALATE
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_ATTEMPT, attempt)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(reminder.id, 20 + attempt),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setWakeup(context, System.currentTimeMillis() + gap * 60_000L, pending)
        Log.d(TAG, "Escalation #$attempt queued for '${reminder.title}' in $gap min")
    }

    fun cancelEscalations(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // Only sweep attempts that could actually have been scheduled, rather than the
        // full 1..10 range: each iteration is a binder round-trip to system_server.
        val maxAttempts = AppPreferences(context).escalationMaxAttempts.coerceIn(0, 10)
        if (maxAttempts == 0) return
        for (attempt in 1..maxAttempts) {
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode(reminderId, 20 + attempt),
                Intent(context, ReminderAlarmReceiver::class.java).apply { action = ACTION_ESCALATE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    /** Wakes the app when a vacation/pause window ends so alarms come back automatically. */
    fun scheduleVacationResume(context: Context, untilMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            VACATION_REQUEST,
            Intent(context, ReminderAlarmReceiver::class.java).apply { action = ACTION_VACATION_END },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        if (untilMillis <= System.currentTimeMillis()) return
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, untilMillis + 1_000L, pending)
        }
    }

    fun cancelWidgetRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        val pending = PendingIntent.getBroadcast(
            context,
            WIDGET_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun rescheduleAllActive(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val reminders = AppDatabase.getInstance(appContext).reminderDao().getActiveRemindersSync()
            val vacation = AppPreferences(appContext).isOnVacation
            reminders.filter { !it.isCompleted }.forEach { reminder ->
                // Skip the escalation sweep here: this runs on every launch/boot/time
                // change, and clearing escalations per reminder would cost hundreds of
                // binder calls. A pending re-ring should outlive opening the app anyway.
                cancelReminderAlarm(appContext, reminder.id, includeEscalations = vacation)
                if (!vacation) scheduleReminderAlarm(appContext, reminder)
            }
            scheduleDailyRecap(appContext)
            ReminderAppWidgetProvider.updateAllWidgets(appContext)
            cancelWidgetRefresh(appContext)
        }
    }

    private fun nextRecapTime(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    private fun setWakeup(context: Context, triggerTime: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling alarm", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
        }
    }

    private fun triggerPendingIntent(context: Context, reminder: ReminderItem): PendingIntent {
        // Alarm Reliability must be on in Settings for any reminder to ring as an alarm.
        val ringsAsAlarm = reminder.ringsAsAlarm && AppPreferences(context).alarmReliabilityEnabled
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DETAILS, reminder.dosageOrDetails)
            putExtra(EXTRA_CATEGORY, reminder.category)
            putExtra(EXTRA_SOUND, reminder.notificationSound)
            putExtra(EXTRA_VIBRATE, reminder.vibrate)
            putExtra(EXTRA_AS_ALARM, ringsAsAlarm)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminder.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
