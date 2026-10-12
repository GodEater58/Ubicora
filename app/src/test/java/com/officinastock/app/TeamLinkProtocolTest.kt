package com.officinastock.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The automatic exchange between nearby phones, without the radio: same bytes, same results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TeamLinkProtocolTest {
    private val pw = "squadra-2026"
    private lateinit var central: StockDb
    private lateinit var satellite: StockDb
    private var warehouse = 0L

    @Before fun setup() {
        val c = RuntimeEnvironment.getApplication()
        for (n in listOf("link_central.db", "link_sat.db")) c.deleteDatabase(n)
        c.getSharedPreferences("login_attempts", Context.MODE_PRIVATE).edit().clear().commit()
        central = StockDb(c, "link_central.db"); satellite = StockDb(c, "link_sat.db")
        warehouse = central.createWarehouse(central.createProfile("Nicola", "1234"), "Officina", "Rovigo")
        central.addItem(warehouse, "Vite M8", "Ricambi", 10.0, 2.0, "pz", "")
        TeamSync(central).becomeCentral(warehouse, pw)
    }
    @After fun close() { central.close(); satellite.close() }

    private fun joinNearby(db: StockDb = satellite, letter: String = "B", password: String = pw): TeamLinkProtocol.Outcome {
        val answer = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.joinRequest(TeamSync(db), password, letter))
        return TeamLinkProtocol.finish(TeamSync(db), answer.bytes,
            TeamLinkProtocol.Join(db.createProfile("Mario $letter ${password.length}", "5678"), password, letter))
    }

    @Test fun aNewPhoneJoinsFromTheNearbyCentral() {
        val done = joinNearby() as TeamLinkProtocol.Outcome.Done
        val wh = done.joinedWarehouseId!!
        assertEquals(10.0, satellite.items(wh).single().quantity, 0.0)
        assertEquals(TeamRole.SATELLITE, TeamSync(satellite).status().role)
    }

    @Test fun oneRoundSendsTheChangesAndConfirmsThem() {
        val wh = (joinNearby() as TeamLinkProtocol.Outcome.Done).joinedWarehouseId!!
        satellite.movement(satellite.items(wh).single().id, -3.0, "Prelievo")
        val answer = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.changesRequest(TeamSync(satellite)))
        assertFalse(answer.failed)
        assertEquals("B", answer.letter)
        assertEquals(7.0, central.items(warehouse).single().quantity, 0.0)
        assertTrue(TeamLinkProtocol.finish(TeamSync(satellite), answer.bytes, null) is TeamLinkProtocol.Outcome.Done)
        assertEquals(0, TeamSync(satellite).status().pending)
        assertEquals(7.0, satellite.items(wh).single().quantity, 0.0)
        // The same round again (link dropped and retried): nothing counted twice.
        val again = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.changesRequest(TeamSync(satellite)))
        TeamLinkProtocol.finish(TeamSync(satellite), again.bytes, null)
        assertEquals(7.0, central.items(warehouse).single().quantity, 0.0)
    }

    @Test fun wrongPasswordAndGarbageAreReportedNotCrashing() {
        val refused = joinNearby(password = "sbagliata!!")
        assertTrue(refused is TeamLinkProtocol.Outcome.Failed)
        assertTrue((refused as TeamLinkProtocol.Outcome.Failed).message.contains("Password"))
        assertTrue(TeamSync(central).status().peers.isEmpty())   // a wrong password books nothing
        assertEquals(TeamRole.NONE, TeamSync(satellite).status().role)
        val bad = TeamLinkProtocol.answer(TeamSync(central), byteArrayOf(1, 2, 3))
        assertTrue(bad.failed)
        val out = TeamLinkProtocol.finish(TeamSync(satellite), bad.bytes, null)
        assertTrue(out is TeamLinkProtocol.Outcome.Failed)
        assertTrue(TeamLinkProtocol.finish(TeamSync(satellite), ByteArray(0), null) is TeamLinkProtocol.Outcome.Failed)
    }

    @Test fun theLetterIsBookedAtTheFirstNearbyConnection() {
        val c = RuntimeEnvironment.getApplication()
        c.deleteDatabase("link_other.db")
        val other = StockDb(c, "link_other.db")
        try {
            assertTrue(joinNearby() is TeamLinkProtocol.Outcome.Done)
            assertEquals(listOf("B"), TeamSync(central).status().peers.map { it.letter })
            // A second phone asking for B is stopped before creating anything.
            val second = joinNearby(other, "B")
            assertTrue(second is TeamLinkProtocol.Outcome.Failed)
            assertEquals(TeamRole.NONE, TeamSync(other).status().role)
            assertTrue(joinNearby(other, "C") is TeamLinkProtocol.Outcome.Done)
        } finally { other.close() }
    }

    private fun joinWithCode(code: String): TeamLinkProtocol.Outcome {
        val answer = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.joinRequest(TeamSync(satellite), code, "B"))
        return TeamLinkProtocol.finish(TeamSync(satellite), answer.bytes,
            TeamLinkProtocol.Join(satellite.createProfile("Mario QR", "5678"), code, "B", viaCode = true))
    }

    @Test fun theQrCodeJoinsWithoutEverShowingThePassword() {
        val code = TeamSync(central).createInvite()
        assertNotEquals(pw, code)
        val done = joinWithCode(code) as TeamLinkProtocol.Outcome.Done
        val wh = done.joinedWarehouseId!!
        assertEquals(10.0, satellite.items(wh).single().quantity, 0.0)
        // The satellite received the real team password (encrypted with the code) and can sync normally.
        assertFalse(TeamSync(satellite).status().needsPassword)
        satellite.movement(satellite.items(wh).single().id, -2.0, "Prelievo")
        val round = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.changesRequest(TeamSync(satellite)))
        assertFalse(round.failed)
        assertEquals(8.0, central.items(warehouse).single().quantity, 0.0)
    }

    @Test fun anExpiredOrWrongCodeIsRefused() {
        val code = TeamSync(central).createInvite()
        assertTrue(joinWithCode("ZZZZZZZZZZ") is TeamLinkProtocol.Outcome.Failed)
        central.writableDatabase.execSQL("UPDATE team_sync_settings SET value='1' WHERE key='invite_until'")
        val expired = joinWithCode(code)
        assertTrue(expired is TeamLinkProtocol.Outcome.Failed)
        assertTrue((expired as TeamLinkProtocol.Outcome.Failed).message.contains("scaduto"))
        assertEquals(TeamRole.NONE, TeamSync(satellite).status().role)
    }

    @Test fun aPasswordSealedOnAnotherPhoneIsAskedAgain() {
        // As after restoring a backup on a new phone: the stored value cannot be opened here.
        central.writableDatabase.execSQL("UPDATE team_sync_settings SET value='ks1:AAAA:BBBB' WHERE key='password'")
        assertTrue(TeamSync(central).status().needsPassword)
        val failed = TeamLinkProtocol.answer(TeamSync(central), TeamLinkProtocol.joinRequest(TeamSync(satellite), pw, "B"))
        assertTrue(failed.failed)
        TeamSync(central).restorePassword(pw)
        assertFalse(TeamSync(central).status().needsPassword)
        assertTrue(joinNearby() is TeamLinkProtocol.Outcome.Done)
    }

    @Test fun satellitesRecogniseOnlyTheirCentral() {
        val uid = TeamSync(central).warehouseUid()!!
        val name = TeamLinkProtocol.endpointName(TeamLinkProtocol.tag(uid), "Officina|Rovigo")
        val (tag, warehouseName) = TeamLinkProtocol.parseEndpointName(name)!!
        assertEquals(TeamLinkProtocol.tag(uid), tag)
        assertEquals("Officina Rovigo", warehouseName)
        assertNotEquals(TeamLinkProtocol.tag(uid), TeamLinkProtocol.tag("altro-magazzino"))
        assertNull(TeamLinkProtocol.parseEndpointName("Satellite B"))
    }
}
