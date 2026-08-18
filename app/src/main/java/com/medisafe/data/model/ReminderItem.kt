package com.medisafe.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medisafe.util.DoseTimes

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["isActive"]),
        Index(value = ["scheduledTimeMillis"])
    ]
)
data class ReminderItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = ReminderCategory.MEDICATION.name,
    val dosageOrDetails: String = "",
    val scheduledTimeMillis: Long,
    val recurrence: String = RecurrenceType.DAILY.name,
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
    val snoozedUntilMillis: Long? = null,
    val doseTimes: String = "",
    val lastAcknowledgedMillis: Long? = null,
    val pillsRemaining: Int? = null,
    val refillThreshold: Int = 5
) {
    val categoryEnum: ReminderCategory
        get() = runCatching { ReminderCategory.valueOf(category) }.getOrDefault(ReminderCategory.MEDICATION)

    val recurrenceEnum: RecurrenceType
        get() = runCatching { RecurrenceType.valueOf(recurrence) }.getOrDefault(RecurrenceType.DAILY)

    val priorityEnum: Priority
        get() = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.NORMAL)

    val effectiveTriggerTimeMillis: Long
        get() = snoozedUntilMillis ?: scheduledTimeMillis

    val parsedDoseTimes: List<Pair<Int, Int>>
        get() = DoseTimes.parseOrFallback(doseTimes, scheduledTimeMillis)

    val needsRefill: Boolean
        get() {
            val remaining = pillsRemaining ?: return false
            return remaining <= refillThreshold.coerceAtLeast(0)
        }
}
