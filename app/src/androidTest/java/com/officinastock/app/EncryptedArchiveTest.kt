package com.officinastock.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 4.12 on a real Android (emulator): the archive is encrypted, an archive from 4.11 is migrated
 * without losing anything, and the plain file does not stay on the phone.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedArchiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "encrypted_archive_test.db"
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private fun file() = context.getDatabasePath(name)
    private fun isPlain(f: File) = f.exists() && f.inputStream().use { i -> ByteArray(16).also { i.read(it) } }.contentEquals(header)

    @Before fun clean() {
        DbCipher.plainForTests = false
        context.deleteDatabase(name)
        for (s in listOf(".enc-tmp", ".plain-old")) File(file().path + s).delete()
    }
    @After fun reset() { DbCipher.plainForTests = false; context.deleteDatabase(name) }

    @Test fun anArchiveFrom411IsMigratedWithEverything() {
        // 1. The archive as 4.11 left it: plain SQLite with real data.
        DbCipher.plainForTests = true
        val before = StockDb(context, name)
        val profile = before.createProfile("Nicola", "1234")
        val warehouse = before.createWarehouse(profile, "Officina", "Rovigo")
        repeat(250) { before.addItem(warehouse, "Vite $it", "Ricambi", (it % 7).toDouble(), 1.0, "pz", "nota $it") }
        val vite = before.items(warehouse).first().id
        before.movement(vite, 3.0, "Carico")
        val items = before.items(warehouse).map { it.sku to it.quantity }.toSet()
        val moves = before.movements(warehouse).size
        assertFalse(before.encrypted)
        before.close()
        assertTrue("prima della 4.12 l'archivio è in chiaro", isPlain(file()))

        // 2. First start of 4.12: migration.
        DbCipher.plainForTests = false
        val after = StockDb(context, name)
        assertTrue("archivio cifrato", after.encrypted)
        assertNull(DbCipher.lastProblem)
        assertEquals(items, after.items(warehouse).map { it.sku to it.quantity }.toSet())
        assertEquals(moves, after.movements(warehouse).size)
        assertEquals(13, after.readableDatabase.version)
        assertTrue("il PIN funziona ancora", after.authenticate(profile, "1234"))
        // Everything keeps working on the encrypted archive.
        after.movement(vite, -1.0, "Prelievo")
        after.close()

        assertFalse("nessun file in chiaro", isPlain(file()))
        assertFalse(File(file().path + ".plain-old").exists())
        assertFalse(File(file().path + ".enc-tmp").exists())
        assertFalse("nessun nome articolo leggibile nel file", file().readBytes().toString(Charsets.ISO_8859_1).contains("Vite 12"))

        // 3. Reopening: same data, still encrypted.
        val again = StockDb(context, name)
        assertTrue(again.encrypted)
        assertEquals(250, again.items(warehouse).size)
        again.close()
    }

    @Test fun aNewArchiveIsEncryptedFromTheStart() {
        val db = StockDb(context, name)
        val profile = db.createProfile("Mario", "5678")
        db.createWarehouse(profile, "Magazzino", "")
        assertTrue(db.encrypted)
        db.close()
        assertFalse(isPlain(file()))
    }

    @Test fun anInterruptedMigrationIsCompletedAtTheNextStart() {
        DbCipher.plainForTests = true
        val plain = StockDb(context, name)
        val profile = plain.createProfile("Nicola", "1234")
        plain.createWarehouse(profile, "Officina", "")
        plain.close()
        // The phone switched off between "rename plain → .plain-old" and "activate the copy".
        assertTrue(file().renameTo(File(file().path + ".plain-old")))
        DbCipher.plainForTests = false
        val db = StockDb(context, name)
        assertEquals(1, db.profiles().size)
        assertTrue(db.encrypted)
        db.close()
        assertFalse(File(file().path + ".plain-old").exists())
    }
}
