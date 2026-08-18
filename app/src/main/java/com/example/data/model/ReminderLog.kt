package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_logs")
data class ReminderLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: Long,
    val reminderTitle: String,
    val category: String = ReminderCategory.MEDICATION.name,
    val action: String = LogAction.TAKEN.name, // LogAction
    val timestampMillis: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    val actionEnum: LogAction
        get() = try {
            LogAction.valueOf(action)
        } catch (e: Exception) {
            LogAction.TAKEN
        }
}
