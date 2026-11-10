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

    @Test fun mapGuideShowsVisitedNextAndWhereTheWalkComesFrom() {
        val places = listOf(shelf(1, "A", 0f, 0f), shelf(2, "B", 5f, 0f), shelf(3, "C", 9f, 0f))
        val items = listOf(item(1, "Vite", 1), item(2, "Dado", 2), item(3, "Rondella", 3), item(4, "Bullone", 1))
        // Vite (A) taken; Bullone (A), Dado (B), Rondella (C) still to take: A is not "visited" yet.
        val lines = listOf(PickLine(4, 1.0), PickLine(2, 1.0), PickLine(3, 1.0), PickLine(1, 1.0, done = true))
        val start = PickRoute.guide(lines, items, places)
        assertEquals(1L, start.next!!.id); assertTrue(start.done.isEmpty()); assertNull(start.from)
        // Bullone taken too: A is visited, B is next, the stretch goes from A to B.
        val later = PickRoute.guide(listOf(PickLine(2, 1.0), PickLine(3, 1.0), PickLine(1, 1.0, done = true), PickLine(4, 1.0, done = true)), items, places)
        assertEquals(setOf(1L), later.done); assertEquals(2L, later.next!!.id); assertEquals(1L, later.from!!.id)
        assertEquals(listOf("Dado", "Rondella"), PickRoute.order(listOf(PickLine(2, 1.0), PickLine(3, 1.0), PickLine(1, 1.0, done = true)), items, places).map { it.item.name })
        assertTrue(PickLists.finished(listOf(PickLine(1, 1.0, done = true))).isEmpty())
        assertEquals(PickGuide(), PickRoute.guide(emptyList(), items, places))
    }

    @Test fun stretchTurnsAtTheCornerThatCrossesFewerStructures() {
        // From A (0,0) to B (6,4). A shelf sits on the horizontal leg, at (3,0): the corner goes the other way.
        val a = shelf(1, "A", 0f, 0f); val b = shelf(2, "B", 6f, 4f); val obstacle = shelf(3, "X", 3f, 0f)
        assertEquals(PointMetres(0f, 4f), PickRoute.elbow(a, b, listOf(a, b, obstacle)))
        // Nothing in the way: horizontal first.
        assertEquals(PointMetres(6f, 0f), PickRoute.elbow(a, b, listOf(a, b)))
        assertEquals("B \u00b7 ora", TodayTasks.who("B", "ora")); assertEquals("ora", TodayTasks.who(null, "ora"))
    }

    @Test fun firstScreenCountsToolsOutForTooLong() {
        val day = 86_400_000L
        val now = 100 * day
        val loans = listOf(Loan(1, 1, "Luca", 1.0, now - 8 * day, null), Loan(2, 1, "Sara", 1.0, now - 2 * day, null), Loan(3, 1, "Ivo", 1.0, now - 30 * day, now - day))
        assertEquals(1, TodayTasks.overdueLoans(loans, now))
        assertEquals(2, TodayTasks.overdueLoans(loans, now, days = 1))
        assertEquals("+3", TodayTasks.signed(3.0)); assertTrue(TodayTasks.signed(-2.0).endsWith("2"))
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

    @Test fun shelfCheckTellsFoundMissingAndMisplaced() {
        val items = listOf(item(1, "Vite", 1), item(2, "Dado", 1), item(3, "Rondella", 2), item(4, "Senza posto", null))
        // Before the label of the structure is read: only what was seen.
        val open = ShelfChecks.summary(ShelfCheck(null, setOf(1L, 3L)), items)
        assertEquals(listOf("Rondella", "Vite"), open.found.map { it.name }); assertTrue(open.missing.isEmpty()); assertTrue(open.strangers.isEmpty())
        // Structure 1: Vite seen, Dado not seen, Rondella and "Senza posto" are there but belong elsewhere.
        val onA = ShelfChecks.summary(ShelfCheck(1, setOf(1L, 3L, 4L)), items)
        assertEquals(listOf("Vite"), onA.found.map { it.name })
        assertEquals(listOf("Dado"), onA.missing.map { it.name })
        assertEquals(listOf("Rondella", "Senza posto"), onA.strangers.map { it.name })
    }
}
