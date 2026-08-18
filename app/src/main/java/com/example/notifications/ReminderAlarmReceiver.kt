package com.example.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.LogAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderLog
import com.example.util.DateTimeUtils
import com.example.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_TRIGGER_REMINDER -> handleTriggerReminder(context, reminderId, intent)
                    AlarmScheduler.ACTION_MARK_DONE -> handleMarkDone(context, reminderId)
                    AlarmScheduler.ACTION_SNOOZE -> handleSnooze(context, reminderId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle $action for $reminderId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTriggerReminder(context: Context, reminderId: Long, intent: Intent) {
        val dbItem = AppDatabase.getInstance(context).reminderDao().getReminderById(reminderId)
        if (dbItem != null && (!dbItem.isActive || dbItem.isCompleted)) return

        val title = dbItem?.title ?: intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Reminder"
        val details = dbItem?.dosageOrDetails ?: intent.getStringExtra(AlarmScheduler.EXTRA_DETAILS).orEmpty()
        val category = dbItem?.category
            ?: intent.getStringExtra(AlarmScheduler.EXTRA_CATEGORY)
            ?: ReminderCategory.MEDICATION.name
        val hasSound = dbItem?.notificationSound ?: intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND, true)
        val hasVibrate = dbItem?.vibrate ?: intent.getBooleanExtra(AlarmScheduler.EXTRA_VIBRATE, true)

        NotificationHelper.ensureChannel(context)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TARGET_REMINDER_ID, reminderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(reminderId, 1),
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = AlarmScheduler.ACTION_MARK_DONE
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(reminderId, 2),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(reminderId, 3),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMed = category == ReminderCategory.MEDICATION.name
        val actionDoneTitle = if (isMed) "Take now" else "Mark done"
        val headerTitle = when (category) {
            ReminderCategory.MEDICATION.name -> "Time for: $title"
            ReminderCategory.DAILY_TASK.name -> "Task: $title"
            ReminderCategory.EVENT.name -> "Event: $title"
            else -> "Reminder: $title"
        }
        val contentText = details.ifBlank { "It's time for your scheduled reminder." }

        val notificationBuilder = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headerTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\nScheduled for ${DateTimeUtils.formatTime(System.currentTimeMillis())}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, actionDoneTitle, donePendingIntent)
            .addAction(0, "Snooze 15m", snoozePendingIntent)

        if (hasVibrate) {
            notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
        }
        if (hasSound) {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        }

        notificationManager.notify(AlarmScheduler.requestCode(reminderId), notificationBuilder.build())

        if (dbItem != null && dbItem.recurrenceEnum != RecurrenceType.ONCE && dbItem.snoozedUntilMillis == null) {
            AlarmScheduler.scheduleReminderAlarm(context, dbItem)
        }
        ReminderAppWidgetProvider.updateAllWidgets(context)
    }

    private suspend fun handleMarkDone(context: Context, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AlarmScheduler.requestCode(reminderId))

        val db = AppDatabase.getInstance(context)
        val item = db.reminderDao().getReminderById(reminderId) ?: return

        val isMed = item.categoryEnum == ReminderCategory.MEDICATION
        val logAction = if (isMed) LogAction.TAKEN.name else LogAction.COMPLETED.name

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
            db.reminderDao().updateReminder(
                item.copy(
                    isCompleted = true,
                    completedAtMillis = System.currentTimeMillis(),
                    snoozedUntilMillis = null
                )
            )
            AlarmScheduler.cancelReminderAlarm(context, item.id)
        } else {
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
    }

    private suspend fun handleSnooze(context: Context, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AlarmScheduler.requestCode(reminderId))

        val snoozeTimeMillis = System.currentTimeMillis() + (15 * 60 * 1000)
        val db = AppDatabase.getInstance(context)
        val item = db.reminderDao().getReminderById(reminderId) ?: return

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
    }

    companion object {
        const val EXTRA_TARGET_REMINDER_ID = "EXTRA_TARGET_REMINDER_ID"
        private const val TAG = "ReminderAlarmReceiver"
    }
}
