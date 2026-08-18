package com.medisafe.util

import java.util.Calendar
import java.util.Locale

object DoseTimes {
    fun parse(raw: String): List<Pair<Int, Int>> {
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token ->
                val parts = token.split(':')
                if (parts.size != 2) return@mapNotNull null
                val hour = parts[0].toIntOrNull() ?: return@mapNotNull null
                val minute = parts[1].toIntOrNull() ?: return@mapNotNull null
                if (hour !in 0..23 || minute !in 0..59) return@mapNotNull null
                hour to minute
            }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
    }

    fun parseOrFallback(raw: String, scheduledTimeMillis: Long): List<Pair<Int, Int>> {
        val parsed = parse(raw)
        if (parsed.isNotEmpty()) return parsed
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledTimeMillis }
        return listOf(cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE))
    }

    fun format(times: List<Pair<Int, Int>>): String {
        return times
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString(",") { (hour, minute) ->
                String.format(Locale.US, "%02d:%02d", hour, minute)
            }
    }

    fun formatDisplay(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return DateTimeUtils.formatTime(cal.timeInMillis)
    }
}
