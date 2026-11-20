package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

/** 4.20: search, walk, front view, names and the turned plan, without Android. */
class MapFindTest {
    private fun item(id: Long, name: String, place: Long? = null, level: Int? = null, slot: Int? = null, sku: String = "ELE-%06d".format(id)) =
        StockItem(id, 1, sku, name, "Elettrico", 3.0, 0.0, "pz", place, "", shelfLevel = level, shelfSlot = slot)
    private fun shelf(id: Long, label: String, x: Float, y: Float, kind: String = "Scaffale", levels: Int = 1, slots: Int = 1, uid: String? = null) =
        Placement(id, 1, kind, label, x, y, false, 1.25f, .65f, 0f, levels, slots, uid)

    @Test fun labelsOfStructuresCarryTheTeamIdAndOldOnesStillWork() {
        val uid = "0123456789abcdef0123456789ABCDEF"
        val v2 = ArticleCodes.locationPayload(12, 345, uid)
        assertEquals("UBICORA-LOC:2:W12:U0123456789abcdef0123456789abcdef", v2)
        assertEquals(ArticleCodes.LocationTarget(12, null, uid.lowercase()), ArticleCodes.parseLocationScan(v2))
        assertNull("a shelf label is never an article", ArticleCodes.parseScan(v2))
        // Without a valid id the label stays version 1, readable by older versions.
        assertEquals("UBICORA-LOC:1:W12:P345", ArticleCodes.locationPayload(12, 345, "not-an-id"))
        assertEquals(ArticleCodes.LocationTarget(12, 345), ArticleCodes.parseLocationScan(ArticleCodes.locationPayload(12, 345)))
        assertNull(ArticleCodes.parseLocationScan("UBICORA-LOC:2:W12:U1234"))
        assertNull(ArticleCodes.parseLocationScan("UBICORA-LOC:2:W0:U$uid"))
        // On a satellite the local number differs: the id finds the same shelf.
        val here = listOf(shelf(7, "sc2", 1f, 1f, uid = uid.lowercase()), shelf(345, "altro", 2f, 2f))
        assertEquals(7L, ArticleCodes.resolve(ArticleCodes.parseLocationScan(v2)!!, here)?.id)
        assertEquals(345L, ArticleCodes.resolve(ArticleCodes.LocationTarget(12, 345), here)?.id)
        assertNull(ArticleCodes.resolve(ArticleCodes.LocationTarget(12, null, "f".repeat(32)), here))
    }

    @Test fun turnedPlanMapsBothWaysAndFillsAPortraitScreen() {
        val cam = PlanViewport(390f, 528f, 20f, 12.2f, 1.4f, 30f, -20f, 20f, turned = true)
        for (p in listOf(PointMetres(0f, 0f), PointMetres(20f, 12.2f), PointMetres(3.5f, 9f))) {
            val back = cam.toWorld(cam.toScreen(p))
            assertEquals(p.x, back.x, .001f); assertEquals(p.y, back.y, .001f)
        }
        // A finger movement converted to metres lands where the finger is.
        val start = PointMetres(5f, 5f); val s = cam.toScreen(start)
        val d = cam.toWorldDelta(40f, -25f)
        val moved = cam.toScreen(PointMetres(start.x + d.x, start.y + d.y))
        assertEquals(s.x + 40f, moved.x, .01f); assertEquals(s.y - 25f, moved.y, .01f)
        // The long side of the room goes down the screen.
        assertTrue(cam.toScreen(PointMetres(20f, 6f)).y > cam.toScreen(PointMetres(0f, 6f)).y)
        assertTrue(PlanViewport.shouldTurn(390f, 528f, 20f, 12.2f, 20f))
        assertFalse(PlanViewport.shouldTurn(390f, 528f, 10f, 10f, 20f))
        assertFalse(PlanViewport.shouldTurn(390f, 528f, 6f, 12f, 20f))
        // Nothing turned: the same numbers as before 4.20.
        val flat = PlanViewport(900f, 700f, 12f, 8f)
        assertEquals(PointMetres(flat.originX + 2f * flat.scale, flat.originY + 3f * flat.scale), flat.toScreen(PointMetres(2f, 3f)))
    }

