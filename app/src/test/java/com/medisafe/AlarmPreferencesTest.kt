package com.medisafe

import androidx.test.core.app.ApplicationProvider
import com.medisafe.data.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmPreferencesTest {

    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext<android.content.Context>())

    @Test
    fun alarmFeatureIsOffByDefault() {
        val p = prefs()
        assertFalse("alarm reliability must default to off", p.alarmReliabilityEnabled)
        assertFalse("silent-mode override must default to off", p.forceAlarmVolume)
        assertFalse("fade-in must default to off", p.gradualAlarmVolume)
        assertEquals("re-ring must default to off", 0, p.escalationMinutes)
        assertEquals(5, p.alarmTimeoutMinutes)
        assertEquals(3, p.escalationMaxAttempts)
    }

    @Test
    fun masterToggleRoundTrips() {
        val p = prefs()
        p.alarmReliabilityEnabled = true
        assertTrue(p.alarmReliabilityEnabled)
        p.alarmReliabilityEnabled = false
        assertFalse(p.alarmReliabilityEnabled)
    }

    @Test
    fun alarmTimeoutIsClamped() {
        val p = prefs()
        p.alarmTimeoutMinutes = 999
        assertEquals(30, p.alarmTimeoutMinutes)
        p.alarmTimeoutMinutes = 0
        assertEquals(1, p.alarmTimeoutMinutes)
    }

    @Test
    fun escalationCanBeDisabledAndClamped() {
        val p = prefs()
        p.escalationMinutes = 0
        assertEquals(0, p.escalationMinutes)
        p.escalationMinutes = 500
        assertEquals(120, p.escalationMinutes)
    }

    @Test
    fun togglesRoundTrip() {
        val p = prefs()
        p.forceAlarmVolume = true
        p.gradualAlarmVolume = true
        assertEquals(true, p.forceAlarmVolume)
        assertEquals(true, p.gradualAlarmVolume)
    }

    @Test
    fun vacationWindowIsRespected() {
        val p = prefs()
        p.vacationUntilMillis = System.currentTimeMillis() + 60_000L
        assertTrue(p.isOnVacation)
        p.vacationUntilMillis = 0L
        assertEquals(false, p.isOnVacation)
    }
}
