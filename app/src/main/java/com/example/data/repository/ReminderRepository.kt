package com.example.data.repository

import android.content.Context
import com.example.data.dao.ReminderDao
import com.example.data.model.LogAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderLog
import com.example.notifications.AlarmScheduler
import com.example.util.DateTimeUtils
import com.example.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val appContext: Context
) {
    val allReminders: Flow<List<ReminderItem>> = reminderDao.getAllReminders()
    val allLogs: Flow<List<ReminderLog>> = reminderDao.getAllLogs()

    suspend fun getReminderById(id: Long): ReminderItem? = reminderDao.getReminderById(id)

    suspend fun saveReminder(reminder: ReminderItem): Long {
        val id = reminderDao.insertReminder(reminder)
        val saved = reminder.copy(id = if (reminder.id == 0L) id else reminder.id)
        if (saved.isActive && !saved.isCompleted) {
            AlarmScheduler.scheduleReminderAlarm(appContext, saved)
        } else {
            AlarmScheduler.cancelReminderAlarm(appContext, saved.id)
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return saved.id
    }

    suspend fun updateReminder(reminder: ReminderItem) {
        reminderDao.updateReminder(reminder)
        if (reminder.isActive && !reminder.isCompleted) {
            AlarmScheduler.scheduleReminderAlarm(appContext, reminder)
        } else {
            AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun deleteReminder(reminder: ReminderItem) {
        AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        reminderDao.deleteLogsForReminder(reminder.id)
        reminderDao.deleteReminder(reminder)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun markDoneOrTaken(reminder: ReminderItem) {
        val isMed = reminder.categoryEnum == ReminderCategory.MEDICATION
        val action = if (isMed) LogAction.TAKEN else LogAction.COMPLETED

        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = action.name,
                timestampMillis = System.currentTimeMillis(),
                note = "Marked ${action.displayName}"
            )
        )

        if (reminder.recurrenceEnum == RecurrenceType.ONCE) {
            val updated = reminder.copy(
                isCompleted = true,
                completedAtMillis = System.currentTimeMillis(),
                snoozedUntilMillis = null
            )
            reminderDao.updateReminder(updated)
            AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        } else {
            val nextOccurrence = DateTimeUtils.computeNextOccurrence(
                currentScheduledMillis = reminder.scheduledTimeMillis,
                recurrenceType = reminder.recurrenceEnum,
                customIntervalHours = reminder.customIntervalHours,
                fromTimeMillis = System.currentTimeMillis()
            )
            val updated = reminder.copy(
                scheduledTimeMillis = nextOccurrence,
                isCompleted = false,
                completedAtMillis = null,
                snoozedUntilMillis = null
            )
            reminderDao.updateReminder(updated)
            AlarmScheduler.scheduleReminderAlarm(appContext, updated)
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun snoozeReminder(reminder: ReminderItem, minutes: Int = 15) {
        val safeMinutes = minutes.coerceIn(1, 24 * 60)
        val snoozeTime = System.currentTimeMillis() + (safeMinutes * 60 * 1000L)
        val updated = reminder.copy(snoozedUntilMillis = snoozeTime)
        reminderDao.updateReminder(updated)

        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = LogAction.SNOOZED.name,
                timestampMillis = System.currentTimeMillis(),
                note = "Snoozed for $safeMinutes minutes"
            )
        )

        AlarmScheduler.scheduleReminderAlarm(appContext, updated)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun skipReminder(reminder: ReminderItem) {
        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = LogAction.SKIPPED.name,
                timestampMillis = System.currentTimeMillis(),
                note = "Skipped scheduled dose/task"
            )
        )

        if (reminder.recurrenceEnum == RecurrenceType.ONCE) {
            val updated = reminder.copy(
                isCompleted = true,
                completedAtMillis = System.currentTimeMillis(),
                snoozedUntilMillis = null
            )
            reminderDao.updateReminder(updated)
            AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        } else {
            val nextOccurrence = DateTimeUtils.computeNextOccurrence(
                currentScheduledMillis = reminder.scheduledTimeMillis,
                recurrenceType = reminder.recurrenceEnum,
                customIntervalHours = reminder.customIntervalHours,
                fromTimeMillis = System.currentTimeMillis()
            )
            val updated = reminder.copy(
                scheduledTimeMillis = nextOccurrence,
                isCompleted = false,
                completedAtMillis = null,
                snoozedUntilMillis = null
            )
            reminderDao.updateReminder(updated)
            AlarmScheduler.scheduleReminderAlarm(appContext, updated)
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun clearLogs() {
        reminderDao.clearLogs()
    }

    suspend fun deleteLogById(logId: Long) {
        reminderDao.deleteLogById(logId)
    }
}