    @Test fun fittingFramesTheBoxAndStaysWithinLimits() {
        for (turned in listOf(false, true)) {
            val cam = PlanViewport(390f, 500f, 20f, 12.2f, 1f, 0f, 0f, 20f, turned)
            val fit = cam.fitting(17f, 3f, 19f, 5f, maxZoom = 2.5f)
            assertTrue(fit.zoom in 1f..2.5f)
            for (corner in listOf(PointMetres(17f, 3f), PointMetres(19f, 5f))) {
                val p = fit.toScreen(corner)
                assertTrue("$turned $p", p.x in 0f..390f && p.y in 0f..500f)
            }
            // A box as large as the room never zooms out below the whole plan.
            assertEquals(1f, cam.fitting(-5f, -5f, 30f, 30f).zoom, .001f)
        }
    }

    @Test fun searchNeedsEveryWordAndListsPlacedArticlesFirst() {
        val places = listOf(shelf(1, "sc2", 1f, 1f), shelf(2, "2", 4f, 4f), shelf(3, "ingresso", 0f, 6f, kind = "Porta"))
        val items = listOf(item(1, "sensore m12 conn"), item(2, "connettore M12 F 90°", 1), item(3, "connettore M12 F dritto", 1),
            item(4, "connettore M12 M dritto", 2), item(5, "Fusibile 16A"), item(6, "Vite M12", 99))
        val r = MapSearch.results("m12", items, places)
        assertEquals(listOf(2L, 3L, 4L, 1L, 6L), r.items.map { it.id })   // 6 points to a deleted shelf: no place
        assertEquals(mapOf(1L to 2, 2L to 1), r.countByPlacement)
        assertEquals(setOf(1L, 2L), r.lit)
        assertEquals(listOf(2L), MapSearch.results("m12 90", items, places).items.map { it.id })
        assertEquals(listOf(1L), MapSearch.results("SC2", items, places).structures.map { it.id })
        assertTrue("doors are not shelves", MapSearch.results("ingresso", items, places).structures.isEmpty())
        assertTrue(MapSearch.results("  ", items, places).isEmpty)
        assertTrue(MapSearch.matches(item(9, "Raccordo", sku = "PNE-000004"), "pne-0000"))
    }

    @Test fun walkStartsFromTheEntranceAndSaysHowFar() {
        val target = shelf(1, "sc2", 18f, 4f)
        val back = shelf(2, "retro", 10f, 0.2f, kind = "Porta")
        val entrance = shelf(3, "Ingresso", 19.8f, 9f, kind = "Porta")
        val places = listOf(target, back, entrance)
        assertEquals(3L, MapRoute.entrance(places, target)?.id)
        val walk = MapRoute.walk(places, target)!!
        // 4.20.1: from the middle of the opening (hinge corner + half the leaf along the wall), not from the leaf.
        assertEquals(19.175f, walk.start.x, .001f); assertEquals(8.7f, walk.start.y, .001f)
        assertEquals(1.175f + 4.7f, walk.metres, .01f)
        assertEquals("Circa 6 m dall'ingresso", MapRoute.describe(walk))
        // No door named "ingresso": the nearest one, by its own name.
        val near = MapRoute.walk(listOf(target, back), target)!!
        assertEquals("Circa 13 m da retro", MapRoute.describe(near))
        assertNull(MapRoute.walk(listOf(target), target))
        // A sliding gate is drawn across the opening: the walk starts from its centre, and it counts as an entrance.
        val gate = Placement(9, 1, "Cancello", "cancello", 10f, 12f, false, 4f, .2f, 0f)
        val gateWalk = MapRoute.walk(listOf(target, gate), target)!!
        assertEquals(PointMetres(10f, 12f), gateWalk.start)
        assertEquals("Circa 16 m dal cancello", MapRoute.describe(gateWalk))
        assertTrue(PlanGeometry.collisions(gate, listOf(target, gate)).isEmpty())
        // Where it is, in full words.
        val sc2 = shelf(4, "sc2", 1f, 1f, levels = 2, slots = 3)
        assertEquals("Scaffale sc2 · Ripiano 2 · Posizione 1", MapRoute.placeText(sc2, item(1, "A", 4, 2, 1)))
        assertEquals("Scaffale 3 · Ripiano 1", MapRoute.placeText(shelf(5, "Scaffale 3", 1f, 1f, levels = 2), item(2, "B", 5, 1, 4)))
        assertEquals("Banco", MapRoute.placeText(shelf(6, "Banco", 1f, 1f, kind = "Banco"), item(3, "C", 6, 9, 9)))
    }

