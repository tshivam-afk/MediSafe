package com.medisafe.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.medisafe.MainActivity
import com.medisafe.R
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.LogAction
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.prefs.AppPreferences
import com.medisafe.data.repository.ReminderRepository
import com.medisafe.util.DateTimeUtils
import com.medisafe.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_DAILY_RECAP -> handleDailyRecap(context)
                    AlarmScheduler.ACTION_WIDGET_REFRESH -> handleWidgetRefresh(context)
                    AlarmScheduler.ACTION_VACATION_END -> AlarmScheduler.rescheduleAllActive(context)
                    else -> {
                        val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1L)
                        if (reminderId == -1L) return@launch
                        when (action) {
                            AlarmScheduler.ACTION_TRIGGER_REMINDER -> handleTriggerReminder(context, reminderId, intent)
                            AlarmScheduler.ACTION_ESCALATE -> handleEscalate(context, reminderId, intent)
                            AlarmScheduler.ACTION_MARK_DONE, AlarmScheduler.ACTION_WIDGET_TAKE -> handleMarkDone(context, reminderId)
                            AlarmScheduler.ACTION_SNOOZE -> handleSnooze(context, reminderId)
                            AlarmScheduler.ACTION_SKIP -> handleSkip(context, reminderId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTriggerReminder(context: Context, reminderId: Long, intent: Intent) {
        val repository = repository(context)
        repository.scanAndLogMissed()
        val dbItem = repository.getReminderById(reminderId)
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

        val skipIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = AlarmScheduler.ACTION_SKIP
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode(reminderId, 4),
            skipIntent,
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
        val food = dbItem?.foodTimingEnum?.displayName
        val sticky = dbItem?.isStickyAlert() == true
        // High/urgent items ring as alarms even without the per-item switch.
        val asAlarm = dbItem?.ringsAsAlarm ?: intent.getBooleanExtra(AlarmScheduler.EXTRA_AS_ALARM, false)
        val contentText = buildString {
            val dose = dbItem?.doseLabel?.ifBlank { details } ?: details
            append(dose.ifBlank { "It's time for your scheduled reminder." })
            if (!food.isNullOrBlank() && food != "No food rule") {
                append(" · ")
                append(food)
            }
        }

        val alarmScreen = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val alarmPending = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(reminderId, 5),
            alarmScreen,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channel = if (asAlarm) NotificationHelper.CHANNEL_ALARM_ID else NotificationHelper.CHANNEL_ID
        val notificationBuilder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(headerTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\nScheduled for ${DateTimeUtils.formatTime(System.currentTimeMillis())}")
            )
            .setPriority(if (asAlarm || sticky) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (asAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(!sticky && !asAlarm)
            .setOngoing(sticky || asAlarm)
            .setOnlyAlertOnce(!asAlarm)
            .setContentIntent(if (asAlarm) alarmPending else contentPendingIntent)
            .addAction(0, actionDoneTitle, donePendingIntent)
            .addAction(0, "Snooze 15m", snoozePendingIntent)
            .addAction(0, "Skip", skipPendingIntent)

        if (asAlarm) {
            notificationBuilder.setFullScreenIntent(alarmPending, true)
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            notificationBuilder.setVibrate(longArrayOf(0, 700, 400, 700, 400))
        } else {
            if (hasVibrate) {
                notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
            }
            if (hasSound) {
                notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
        }

        if (asAlarm) {
            // Hand the ringing to a foreground service. It owns the audio, the wake lock and
            // the full-screen intent, so the alarm is heard even with the screen off — a
            // background startActivity() here would simply be blocked on Android 10+.
            val started = AlarmService.start(context, reminderId)
            if (!started) {
                // Service refused: fall back to the alarm-channel notification, which still
                // carries a full-screen intent and its own alarm sound, so we never go silent.
                notificationManager.notify(AlarmScheduler.requestCode(reminderId), notificationBuilder.build())
            }
            // Even when the alarm rings, queue a re-ring in case it goes unanswered.
            if (dbItem != null) {
                AlarmScheduler.scheduleEscalation(context, dbItem, attempt = 1)
            }
        } else {
            notificationManager.notify(AlarmScheduler.requestCode(reminderId), notificationBuilder.build())
        }

        if (dbItem != null && dbItem.recurrenceEnum != RecurrenceType.ONCE && dbItem.snoozedUntilMillis == null) {
            AlarmScheduler.scheduleReminderAlarm(context, dbItem)
        }
        ReminderAppWidgetProvider.updateAllWidgets(context)
    }

    /**
     * Fires when a high/urgent alarm timed out unanswered: ring again, then queue the
     * next attempt until we run out of retries.
     */
    private suspend fun handleEscalate(context: Context, reminderId: Long, intent: Intent) {
        val attempt = intent.getIntExtra(AlarmScheduler.EXTRA_ATTEMPT, 1)
        val item = repository(context).getReminderById(reminderId) ?: return
        if (!item.isActive || item.isCompleted || !item.ringsAsAlarm) return
        // Already dealt with since the alarm fired? Then stop nagging.
        val lastAck = item.lastAcknowledgedMillis ?: 0L
        if (lastAck >= item.scheduledTimeMillis) return
        if (AppPreferences(context).isOnVacation) return

        AlarmService.start(context, reminderId)
        AlarmScheduler.scheduleEscalation(context, item, attempt + 1)
    }

    private suspend fun handleMarkDone(context: Context, reminderId: Long) {
        stopRinging(context, reminderId)
        val item = repository(context).getReminderById(reminderId) ?: return
        repository(context).markDoneOrTaken(item)
    }

    private suspend fun handleSnooze(context: Context, reminderId: Long) {
        stopRinging(context, reminderId)
        val item = repository(context).getReminderById(reminderId) ?: return
        repository(context).snoozeReminder(item, 15)
    }

    private suspend fun handleSkip(context: Context, reminderId: Long) {
        stopRinging(context, reminderId)
        val item = repository(context).getReminderById(reminderId) ?: return
        repository(context).skipReminder(item)
    }

    private fun stopRinging(context: Context, reminderId: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AlarmScheduler.requestCode(reminderId))
        notificationManager.cancel(AlarmService.NOTIFICATION_ID)
        // Stop the service (which owns the tone/wake lock) and drop any queued re-rings.
        AlarmService.stop(context)
        AlarmPlayer.stop(context)
        AlarmScheduler.cancelEscalations(context, reminderId)
        runCatching {
            context.sendBroadcast(
                Intent(AlarmRingActivity.ACTION_STOP_RINGING).setPackage(context.packageName)
            )
        }
    }

    private suspend fun handleDailyRecap(context: Context) {
        val repository = repository(context)
        repository.scanAndLogMissed()
        val logs = repository.getAllLogsSync()
        val today = logs.filter { DateTimeUtils.isToday(it.timestampMillis) }
        val taken = today.count { it.actionEnum == LogAction.TAKEN || it.actionEnum == LogAction.COMPLETED }
        val missed = today.count { it.actionEnum == LogAction.MISSED }
        val skipped = today.count { it.actionEnum == LogAction.SKIPPED }
        if (taken + missed + skipped > 0) {
            NotificationHelper.showDailyRecap(context, taken, missed, skipped)
        }
        AlarmScheduler.scheduleDailyRecap(context)
    }

    private fun handleWidgetRefresh(context: Context) {
        ReminderAppWidgetProvider.updateAllWidgets(context)
    }

    private fun repository(context: Context): ReminderRepository {
        return ReminderRepository(AppDatabase.getInstance(context).reminderDao(), context.applicationContext)
    }

    companion object {
        const val EXTRA_TARGET_REMINDER_ID = "EXTRA_TARGET_REMINDER_ID"
        const val EXTRA_OPEN_CREATE = "EXTRA_OPEN_CREATE"
        const val EXTRA_OPEN_NEXT = "EXTRA_OPEN_NEXT"
        private const val TAG = "ReminderAlarmReceiver"
    }
}
