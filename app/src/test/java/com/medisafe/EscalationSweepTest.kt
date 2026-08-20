package com.medisafe

import androidx.test.core.app.ApplicationProvider
import com.medisafe.data.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The escalation sweep costs one PendingIntent round-trip per attempt, and bulk
 * rescheduling runs on every launch, boot and time change. These pin the bounds that
 * keep that cost proportional.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EscalationSweepTest {

    private fun prefs() = AppPreferences(ApplicationProvider.getApplicationContext<android.content.Context>())

    @Test
    fun maxAttemptsIsBoundedForSweeping() {
        val p = prefs()
        p.escalationMaxAttempts = 99
        assertEquals("sweep must stay bounded", 10, p.escalationMaxAttempts)
        assertTrue(p.escalationMaxAttempts <= 10)
    }

    @Test
    fun escalationCanBeFullyDisabled() {
        val p = prefs()
        p.escalationMaxAttempts = 0
        assertEquals(0, p.escalationMaxAttempts)
    }

    @Test
    fun defaultSweepIsSmall() {
        // Default of 3 keeps a bulk reschedule cheap even with many reminders.
        assertEquals(3, prefs().escalationMaxAttempts)
    }

    @Test
    fun negativeAttemptsClampToZero() {
        val p = prefs()
        p.escalationMaxAttempts = -5
        assertEquals(0, p.escalationMaxAttempts)
    }
}
