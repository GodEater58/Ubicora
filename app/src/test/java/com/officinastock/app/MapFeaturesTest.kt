package com.officinastock.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.7 map: shelf positions, landmarks, duplicates, undo/redo, geometry helpers, shelf QR. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapFeaturesTest {
    private lateinit var db: StockDb
    private var warehouse = 0L
    @Before fun setup() {
        val c = RuntimeEnvironment.getApplication()
        c.deleteDatabase("officina_stock_native4.db")
        c.getSharedPreferences("login_attempts", Context.MODE_PRIVATE).edit().clear().commit()
        db = StockDb(c)
        val profile = db.createProfile("Test", "1234")
        warehouse = db.createWarehouse(profile, "A", "")
    }
    @After fun close() { db.close() }
    private fun item(name: String = "Vite") = db.addItem(warehouse, name, "Ricambi", 5.0, 1.0, "pz", "")
    private fun rejects(block: () -> Unit) { try { block(); fail("Operazione non valida accettata") } catch (expected: IllegalArgumentException) {} }

    @Test fun shelfLevelsAndPositionsAreValidatedAndPersisted() {
        val shelf = db.addPlacement(warehouse, "Scaffale", "A", 3f, 3f)
        val first = item("Bullone"); val second = item("Dado")
        rejects { db.assignItem(first, shelf, 2, null) } // only 1 level so far
        db.updatePlacementLayout(shelf, 4, 6)
        db.assignItems(listOf(first, second), shelf, 3, 5)
        val stored = db.item(first)!!
        assertEquals(3, stored.shelfLevel); assertEquals(5, stored.shelfSlot)
        assertEquals("A · R3 P5", Locations.describe(db.placement(shelf), stored))
        rejects { db.updatePlacementLayout(shelf, 2, 6) } // would orphan items on level 3
        rejects { db.assignItem(first, shelf, 5, null) }
        db.assignItem(first, null)
        assertNull(db.item(first)!!.shelfLevel)
        rejects { db.assignItems(listOf(first), null, 1, null) }
    }

    @Test fun landmarksNeverHoldStock() {
        val door = db.addPlacement(warehouse, "Porta", "Ingresso", 1f, 1f)
        val id = item()
        rejects { db.assignItem(id, door) }
        rejects { db.updatePlacementLayout(door, 2, 1) }
        rejects { db.addPlacement(warehouse, "Navicella", "X", 1f, 1f) }
        assertFalse(db.placement(door)!!.holdsStock)
    }

    @Test fun deletingStructureClearsPositionAndUndoRestoresEverything() {
        val shelf = db.addPlacement(warehouse, "Scaffale", "B", 4f, 4f)
        db.updatePlacementLayout(shelf, 3, 3)
        val id = item()
        db.assignItem(id, shelf, 2, 1)
        val delete = MapEdit.Created(listOf(shelf)).revert(db)
        assertNull(db.placement(shelf))
        val orphan = db.item(id)!!
        assertNull(orphan.placementId); assertNull(orphan.shelfLevel); assertNull(orphan.shelfSlot)
        val redo = delete.revert(db) // undo of delete
        assertEquals(3, db.placement(shelf)!!.levels)
        val back = db.item(id)!!
        assertEquals(shelf, back.placementId); assertEquals(2, back.shelfLevel); assertEquals(1, back.shelfSlot)
        redo.revert(db)
        assertNull(db.placement(shelf))
    }

    @Test fun moveAndWallEditsAreReversible() {
        val shelf = db.addPlacement(warehouse, "Scaffale", "C", 3f, 3f)
        db.movePlacement(shelf, 6f, 4f)
        val after = db.placement(shelf)!!
        val back = MapEdit.Moved(shelf, 3f, 3f, after.x, after.y).revert(db)
        assertEquals(3f, db.placement(shelf)!!.x, .001f)
        back.revert(db)
        assertEquals(after.x, db.placement(shelf)!!.x, .001f)

        val wall = db.addWall(warehouse, floatArrayOf(1f, 1f, 4f, 1f))
        val before = db.wall(wall)!!
        db.updateWall(wall, PlanGeometry.withLength(before, 5f))
        assertEquals(5f, PlanGeometry.wallLength(db.wall(wall)!!), .001f)
        MapEdit.WallChanged(wall, floatArrayOf(before.x1, before.y1, before.x2, before.y2)).revert(db)
        assertEquals(3f, PlanGeometry.wallLength(db.wall(wall)!!), .001f)
        val deleted = MapEdit.WallsAdded(listOf(wall)).revert(db)
        assertNull(db.wall(wall))
        deleted.revert(db)
        assertEquals("manual", db.wall(wall)!!.source)
        rejects { db.updateWall(wall, floatArrayOf(1f, 1f, 40f, 1f)) } // outside the 12 m room
    }

    @Test fun duplicatesAndRowsUseNextLabelsAndStayInsideRoom() {
        val shelf = db.addPlacement(warehouse, "Scaffale", "Scaffale A", 1.5f, 2f)
        db.updatePlacementLayout(shelf, 5, 4)
        val copy = db.placement(db.duplicatePlacement(shelf, 5f, 2f))!!
        assertEquals("Scaffale B", copy.label); assertEquals(5, copy.levels); assertEquals(4, copy.slots)
        val row = db.createPlacementRow(shelf, 3, .5f, true)
        assertEquals(listOf("Scaffale B", "Scaffale C", "Scaffale D"), row.map { db.placement(it)!!.label })
        val before = db.placements(warehouse).size
        rejects { db.createPlacementRow(shelf, 20, 1f, false) } // 20 shelves of 2 m cannot fit in 12 m
        assertEquals(before, db.placements(warehouse).size)
    }

    @Test fun geometryHelpers() {
        assertEquals("Scaffale B", PlanGeometry.nextLabel("Scaffale A"))
        assertEquals("S10", PlanGeometry.nextLabel("S9"))
        assertEquals("R007", PlanGeometry.nextLabel("R006"))
        assertEquals("Banco 2", PlanGeometry.nextLabel("Banco"))
        val a = Placement(1, 1, "Scaffale", "A", 2f, 2f, false, 2f, 1f, 0f)
        val touching = a.copy(id = 2, x = 4f)
        val crossing = a.copy(id = 3, x = 3f)
        val rotated = a.copy(id = 4, x = 2f, y = 3.2f, rotation = 90f)
        assertFalse(PlanGeometry.overlaps(a, touching))
        assertTrue(PlanGeometry.overlaps(a, crossing))
        assertTrue(PlanGeometry.overlaps(a, rotated))
        assertTrue(PlanGeometry.collisions(a, listOf(a.copy(id = 9, kind = "Corridoio", x = 2.5f))).isEmpty())
        val near = a.copy(id = 5, x = 4.1f)
        assertEquals(4f, PlanGeometry.snapToNeighbors(near, listOf(a, near), emptyList()).x, .001f)
        val wall = Wall(1, 1, 0f, 0f, 4f, 0f, "manual")
        assertEquals(1f, PlanGeometry.distanceToWall(PointMetres(2f, 1f), wall), .001f)
        assertEquals(3, PlanGeometry.rowCentres(a, 3, 1f, false).size)
    }

    @Test fun shelfQrRoundTripAndIsNotAnArticleCode() {
        val payload = ArticleCodes.locationPayload(12, 345)
        assertEquals(ArticleCodes.LocationTarget(12, 345), ArticleCodes.parseLocationScan(payload))
        assertNull(ArticleCodes.parseScan(payload))
        assertNull(ArticleCodes.parseLocationScan("OFFICINA-STOCK:1:W12:RIC-000001"))
        assertNull(ArticleCodes.parseLocationScan("UBICORA-LOC:1:W0:P1"))
    }

    @Test fun activityCountsMovementsPerStructure() {
        val shelf = db.addPlacement(warehouse, "Scaffale", "D", 3f, 3f)
        val id = item()
        db.assignItem(id, shelf)
        db.movement(id, -1.0, "Prelievo")
        val counts = db.placementActivity(warehouse, 0L)
        assertEquals(2, counts[shelf]) // initial load + withdrawal
    }
}
