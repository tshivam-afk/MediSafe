package com.medisafe.data.model

enum class ReminderCategory(val displayName: String, val emoji: String) {
    MEDICATION("Medication", "💊"),
    DAILY_TASK("Daily Task", "📋"),
    EVENT("Upcoming Event", "🗓️"),
    OTHER("Other", "⭐")
}

enum class RecurrenceType(val displayName: String) {
    ONCE("Once"),
    DAILY("Every Day"),
    WEEKDAYS("Weekdays (Mon-Fri)"),
    WEEKENDS("Weekends (Sat-Sun)"),
    WEEKLY("Weekly"),
    CUSTOM_DAYS("Custom days"),
    EVERY_4_HOURS("Every 4 Hours"),
    EVERY_6_HOURS("Every 6 Hours"),
    EVERY_8_HOURS("Every 8 Hours"),
    EVERY_12_HOURS("Every 12 Hours"),
    CUSTOM_HOURS("Custom hours")
}

enum class Priority(val displayName: String) {
    LOW("Low"),
    NORMAL("Normal"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class LogAction(val displayName: String) {
    TAKEN("Taken"),
    COMPLETED("Completed"),
    SNOOZED("Snoozed"),
    SKIPPED("Skipped"),
    MISSED("Missed"),
    REFILLED("Refilled")
}

enum class FoodTiming(val displayName: String) {
    NONE("No food rule"),
    BEFORE("Before food"),
    AFTER("After food"),
    WITH("With food")
}

enum class MedForm(val displayName: String) {
    NONE("Form"),
    TABLET("Tablet"),
    CAPSULE("Capsule"),
    SYRUP("Syrup"),
    INJECTION("Injection"),
    DROPS("Drops"),
    INHALER("Inhaler"),
    OTHER("Other")
}

enum class HomeSort(val displayName: String) {
    NEXT_DUE("Next due"),
    OVERDUE_FIRST("Overdue"),
    NAME("Name"),
    PRIORITY("Priority")
}

data class DayAdherence(
    val dayStartMillis: Long,
    val taken: Int,
    val missed: Int,
    val skipped: Int
) {
    val total: Int get() = taken + missed + skipped
    val percentage: Int
        get() = if (total == 0) 0 else ((taken.toFloat() / total) * 100).toInt()
}
