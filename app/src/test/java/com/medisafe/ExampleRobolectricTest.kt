package com.medisafe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameIsMediSafe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("MediSafe", context.getString(R.string.app_name))
    }
}
