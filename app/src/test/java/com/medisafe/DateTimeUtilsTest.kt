package com.medisafe

import com.medisafe.data.model.RecurrenceType
import com.medisafe.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateTimeUtilsTest {

    @Test
    fun countdownMarksDueWhenTargetIsPast() {
        val now = 1_700_000_000_000L
        val countdown = DateTimeUtils.getCountdown(now - 5_000L, now)
        assertTrue(countdown.isDue)
        assertEquals("Due right now", countdown.formattedString)
    }

    @Test
    fun countdownFormatsHoursAndMinutes() {
        val now = 1_700_000_000_000L
        val target = now + (2 * 60 * 60 * 1000L) + (15 * 60 * 1000L) + 8_000L
        val countdown = DateTimeUtils.getCountdown(target, now)
        assertFalse(countdown.isDue)
        assertEquals(2, countdown.hours)
        assertEquals(15, countdown.minutes)
        assertTrue(countdown.formattedString.contains("02h"))
    }

    @Test
    fun dailyOccurrenceMovesToNextDayWhenTimePassed() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 18, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val scheduled = calendar.timeInMillis
        val after = scheduled + 60_000L
        val next = DateTimeUtils.computeNextOccurrence(
            currentScheduledMillis = scheduled,
            recurrenceType = RecurrenceType.DAILY,
            fromTimeMillis = after
        )
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(19, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun intervalOccurrenceSkipsMissedSlots() {
        val start = 1_700_000_000_000L
        val from = start + (10 * 60 * 60 * 1000L)
        val next = DateTimeUtils.computeNextOccurrence(
            currentScheduledMillis = start,
            recurrenceType = RecurrenceType.EVERY_4_HOURS,
            fromTimeMillis = from
        )
        assertEquals(start + (12 * 60 * 60 * 1000L), next)
    }

    @Test
    fun isTodayMatchesSameCalendarDay() {
        val now = System.currentTimeMillis()
        assertTrue(DateTimeUtils.isToday(now))
        assertFalse(DateTimeUtils.isToday(now - 48L * 60L * 60L * 1000L))
    }

    @Test
    fun multiDosePicksLaterSlotSameDay() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 18, 8, 5, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = DateTimeUtils.computeNextOccurrence(
            currentScheduledMillis = calendar.timeInMillis,
            recurrenceType = RecurrenceType.DAILY,
            fromTimeMillis = calendar.timeInMillis,
            doseTimes = listOf(8 to 0, 14 to 0, 20 to 0)
        )
        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(18, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, nextCal.get(Calendar.HOUR_OF_DAY))
    }
}
