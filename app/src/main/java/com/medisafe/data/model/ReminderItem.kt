package com.medisafe.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.medisafe.util.DateTimeUtils
import com.medisafe.util.DoseTimes
import com.medisafe.util.Weekdays

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
    val refillThreshold: Int = 5,
    val courseEndMillis: Long? = null,
    val weekdaysMask: Int = 0,
    val isPrn: Boolean = false,
    val prnMaxPerDay: Int = 3,
    val foodTiming: String = FoodTiming.NONE.name,
    val expiryMillis: Long? = null,
    val strength: String = "",
    val form: String = MedForm.NONE.name,
    val pharmacyName: String = "",
    val pharmacyPhone: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val alertAsAlarm: Boolean = false
) {
    val categoryEnum: ReminderCategory
        get() = runCatching { ReminderCategory.valueOf(category) }.getOrDefault(ReminderCategory.MEDICATION)

    val recurrenceEnum: RecurrenceType
        get() = runCatching { RecurrenceType.valueOf(recurrence) }.getOrDefault(RecurrenceType.DAILY)

    val priorityEnum: Priority
        get() = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.NORMAL)

    val foodTimingEnum: FoodTiming
        get() = runCatching { FoodTiming.valueOf(foodTiming) }.getOrDefault(FoodTiming.NONE)

    val formEnum: MedForm
        get() = runCatching { MedForm.valueOf(form) }.getOrDefault(MedForm.NONE)

    val effectiveTriggerTimeMillis: Long
        get() = snoozedUntilMillis ?: scheduledTimeMillis

    val parsedDoseTimes: List<Pair<Int, Int>>
        get() = DoseTimes.parseOrFallback(doseTimes, scheduledTimeMillis)

    val needsRefill: Boolean
        get() {
            val remaining = pillsRemaining ?: return false
            return remaining <= refillThreshold.coerceAtLeast(0)
        }

    val isExpired: Boolean
        get() = expiryMillis != null && expiryMillis < System.currentTimeMillis()

    val expirySoon: Boolean
        get() {
            val expiry = expiryMillis ?: return false
            val now = System.currentTimeMillis()
            return expiry in now..(now + 7L * 24 * 60 * 60 * 1000)
        }

    val shouldAlert: Boolean
        get() = isActive && !isCompleted && !isPrn

    val doseLabel: String
        get() = buildString {
            if (strength.isNotBlank()) append(strength)
            if (formEnum != MedForm.NONE) {
                if (isNotEmpty()) append(" ")
                append(formEnum.displayName.lowercase())
            }
            if (dosageOrDetails.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(dosageOrDetails)
            }
        }

    val recurrenceLabel: String
        get() = if (recurrenceEnum == RecurrenceType.CUSTOM_DAYS) {
            Weekdays.shortLabel(weekdaysMask)
        } else {
            recurrenceEnum.displayName
        }

    fun isCourseOver(atMillis: Long = System.currentTimeMillis()): Boolean {
        val end = courseEndMillis ?: return false
        return atMillis > end
    }

    fun isStickyAlert(): Boolean =
        priorityEnum == Priority.HIGH || priorityEnum == Priority.URGENT

    /**
     * Whether this reminder rings like a real alarm — full-screen, looping, and audible
     * with the screen off. Purely opt-in per reminder via the "Ring like an alarm" switch;
     * priority alone never forces it. The option also requires the Alarm Reliability
     * master switch in Settings to be on — that gate is applied where alarms are
     * scheduled (the model itself has no access to preferences).
     */
    val ringsAsAlarm: Boolean
        get() = alertAsAlarm

    /** Urgent items additionally try to punch through Do Not Disturb and force alarm volume. */
    val isCritical: Boolean
        get() = priorityEnum == Priority.URGENT

    /** Why this item is ringing as an alarm, for display in the UI. */
    val alarmReasonLabel: String
        get() = if (alertAsAlarm) "Ring like an alarm" else ""
}
