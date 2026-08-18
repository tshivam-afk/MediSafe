package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.database.AppDatabase
import com.example.data.model.ReminderCategory
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ReminderAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, ReminderAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.reminder_widget_layout)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val activeReminders = db.reminderDao().getActiveRemindersSync()
                        .filter { !it.isCompleted }
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
                                if (nextReminder.dosageOrDetails.isNotBlank()) nextReminder.dosageOrDetails else "Scheduled reminder"
                            )

                            val countdown = DateTimeUtils.getCountdown(nextReminder.effectiveTriggerTimeMillis)
                            val timeStr = DateTimeUtils.getRelativeTimeLabel(nextReminder.effectiveTriggerTimeMillis)

                            views.setTextViewText(R.id.widget_badge_time, if (countdown.isDue) "DUE NOW" else countdown.formattedString)
                            views.setTextViewText(R.id.widget_time_label, timeStr)
                        } else {
                            views.setTextViewText(R.id.widget_reminder_title, "All Caught Up! 🎉")
                            views.setTextViewText(R.id.widget_reminder_details, "No pending pills or tasks today")
                            views.setTextViewText(R.id.widget_badge_time, "Relax")
                            views.setTextViewText(R.id.widget_time_label, "Tap to create a new reminder")
                        }

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    Log.e("ReminderWidget", "Error updating widget", e)
                }
            }
        }
    }
}
