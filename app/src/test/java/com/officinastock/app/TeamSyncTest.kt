package com.officinastock.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Central phone + satellites exchanging files: stock is merged by movements, never overwritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TeamSyncTest {
    private val pw = "squadra-2026"
    private lateinit var central: StockDb
    private lateinit var satellite: StockDb
    private lateinit var other: StockDb
    private var warehouse = 0L
    private var shelf = 0L

    @Before fun setup() {
        val c = RuntimeEnvironment.getApplication()
        for (n in listOf("central.db", "sat_b.db", "sat_c.db")) c.deleteDatabase(n)
        c.getSharedPreferences("login_attempts", Context.MODE_PRIVATE).edit().clear().commit()
        central = StockDb(c, "central.db"); satellite = StockDb(c, "sat_b.db"); other = StockDb(c, "sat_c.db")
        val profile = central.createProfile("Nicola", "1234")
        warehouse = central.createWarehouse(profile, "Officina", "Rovigo")
        shelf = central.addPlacement(warehouse, "Scaffale", "Scaffale A", 3f, 3f)
        central.updatePlacementLayout(shelf, 3, 4)
        val vite = central.addItem(warehouse, "Vite M8", "Ricambi", 10.0, 2.0, "pz", "")
        central.assignItem(vite, shelf, 2, 1)
        TeamSync(central).becomeCentral(warehouse, pw)
    }
    @After fun close() { central.close(); satellite.close(); other.close() }

    private fun state(): ByteArray = ByteArrayOutputStream().also { TeamSync(central).exportState(it) }.toByteArray()
    private fun changes(db: StockDb): ByteArray = ByteArrayOutputStream().also { TeamSync(db).exportChanges(it) }.toByteArray()
    private fun join(db: StockDb, letter: String): Long {
        val p = db.createProfile("Mario", "5678")
        TeamSync(db).joinAsSatellite(p, ByteArrayInputStream(state()), pw, letter)
        return TeamSync(db).status().warehouseId!!
    }
    private fun qty(db: StockDb, wh: Long, name: String) = db.items(wh).single { it.name == name }.quantity
    private fun rejects(block: () -> Unit) { try { block(); fail("accettato") } catch (expected: IllegalArgumentException) {} }

    @Test fun satelliteStartsFromTheCentralStateIncludingMapAndLocations() {
        val wh = join(satellite, "B")
        val item = satellite.items(wh).single()
        assertEquals(10.0, item.quantity, 0.0)
        val place = satellite.placements(wh).single()
        assertEquals("Scaffale A", place.label)
        assertEquals(3, place.levels)
        assertEquals(place.id, item.placementId)
        assertEquals(2, item.shelfLevel)
        // Nothing to send yet: the central state is not a local change.
        assertEquals(0, TeamSync(satellite).status().pending)
    }

    @Test fun movementsFromSeveralPhonesAddUpAndReimportingChangesNothing() {
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single().id
        satellite.movement(vite, -3.0, "Prelievo")
        val guanti = satellite.addItem(wh, "Guanti", "DPI", 5.0, 1.0, "paia", "")
        assertEquals("DPI-B000001", satellite.item(guanti)!!.sku)
        satellite.assignItem(guanti, satellite.placements(wh).single().id)
        // Meanwhile the central phone works too.
        central.movement(central.items(warehouse).single { it.name == "Vite M8" }.id, -2.0, "Prelievo")
        val file = changes(satellite)
        val r = TeamSync(central).importChanges(ByteArrayInputStream(file))
        assertEquals(1, r.articles)
        assertEquals(0, r.conflicts)
        assertEquals(5.0, qty(central, warehouse, "Vite M8"), 0.0)   // 10 - 2 - 3
        assertEquals(5.0, qty(central, warehouse, "Guanti"), 0.0)
        assertEquals("DPI-B000001", central.items(warehouse).single { it.name == "Guanti" }.sku)
        assertEquals(shelf, central.items(warehouse).single { it.name == "Guanti" }.placementId)
        // The same file again (sent twice on WhatsApp): nothing is counted twice.
        TeamSync(central).importChanges(ByteArrayInputStream(file))
        assertEquals(5.0, qty(central, warehouse, "Vite M8"), 0.0)
        assertEquals(2, central.items(warehouse).size)
    }

    @Test fun olderCentralStateIsRejectedAndKeepsNewerArticlesAndPendingMovements() {
        val wh = join(satellite, "B")
        val older = state()
        Thread.sleep(5)
        central.addItem(warehouse, "Dado M8", "Ricambi", 7.0, 1.0, "pz", "")
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val dado = satellite.items(wh).single { it.name == "Dado M8" }.id
        satellite.movement(dado, -2.0, "Prelievo")
        rejects { TeamSync(satellite).importState(ByteArrayInputStream(older)) }
        assertEquals(5.0, qty(satellite, wh, "Dado M8"), 0.0)
        assertEquals(1, TeamSync(satellite).status().pending)
        // The same (latest) file twice is still fine.
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(5.0, qty(central, warehouse, "Dado M8"), 0.0)
    }

    @Test fun centralUpdateKeepsTheSatelliteChangesNotYetSent() {
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single().id
        satellite.movement(vite, -3.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        central.movement(central.items(warehouse).single().id, 4.0, "Carico")   // central: 10 - 3 + 4 = 11
        satellite.movement(vite, -1.0, "Prelievo dopo l'invio")                // not sent yet
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(10.0, qty(satellite, wh, "Vite M8"), 0.0)                 // 11 - 1 still pending
        assertEquals(1, TeamSync(satellite).status().pending)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(10.0, qty(central, warehouse, "Vite M8"), 0.0)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(0, TeamSync(satellite).status().pending)
        assertEquals(10.0, qty(satellite, wh, "Vite M8"), 0.0)
    }

    @Test fun twoSatellitesConvergeAndCodesNeverCollide() {
        val b = join(satellite, "B"); val c = join(other, "C")
        val gb = satellite.addItem(b, "Dado", "Ricambi", 1.0, 0.0, "pz", "")
        val gc = other.addItem(c, "Dado", "Ricambi", 2.0, 0.0, "pz", "")
        assertNotEquals(satellite.item(gb)!!.sku, other.item(gc)!!.sku)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        TeamSync(central).importChanges(ByteArrayInputStream(changes(other)))
        assertEquals(3, central.items(warehouse).size)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        TeamSync(other).importState(ByteArrayInputStream(state()))
        assertEquals(central.items(warehouse).map { it.sku to it.quantity }.toSet(), satellite.items(b).map { it.sku to it.quantity }.toSet())
        assertEquals(central.items(warehouse).map { it.sku to it.quantity }.toSet(), other.items(c).map { it.sku to it.quantity }.toSet())
    }

    @Test fun aWithdrawalLargerThanTheStockIsAConflictNotNegativeStock() {
        val wh = join(satellite, "B")
        satellite.movement(satellite.items(wh).single().id, -8.0, "Prelievo grande")
        central.reconcileQuantity(central.items(warehouse).single().id, 5.0, "Conteggio fisico")
        val r = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(1, r.conflicts)
        assertEquals(5.0, qty(central, warehouse, "Vite M8"), 0.0)
        assertEquals(1, TeamSync(central).status().conflicts.size)
        // The satellite realigns with the central at the next update.
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(5.0, qty(satellite, wh, "Vite M8"), 0.0)
        assertEquals(0, TeamSync(satellite).status().pending)
    }

    @Test fun safetyChecks() {
        rejects { TeamSync(satellite).joinAsSatellite(satellite.createProfile("X", "1111"), ByteArrayInputStream(state()), "sbagliata!!", "B") }
        join(satellite, "B")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        // A second phone claiming the same letter is refused by the central.
        rejects { join(other, "B") }
        rejects { TeamSync(satellite).importChanges(ByteArrayInputStream(changes(satellite))) }   // satellites don't import changes
        rejects { TeamSync(central).importState(ByteArrayInputStream(state())) }                 // central doesn't import its own state
        // A satellite with unsent work cannot leave by mistake.
        val wh = TeamSync(satellite).status().warehouseId!!
        satellite.movement(satellite.items(wh).single().id, 1.0, "Carico")
        rejects { TeamSync(satellite).leave() }
    }

    @Test fun aRefusedSatelliteCanLeaveAndJoinAgainWithoutBlockingTheCentral() {
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single().id
        satellite.movement(vite, -1.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        satellite.movement(vite, -2.0, "Mai inviato")
        rejects { TeamSync(satellite).leave() }
        TeamSync(satellite).leave(force = true)
        assertEquals(TeamRole.NONE, TeamSync(satellite).status().role)
        // Same phone, same letter: the central recognises it; the lost numbers are not a blocking gap.
        val again = TeamSync(satellite).joinAsSatellite(satellite.createProfile("Luca", "9999"), ByteArrayInputStream(state()), pw, "B")
        assertEquals(1, again.articles)
        val wh2 = TeamSync(satellite).status().warehouseId!!
        satellite.movement(satellite.items(wh2).single().id, -4.0, "Dopo il ricollegamento")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(5.0, qty(central, warehouse, "Vite M8"), 0.0)   // 10 - 1 - 4
    }

    @Test fun aCopyOfTheCentralAlreadyOnThePhoneDoesNotBlockTheJoin() {
        // The satellite restored the central's backup in the past: same global ids in another warehouse.
        val p = satellite.createProfile("Copia", "4321")
        val old = satellite.createWarehouse(p, "Copia vecchia", "Rovigo")
        val copy = satellite.addItem(old, "Vite M8", "Ricambi", 3.0, 0.0, "pz", "")
        val uid = central.readableDatabase.rawQuery("SELECT uid FROM items", null).use { it.moveToFirst(); it.getString(0) }
        satellite.writableDatabase.execSQL("UPDATE items SET uid=? WHERE id=?", arrayOf(uid, copy))
        val wh = join(satellite, "B")
        assertEquals(10.0, qty(satellite, wh, "Vite M8"), 0.0)
        assertEquals(3.0, qty(satellite, old, "Vite M8"), 0.0)   // the old copy is untouched
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(3.0, qty(satellite, old, "Vite M8"), 0.0)
    }

    @Test fun codesFromOlderVersionsTravelToo() {
        central.writableDatabase.execSQL("UPDATE items SET sku='RIC-12'")
        val wh = join(satellite, "B")
        assertEquals("RIC-12", satellite.items(wh).single().sku)
    }

    @Test fun onASatelliteOnlyItsOwnUnsentArticlesCanBeEdited() {
        val wh = join(satellite, "B")
        val fromCentral = satellite.items(wh).single().id
        rejects { TeamSync(satellite).requireDetailsEditable(fromCentral) }
        val mine = satellite.addItem(wh, "Guanti", "DPI", 1.0, 0.0, "paia", "")
        TeamSync(satellite).requireDetailsEditable(mine)
        TeamSync(central).requireDetailsEditable(central.items(warehouse).single().id)
    }

    private fun refused(block: () -> Unit) { try { block(); fail("accettato") } catch (expected: Exception) {} }

    @Test fun aDepartmentLimitsTheSatelliteButWithdrawalsStayFree() {
        TeamSync(central).setScope("B", listOf("Meccanica", "Pneumatica"))
        assertEquals(listOf("Pneumatica", "Meccanica"), TeamSync(central).status().scopes["B"])   // app order
        val wh = join(satellite, "B")
        assertEquals(listOf("Pneumatica", "Meccanica"), TeamSync(satellite).status().scope)
        val vite = satellite.items(wh).single().id                     // "Ricambi": outside the department
        try { satellite.addItem(wh, "Cavo", "Elettrico", 0.0, 0.0, "m", ""); fail("accettato") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("reparto")) }
        satellite.addItem(wh, "Molla", "Meccanica", 2.0, 0.0, "pz", "")
        satellite.movement(vite, -1.0, "Prelievo")                     // withdrawals are free
        refused { satellite.movement(vite, 1.0, "Carico") }
        refused { satellite.assignItem(vite, null) }
        TeamSync(satellite).returning { satellite.movement(vite, 1.0, "Annullato: prelievo") }   // undo is allowed
        val r = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(0, r.conflicts)
        assertEquals(10.0, qty(central, warehouse, "Vite M8"), 0.0)    // -1 +1
        assertEquals(2.0, qty(central, warehouse, "Molla"), 0.0)
    }

    @Test fun theCentralRejectsWorkOutsideTheDepartmentEvenFromAnOldSatellite() {
        val wh = join(satellite, "B")                                    // no department yet
        satellite.addItem(wh, "Cavo", "Elettrico", 0.0, 0.0, "m", "")
        satellite.movement(satellite.items(wh).single { it.name == "Vite M8" }.id, 2.0, "Carico")
        TeamSync(central).setScope("B", listOf("Meccanica"))
        val r = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(0, r.articles)
        assertEquals(2, r.conflicts)
        assertEquals(10.0, qty(central, warehouse, "Vite M8"), 0.0)
        assertEquals(1, central.items(warehouse).size)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(listOf("Meccanica"), TeamSync(satellite).status().scope)
        // Back to all departments.
        TeamSync(central).setScope("B", emptyList())
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertTrue(TeamSync(satellite).status().scope.isEmpty())
    }

    @Test fun aBadArticleBecomesAConflictInsteadOfBlockingThePhone() {
        val wh = join(satellite, "B")
        val mine = satellite.addItem(wh, "Guanti", "DPI", 0.0, 0.0, "paia", "")
        // The same code already exists on the central (e.g. an old CSV): before 4.9.1 every file was refused.
        central.addItem(warehouse, "Altro", "DPI", 0.0, 0.0, "pz", "")
        central.writableDatabase.execSQL("UPDATE items SET sku=? WHERE name='Altro'", arrayOf(satellite.item(mine)!!.sku))
        satellite.movement(satellite.items(wh).single { it.name == "Vite M8" }.id, -1.0, "Prelievo")
        val r = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(1, r.conflicts)
        assertEquals(9.0, qty(central, warehouse, "Vite M8"), 0.0)          // the rest went through
    }

    @Test fun anArticleDeletedOnTheCentralLeavesTheSatellitesButUnsentOnesStay() {
        val wh = join(satellite, "B")
        val dado = central.addItem(warehouse, "Dado M8", "Ricambi", 4.0, 0.0, "pz", "")
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(2, satellite.items(wh).size)
        val mine = satellite.addItem(wh, "Guanti", "DPI", 1.0, 0.0, "paia", "")   // not sent yet
        assertNull(central.deleteItem(warehouse, dado))
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(listOf("Guanti", "Vite M8"), satellite.items(wh).map { it.name }.sorted())
        assertNotNull(satellite.item(mine))
        // Once sent and confirmed it is the central's article like the others.
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(2, satellite.items(wh).size)
        assertEquals(0, TeamSync(satellite).status().pending)
    }

    @Test fun anArticleOnLoanCannotBeDeleted() {
        val vite = central.items(warehouse).single().id
        central.lend(vite, "Mario", 1.0)
        rejects { central.deleteItem(warehouse, vite) }
        assertEquals(1, central.items(warehouse).size)
    }

    @Test fun backupsKeepTheSyncRoleAndOldBackupsGetGlobalIds() {
        val wh = join(satellite, "B")
        assertTrue(satellite.items(wh).all { it.id > 0 })
        satellite.validateRestoredData()
        assertEquals(TeamRole.SATELLITE, TeamSync(satellite).status().role)
    }

    /** 4.15: every phone sees who has which tool; a returned tool disappears at the next exchange. */
    @Test fun openLoansTravelToEveryPhoneAndDisappearWhenReturned() {
        val whB = join(satellite, "B"); val whC = join(other, "C")
        val viteB = satellite.items(whB).single().id
        satellite.lend(viteB, "Luca", 2.0)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(8.0, qty(central, warehouse, "Vite M8"), 0.0)
        val seen = TeamSync(central).status().loans.single()
        assertEquals("B", seen.letter); assertEquals("Luca", seen.borrower); assertEquals("Vite M8", seen.itemName); assertEquals(2.0, seen.quantity, 0.0)
        central.lend(central.items(warehouse).single().id, "Sara", 1.0)
        // C sees both; B sees only the central's (its own loan is already in its list).
        TeamSync(other).importState(ByteArrayInputStream(state()))
        assertEquals(setOf("A" to "Sara", "B" to "Luca"), TeamSync(other).status().loans.map { it.letter to it.borrower }.toSet())
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(listOf("A" to "Sara"), TeamSync(satellite).status().loans.map { it.letter to it.borrower })
        // Returned on B: gone from the central and from C after the exchanges; stock back.
        satellite.returnLoan(satellite.activeLoans(whB).single().id)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertTrue(TeamSync(central).status().loans.isEmpty())
        TeamSync(other).importState(ByteArrayInputStream(state()))
        assertEquals(listOf("Sara"), TeamSync(other).status().loans.map { it.borrower })
        assertEquals(9.0, qty(other, whC, "Vite M8"), 0.0)   // 10 - 1 (Sara) - 2 + 2
    }

    /** 4.15: the letter of the phone stays with each movement. */
    @Test fun everyMovementRemembersThePhoneThatMadeIt() {
        val whB = join(satellite, "B")
        satellite.movement(satellite.items(whB).single().id, -3.0, "Prelievo")
        assertEquals("B", satellite.movements(whB).first().actor)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        central.movement(central.items(warehouse).single().id, -1.0, "Prelievo")
        val onCentral = central.movements(warehouse)
        assertEquals("A", onCentral.first { it.delta == -1.0 }.actor)
        assertEquals("B", onCentral.first { it.delta == -3.0 }.actor)
    }

    /** 4.20.3: a backup restored on the central keeps which phone made each movement. */
    @Test fun backupRestoreKeepsThePhoneOfEveryMovement() {
        val whB = join(satellite, "B")
        satellite.movement(satellite.items(whB).single().id, -3.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        central.movement(central.items(warehouse).single().id, -1.0, "Prelievo")
        val app = RuntimeEnvironment.getApplication()
        val file = java.io.File(app.cacheDir, "actors.osbackup")
        try {
            BackupArchive(app, central).export(android.net.Uri.fromFile(file), "test-password-1234")
            BackupArchive(app, central).restore(android.net.Uri.fromFile(file), "test-password-1234")
        } finally { file.delete() }
        val restored = central.movements(TeamSync(central).status().warehouseId!!)
        assertEquals("A", restored.first { it.delta == -1.0 }.actor)
        assertEquals("B", restored.first { it.delta == -3.0 }.actor)
    }

    /** 4.20.3: showing the QR again (a phone joined meanwhile) keeps the code a second phone already scanned. */
    @Test fun inviteCodeWithTimeLeftIsReused() {
        val first = TeamSync(central).createInvite()
        assertEquals(first, TeamSync(central).createInvite())
    }

    /** 4.15: a satellite replaces a lost central without applying twice what was already delivered. */
    @Test fun satelliteReplacesALostCentralAndTheOthersCarryOn() {
        val whB = join(satellite, "B"); val whC = join(other, "C")
        // C delivers a withdrawal to the central; everyone gets the update.
        other.movement(other.items(whC).single().id, -1.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(other)))
        satellite.movement(satellite.items(whB).single().id, -3.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        TeamSync(satellite).importState(ByteArrayInputStream(state())); TeamSync(other).importState(ByteArrayInputStream(state()))
        assertEquals(6.0, qty(satellite, whB, "Vite M8"), 0.0)
        // The central is lost. Meanwhile C has withdrawn two more, never delivered.
        other.movement(other.items(whC).single().id, -2.0, "Prelievo")
        TeamSync(satellite).promoteToCentral()
        assertEquals(TeamRole.CENTRAL, TeamSync(satellite).status().role)
        // 4.19.2: the labels printed by the lost central are still read on the new one.
        assertEquals(whB, TeamSync(satellite).status().labelIds[warehouse])
        rejects { TeamSync(central).promoteToCentral() }
        fun newState() = ByteArrayOutputStream().also { TeamSync(satellite).exportState(it) }.toByteArray()
        // C keeps working with the new central: only the two undelivered pieces are applied, once.
        TeamSync(other).importState(ByteArrayInputStream(newState()))
        assertEquals(4.0, qty(other, whC, "Vite M8"), 0.0)
        val file = changes(other)
        assertEquals(1, TeamSync(satellite).importChanges(ByteArrayInputStream(file)).movements)
        assertEquals(4.0, qty(satellite, whB, "Vite M8"), 0.0)
        assertEquals(0, TeamSync(satellite).importChanges(ByteArrayInputStream(file)).movements)
        assertEquals(4.0, qty(satellite, whB, "Vite M8"), 0.0)
        TeamSync(other).importState(ByteArrayInputStream(newState()))
        assertEquals(4.0, qty(other, whC, "Vite M8"), 0.0); assertEquals(0, TeamSync(other).status().pending)
    }

    /** 4.15: an article takes the photo the central names; a photo missing on the satellite is asked for and arrives with the next update. */
    @Test fun photosFollowTheCentralAndMissingOnesAreAskedFor() {
        val app = RuntimeEnvironment.getApplication()
        val wh = join(satellite, "B")
        assertNull(satellite.items(wh).single().photoFile)
        // The central names a photo this phone does not have yet (the file is not there).
        val name = "item_${java.util.UUID.randomUUID()}.jpg"
        val vite = central.items(warehouse).single().id
        central.updatePhoto(vite, name)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertNull("no file yet: the article keeps what it had", satellite.items(wh).single().photoFile)
        // The satellite asks for it; the photo exists by then and travels with the central's answer.
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        PhotoFiles.receive(app, name, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte()))
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(name, satellite.items(wh).single().photoFile)
        // Photo removed on the central: removed on the satellite too. Stock untouched all along.
        central.updatePhoto(vite, null)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertNull(satellite.items(wh).single().photoFile)
        assertEquals(10.0, qty(satellite, wh, "Vite M8"), 0.0)
        // Names that are not photos of the app, or files that are not JPEG, are refused.
        rejects { PhotoFiles.receive(app, "../x.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)) }
        rejects { PhotoFiles.receive(app, "item_${java.util.UUID.randomUUID()}.jpg", byteArrayOf(1, 2, 3, 4)) }
    }

    /** 4.15: the photo of an article created on a satellite reaches the central with the article. */
    @Test fun photoOfANewSatelliteArticleReachesTheCentral() {
        val app = RuntimeEnvironment.getApplication()
        val wh = join(satellite, "B")
        val name = "item_${java.util.UUID.randomUUID()}.jpg"
        PhotoFiles.receive(app, name, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9, 0xFF.toByte(), 0xD9.toByte()))
        val guanti = satellite.addItem(wh, "Guanti", "DPI", 5.0, 1.0, "paia", "")
        satellite.updatePhoto(guanti, name)
        assertEquals(1, TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite))).articles)
        assertEquals(name, central.items(warehouse).single { it.name == "Guanti" }.photoFile)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(name, satellite.items(wh).single { it.name == "Guanti" }.photoFile)
    }

    /** 4.16: a photo taken on a satellite is never deleted by an update; it is offered to the central, which takes it only where it has none. */
    @Test fun photoTakenOnASatelliteIsKeptAndOfferedToTheCentral() {
        val app = RuntimeEnvironment.getApplication()
        val wh = join(satellite, "B")
        val mine = "item_${java.util.UUID.randomUUID()}.jpg"
        PhotoFiles.receive(app, mine, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 5, 5, 0xFF.toByte(), 0xD9.toByte()))
        satellite.updatePhoto(satellite.items(wh).single().id, mine)
        // The central has no photo of this article: the satellite keeps its own.
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(mine, satellite.items(wh).single().photoFile)
        assertTrue(PhotoFiles.exists(app, mine))
        // Offered with the next file of changes: the central takes it.
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(mine, central.items(warehouse).single().photoFile)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(mine, satellite.items(wh).single().photoFile)
        // An offer never replaces a photo the central already has.
        val centralOwn = "item_${java.util.UUID.randomUUID()}.jpg"
        PhotoFiles.receive(app, centralOwn, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 7, 7, 0xFF.toByte(), 0xD9.toByte()))
        central.updatePhoto(central.items(warehouse).single().id, centralOwn)
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(centralOwn, central.items(warehouse).single().photoFile)
    }

    /** 4.18: one company, two sites. A satellite receives both, works on both, and the central applies each change in its site. */
    @Test fun secondSiteOfTheCompanyReachesTheSatellitesAndComesBack() {
        val company = central.profiles().single().id
        val second = central.createWarehouse(company, "Secondaria", "Padova")
        val rack = central.addPlacement(second, "Scaffale", "Scaffale S", 2f, 2f)
        val dado = central.addItem(second, "Dado M10", "Ricambi", 6.0, 1.0, "pz", "")
        central.assignItem(dado, rack, 1, 1)
        // Another company on the same phone can never be shared.
        val stranger = central.createWarehouse(central.createProfile("Altra", "9999"), "Estranea", "")
        rejects { TeamSync(central).addSite(stranger) }
        rejects { TeamSync(central).addSite(warehouse) }
        TeamSync(central).addSite(second)
        rejects { TeamSync(central).addSite(second) }
        assertEquals(listOf(second), TeamSync(central).status().sites)

        // A new satellite gets both sites at once, with plan and stock.
        val whB = join(satellite, "B")
        val status = TeamSync(satellite).status()
        val secondB = status.sites.single()
        assertTrue(status.covers(whB)); assertTrue(status.covers(secondB)); assertFalse(status.covers(999L))
        assertEquals("Secondaria", satellite.warehouses(satellite.profiles().single().id).single { it.id == secondB }.name)
        assertEquals(6.0, qty(satellite, secondB, "Dado M10"), 0.0)
        assertEquals("Scaffale S", satellite.placements(secondB).single().label)
        assertEquals(secondB, status.labelIds[second]); assertEquals(whB, status.labelIds[warehouse])

        // Work on the second site from the satellite: a withdrawal and a new article.
        satellite.movement(satellite.items(secondB).single().id, -2.0, "Prelievo")
        satellite.addItem(secondB, "Rondella 10", "Ricambi", 3.0, 0.0, "pz", "")
        satellite.movement(satellite.items(whB).single().id, -1.0, "Prelievo")
        assertEquals(4, TeamSync(satellite).status().pending)
        val file = changes(satellite)
        val result = TeamSync(central).importChanges(ByteArrayInputStream(file))
        assertEquals(1, result.articles); assertEquals(0, result.conflicts)
        assertEquals(4.0, qty(central, second, "Dado M10"), 0.0)
        assertEquals(3.0, qty(central, second, "Rondella 10"), 0.0)
        assertEquals(9.0, qty(central, warehouse, "Vite M8"), 0.0)
        assertTrue("the new article belongs to the second site only", central.items(warehouse).none { it.name == "Rondella 10" })
        // The same file again changes nothing.
        TeamSync(central).importChanges(ByteArrayInputStream(file))
        assertEquals(4.0, qty(central, second, "Dado M10"), 0.0)
        // Back on the satellite: confirmed, nothing pending, quantities equal.
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(0, TeamSync(satellite).status().pending)
        assertEquals(4.0, qty(satellite, secondB, "Dado M10"), 0.0); assertEquals(3.0, qty(satellite, secondB, "Rondella 10"), 0.0)
        assertEquals(listOf(secondB), TeamSync(satellite).status().sites)
    }

    /** 4.18: a satellite already linked to the first site receives the second one with the next update, keeping its unsent changes. */
    @Test fun linkedSatelliteReceivesANewSiteWithTheNextUpdate() {
        val whC = join(other, "C")
        other.movement(other.items(whC).single().id, -4.0, "Prelievo")
        assertTrue(TeamSync(other).status().sites.isEmpty())
        val second = central.createWarehouse(central.profiles().single().id, "Secondaria", "Padova")
        central.addItem(second, "Dado M10", "Ricambi", 6.0, 1.0, "pz", "")
        TeamSync(central).addSite(second)
        TeamSync(other).importState(ByteArrayInputStream(state()))
        val secondC = TeamSync(other).status().sites.single()
        assertEquals(6.0, qty(other, secondC, "Dado M10"), 0.0)
        assertEquals("the unsent withdrawal stays on top of the central state", 6.0, qty(other, whC, "Vite M8"), 0.0)
        assertEquals(1, TeamSync(other).status().pending)
        // Departments hold on the second site too: outside its department the satellite cannot load.
        TeamSync(central).setScope("C", listOf("DPI"))
        TeamSync(other).importState(ByteArrayInputStream(state()))
        val dadoC = other.items(secondC).single().id
        // Refused by the department rule of the database itself.
        assertTrue(runCatching { other.movement(dadoC, 1.0, "Carico") }.exceptionOrNull() is android.database.sqlite.SQLiteConstraintException)
        assertEquals(6.0, qty(other, secondC, "Dado M10"), 0.0)
        other.movement(dadoC, -1.0, "Prelievo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(other)))
        assertEquals(5.0, qty(central, second, "Dado M10"), 0.0); assertEquals(6.0, qty(central, warehouse, "Vite M8"), 0.0)
    }

    /** 4.19: pieces move between two sites as one operation, and a satellite's transfer reaches the central whole. */
    @Test fun transferBetweenSitesOnTheCentralAndFromASatellite() {
        val company = central.profiles().single().id
        val second = central.createWarehouse(company, "Secondaria", "Padova")
        val vite = central.items(warehouse).single().id
        rejects { central.transferToSite(vite, second, 3.0, createMissing = false) }   // not there yet
        rejects { central.transferToSite(vite, second, 99.0, createMissing = true) }   // more than the shelf
        rejects { central.transferToSite(vite, warehouse, 1.0, createMissing = true) }
        rejects { central.transferToSite(vite, central.createWarehouse(central.createProfile("Altra", "9999"), "Estranea", ""), 1.0, createMissing = true) }
        assertEquals(10.0, qty(central, warehouse, "Vite M8"), 0.0)
        val copy = central.transferToSite(vite, second, 3.0, createMissing = true)
        assertEquals(7.0, qty(central, warehouse, "Vite M8"), 0.0); assertEquals(3.0, qty(central, second, "Vite M8"), 0.0)
        assertEquals("same code in the two sites", central.items(warehouse).single().sku, central.items(second).single().sku)
        assertEquals(copy, central.transferToSite(vite, second, 1.0, createMissing = false))
        assertTrue(central.movements(second).first().note.startsWith("Trasferito da Officina"))
        // From a satellite: both movements travel with its file and land in the right sites.
        TeamSync(central).addSite(second)
        val whB = join(satellite, "B")
        val secondB = TeamSync(satellite).status().sites.single()
        satellite.transferToSite(satellite.items(secondB).single().id, whB, 2.0, createMissing = false)
        assertEquals(8.0, qty(satellite, whB, "Vite M8"), 0.0); assertEquals(2.0, qty(satellite, secondB, "Vite M8"), 0.0)
        val result = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(2, result.movements); assertEquals(0, result.conflicts)
        assertEquals(8.0, qty(central, warehouse, "Vite M8"), 0.0); assertEquals(2.0, qty(central, second, "Vite M8"), 0.0)
    }

    /** 4.19: a pick-only satellite withdraws and lends but cannot load, create or move; the central refuses it too. */
    @Test fun pickOnlySatelliteCanOnlyWithdraw() {
        val wh = join(satellite, "B")
        TeamSync(central).setPickOnly("B")
        assertEquals(listOf(TeamSync.PICK_ONLY), TeamSync(central).status().scopes["B"])
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val vite = satellite.items(wh).single().id
        assertTrue(runCatching { satellite.movement(vite, 1.0, "Carico") }.isFailure)
        assertTrue(runCatching { satellite.addItem(wh, "Guanti", "DPI", 1.0, 0.0, "paia", "") }.isFailure)
        assertTrue(runCatching { satellite.assignItem(vite, null, null, null) }.isFailure)
        satellite.movement(vite, -2.0, "Prelievo"); satellite.lend(vite, "Luca", 1.0)
        val result = TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(0, result.conflicts); assertEquals(7.0, qty(central, warehouse, "Vite M8"), 0.0)
        // Back to the whole warehouse: loading works again.
        TeamSync(central).setScope("B", emptyList())
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        satellite.movement(vite, 1.0, "Carico")
        assertEquals(8.0, qty(satellite, wh, "Vite M8"), 0.0)
    }

    // ---------------------------------------------------------------- 4.21 maintainers change cards, the central validates

    @Test fun aMaintainerChangesACardOfHisDepartmentOnlyAfterTheCentralValidates() {
        TeamSync(central).setScope("B", listOf("Ricambi"), maintainer = true)
        assertEquals(listOf(TeamSync.MAINTAINER, "Ricambi"), TeamSync(central).status().scopes["B"])
        val wh = join(satellite, "B")
        val status = TeamSync(satellite).status()
        assertTrue(status.maintainer)
        assertEquals(listOf("Ricambi"), status.department)
        val vite = satellite.items(wh).single().id
        assertEquals(DetailsEdit.REVIEW, TeamSync(satellite).detailsEdit(vite))
        TeamSync(satellite).requestEdit(vite, TeamSync.card("RIC-VITE8", "Vite M8 zincata", "Ricambi", 3.0, "pz", "Testa esagonale", null, "", null))
        assertEquals("Vite M8", satellite.item(vite)!!.name)                 // nothing changes before the validation
        assertEquals("sending", TeamSync(satellite).status().edits.single().status)
        val file = changes(satellite)
        assertEquals(1, TeamSync(central).importChanges(ByteArrayInputStream(file)).edits)
        assertEquals(0, TeamSync(central).importChanges(ByteArrayInputStream(file)).edits)   // the same file twice
        val pending = TeamSync(central).status().edits.single()
        assertEquals("B", pending.letter)
        assertEquals(listOf("sku", "name", "minimum", "note"), pending.fields.map { it.key })
        assertEquals("Vite M8", pending.fields[1].before)
        assertEquals("Vite M8 zincata", pending.fields[1].after)
        assertEquals("Vite M8", central.items(warehouse).single().name)
        TeamSync(central).approveEdit(pending.uid)
        val card = central.items(warehouse).single()
        assertEquals("Vite M8 zincata", card.name); assertEquals("RIC-VITE8", card.sku)
        assertEquals("Testa esagonale", card.note); assertEquals(3.0, card.minimum, 0.0)
        assertEquals(10.0, card.quantity, 0.0)                               // stock untouched
        assertTrue(TeamSync(central).status().edits.isEmpty())
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals("Vite M8 zincata", satellite.item(vite)!!.name)
        assertEquals("RIC-VITE8", satellite.item(vite)!!.sku)
        assertEquals("approved", TeamSync(satellite).status().edits.single().status)
    }

    @Test fun aChangeSentBackKeepsTheCardAndTellsTheMaintainerWhatToCorrect() {
        TeamSync(central).setScope("B", emptyList())                        // maintainer of every category
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single()
        TeamSync(satellite).requestEdit(vite.id, TeamSync.card(vite.sku, "Vite M80", "Ricambi", 2.0, "pz", "", null, "", null))
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        val uid = TeamSync(central).status().edits.single().uid
        TeamSync(central).rejectEdit(uid, "Il nome è sbagliato: è una M8")
        refused { TeamSync(central).approveEdit(uid) }                       // already answered
        assertEquals("Vite M8", central.items(warehouse).single().name)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val back = TeamSync(satellite).status().edits.single()
        assertEquals("rejected", back.status)
        assertEquals("Il nome è sbagliato: è una M8", back.reason)
        assertEquals("Vite M8", satellite.item(vite.id)!!.name)
        // Done again: the new change replaces the one sent back.
        TeamSync(satellite).requestEdit(vite.id, TeamSync.card(vite.sku, "Vite M8 inox", "Ricambi", 2.0, "pz", "", null, "", null))
        assertEquals(listOf("sending"), TeamSync(satellite).status().edits.map { it.status })
        assertEquals(1, TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite))).edits)
    }

    @Test fun operatorsAndMaintainersOfAnotherDepartmentCannotChangeTheCard() {
        TeamSync(central).setScope("B", listOf("Elettrico"), maintainer = true)
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single().id                          // "Ricambi"
        refused { TeamSync(satellite).detailsEdit(vite) }
        TeamSync(central).setScope("B", listOf("Ricambi"))                   // operator: no cards
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertFalse(TeamSync(satellite).status().maintainer)
        refused { TeamSync(satellite).detailsEdit(vite) }
        // Unchanged values are not a change.
        TeamSync(central).setScope("B", emptyList())
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val v = satellite.item(vite)!!
        refused { TeamSync(satellite).requestEdit(vite, TeamSync.card(v.sku, v.name, v.category, v.minimum, v.unit, v.note, v.unitCost, v.supplier, v.leadDays)) }
    }

    @Test fun theCentralChecksAgainAChangeFromAPhoneThatIsNoLongerAMaintainer() {
        TeamSync(central).setScope("B", emptyList())
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single()
        TeamSync(satellite).requestEdit(vite.id, TeamSync.card(vite.sku, "Vite", "Ricambi", 2.0, "pz", "", null, "", null))
        TeamSync(central).setScope("B", listOf("Ricambi"))                   // meanwhile: operator
        assertEquals(0, TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite))).edits)
        assertTrue(TeamSync(central).status().edits.isEmpty())
        assertEquals("Vite M8", central.items(warehouse).single().name)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val back = TeamSync(satellite).status().edits.single()
        assertEquals("rejected", back.status)
        assertTrue(back.reason.contains("manutentore"))
    }

    @Test fun anOperatorOfEveryCategoryStaysAnOperator() {
        val all = ArticleCodes.categoryPrefixes.keys.toList()
        TeamSync(central).setScope("B", all)
        assertEquals(all, TeamSync(central).status().scopes["B"])
        TeamSync(central).setScope("B", all, maintainer = true)
        assertNull(TeamSync(central).status().scopes["B"])                  // maintainer of everything, as before
    }

    @Test fun aCodeChangeOnTheCentralNeverTakesAnotherArticlesCode() {
        val guanti = central.addItem(warehouse, "Guanti", "DPI", 0.0, 0.0, "paia", "")
        val vite = central.items(warehouse).single { it.name == "Vite M8" }
        try {
            central.updateItemDetails(warehouse, vite.id, vite.name, vite.category, vite.minimum, vite.unit, vite.note, null, "", null,
                sku = central.item(guanti)!!.sku)
            fail("accettato")
        } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("già")) }
        central.updateItemDetails(warehouse, vite.id, vite.name, vite.category, vite.minimum, vite.unit, vite.note, null, "", null, sku = "RIC-999")
        assertEquals("RIC-999", central.item(vite.id)!!.sku)
    }

    @Test fun aMalformedCardChangeIsSentBackAndDoesNotBreakTheCentralScreen() {
        TeamSync(central).setScope("B", emptyList())
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single()
        TeamSync(satellite).requestEdit(vite.id, TeamSync.card(vite.sku, vite.name, "Ricambi", 5.0, "pz", "", null, "", null))
        // As another version or a damaged file could send it: the minimum stock as text.
        satellite.writableDatabase.execSQL("UPDATE team_edits SET changes=?",
            arrayOf("""{"after":{"minimum":"tanti","note":"x"},"before":{"minimum":2}}"""))
        assertEquals(0, TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite))).edits)
        assertTrue(TeamSync(central).status().edits.isEmpty())              // nothing to validate, and no crash
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        val back = TeamSync(satellite).status().edits.single()
        assertEquals("rejected", back.status)
        assertEquals("tanti", back.fields.single { it.key == "minimum" }.after)
    }

    @Test fun aNewTeamGetsALongPasswordWhileOldTeamsKeepTheirs() {
        val proposed = TeamSync.newPassword()
        assertTrue(proposed, Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}").matches(proposed))
        assertNotEquals(proposed, TeamSync.newPassword())
        val c = RuntimeEnvironment.getApplication(); c.deleteDatabase("new_team.db")
        val db = StockDb(c, "new_team.db")
        try {
            val site = db.createWarehouse(db.createProfile("Anna", "1234"), "Magazzino", "Adria")
            refused { TeamSync(db).becomeCentral(site, "corta-123") }
            TeamSync(db).becomeCentral(site, proposed)
        } finally { db.close() }
    }

    @Test fun oldMovementsLeaveTheHistoryButQuantitiesAndUnsentOnesStay() {
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single()
        satellite.movement(vite.id, -3.0, "Prelievo")                        // not yet sent to the central
        val old = System.currentTimeMillis() - 800 * 86_400_000L
        satellite.writableDatabase.execSQL("UPDATE movements SET created_at=?", arrayOf(old))
        satellite.writableDatabase.execSQL("INSERT INTO movements(item_id,delta,note,created_at) VALUES(?,?,?,?)", arrayOf(vite.id, 1.0, "Vecchio", old))
        // ...already sent long ago: it is no longer in the queue to the central.
        satellite.writableDatabase.execSQL("DELETE FROM team_sync_outbox WHERE kind='MOVE' AND ref_id=(SELECT MAX(id) FROM movements)")
        val before = System.currentTimeMillis() - 730 * 86_400_000L
        val unsentBefore = TeamSync(satellite).status().pending
        assertTrue(satellite.oldMovements(wh, before) >= 1)
        assertTrue(satellite.deleteOldMovements(wh, before) >= 1)
        assertEquals(0, satellite.oldMovements(wh, before))
        assertEquals(7.0, qty(satellite, wh, "Vite M8"), 0.0)                // the quantity lives on the article
        assertEquals(unsentBefore, TeamSync(satellite).status().pending)     // the unsent withdrawal still reaches the central
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        assertEquals(7.0, qty(central, warehouse, "Vite M8"), 0.0)
    }

    @Test fun aBackupKeepsTheCardChangesNotYetSentAndForgetsTheOtherPhonesLoans() {
        TeamSync(central).setScope("B", emptyList())
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single()
        TeamSync(satellite).requestEdit(vite.id, TeamSync.card(vite.sku, "Vite M8 inox", "Ricambi", 2.0, "pz", "", null, "", null))
        val c = RuntimeEnvironment.getApplication()
        val file = java.io.File(c.cacheDir, "team-edits.osbackup")
        try {
            BackupArchive(c, satellite).export(android.net.Uri.fromFile(file), "password-di-test")
            satellite.writableDatabase.execSQL("DELETE FROM team_edits")
            satellite.writableDatabase.execSQL("INSERT INTO team_loans(letter,item_uid,borrower,quantity,created_at) VALUES('C','u','Ugo',1,0)")
            BackupArchive(c, satellite).restore(android.net.Uri.fromFile(file), "password-di-test")
            assertEquals(listOf("sending"), TeamSync(satellite).status().edits.map { it.status })
            assertTrue(TeamSync(satellite).status().loans.isEmpty())
        } finally { file.delete() }
    }

    /** 4.24.1: a satellite restored on a new phone keeps sending: its new changes are not taken as already received. */
    @Test fun aSatelliteRestoredOnANewPhoneStillDeliversItsNewChanges() {
        val wh = join(satellite, "B")
        val vite = satellite.items(wh).single().id
        repeat(3) { satellite.movement(vite, -1.0, "Prelievo") }
        TeamSync(central).importChanges(ByteArrayInputStream(changes(satellite)))
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(7.0, qty(central, warehouse, "Vite M8"), 0.0)
        val c = RuntimeEnvironment.getApplication()
        val file = java.io.File(c.cacheDir, "satellite-b.osbackup")
        try {
            BackupArchive(c, satellite).export(android.net.Uri.fromFile(file), "password-di-test")
            BackupArchive(c, other).restore(android.net.Uri.fromFile(file), "password-di-test")   // the new phone
        } finally { file.delete() }
        val restoredWh = TeamSync(other).status().warehouseId!!
        other.movement(other.items(restoredWh).single().id, -2.0, "Prelievo dal telefono nuovo")
        TeamSync(central).importChanges(ByteArrayInputStream(changes(other)))
        assertEquals(5.0, qty(central, warehouse, "Vite M8"), 0.0)
    }

    /** 4.24.1: states a team exchange can produce never make a backup impossible to restore. */
    @Test fun aBackupRestoresEvenWithAShelfThatNoLongerExists() {
        val c = RuntimeEnvironment.getApplication()
        val vite = central.items(warehouse).single()
        central.writableDatabase.execSQL("UPDATE items SET shelf_level=9 WHERE id=?", arrayOf(vite.id))   // shelf removed elsewhere
        val file = java.io.File(c.cacheDir, "shelf.osbackup")
        try {
            BackupArchive(c, central).export(android.net.Uri.fromFile(file), "password-di-test")
            BackupArchive(c, central).restore(android.net.Uri.fromFile(file), "password-di-test")
        } finally { file.delete() }
        val back = central.items(warehouse).single()
        assertEquals(10.0, back.quantity, 0.0)
        assertNull(back.shelfLevel)
        assertNotNull(back.placementId)
    }

    /** 4.24.1: two codes swapped on the central no longer block the satellite's updates. */
    @Test fun swappedCodesOnTheCentralReachTheSatellite() {
        val dado = central.addItem(warehouse, "Dado M8", "Ricambi", 5.0, 1.0, "pz", "")
        val wh = join(satellite, "B")
        val vite = central.items(warehouse).single { it.name == "Vite M8" }
        val d = central.item(dado)!!
        val (codeV, codeD) = vite.sku to d.sku
        fun rename(id: Long, sku: String) { val i = central.item(id)!!
            central.updateItemDetails(warehouse, id, i.name, i.category, i.minimum, i.unit, i.note, i.unitCost, i.supplier, i.leadDays, sku = sku) }
        rename(vite.id, "TMP-1"); rename(dado, codeV); rename(vite.id, codeD)
        TeamSync(satellite).importState(ByteArrayInputStream(state()))
        assertEquals(codeD, satellite.items(wh).single { it.name == "Vite M8" }.sku)
        assertEquals(codeV, satellite.items(wh).single { it.name == "Dado M8" }.sku)
    }
}
