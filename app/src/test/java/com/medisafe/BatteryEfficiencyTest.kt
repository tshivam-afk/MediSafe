package com.medisafe

import com.medisafe.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Guards the day-boundary helpers used by the COUNT-based log queries, which replaced
 * loading a reminder's entire log history on every alarm and every "take".
 */
class BatteryEfficiencyTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 30)
            set(Calendar.MILLISECOND, 500)
        }.timeInMillis

    @Test
    fun startOfDayIsMidnight() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = DateTimeUtils.startOfDay(at(2026, Calendar.AUGUST, 20, 14, 45))
        }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun endOfDayIsLastMillisecond() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = DateTimeUtils.endOfDay(at(2026, Calendar.AUGUST, 20, 3, 5))
        }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
        assertEquals(59, cal.get(Calendar.SECOND))
        assertEquals(999, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun dayWindowBracketsAnyInstantOnThatDay() {
        val noon = at(2026, Calendar.AUGUST, 20, 12, 0)
        val start = DateTimeUtils.startOfDay(noon)
        val end = DateTimeUtils.endOfDay(noon)
        assertTrue("start must precede the instant", start <= noon)
        assertTrue("end must follow the instant", end >= noon)
        // A full day, give or take DST transitions.
        assertTrue("window should span roughly one day", (end - start) > 22 * 60 * 60 * 1000L)
    }

    @Test
    fun windowExcludesAdjacentDays() {
        val today = at(2026, Calendar.AUGUST, 20, 12, 0)
        val yesterdayNoon = at(2026, Calendar.AUGUST, 19, 12, 0)
        val tomorrowNoon = at(2026, Calendar.AUGUST, 21, 12, 0)
        val start = DateTimeUtils.startOfDay(today)
        val end = DateTimeUtils.endOfDay(today)
        assertTrue(yesterdayNoon < start)
        assertTrue(tomorrowNoon > end)
    }
}
