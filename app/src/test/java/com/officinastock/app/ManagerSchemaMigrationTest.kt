package com.officinastock.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Simulates upgrading an existing SQLite v6 install to v7 without wiping user data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ManagerSchemaMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: StockDb

    @Before fun setup() {
        context.deleteDatabase("officina_stock_native4.db")
        db = StockDb(context)
    }
    @After fun cleanup() { db.close() }

    @Test fun upgradingExistingDatabasePreservesProfilesStockLoansAndMap() {
        val profile = db.createProfile("Privato", "1234")
        val warehouse = db.createWarehouse(profile, "Garage", "Beverare")
        val shelf = db.addPlacement(warehouse, "Scaffale", "A1", 2f, 2f)
        val item = db.addItem(warehouse,"Cuscinetto", "Ricambi", 10.0, 2.0, "pz", "Originale")
        db.assignItem(item, shelf)
        db.lend(item, "Tecnico", 2.0)
        db.addWall(warehouse, floatArrayOf(0.5f, 0.5f, 4f, 0.5f))
        val before = db.item(item)!!
        val movementCount = db.movements(warehouse).size

        // Existing pre-manager installations had no ops_* tables and schema user_version=6.
        val sqlite = db.writableDatabase
        listOf("ops_orders", "ops_counts", "ops_transfers", "ops_maintenance",
            "ops_events", "ops_settings").forEach {
            sqlite.execSQL("DROP TABLE IF EXISTS $it")
        }
        sqlite.version = 6
        db.close()

        db = StockDb(context)
        val upgraded = db.writableDatabase
        assertEquals(10, upgraded.version)
        assertEquals("Privato", db.profiles().single().name)
        assertTrue(db.authenticate(profile, "1234"))
        assertEquals("Garage", db.warehouses(profile).single().name)
        assertEquals(before, db.item(item))
        assertEquals(shelf, db.placements(warehouse).single().id)
        assertEquals(1, db.walls(warehouse).size)
        assertEquals(1, db.activeLoans(warehouse).size)
        assertEquals(movementCount, db.movements(warehouse).size)

        val operations = OperationsDb(db)
        assertFalse(operations.businessMode(profile))
        val orderId = operations.createOrder(warehouse, item, 3.0, "Acme", "Manager")
        assertEquals(orderId, operations.orders(warehouse).single().id)
        assertEquals(before.quantity, db.item(item)!!.quantity, 0.0)
    }
}
