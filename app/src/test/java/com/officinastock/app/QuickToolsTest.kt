package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class QuickToolsTest {
    private fun item(name: String, qty: Double, min: Double, supplier: String = "", unit: String = "pz") =
        StockItem(1, 1, "RIC-00000${name.length}", name, "Ricambi", qty, min, unit, null, "", supplier = supplier)

    @Test fun teamQrCarriesAOneTimeCodeNotThePassword() {
        val raw = TeamQr.encode("K7M2P9QX4T", listOf("C", "B"), "Officina Rovigo")
        val invite = TeamQr.parse(raw)!!
        assertTrue(invite.viaCode)
        assertEquals("K7M2P9QX4T", invite.secret)
        assertEquals(setOf("B", "C"), invite.taken)
        assertEquals("D", invite.suggestedLetter)
        assertEquals("Officina Rovigo", invite.warehouse)
    }

    @Test fun qrPrintedBefore411StillWork() {
        val invite = TeamQr.parse(TeamQr.encodeLegacy("squadra:2026 è", listOf("B"), "Officina"))!!
        assertFalse(invite.viaCode)
        assertEquals("squadra:2026 è", invite.secret)
    }

    @Test fun otherCodesAreNotTeamInvites() {
        assertNull(TeamQr.parse("OFFICINA-STOCK:1:W1:RIC-000001"))
        assertNull(TeamQr.parse("UBICORA-TEAM:2:%%%:B:x"))
        assertNull(TeamQr.parse(TeamQr.encode("corta", emptyList(), "X")))   // under 8 characters
    }

    @Test fun reorderListGroupsBySupplierAndSuggestsTwiceTheMinimum() {
        val text = ReorderList.text("Officina", listOf(
            item("Vite", 2.0, 5.0, "Ferramenta Rossi"),
            item("Dado", 0.0, 0.0),
            item("Rondella", 10.0, 3.0, "Ferramenta Rossi"),        // not low: left out
            item("Cavo", 1.5, 2.0, "Elettro Srl", "m")))
        assertTrue(text.contains("• Vite (RIC-000004): 2/5 pz → ordina 8 pz"))
        assertTrue(text.contains("• Dado (RIC-000004): 0/0 pz → ordina 1 pz"))
        assertTrue(text.contains("• Cavo (RIC-000004): 1.50/2 m → ordina 3 m") || text.contains("• Cavo (RIC-000004): 1,50/2 m → ordina 3 m"))
        assertFalse(text.contains("Rondella"))
        // Suppliers in alphabetical order, articles without supplier at the end.
        assertTrue(text.indexOf("— Elettro Srl —") < text.indexOf("— Ferramenta Rossi —"))
        assertTrue(text.indexOf("— Ferramenta Rossi —") < text.indexOf("— Senza fornitore —"))
        assertTrue(text.trimEnd().endsWith("3 articoli sotto scorta"))
        assertTrue(ReorderList.text("Officina", listOf(item("Rondella", 10.0, 3.0))).contains("Nessun articolo da ordinare"))
    }

    @Test fun agoSpeaksLikeAPerson() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 15, 0, 0) }.timeInMillis
        assertEquals("mai", ago(0, now))
        assertEquals("adesso", ago(now - 20_000, now))
        assertEquals("12 min fa", ago(now - 12 * 60_000, now))
        assertEquals("oggi alle 9:05", ago(Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 5) }.timeInMillis, now))
        assertTrue(ago(now - 26 * 3_600_000L, now).startsWith("ieri alle"))
        assertEquals("3 giorni fa", ago(now - 3 * 86_400_000L - 1000, now))
    }
}
