package com.example.data.repository

import android.content.Context
import com.example.data.dao.ReminderDao
import com.example.data.model.LogAction
import com.example.data.model.Priority
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderCategory
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderLog
import com.example.notifications.AlarmScheduler
import com.example.util.DateTimeUtils
import com.example.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val appContext: Context
) {
    val allReminders: Flow<List<ReminderItem>> = reminderDao.getAllReminders()
    val activeReminders: Flow<List<ReminderItem>> = reminderDao.getActiveReminders()
    val allLogs: Flow<List<ReminderLog>> = reminderDao.getAllLogs()

    suspend fun getReminderById(id: Long): ReminderItem? = reminderDao.getReminderById(id)

    fun getLogsForReminder(reminderId: Long): Flow<List<ReminderLog>> =
        reminderDao.getLogsForReminder(reminderId)

    suspend fun saveReminder(reminder: ReminderItem): Long {
        val id = reminderDao.insertReminder(reminder)
        val saved = reminder.copy(id = if (reminder.id == 0L) id else reminder.id)
        if (saved.isActive && !saved.isCompleted) {
            AlarmScheduler.scheduleReminderAlarm(appContext, saved)
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return id
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
        reminderDao.deleteReminder(reminder)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun deleteReminderById(id: Long) {
        AlarmScheduler.cancelReminderAlarm(appContext, id)
        reminderDao.deleteReminderById(id)
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
        val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000)
        val updated = reminder.copy(snoozedUntilMillis = snoozeTime)
        reminderDao.updateReminder(updated)

        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = LogAction.SNOOZED.name,
                timestampMillis = System.currentTimeMillis(),
                note = "Snoozed for $minutes minutes"
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

    suspend fun seedInitialDataIfEmpty() {
        val existing = reminderDao.getActiveRemindersSync()
        if (existing.isEmpty()) {
            val now = Calendar.getInstance()

            // Sample 1: Vitamin D3 (Medication - Today at 8:00 PM or in 1 hour)
            val medCal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val sampleMed = ReminderItem(
                title = "Vitamin D3 + Omega-3",
                category = ReminderCategory.MEDICATION.name,
                dosageOrDetails = "1 softgel capsule with water after meal",
                scheduledTimeMillis = medCal.timeInMillis,
                recurrence = RecurrenceType.DAILY.name,
                priority = Priority.NORMAL.name,
                iconName = "capsule",
                colorHex = 0xFF0D9488
            )

            // Sample 2: Daily Hydration / Walk (Daily Task - in 3 hours)
            val taskCal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
            }
            val sampleTask = ReminderItem(
                title = "Hydration & Posture Break",
                category = ReminderCategory.DAILY_TASK.name,
                dosageOrDetails = "Drink 500ml water and 5-min stretch",
                scheduledTimeMillis = taskCal.timeInMillis,
                recurrence = RecurrenceType.DAILY.name,
                priority = Priority.LOW.name,
                iconName = "water",
                colorHex = 0xFF0284C7
            )

            // Sample 3: Upcoming Event (Event - tomorrow)
            val eventCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val sampleEvent = ReminderItem(
                title = "Dentist Checkup & Cleaning",
                category = ReminderCategory.EVENT.name,
                dosageOrDetails = "City Dental Clinic - Bring insurance card",
                scheduledTimeMillis = eventCal.timeInMillis,
                recurrence = RecurrenceType.ONCE.name,
                priority = Priority.HIGH.name,
                iconName = "calendar",
                colorHex = 0xFFF59E0B
            )

            saveReminder(sampleMed)
            saveReminder(sampleTask)
            saveReminder(sampleEvent)
        }
    }
}
