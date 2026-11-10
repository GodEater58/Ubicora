package com.officinastock.app

/** 4.15: outcome of a reading in the burst screen (token = one sound and one vibration per reading). */
data class ScanSignal(val ok: Boolean, val token: Long)

/** 4.15 burst picking quantities: whole steps, never more than what is on the shelf. */
object BurstQty {
    /** Less than one unit left (e.g. 0.5 kg): offer exactly what is there. */
    fun initial(available: Double): Double = if (available > 0.0 && available < 1.0) available else 1.0
    fun more(current: Double, available: Double): Double = if (current + 1 <= available) current + 1 else current
    fun less(current: Double): Double = if (current > 1.0) (current - 1).coerceAtLeast(1.0) else current
}

/**
 * 4.15: the camera reads the same label many times a second while it stays in view. A reading
 * counts only when that label has been out of view for [gapMs]: holding the phone still never
 * adds pieces, moving away and coming back does.
 */
class ScanGate(private val gapMs: Long = 2000) {
    private val seen = HashMap<String, Long>()
    fun accept(raw: String, nowMs: Long): Boolean {
        val last = seen.put(raw, nowMs)
        if (seen.size > 64) seen.entries.removeAll { nowMs - it.value > gapMs }
        return last == null || nowMs - last > gapMs
    }
}
