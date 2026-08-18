package com.medisafe

import com.medisafe.util.DoseTimes
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseTimesTest {
    @Test
    fun parseSortsAndDedupes() {
        val parsed = DoseTimes.parse("20:00, 08:00, 08:00, 14:00")
        assertEquals(listOf(8 to 0, 14 to 0, 20 to 0), parsed)
    }

    @Test
    fun formatRoundTrip() {
        val raw = DoseTimes.format(listOf(20 to 0, 8 to 0))
        assertEquals("08:00,20:00", raw)
        assertEquals(listOf(8 to 0, 20 to 0), DoseTimes.parse(raw))
    }
}
