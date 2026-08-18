package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderItem
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AlarmScheduler {

    const val ACTION_TRIGGER_REMINDER = "com.medisafe.app.ACTION_TRIGGER"
    const val ACTION_MARK_DONE = "com.medisafe.app.ACTION_MARK_DONE"
    const val ACTION_SNOOZE = "com.medisafe.app.ACTION_SNOOZE"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DETAILS = "extra_details"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_SOUND = "extra_sound"
    const val EXTRA_VIBRATE = "extra_vibrate"

    private const val TAG = "AlarmScheduler"
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
                fromTimeMillis = now
            )
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = triggerPendingIntent(context, reminder)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm for '${reminder.title}' at $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception scheduling alarm", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback alarm scheduling failed", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
        }
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

    fun rescheduleAllActive(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val reminders = AppDatabase.getInstance(appContext).reminderDao().getActiveRemindersSync()
            reminders.filter { !it.isCompleted }.forEach { reminder ->
                scheduleReminderAlarm(appContext, reminder)
            }
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
