package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

/** 4.15: order of the guided picking and typed quantities (plain logic, no Android). */
class GuidedPickTest {
    private fun item(id: Long, name: String, place: Long?, level: Int? = null, slot: Int? = null) =
        StockItem(id, 1, "SKU-$id", name, "Ricambi", 10.0, 1.0, "pz", place, "", shelfLevel = level, shelfSlot = slot)
    private fun shelf(id: Long, label: String, x: Float, y: Float) = Placement(id, 1, "Scaffale", label, x, y, false)

    @Test fun walkGoesFromTheFirstStructureToTheNearestOne() {
        // A at the origin, C close to it, B far away: A, then C (nearest), then B.
        val places = listOf(shelf(1, "A", 0f, 0f), shelf(2, "B", 10f, 0f), shelf(3, "C", 2f, 0f))
        val items = listOf(item(11, "Dado", 2), item(12, "Vite", 1), item(13, "Rondella", 3), item(14, "Senza posto", null))
        val lines = items.map { PickLine(it.id, 1.0) }
        val names = PickRoute.order(lines, items, places).map { it.item.name }
        assertEquals(listOf("Vite", "Rondella", "Dado", "Senza posto"), names)
    }

    @Test fun insideAStructureShelvesAndPositionsComeInOrder() {
        val places = listOf(shelf(1, "A", 0f, 0f))
        val items = listOf(item(1, "Terzo", 1, 2, 1), item(2, "Primo", 1, 1, 1), item(3, "Secondo", 1, 1, 4))
        val names = PickRoute.order(items.map { PickLine(it.id, 1.0) }, items, places).map { it.item.name }
        assertEquals(listOf("Primo", "Secondo", "Terzo"), names)
    }

    @Test fun skippedArticlesGoLastAndUnknownOnesDisappear() {
        val places = listOf(shelf(1, "A", 0f, 0f), shelf(2, "B", 5f, 0f))
        val items = listOf(item(1, "Vite", 1), item(2, "Dado", 2))
        val lines = listOf(PickLine(1, 1.0, skipped = true), PickLine(2, 3.0), PickLine(99, 1.0))
        val route = PickRoute.order(lines, items, places)
        assertEquals(listOf("Dado", "Vite"), route.map { it.item.name })
        assertEquals(3.0, route.first().line.quantity, 0.0)
        assertTrue(PickRoute.order(emptyList(), items, places).isEmpty())
    }

    @Test fun typedQuantityAcceptsCommaAndRespectsTheShelf() {
        assertEquals(2.5, QuickQuantity.parse(" 2,5 ")!!, 0.0)
        assertEquals(3.0, QuickQuantity.parse("3")!!, 0.0)
        assertNull(QuickQuantity.parse("0")); assertNull(QuickQuantity.parse("-1")); assertNull(QuickQuantity.parse("abc")); assertNull(QuickQuantity.parse(""))
        assertTrue(QuickQuantity.valid(4.0, load = false, available = 4.0))
        assertFalse(QuickQuantity.valid(4.5, load = false, available = 4.0))
        assertTrue(QuickQuantity.valid(100.0, load = true, available = 4.0))
        assertFalse(QuickQuantity.valid(null, load = true, available = 4.0))
    }
}
