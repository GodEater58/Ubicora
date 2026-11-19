package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.21.1: the fingerprint is good only for 7 days after the last PIN. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BiometricLoginTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun thePinIsAskedAgainAfterSevenDays() {
        val c = RuntimeEnvironment.getApplication()
        assertFalse(BiometricLogin.pinStillFresh(c, 41, 1_000 * day))          // never typed: PIN first
        BiometricLogin.pinUsed(c, 41, 1_000 * day)
        assertTrue(BiometricLogin.pinStillFresh(c, 41, 1_000 * day + 6 * day))
        assertFalse(BiometricLogin.pinStillFresh(c, 41, 1_000 * day + 7 * day))
        assertFalse(BiometricLogin.pinStillFresh(c, 42, 1_000 * day))          // each profile on its own
    }

    @Test fun movingTheDateBackDoesNotStretchTheSevenDays() {
        val c = RuntimeEnvironment.getApplication()
        BiometricLogin.pinUsed(c, 43, 2_000 * day)
        assertFalse(BiometricLogin.pinStillFresh(c, 43, 2_000 * day - 1))
    }
}
