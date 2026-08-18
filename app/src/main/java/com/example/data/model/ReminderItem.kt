package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = ReminderCategory.MEDICATION.name, // ReminderCategory
    val dosageOrDetails: String = "",
    val scheduledTimeMillis: Long, // timestamp when the reminder is set to fire
    val recurrence: String = RecurrenceType.DAILY.name, // RecurrenceType
    val customIntervalHours: Int = 0,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val priority: String = Priority.NORMAL.name,
    val iconName: String = "pill",
    val colorHex: Long = 0xFF0D9488,
    val notificationSound: Boolean = true,
    val vibrate: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val snoozedUntilMillis: Long? = null
) {
    val categoryEnum: ReminderCategory
        get() = try {
            ReminderCategory.valueOf(category)
        } catch (e: Exception) {
            ReminderCategory.MEDICATION
        }

    val recurrenceEnum: RecurrenceType
        get() = try {
            RecurrenceType.valueOf(recurrence)
        } catch (e: Exception) {
            RecurrenceType.DAILY
        }

    val priorityEnum: Priority
        get() = try {
            Priority.valueOf(priority)
        } catch (e: Exception) {
            Priority.NORMAL
        }

    /**
     * The actual trigger time taking snooze into account
     */
    val effectiveTriggerTimeMillis: Long
        get() = snoozedUntilMillis ?: scheduledTimeMillis
}
