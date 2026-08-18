package com.example.data.model

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
    EVERY_4_HOURS("Every 4 Hours"),
    EVERY_6_HOURS("Every 6 Hours"),
    EVERY_8_HOURS("Every 8 Hours"),
    EVERY_12_HOURS("Every 12 Hours")
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
    SKIPPED("Skipped")
}
