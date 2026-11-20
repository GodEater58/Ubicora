package com.officinastock.app

import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 4.21.2: a new article whose location no longer exists is not saved at all (no duplicate on retry). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AtomicNewItemTest {
    @Test fun missingLocationRollsBackTheArticle() {
        val app=RuntimeEnvironment.getApplication()
        app.deleteDatabase("officina_stock_native4.db")
        val db=StockDb(app)
        try {
            val warehouse=db.createWarehouse(db.createProfile("Test","1234"),"Officina","")
            assertThrows(IllegalArgumentException::class.java) {
                db.atomically {
                    val id=db.addItem(warehouse,"Vite M6","Ricambi",3.0,0.0,"pz","")
                    db.assignItems(listOf(id),999_999L)
                }
            }
            assertTrue(db.items(warehouse).isEmpty())
            val id=db.atomically { db.addItem(warehouse,"Vite M6","Ricambi",3.0,0.0,"pz","") }
            assertEquals(listOf(id),db.items(warehouse).map { it.id })
        } finally { db.close() }
    }
}
