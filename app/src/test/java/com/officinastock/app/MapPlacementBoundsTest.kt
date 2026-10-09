package com.officinastock.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapPlacementBoundsTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var db:StockDb
    private var warehouse=0L
    @Before fun setup(){
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        warehouse=db.createWarehouse(db.createProfile("Mappe","1234"),"Garage","")
    }
    @After fun clean(){db.close()}

    @Test fun draggedAndRotatedFurnitureStaysInsideRoom() {
        val id=db.addPlacement(warehouse,"Scaffale","A1",0f,0f)
        val first=db.placements(warehouse).single()
        assertEquals(1f,first.x,.001f)
        assertEquals(.375f,first.y,.001f)
        assertTrue(db.movePlacement(id,0f,0f))
        db.updatePlacementGeometry(id,2f,.75f,90f)
        val rotated=db.placements(warehouse).single()
        assertEquals(.375f,rotated.x,.001f)
        assertEquals(1f,rotated.y,.001f)
        assertTrue(db.movePlacement(id,0f,0f))
        assertEquals(rotated,db.placements(warehouse).single())
        db.lockPlacement(id,true)
        assertFalse(db.movePlacement(id,11f,7f))
        val stillLocked=db.placements(warehouse).single()
        assertEquals(rotated.x,stillLocked.x,.001f)
        assertEquals(rotated.y,stillLocked.y,.001f)
    }

    @Test fun geometryAndRoomResizeRejectClippedFixtures() {
        val id=db.addPlacement(warehouse,"Scaffale","B1",6f,4f)
        assertTrue(runCatching{db.updateMapSize(warehouse,6f,8f)}.isFailure)
        assertTrue(runCatching{db.updatePlacementGeometry(id,15f,15f,0f)}.isFailure)
        assertEquals(2f,db.placements(warehouse).single().width,.001f)
        db.updatePlacementGeometry(id,2f,.75f,45f)
        assertTrue(PlanGeometry.placementFitsRoom(db.placements(warehouse).single(),12f,8f))
        assertTrue(db.movePlacement(id,1f,1f))
        db.updateMapSize(warehouse,6f,8f)
        assertEquals(6f,db.warehouses(db.profiles().single().id).single().mapWidth,.001f)
    }
}
