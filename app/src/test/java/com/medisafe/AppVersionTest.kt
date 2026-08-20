package com.medisafe

import com.medisafe.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(AppVersion.isNewer("1.0.4", "1.0.3"))
        assertFalse(AppVersion.isNewer("1.0.3", "1.0.4"))
        assertFalse(AppVersion.isNewer("1.0.3", "1.0.3"))
    }

    @Test
    fun tagPrefixDoesNotAffectCompare() {
        assertTrue(AppVersion.isNewer("v1.0.4", "1.0.3"))
        assertEquals("1.0.4", AppVersion.normalize("v1.0.4"))
    }
}