    @Test fun frontViewGoesFromTheTopShelfAndKeepsArticlesWithoutAShelf() {
        val p = shelf(1, "sc2", 1f, 1f, levels = 2, slots = 3)
        val items = listOf(item(1, "A", 1, 2, 1), item(2, "B", 1, 1, 3), item(3, "C", 1, 2, null), item(4, "D", 1, null, null),
            item(5, "E", 1, 7, 1), item(6, "Altrove", 2, 1, 1))
        val view = ShelfLayout.of(p, items)
        assertEquals(listOf(2, 1), view.rows.map { it.level })
        assertEquals(listOf(listOf(1L), emptyList(), emptyList()), view.rows[0].cells.map { c -> c.items.map { it.id } })
        assertEquals(listOf(3L), view.rows[0].withoutSlot.map { it.id })
        assertEquals(listOf(2L), view.rows[1].cells[2].items.map { it.id })
        assertEquals(listOf(4L, 5L), view.loose.map { it.id })   // shelf 7 does not exist any more
        assertEquals(5, view.count)
        assertEquals("Ripiano 2 · in alto", ShelfLayout.rowTitle(view.rows[0], 2))
        assertEquals(2, view.rows[0].cells[0].level); assertEquals(1, view.rows[0].cells[0].slot)
        // A bench has one place: everything in it, no shelf or position to give.
        val bench = ShelfLayout.of(shelf(2, "Banco", 1f, 1f, kind = "Banco"), items)
        assertEquals(1, bench.rows.single().cells.size)
        assertEquals(listOf(6L), bench.rows.single().cells.single().items.map { it.id })
        assertNull(bench.rows.single().cells.single().level)
        // One shelf with several positions: the cells carry only the position.
        val row = ShelfLayout.of(shelf(3, "x", 0f, 0f, slots = 2), listOf(item(7, "F", 3, null, 2)))
        assertEquals(listOf(null), row.rows.map { it.level })
        assertEquals(listOf(7L), row.rows.single().cells[1].items.map { it.id })
        assertEquals("Contenuto", ShelfLayout.rowTitle(row.rows.single(), 1))
    }

    @Test fun namesGoBesideStructuresAndNeverOverThem() {
        val turned = MapLabels.footprint(100f, 100f, 60f, 20f, 90f)
        assertEquals(20f, turned.right - turned.left, .01f); assertEquals(60f, turned.bottom - turned.top, .01f)
        val box = ScreenBox(100f, 100f, 120f, 160f)
        val right = MapLabels.place(box, 40f, 20f, 6f, listOf(box), 400f, 400f)!!
        assertEquals(ScreenBox(126f, 120f, 166f, 140f), right)
        // Right taken by a neighbour: the name goes to the left.
        val neighbour = ScreenBox(124f, 100f, 150f, 160f)
        val left = MapLabels.place(box, 40f, 20f, 6f, listOf(box, neighbour), 400f, 400f)!!
        assertTrue(left.right <= box.left)
        // Against the screen edge and surrounded: no place, the name is left out.
        val edge = ScreenBox(0f, 0f, 20f, 400f)
        assertNull(MapLabels.place(edge, 40f, 20f, 6f, listOf(edge, ScreenBox(20f, 0f, 400f, 400f)), 400f, 400f))
    }

