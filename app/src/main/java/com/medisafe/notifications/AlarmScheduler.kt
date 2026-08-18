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
    const val ACTION_DAILY_RECAP = "com.medisafe.app.ACTION_DAILY_RECAP"
    const val ACTION_WIDGET_REFRESH = "com.medisafe.app.ACTION_WIDGET_REFRESH"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DETAILS = "extra_details"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_SOUND = "extra_sound"
    const val EXTRA_VIBRATE = "extra_vibrate"

    private const val TAG = "AlarmScheduler"
    private const val RECAP_REQUEST = 88001
    private const val WIDGET_REQUEST = 88002
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
        if (!reminder.isActive || reminder.isCompleted) return

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
                doseTimes = reminder.parsedDoseTimes
            )
        }

        setWakeup(context, triggerTime, triggerPendingIntent(context, reminder))
        Log.d(TAG, "Scheduled alarm for '${reminder.title}' at $triggerTime")
        scheduleWidgetRefresh(context, minOf(triggerTime, now + 60_000L))
    }

    fun cancelReminderAlarm(context: Context, reminderId: Long) {
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
        setWakeup(context, trigger, pending)
    }

    fun scheduleWidgetRefresh(context: Context, atMillis: Long = System.currentTimeMillis() + 60_000L) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        val pending = PendingIntent.getBroadcast(
            context,
            WIDGET_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setWakeup(context, atMillis.coerceAtLeast(System.currentTimeMillis() + 15_000L), pending)
    }

    fun rescheduleAllActive(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val reminders = AppDatabase.getInstance(appContext).reminderDao().getActiveRemindersSync()
            reminders.filter { !it.isCompleted }.forEach { reminder ->
                scheduleReminderAlarm(appContext, reminder)
            }
            scheduleDailyRecap(appContext)
            ReminderAppWidgetProvider.updateAllWidgets(appContext)
            scheduleWidgetRefresh(appContext)
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
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DETAILS, reminder.dosageOrDetails)
            putExtra(EXTRA_CATEGORY, reminder.category)
            putExtra(EXTRA_SOUND, reminder.notificationSound)
            putExtra(EXTRA_VIBRATE, reminder.vibrate)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(reminder.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
