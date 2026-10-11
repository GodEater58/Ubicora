package com.officinastock.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PinThrottleTest {
    @Test fun lockDoublesUpToOneHour() {
        assertEquals(0L,PinThrottle.lockMillis(0))
        assertEquals(listOf(1L,2L,4L,8L,16L,32L,60L,60L,60L),
            (1..9).map{PinThrottle.lockMillis(it)/60_000L})
    }
    @Test fun clockMovedBackDoesNotLockForever() {
        assertTrue(PinThrottle.isLocked(10_000L,0L))
        assertFalse(PinThrottle.isLocked(0L,10_000L))
        assertFalse(PinThrottle.isLocked(PinThrottle.MAX_LOCK_MS+1,0L))
        assertEquals("Troppi tentativi. Attendi un minuto e riprova.",PinThrottle.message(60_000L,0L))
        assertEquals("Troppi tentativi. Attendi 2 minuti e riprova.",PinThrottle.message(120_000L,0L))
    }
    @Test fun secondLockoutWaitsLongerAndSuccessResets() {
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        val prefs=app.getSharedPreferences("login_attempts",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val db=StockDb(app)
        try {
            val profile=db.createProfile("Test","1234")
            val key="profile_$profile"
            fun expireLock()=prefs.edit().putLong("${key}_retry",0L).commit()
            repeat(5){assertFalse(db.authenticate(profile,"0000"))}
            assertThrows(IllegalArgumentException::class.java){db.authenticate(profile,"1234")}
            val first=prefs.getLong("${key}_retry",0L)-System.currentTimeMillis()
            assertTrue(first in 1..60_000L)
            expireLock()
            repeat(5){assertFalse(db.authenticate(profile,"0000"))}
            val second=prefs.getLong("${key}_retry",0L)-System.currentTimeMillis()
            assertTrue("second lockout must be longer",second in 60_001L..120_000L)
            expireLock()
            assertTrue(db.authenticate(profile,"1234"))
            assertEquals(0,prefs.getInt("${key}_locks",-1))
        } finally { db.close() }
    }
}
