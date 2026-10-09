package com.officinastock.app

import android.database.Cursor
import android.net.Uri
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A real schema-1 encrypted archive with nine pre-4.3 tables must remain recoverable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LegacyBackupCompatibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: StockDb
    private lateinit var archive: File
    private val oldTables = listOf("profiles","warehouses","placements","items",
        "movements","loans","walls","sku_sequences","demo_assets")

    @Before fun prepare() {
        context.deleteDatabase("officina_stock_native4.db")
        db = StockDb(context)
        archive = File(context.cacheDir, "ubicora-legacy-fixture.osbackup")
    }
    @After fun clean() { db.close(); archive.delete() }

    private fun makeLegacyArchive() {
        BackupCipher.encrypt(archive.outputStream(),"legacy-password").use { encrypted ->
            ZipOutputStream(encrypted).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(JSONObject().put("app","OfficinaStock").put("schema",1)
                    .put("tables",oldTables.size).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for(table in oldTables) {
                    zip.putNextEntry(ZipEntry("db/$table.ndjson"))
                    db.readableDatabase.rawQuery("SELECT * FROM $table",null).use { c ->
                        while(c.moveToNext()) {
                            val obj=JSONObject()
                            for(i in 0 until c.columnCount) {
                                val value:Any=when(c.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                                    else -> error("Unexpected BLOB")
                                }
                                obj.put(c.getColumnName(i),value)
                            }
                            zip.write((obj.toString()+"\n").toByteArray(Charsets.UTF_8))
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    @Test fun restoreOldEncryptedBackupClearsManagerDataAndPreservesLegacyInventory() {
        val profile=db.createProfile("Legacy","1234")
        val warehouse=db.createWarehouse(profile,"Officina","")
        val item=db.addItem(warehouse,"Vite M8","Ricambi",9.0,1.0,"pz","")
        makeLegacyArchive()
        assertTrue(archive.length()>0)

        val ops=OperationsDb(db)
        ops.setBusinessMode(profile,true)
        ops.setMonthlyBudget(profile,350.0)
        ops.createOrder(warehouse,item,5.0,"Test","Manager")
        db.movement(item,-3.0,"After archive")
        assertEquals(6.0,db.item(item)!!.quantity,0.0)

        BackupArchive(context,db).restore(Uri.fromFile(archive),"legacy-password")
        assertEquals(9.0,db.item(item)!!.quantity,0.0)
        assertTrue(ops.orders(warehouse).isEmpty())
        assertTrue(ops.events(warehouse).isEmpty())
        assertFalse(ops.businessMode(profile))
        assertNull(ops.monthlyBudget(profile))
        assertEquals("Officina",db.warehouses(profile).single().name)
        db.validateRestoredData()
    }
}
