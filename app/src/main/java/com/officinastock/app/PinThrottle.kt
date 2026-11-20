package com.officinastock.app

/**
 * Escalating PIN lockout (4.6.3).
 * A fixed 1-minute wait after 5 errors allowed 7,200 guesses a day: a 4-digit PIN fell in
 * about a day and a half. Each consecutive lockout now doubles the wait, up to one hour.
 * A correct PIN resets the escalation.
 *
 * 4.21.2: the wait runs on the monotonic clock (SystemClock.elapsedRealtime), as Android's own
 * lockscreen does. The wall clock can be moved by whoever holds the phone, and moving it
 * forward or backward used to release the lock at once.
 */
object PinThrottle {
    const val MAX_FAILURES = 5
    const val BASE_LOCK_MS = 60_000L
    const val MAX_LOCK_MS = 3_600_000L

    /** Wait for the [lockouts]-th consecutive lockout (1-based): 1, 2, 4, 8, 16, 32, 60 minutes. */
    fun lockMillis(lockouts: Int): Long {
        if (lockouts <= 0) return 0L
        val shift = (lockouts - 1).coerceAtMost(6)
        return (BASE_LOCK_MS shl shift).coerceAtMost(MAX_LOCK_MS)
    }

    /**
     * Milliseconds still to wait. [deadline] and [now] are elapsedRealtime values, [length] the
     * lock's full wait, [lockBoot]/[boot] the boot count when locked and now (-1 if unknown).
     * After a reboot the monotonic clock restarts from zero: the full wait then counts from the
     * boot, so restarting the phone never shortens it.
     */
    fun remaining(deadline: Long, length: Long, lockBoot: Int, now: Long, boot: Int): Long {
        if (deadline <= 0L || length <= 0L) return 0L
        val sameBoot = boot == lockBoot && deadline - now <= length
        val left = if (sameBoot) deadline - now else length - now
        return left.coerceIn(0L, length)
    }

    fun message(remaining: Long): String {
        val minutes = ((remaining + 59_999L) / 60_000L).coerceAtLeast(1L)
        return if (minutes == 1L) "Troppi tentativi. Attendi un minuto e riprova."
        else "Troppi tentativi. Attendi $minutes minuti e riprova."
    }
}
