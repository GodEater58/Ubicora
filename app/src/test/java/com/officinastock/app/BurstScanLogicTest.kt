package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

/** 4.15: quantities and repeated readings of the burst screen (plain logic, no Android). */
class BurstScanLogicTest {
    @Test fun quantityStaysBetweenOneAndTheShelf() {
        assertEquals(1.0, BurstQty.initial(8.0), 0.0)
        assertEquals(0.5, BurstQty.initial(0.5), 0.0)
        assertEquals(1.0, BurstQty.initial(0.0), 0.0)
        assertEquals(3.0, BurstQty.more(2.0, 3.0), 0.0)
        assertEquals(3.0, BurstQty.more(3.0, 3.0), 0.0)
        assertEquals(2.5, BurstQty.more(2.5, 3.0), 0.0)
        assertEquals(1.0, BurstQty.less(2.0), 0.0)
        assertEquals(1.0, BurstQty.less(1.0), 0.0)
        assertEquals(0.5, BurstQty.less(0.5), 0.0)
        assertEquals(1.0, BurstQty.less(1.5), 0.0)
    }

    @Test fun labelHeldInViewCountsOnce() {
        val gate = ScanGate(gapMs = 2000)
        assertTrue(gate.accept("A", 0))
        // Same label seen every 300 ms for 6 seconds: still one reading.
        var t = 300L
        while (t <= 6000) { assertFalse(gate.accept("A", t)); t += 300 }
        // Out of view for more than the gap, then back: a new reading.
        assertTrue(gate.accept("A", 6000 + 2001))
        assertFalse(gate.accept("A", 6000 + 2500))
    }

    @Test fun differentLabelsDoNotBlockEachOther() {
        val gate = ScanGate(gapMs = 2000)
        assertTrue(gate.accept("A", 0))
        assertTrue(gate.accept("B", 100))
        assertFalse(gate.accept("A", 200))
        assertFalse(gate.accept("B", 300))
    }

    @Test fun manyLabelsDoNotGrowWithoutLimit() {
        val gate = ScanGate(gapMs = 10)
        repeat(500) { assertTrue(gate.accept("L$it", it * 100L)) }
        assertTrue(gate.accept("L0", 500 * 100L))
    }
}
