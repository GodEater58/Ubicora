package com.officinastock.app

/**
 * Escalating PIN lockout (4.6.3).
 * A fixed 1-minute wait after 5 errors allowed 7,200 guesses a day: a 4-digit PIN fell in
 * about a day and a half. Each consecutive lockout now doubles the wait, up to one hour.
 * A correct PIN resets the escalation.
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
     * True when a stored deadline still blocks login. A deadline further away than the longest
     * possible lock can only come from a clock moved backwards: it is ignored rather than
     * locking the profile out indefinitely.
     */
    fun isLocked(retryAt: Long, now: Long): Boolean {
        val wait = retryAt - now
        return wait > 0L && wait <= MAX_LOCK_MS
    }

    fun message(retryAt: Long, now: Long): String {
        val minutes = ((retryAt - now + 59_999L) / 60_000L).coerceAtLeast(1L)
        return if (minutes == 1L) "Troppi tentativi. Attendi un minuto e riprova."
        else "Troppi tentativi. Attendi $minutes minuti e riprova."
    }
}
