package com.medisafe.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.medisafe.MainActivity
import com.medisafe.R
import com.medisafe.data.model.ReminderItem

object NotificationHelper {
    const val CHANNEL_ID = "channel_med_task_reminders"
    const val CHANNEL_NAME = "Medication & Task Reminders"
    const val CHANNEL_STATUS_ID = "channel_med_status"
    const val CHANNEL_STATUS_NAME = "Refills & daily recap"
    const val CHANNEL_ALARM_ID = "channel_med_alarms"
    const val CHANNEL_ALARM_NAME = "Alarm-style reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .build()

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts for medication times, daily tasks, and upcoming events"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    setSound(soundUri, audioAttributes)
                    setShowBadge(true)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ALARM_ID) == null) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val alarmAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALARM_ID, CHANNEL_ALARM_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Full-screen looping alarm for selected medications, tasks, and events"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 700, 400, 700)
                    setSound(alarmUri, alarmAttrs)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_STATUS_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS_ID, CHANNEL_STATUS_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Low-pill refill alerts and the evening recap"
                    setShowBadge(true)
                }
            )
        }
    }

    fun showRefillNotification(context: Context, reminder: ReminderItem) {
        ensureChannel(context)
        val remaining = reminder.pillsRemaining ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val open = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode(reminder.id, 8),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderAlarmReceiver.EXTRA_TARGET_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Refill ${reminder.title}")
            .setContentText("Only $remaining left. Time to refill.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(AlarmScheduler.requestCode(reminder.id, 9), notification)
    }

    fun showDailyRecap(context: Context, taken: Int, missed: Int, skipped: Int) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val open = PendingIntent.getActivity(
            context,
            7101,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = "Taken $taken · Missed $missed · Skipped $skipped"
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today's MediSafe recap")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(7102, notification)
    }
}
