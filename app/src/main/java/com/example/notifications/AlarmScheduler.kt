package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.ReminderItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AlarmScheduler {

    const val ACTION_TRIGGER_REMINDER = "com.example.medtaskreminder.ACTION_TRIGGER"
    const val ACTION_MARK_DONE = "com.example.medtaskreminder.ACTION_MARK_DONE"
    const val ACTION_SNOOZE = "com.example.medtaskreminder.ACTION_SNOOZE"

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_DETAILS = "extra_details"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_SOUND = "extra_sound"
    const val EXTRA_VIBRATE = "extra_vibrate"

    fun scheduleReminderAlarm(context: Context, reminder: ReminderItem) {
        if (!reminder.isActive) return

        val triggerTime = reminder.effectiveTriggerTimeMillis
        // If trigger time is in the past (more than 10 seconds ago), do not trigger old alarm
        if (triggerTime < System.currentTimeMillis() - 10000) {
            Log.d("AlarmScheduler", "Skipping alarm in the past: ${reminder.title} at $triggerTime")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DETAILS, reminder.dosageOrDetails)
            putExtra(EXTRA_CATEGORY, reminder.category)
            putExtra(EXTRA_SOUND, reminder.notificationSound)
            putExtra(EXTRA_VIBRATE, reminder.vibrate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("AlarmScheduler", "Scheduled alarm for '${reminder.title}' at $triggerTime")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Security exception scheduling alarm", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e("AlarmScheduler", "Fallback alarm scheduling failed", ex)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling alarm", e)
        }
    }

    fun cancelReminderAlarm(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAllActive(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val reminders = db.reminderDao().getActiveRemindersSync()
                for (reminder in reminders) {
                    if (!reminder.isCompleted) {
                        scheduleReminderAlarm(context, reminder)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmScheduler", "Error in rescheduleAllActive", e)
            }
        }
    }
}
