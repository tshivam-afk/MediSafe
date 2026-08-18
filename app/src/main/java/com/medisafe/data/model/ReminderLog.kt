package com.medisafe.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_logs",
    indices = [
        Index(value = ["reminderId"]),
        Index(value = ["timestampMillis"])
    ]
)
data class ReminderLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val reminderTitle: String,
    val category: String = ReminderCategory.MEDICATION.name,
    val action: String = LogAction.TAKEN.name,
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    val actionEnum: LogAction
        get() = runCatching { LogAction.valueOf(action) }.getOrDefault(LogAction.TAKEN)
}
