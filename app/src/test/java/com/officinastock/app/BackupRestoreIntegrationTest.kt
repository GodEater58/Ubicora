package com.officinastock.app

import android.net.Uri
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/** End-to-end logical archive test. Robolectric is NOT a physical-device backup test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BackupRestoreIntegrationTest {
    @Test fun failedBackupLeavesExistingDestinationUntouched() {
        val target=File(context.cacheDir,"previous-backup.osbackup")
        val previous="existing encrypted backup".toByteArray()
        target.writeBytes(previous)
        try {
            val backup=BackupArchive(context,db)
            assertTrue(runCatching{backup.export(Uri.fromFile(target),"short")}.isFailure)
            assertArrayEquals(previous,target.readBytes())
            val profile=db.createProfile("Backup test","1234")
            val warehouse=db.createWarehouse(profile,"Deposito","")
            db.addItem(warehouse,"Foto mancante","Ricambi",1.0,0.0,"pz","",
                photoFile="item_00000000-0000-0000-0000-000000000000.jpg")
            assertTrue(runCatching{backup.export(Uri.fromFile(target),"valid-password-1234")}.isFailure)
            assertArrayEquals(previous,target.readBytes())
        } finally {target.delete()}
    }
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var db: StockDb
    private lateinit var backup: BackupArchive
    private lateinit var file: File
    private var photoName: String? = null

    @Before fun prepare() {
        context.deleteDatabase("officina_stock_native4.db")
        db=StockDb(context)
        backup=BackupArchive(context,db)
        file=File(context.cacheDir,"backup-integration-${UUID.randomUUID()}.osbackup")
    }

    @After fun clean() {
        db.close()
        file.delete()
        photoName?.let {File(context.filesDir,"article_photos/$it").delete()}
    }

    @Test fun encryptedArchiveRestoresProfilesLocationsPhotosLoansAndHistoryAtomically() {
        val alice=db.createProfile("Personale","1357")
        val bob=db.createProfile("Officina","2468")
        val home=db.createWarehouse(alice,"Garage","Beverare")
        val shop=db.createWarehouse(bob,"Magazzino","Zona industriale")
        val shelf=db.addPlacement(home,"Scaffale","A1",3f,2f)
        val first=db.addItem(home,"Cuscinetto","Ricambi",15.0,4.0,"pz","Revisione")
        db.assignItem(first,shelf)
        db.addWall(home,floatArrayOf(0.5f,0.5f,5f,0.5f))
        val second=db.addItem(shop,"Multimetro","Utensili",3.0,1.0,"pz","")
        db.lend(second,"Tecnico",1.0)
        val originalPhotoName="item_${UUID.randomUUID()}.jpg"
        photoName=originalPhotoName
        val image=File(context.filesDir,"article_photos/$originalPhotoName")
        image.parentFile!!.mkdirs()
        val photoBytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),1,2,3,0xff.toByte(),0xd9.toByte())
        image.writeBytes(photoBytes)
        db.updatePhoto(first,originalPhotoName)
        val expectedHistoryHome=db.movements(home).map { Triple(it.itemId,it.delta,it.note) }
        val expectedHistoryShop=db.movements(shop).map { Triple(it.itemId,it.delta,it.note) }

        backup.export(Uri.fromFile(file),"valid-password-1234")
        assertTrue("Il file cifrato deve essere presente",file.length()>200)
        val encrypted=file.readBytes()

        db.movement(first,-5.0,"Prelievo dopo backup")
        val extra=db.addItem(shop,"Temporary","Ricambi",4.0,0.0,"pz","")
        assertNotNull(db.item(extra))
        val mutatedCount=db.items(shop).size

        // Password errata: il database deve rimanere intatto, senza restore parziale.
        assertTrue(runCatching {backup.restore(Uri.fromFile(file),"wrong-password")}.isFailure)
        assertEquals(mutatedCount,db.items(shop).size)
        assertEquals(10.0,db.item(first)!!.quantity,0.0)

        // Backup manomesso: rifiutato e dati mantenuti.
        val corrupt=encrypted.clone().also {it[it.size/2]=(it[it.size/2].toInt() xor 1).toByte()}
        file.writeBytes(corrupt)
        assertTrue(runCatching {backup.restore(Uri.fromFile(file),"valid-password-1234")}.isFailure)
        assertEquals(mutatedCount,db.items(shop).size)

        file.writeBytes(encrypted)
        backup.restore(Uri.fromFile(file),"valid-password-1234")
        assertEquals(2,db.profiles().size)
        assertEquals("Garage",db.warehouses(alice).single().name)
        assertEquals("Magazzino",db.warehouses(bob).single().name)
        assertEquals(15.0,db.item(first)!!.quantity,0.0)
        assertNull("Il nuovo articolo non era nel backup",db.item(extra))
        assertEquals(shelf,db.item(first)!!.placementId)
        assertEquals("A1",db.placements(home).single().label)
        assertEquals(1,db.walls(home).size)
        assertEquals(1,db.activeLoans(shop).size)
        assertEquals(2.0,db.item(second)!!.quantity,0.0)
        assertEquals(expectedHistoryHome,db.movements(home).map {Triple(it.itemId,it.delta,it.note)})
        assertEquals(expectedHistoryShop,db.movements(shop).map {Triple(it.itemId,it.delta,it.note)})
        val restoredPhoto=db.item(first)!!.photoFile!!
        assertNotEquals(originalPhotoName,restoredPhoto)
        assertArrayEquals(photoBytes,File(context.filesDir,"article_photos/$restoredPhoto").readBytes())
        assertFalse(image.exists())
        File(context.filesDir,"article_photos/$restoredPhoto").delete()
        db.validateRestoredData()
    }
}

