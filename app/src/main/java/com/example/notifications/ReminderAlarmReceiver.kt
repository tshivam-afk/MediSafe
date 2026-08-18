package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.LogAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderLog
import com.example.util.DateTimeUtils
import com.example.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "channel_med_task_reminders"
        const val CHANNEL_NAME = "Medication & Task Reminders"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        Log.d("ReminderAlarmReceiver", "Received action: $action for reminderId: $reminderId")

        when (action) {
            AlarmScheduler.ACTION_TRIGGER_REMINDER -> {
                handleTriggerReminder(context, reminderId, intent)
            }
            AlarmScheduler.ACTION_MARK_DONE -> {
                handleMarkDone(context, reminderId)
            }
            AlarmScheduler.ACTION_SNOOZE -> {
                handleSnooze(context, reminderId)
            }
        }
    }

    private fun handleTriggerReminder(context: Context, reminderId: Long, intent: Intent) {
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Reminder"
        val details = intent.getStringExtra(AlarmScheduler.EXTRA_DETAILS) ?: ""
        val category = intent.getStringExtra(AlarmScheduler.EXTRA_CATEGORY) ?: ReminderCategory.MEDICATION.name
        val hasSound = intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND, true)
        val hasVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)

        createNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to launch MainActivity when tapped
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TARGET_REMINDER_ID", reminderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Mark Done / Taken
        val doneIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = AlarmScheduler.ACTION_MARK_DONE
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + 100000).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Snooze 15 min
        val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + 200000).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMed = category == ReminderCategory.MEDICATION.name
        val actionDoneTitle = if (isMed) "💊 Take Pill" else "✓ Mark Done"
        val headerTitle = when (category) {
            ReminderCategory.MEDICATION.name -> "💊 Time for: $title"
            ReminderCategory.DAILY_TASK.name -> "📋 Task: $title"
            ReminderCategory.EVENT.name -> "🗓️ Event: $title"
            else -> "⭐ Reminder: $title"
        }

        val contentText = if (details.isNotBlank()) details else "It's time for your scheduled reminder!"

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(headerTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\nScheduled for ${DateTimeUtils.formatTime(System.currentTimeMillis())}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_agenda, actionDoneTitle, donePendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "⏰ Snooze 15m", snoozePendingIntent)

        if (hasVibrate) {
            notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
        }

        if (hasSound) {
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            notificationBuilder.setSound(alarmSound)
        }

        notificationManager.notify(reminderId.toInt(), notificationBuilder.build())

        // Update database if it's recurring: prepare next occurrence if needed
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val item = db.reminderDao().getReminderById(reminderId)
                if (item != null && item.recurrenceEnum != RecurrenceType.ONCE) {
                    // For recurring item, if user hasn't marked done, we keep next occurrence queued
                    ReminderAppWidgetProvider.updateAllWidgets(context)
                }
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error updating recurring reminder on trigger", e)
            }
        }
    }

    private fun handleMarkDone(context: Context, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId.toInt())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val item = db.reminderDao().getReminderById(reminderId) ?: return@launch

                val isMed = item.categoryEnum == ReminderCategory.MEDICATION
                val logAction = if (isMed) LogAction.TAKEN.name else LogAction.COMPLETED.name

                // Add log
                db.reminderDao().insertLog(
                    ReminderLog(
                        reminderId = item.id,
                        reminderTitle = item.title,
                        category = item.category,
                        action = logAction,
                        timestampMillis = System.currentTimeMillis(),
                        note = "Marked $logAction via notification"
                    )
                )

                if (item.recurrenceEnum == RecurrenceType.ONCE) {
                    // Mark completed
                    db.reminderDao().updateReminder(
                        item.copy(
                            isCompleted = true,
                            completedAtMillis = System.currentTimeMillis(),
                            snoozedUntilMillis = null
                        )
                    )
                } else {
                    // Recurring: calculate next scheduled occurrence
                    val nextOccurrence = DateTimeUtils.computeNextOccurrence(
                        currentScheduledMillis = item.scheduledTimeMillis,
                        recurrenceType = item.recurrenceEnum,
                        customIntervalHours = item.customIntervalHours,
                        fromTimeMillis = System.currentTimeMillis()
                    )
                    val updated = item.copy(
                        scheduledTimeMillis = nextOccurrence,
                        isCompleted = false,
                        completedAtMillis = null,
                        snoozedUntilMillis = null
                    )
                    db.reminderDao().updateReminder(updated)
                    AlarmScheduler.scheduleReminderAlarm(context, updated)
                }

                ReminderAppWidgetProvider.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error handling mark done", e)
            }
        }
    }

    private fun handleSnooze(context: Context, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(reminderId.toInt())

        val snoozeTimeMillis = System.currentTimeMillis() + (15 * 60 * 1000)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val item = db.reminderDao().getReminderById(reminderId) ?: return@launch

                val updated = item.copy(snoozedUntilMillis = snoozeTimeMillis)
                db.reminderDao().updateReminder(updated)

                db.reminderDao().insertLog(
                    ReminderLog(
                        reminderId = item.id,
                        reminderTitle = item.title,
                        category = item.category,
                        action = LogAction.SNOOZED.name,
                        timestampMillis = System.currentTimeMillis(),
                        note = "Snoozed for 15 minutes"
                    )
                )

                AlarmScheduler.scheduleReminderAlarm(context, updated)
                ReminderAppWidgetProvider.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e("ReminderAlarmReceiver", "Error handling snooze", e)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()

                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for medication times, daily tasks, and upcoming events"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
