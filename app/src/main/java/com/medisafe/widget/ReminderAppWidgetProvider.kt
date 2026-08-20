package com.medisafe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.view.View
import com.medisafe.MainActivity
import com.medisafe.R
import com.medisafe.data.database.AppDatabase
import com.medisafe.data.model.ReminderCategory
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.ReminderAlarmReceiver
import com.medisafe.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        com.medisafe.notifications.AlarmScheduler.cancelWidgetRefresh(context)
    }

    companion object {
        private const val TAG = "ReminderWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, ReminderAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(appContext, appWidgetManager, appWidgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.reminder_widget_layout)
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            scope.launch {
                try {
                    val activeReminders = AppDatabase.getInstance(context).reminderDao()
                        .getActiveRemindersSync()
                        .filter { !it.isCompleted && !it.isPrn }
                        .sortedBy { it.effectiveTriggerTimeMillis }
                    val nextReminder = activeReminders.firstOrNull()

                    withContext(Dispatchers.Main) {
                        if (nextReminder != null) {
                            val emoji = when (nextReminder.categoryEnum) {
                                ReminderCategory.MEDICATION -> "💊"
                                ReminderCategory.DAILY_TASK -> "📋"
                                ReminderCategory.EVENT -> "🗓️"
                                else -> "⭐"
                            }
                            views.setTextViewText(R.id.widget_reminder_title, "$emoji ${nextReminder.title}")
                            views.setTextViewText(
                                R.id.widget_reminder_details,
                                nextReminder.dosageOrDetails.ifBlank { "Scheduled reminder" }
                            )
                            val countdown = DateTimeUtils.getCountdown(nextReminder.effectiveTriggerTimeMillis)
                            views.setTextViewText(
                                R.id.widget_badge_time,
                                if (countdown.isDue) "DUE NOW" else countdown.formattedString
                            )
                            views.setTextViewText(
                                R.id.widget_time_label,
                                DateTimeUtils.getRelativeTimeLabel(nextReminder.effectiveTriggerTimeMillis)
                            )
                        } else {
                            views.setTextViewText(R.id.widget_reminder_title, "All caught up")
                            views.setTextViewText(R.id.widget_reminder_details, "No pending pills or tasks")
                            views.setTextViewText(R.id.widget_badge_time, "Relax")
                            views.setTextViewText(R.id.widget_time_label, "Tap to create a reminder")
                        }
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget", e)
                }
            }
        }
    }
}
