package com.medisafe.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.medisafe.data.model.ReminderItem
import com.medisafe.data.model.ReminderLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY scheduledTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY scheduledTimeMillis ASC")
    fun getActiveReminders(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY scheduledTimeMillis ASC")
    suspend fun getActiveRemindersSync(): List<ReminderItem>

    @Query("SELECT * FROM reminders ORDER BY scheduledTimeMillis ASC")
    suspend fun getAllRemindersSync(): List<ReminderItem>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(item: ReminderItem): Long

    @Update
    suspend fun updateReminder(item: ReminderItem)

    @Delete
    suspend fun deleteReminder(item: ReminderItem)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()

    @Query("SELECT * FROM reminder_logs ORDER BY timestampMillis DESC")
    fun getAllLogs(): Flow<List<ReminderLog>>

    @Query("SELECT * FROM reminder_logs ORDER BY timestampMillis DESC")
    suspend fun getAllLogsSync(): List<ReminderLog>

    @Query("SELECT * FROM reminder_logs WHERE reminderId = :reminderId ORDER BY timestampMillis DESC")
    fun getLogsForReminder(reminderId: Long): Flow<List<ReminderLog>>

    @Query("SELECT * FROM reminder_logs WHERE reminderId = :reminderId ORDER BY timestampMillis DESC")
    suspend fun getLogsForReminderSync(reminderId: Long): List<ReminderLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ReminderLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ReminderLog>)

    @Query("DELETE FROM reminder_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM reminder_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM reminder_logs WHERE reminderId = :reminderId")
    suspend fun deleteLogsForReminder(reminderId: Long)
}