    @Test fun middleOfADoorOpeningFollowsItsRotation() {
        val door = Placement(1, 1, "Porta", "ingresso", 5f, 5f, false, 1f, .2f, 0f)
        assertEquals(4.5f, PlanGeometry.passageMid(door).x, .001f); assertEquals(4.6f, PlanGeometry.passageMid(door).y, .001f)
        val turned = PlanGeometry.passageMid(door.copy(rotation = 90f))
        assertEquals(5.4f, turned.x, .001f); assertEquals(4.5f, turned.y, .001f)
        val back = PlanGeometry.passageMid(door.copy(rotation = 180f))
        assertEquals(5.5f, back.x, .001f); assertEquals(5.4f, back.y, .001f)
        assertTrue(PlacementKinds.isPassage("Cancello") && !PlacementKinds.holdsStock("Cancello"))
        assertEquals(4.0f to .2f, PlanGeometry.defaultSize("Cancello"))
    }

    @Test fun walkGoesAroundStructuresAndInnerWalls() {
        val gate = Placement(1, 1, "Cancello", "ingresso", 5f, 9.9f, false, 2f, .2f, 0f)
        val target = shelf(2, "sc2", 5f, 2f)
        val block = Placement(3, 1, "Banco", "banco", 5f, 6f, false, 6f, .5f, 0f)
        val walk = MapRoute.walk(listOf(gate, target, block), target, emptyList(), 10f, 10f)!!
        assertEquals(PointMetres(5f, 9.9f), walk.start)
        assertEquals(PointMetres(5f, 2f), walk.path.last())
        assertTrue("around the bench, longer than straight", walk.metres > 7.9f)
        for ((a, b) in walk.path.zipWithNext()) for (k in 0..40) {
            val t = k / 40f
            val pt = PointMetres(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            assertFalse("crosses the bench at $pt", PlanGeometry.contains(pt, block, 0f))
        }
        // An inner wall across most of the room: the walk goes round its free end.
        val wall = Wall(1, 1, 0f, 5f, 8f, 5f, "manual")
        val round = MapRoute.walk(listOf(gate, target), target, listOf(wall), 10f, 10f)!!
        assertTrue(round.path.any { it.x > 8f })
        // Shut in by walls: no way, the right-angled hint of before.
        val box = listOf(Wall(2, 1, 3f, 1f, 7f, 1f, "m"), Wall(3, 1, 7f, 1f, 7f, 3f, "m"), Wall(4, 1, 7f, 3f, 3f, 3f, "m"), Wall(5, 1, 3f, 3f, 3f, 1f, "m"))
        assertEquals(3, MapRoute.walk(listOf(gate, target), target, box, 10f, 10f)!!.path.size)
    }

    @Test fun aWallBecomesSmallBoxesAlongItSoNamesStayOffIt() {
        val boxes = MapLabels.wall(0f, 0f, 100f, 0f, 5f)
        assertTrue(boxes.size >= 10)
        assertTrue(boxes.any { it.intersects(ScreenBox(48f, -2f, 52f, 2f)) })          // on the wall
        assertFalse(boxes.any { it.intersects(ScreenBox(40f, 10f, 60f, 30f)) })        // just beside it: free
        // A diagonal wall does not block the empty corner of its bounding box.
        assertFalse(MapLabels.wall(0f, 0f, 100f, 100f, 5f).any { it.intersects(ScreenBox(70f, 0f, 100f, 20f)) })
    }
}
