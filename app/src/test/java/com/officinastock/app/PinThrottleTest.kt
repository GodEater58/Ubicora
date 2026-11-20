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
    @Test fun monotonicWaitSurvivesRebootAndIgnoresWallClock() {
        val min=60_000L
        // Same boot: counts down on the monotonic clock only.
        assertEquals(min,PinThrottle.remaining(1_000_000L+min,min,3,1_000_000L,3))
        assertEquals(0L,PinThrottle.remaining(1_000_000L+min,min,3,1_000_000L+min,3))
        assertEquals(0L,PinThrottle.remaining(0L,0L,3,5L,3))
        // Rebooted (boot count changed or monotonic clock went back): full wait from boot.
        assertEquals(min-10_000L,PinThrottle.remaining(9_000_000L,min,3,10_000L,4))
        assertEquals(min-10_000L,PinThrottle.remaining(9_000_000L,min,-1,10_000L,-1))
        assertEquals("Troppi tentativi. Attendi un minuto e riprova.",PinThrottle.message(60_000L))
        assertEquals("Troppi tentativi. Attendi 2 minuti e riprova.",PinThrottle.message(120_000L))
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
            val first=prefs.getLong("${key}_retry",0L)-android.os.SystemClock.elapsedRealtime()
            assertTrue(first in 1..60_000L)
            expireLock()
            repeat(5){assertFalse(db.authenticate(profile,"0000"))}
            val second=prefs.getLong("${key}_retry",0L)-android.os.SystemClock.elapsedRealtime()
            assertTrue("second lockout must be longer",second in 60_001L..120_000L)
            expireLock()
            assertTrue(db.authenticate(profile,"1234"))
            assertEquals(0,prefs.getInt("${key}_locks",-1))
        } finally { db.close() }
    }
}
