package com.medisafe.util

import java.util.Calendar

object Weekdays {
    private val labels = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun bit(calendarDay: Int): Int = 1 shl (calendarDay - 1).coerceIn(0, 6)

    fun has(mask: Int, calendarDay: Int): Boolean {
        if (mask == 0) return true
        return mask and bit(calendarDay) != 0
    }

    fun toggle(mask: Int, calendarDay: Int): Int = mask xor bit(calendarDay)

    fun shortLabel(mask: Int): String {
        if (mask == 0) return "Custom days"
        return (Calendar.SUNDAY..Calendar.SATURDAY)
            .filter { has(mask, it) }
            .joinToString("·") { labels[it - 1] }
            .ifBlank { "Custom days" }
    }

    val orderedDays: IntRange = Calendar.SUNDAY..Calendar.SATURDAY

    fun labelFor(calendarDay: Int): String = labels[(calendarDay - 1).coerceIn(0, 6)]
}
