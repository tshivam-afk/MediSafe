package com.medisafe.data.repository

import android.content.Context
import com.medisafe.data.backup.BackupManager
import com.medisafe.data.backup.BackupPayload
import com.medisafe.data.dao.ReminderDao
import com.medisafe.data.model.DayAdherence
import com.medisafe.data.model.LogAction
import com.medisafe.data.model.RecurrenceType
import com.medisafe.data.model.ReminderCategory
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.model.ReminderLog
import com.medisafe.notifications.AlarmScheduler
import com.medisafe.notifications.NotificationHelper
import com.medisafe.util.DateTimeUtils
import com.medisafe.widget.ReminderAppWidgetProvider
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val appContext: Context
) {
    val allReminders: Flow<List<ReminderItem>> = reminderDao.getAllReminders()
    val allLogs: Flow<List<ReminderLog>> = reminderDao.getAllLogs()

    suspend fun getReminderById(id: Long): ReminderItem? = reminderDao.getReminderById(id)
    suspend fun getLogsForReminderSync(id: Long): List<ReminderLog> = reminderDao.getLogsForReminderSync(id)
    suspend fun getAllRemindersSync(): List<ReminderItem> = reminderDao.getAllRemindersSync()
    suspend fun getAllLogsSync(): List<ReminderLog> = reminderDao.getAllLogsSync()

    suspend fun saveReminder(reminder: ReminderItem): Long {
        val normalized = reminder.normalizedSchedule()
        val id = reminderDao.insertReminder(normalized)
        val saved = normalized.copy(id = if (normalized.id == 0L) id else normalized.id)
        syncAlarm(saved)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return saved.id
    }

    suspend fun updateReminder(reminder: ReminderItem) {
        val normalized = reminder.normalizedSchedule()
        reminderDao.updateReminder(normalized)
        syncAlarm(normalized)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun deleteReminder(reminder: ReminderItem): List<ReminderLog> {
        val logs = reminderDao.getLogsForReminderSync(reminder.id)
        AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        reminderDao.deleteLogsForReminder(reminder.id)
        reminderDao.deleteReminder(reminder)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return logs
    }

    suspend fun restoreDeleted(reminder: ReminderItem, logs: List<ReminderLog>) {
        val id = reminderDao.insertReminder(reminder.copy(id = 0L))
        if (logs.isNotEmpty()) {
            reminderDao.insertLogs(logs.map { it.copy(id = 0L, reminderId = id) })
        }
        reminderDao.getReminderById(id)?.let { syncAlarm(it) }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun restorePreviousState(previous: ReminderItem) {
        reminderDao.updateReminder(previous)
        syncAlarm(previous)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun markDoneOrTaken(reminder: ReminderItem, note: String = ""): ReminderItem {
        val now = System.currentTimeMillis()
        val lastAck = reminder.lastAcknowledgedMillis
        if (lastAck != null && now - lastAck < ACK_DEBOUNCE_MS) {
            return reminder
        }
        if (reminder.isPrn && reminder.prnMaxPerDay > 0) {
            val takenToday = reminderDao.getLogsForReminderSync(reminder.id).count { log ->
                DateTimeUtils.isToday(log.timestampMillis) &&
                    (log.actionEnum == LogAction.TAKEN || log.actionEnum == LogAction.COMPLETED)
            }
            if (takenToday >= reminder.prnMaxPerDay) {
                return reminder
            }
        }
        val isMed = reminder.categoryEnum == ReminderCategory.MEDICATION
        val action = if (isMed) LogAction.TAKEN else LogAction.COMPLETED
        val logNote = note.trim().ifBlank { "Marked ${action.displayName}" }

        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = action.name,
                timestampMillis = now,
                note = logNote
            )
        )

        var pills = reminder.pillsRemaining
        if (isMed && pills != null) {
            pills = (pills - 1).coerceAtLeast(0)
        }

        val updated = when {
            reminder.isPrn -> reminder.copy(
                snoozedUntilMillis = null,
                lastAcknowledgedMillis = now,
                pillsRemaining = pills
            )
            reminder.recurrenceEnum == RecurrenceType.ONCE -> reminder.copy(
                isCompleted = true,
                completedAtMillis = now,
                snoozedUntilMillis = null,
                lastAcknowledgedMillis = now,
                pillsRemaining = pills
            )
            else -> {
                val next = nextOccurrence(reminder, now)
                if (next <= 0L) {
                    reminder.copy(
                        isCompleted = true,
                        isActive = false,
                        completedAtMillis = now,
                        snoozedUntilMillis = null,
                        lastAcknowledgedMillis = now,
                        pillsRemaining = pills
                    )
                } else {
                    reminder.copy(
                        scheduledTimeMillis = next,
                        isCompleted = false,
                        completedAtMillis = null,
                        snoozedUntilMillis = null,
                        lastAcknowledgedMillis = now,
                        pillsRemaining = pills
                    )
                }
            }
        }
        reminderDao.updateReminder(updated)
        syncAlarm(updated)
        maybeNotifyRefill(updated)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return updated
    }

    suspend fun refillPills(reminder: ReminderItem, amount: Int): ReminderItem {
        val add = amount.coerceIn(1, 9_999)
        val nextCount = (reminder.pillsRemaining ?: 0) + add
        val updated = reminder.copy(pillsRemaining = nextCount)
        reminderDao.updateReminder(updated)
        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = LogAction.REFILLED.name,
                timestampMillis = System.currentTimeMillis(),
                note = "Added $add · now $nextCount"
            )
        )
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return updated
    }

    suspend fun snoozeReminder(reminder: ReminderItem, minutes: Int = 15): ReminderItem {
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
        return updated
    }

    suspend fun skipReminder(reminder: ReminderItem): ReminderItem {
        val now = System.currentTimeMillis()
        reminderDao.insertLog(
            ReminderLog(
                reminderId = reminder.id,
                reminderTitle = reminder.title,
                category = reminder.category,
                action = LogAction.SKIPPED.name,
                timestampMillis = now,
                note = "Skipped scheduled dose/task"
            )
        )
        val updated = when {
            reminder.isPrn -> reminder.copy(
                snoozedUntilMillis = null,
                lastAcknowledgedMillis = now
            )
            reminder.recurrenceEnum == RecurrenceType.ONCE -> reminder.copy(
                isCompleted = true,
                completedAtMillis = now,
                snoozedUntilMillis = null,
                lastAcknowledgedMillis = now
            )
            else -> {
                val next = nextOccurrence(reminder, now)
                if (next <= 0L) {
                    reminder.copy(
                        isCompleted = true,
                        isActive = false,
                        completedAtMillis = now,
                        snoozedUntilMillis = null,
                        lastAcknowledgedMillis = now
                    )
                } else {
                    reminder.copy(
                        scheduledTimeMillis = next,
                        isCompleted = false,
                        completedAtMillis = null,
                        snoozedUntilMillis = null,
                        lastAcknowledgedMillis = now
                    )
                }
            }
        }
        reminderDao.updateReminder(updated)
        syncAlarm(updated)
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return updated
    }

    suspend fun scanAndLogMissed(graceMinutes: Int = 30) {
        val now = System.currentTimeMillis()
        val grace = graceMinutes * 60 * 1000L
        val reminders = reminderDao.getActiveRemindersSync()
        reminders.forEach { reminder ->
            if (reminder.isCompleted || reminder.isPrn) return@forEach
            val dueAt = reminder.scheduledTimeMillis
            if (dueAt > now - grace) return@forEach
            val lastAck = reminder.lastAcknowledgedMillis ?: reminder.createdAtMillis
            if (dueAt <= lastAck) return@forEach
            val alreadyLogged = reminderDao.getLogsForReminderSync(reminder.id).any { log ->
                log.actionEnum == LogAction.MISSED &&
                    log.timestampMillis >= dueAt &&
                    log.timestampMillis <= now
            }
            if (alreadyLogged) return@forEach
            reminderDao.insertLog(
                ReminderLog(
                    reminderId = reminder.id,
                    reminderTitle = reminder.title,
                    category = reminder.category,
                    action = LogAction.MISSED.name,
                    timestampMillis = now,
                    note = "Missed ${DateTimeUtils.getRelativeTimeLabel(dueAt)}"
                )
            )
        }
    }

    suspend fun rebuildSchedulesAfterTimeChange() {
        val now = System.currentTimeMillis()
        reminderDao.getActiveRemindersSync()
            .filter { !it.isCompleted }
            .forEach { reminder ->
                val next = if (reminder.effectiveTriggerTimeMillis > now) {
                    reminder.effectiveTriggerTimeMillis
                } else if (reminder.recurrenceEnum == RecurrenceType.ONCE) {
                    reminder.scheduledTimeMillis
                } else {
                    nextOccurrence(reminder, now)
                }
                val updated = reminder.copy(
                    scheduledTimeMillis = next,
                    snoozedUntilMillis = reminder.snoozedUntilMillis?.takeIf { it > now }
                )
                reminderDao.updateReminder(updated)
                AlarmScheduler.scheduleReminderAlarm(appContext, updated)
            }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun exportBackup(): String {
        return BackupManager.exportJson(
            reminderDao.getAllRemindersSync(),
            reminderDao.getAllLogsSync()
        )
    }

    suspend fun importBackup(raw: String, replaceExisting: Boolean = false): Int {
        val payload: BackupPayload = BackupManager.importJson(raw)
        if (replaceExisting) {
            reminderDao.clearLogs()
            reminderDao.deleteAllReminders()
        }
        payload.reminders.forEach { reminder ->
            val id = reminderDao.insertReminder(reminder.copy(id = 0L))
            val saved = reminder.copy(id = id)
            syncAlarm(saved)
            payload.logs
                .filter { it.reminderTitle == reminder.title }
                .forEach { log ->
                    reminderDao.insertLog(log.copy(id = 0L, reminderId = id))
                }
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
        return payload.reminders.size
    }

    fun weeklyAdherence(logs: List<ReminderLog>, now: Long = System.currentTimeMillis()): List<DayAdherence> {
        val todayStart = DateTimeUtils.startOfDay(now)
        return (6 downTo 0).map { offset ->
            val dayStart = todayStart - offset * 24L * 60L * 60L * 1000L
            val dayEnd = dayStart + 24L * 60L * 60L * 1000L
            val dayLogs = logs.filter { it.timestampMillis in dayStart until dayEnd }
            DayAdherence(
                dayStartMillis = dayStart,
                taken = dayLogs.count {
                    it.actionEnum == LogAction.TAKEN || it.actionEnum == LogAction.COMPLETED
                },
                missed = dayLogs.count { it.actionEnum == LogAction.MISSED },
                skipped = dayLogs.count { it.actionEnum == LogAction.SKIPPED }
            )
        }
    }

    suspend fun clearLogs() {
        reminderDao.clearLogs()
    }

    suspend fun deleteLogById(logId: Long) {
        reminderDao.deleteLogById(logId)
    }

    suspend fun undoHistoryLog(log: ReminderLog) {
        reminderDao.deleteLogById(log.id)
        val reminder = reminderDao.getReminderById(log.reminderId) ?: return
        if (log.actionEnum == LogAction.TAKEN && reminder.pillsRemaining != null) {
            reminderDao.updateReminder(reminder.copy(pillsRemaining = reminder.pillsRemaining + 1))
        }
        ReminderAppWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun deleteReminders(reminders: List<ReminderItem>): List<Pair<ReminderItem, List<ReminderLog>>> {
        return reminders.map { item -> item to deleteReminder(item) }
    }

    private fun nextOccurrence(reminder: ReminderItem, fromTimeMillis: Long): Long {
        return DateTimeUtils.computeNextOccurrence(
            currentScheduledMillis = reminder.scheduledTimeMillis,
            recurrenceType = reminder.recurrenceEnum,
            customIntervalHours = reminder.customIntervalHours,
            fromTimeMillis = fromTimeMillis,
            doseTimes = reminder.parsedDoseTimes
        )
    }

    private fun ReminderItem.normalizedSchedule(): ReminderItem {
        val times = parsedDoseTimes
        if (times.isEmpty()) return this
        val first = times.first()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = scheduledTimeMillis
            set(java.util.Calendar.HOUR_OF_DAY, first.first)
            set(java.util.Calendar.MINUTE, first.second)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return copy(
            scheduledTimeMillis = cal.timeInMillis,
            doseTimes = com.medisafe.util.DoseTimes.format(times)
        )
    }

    private fun syncAlarm(reminder: ReminderItem) {
        if (reminder.shouldAlert) {
            AlarmScheduler.scheduleReminderAlarm(appContext, reminder)
        } else {
            AlarmScheduler.cancelReminderAlarm(appContext, reminder.id)
        }
    }

    private fun maybeNotifyRefill(reminder: ReminderItem) {
        if (reminder.needsRefill) {
            NotificationHelper.showRefillNotification(appContext, reminder)
        }
    }

    companion object {
        const val ACK_DEBOUNCE_MS = 90_000L
    }
}
