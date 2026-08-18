package com.example.util

import com.example.data.model.RecurrenceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateTimeUtils {

    fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatShortDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatDateTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun isToday(timeMillis: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun isTomorrow(timeMillis: Long): Boolean {
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val target = Calendar.getInstance().apply { timeInMillis = timeMillis }
        return tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun getRelativeTimeLabel(timeMillis: Long): String {
        return when {
            isToday(timeMillis) -> "Today at ${formatTime(timeMillis)}"
            isTomorrow(timeMillis) -> "Tomorrow at ${formatTime(timeMillis)}"
            else -> formatDateTime(timeMillis)
        }
    }

    /**
     * Formats remaining time into a readable countdown string:
     * e.g., "02h 15m 30s", "45m 12s", "03s", "Due now!"
     */
    data class CountdownComponents(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val isDue: Boolean,
        val formattedString: String
    )

    fun getCountdown(targetTimeMillis: Long, currentTimeMillis: Long = System.currentTimeMillis()): CountdownComponents {
        val diff = targetTimeMillis - currentTimeMillis
        if (diff <= 0) {
            val overdueSeconds = TimeUnit.MILLISECONDS.toSeconds(-diff)
            val overdueMinutes = TimeUnit.MILLISECONDS.toMinutes(-diff)
            val overdueHours = TimeUnit.MILLISECONDS.toHours(-diff)
            val formatted = when {
                overdueHours > 0 -> "Due ${overdueHours}h ago"
                overdueMinutes > 0 -> "Due ${overdueMinutes}m ago"
                else -> "Due right now"
            }
            return CountdownComponents(
                days = 0,
                hours = 0,
                minutes = 0,
                seconds = 0,
                isDue = true,
                formattedString = formatted
            )
        }

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60

        val formatted = buildString {
            if (days > 0) {
                append("${days}d ")
            }
            if (days > 0 || hours > 0) {
                append(String.format(Locale.US, "%02dh ", hours))
            }
            append(String.format(Locale.US, "%02dm ", minutes))
            append(String.format(Locale.US, "%02ds", seconds))
        }.trim()

        return CountdownComponents(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            isDue = false,
            formattedString = formatted
        )
    }

    /**
     * Computes the next scheduled occurrence given the current recurrence rule and scheduled base time.
     */
    fun computeNextOccurrence(
        currentScheduledMillis: Long,
        recurrenceType: RecurrenceType,
        customIntervalHours: Int = 0,
        fromTimeMillis: Long = System.currentTimeMillis()
    ): Long {
        val baseCal = Calendar.getInstance().apply { timeInMillis = currentScheduledMillis }
        val hour = baseCal.get(Calendar.HOUR_OF_DAY)
        val minute = baseCal.get(Calendar.MINUTE)

        return when (recurrenceType) {
            RecurrenceType.ONCE -> {
                // If it's ONCE and time passed, stay or return future if set
                if (currentScheduledMillis > fromTimeMillis) currentScheduledMillis else fromTimeMillis
            }
            RecurrenceType.DAILY -> {
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (next.timeInMillis <= fromTimeMillis) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
                next.timeInMillis
            }
            RecurrenceType.WEEKDAYS -> {
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (next.timeInMillis <= fromTimeMillis) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
                // Skip weekends (Saturday=7, Sunday=1)
                while (next.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || next.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
                next.timeInMillis
            }
            RecurrenceType.WEEKENDS -> {
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (next.timeInMillis <= fromTimeMillis) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
                while (next.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && next.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
                next.timeInMillis
            }
            RecurrenceType.WEEKLY -> {
                val dayOfWeek = baseCal.get(Calendar.DAY_OF_WEEK)
                val next = Calendar.getInstance().apply {
                    timeInMillis = fromTimeMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var guard = 0
                while (
                    guard < 14 &&
                    (next.get(Calendar.DAY_OF_WEEK) != dayOfWeek || next.timeInMillis <= fromTimeMillis)
                ) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                    guard++
                }
                next.timeInMillis
            }
            RecurrenceType.EVERY_4_HOURS -> computeIntervalTime(currentScheduledMillis, fromTimeMillis, 4)
            RecurrenceType.EVERY_6_HOURS -> computeIntervalTime(currentScheduledMillis, fromTimeMillis, 6)
            RecurrenceType.EVERY_8_HOURS -> computeIntervalTime(currentScheduledMillis, fromTimeMillis, 8)
            RecurrenceType.EVERY_12_HOURS -> computeIntervalTime(currentScheduledMillis, fromTimeMillis, 12)
        }
    }

    private fun computeIntervalTime(
        currentScheduledMillis: Long,
        fromTimeMillis: Long,
        hours: Int
    ): Long {
        val intervalMs = hours.coerceAtLeast(1) * 60L * 60L * 1000L
        val next = if (currentScheduledMillis > 0L) currentScheduledMillis else fromTimeMillis + intervalMs
        if (next > fromTimeMillis) return next
        val elapsed = fromTimeMillis - next
        val steps = (elapsed / intervalMs) + 1
        return next + (steps * intervalMs)
    }

    /**
     * Builds a timestamp from given Date and Hour/Minute.
     */
    fun createTimestamp(year: Int, month: Int, day: Int, hourOfDay: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
