package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    private val header="\"Codice\";\"Descrizione\";\"Categoria\";\"Quantità\";\"Minimo\";\"Unità\";\"Ubicazione\";\"Note\";\"Costo unitario EUR\";\"Fornitore\";\"Consegna giorni\""
    private fun row(sku:String="RIC-000321", name:String="Vite") =
        listOf(sku,name,"Ricambi","3.5","1","pz","","note","2.5","Officina","5").joinToString(";") { value -> "\""+value.replace("\"","\"\"")+"\"" }
    private fun rejects(raw:String) {
        try { CsvImport.parse(raw); fail("CSV non valido accettato") }
        catch(expected:IllegalArgumentException) {}
    }
    @Test fun acceptsExportedSemicolonCsvWithUtf8AndQuotedNewline() {
        val csv="\uFEFF"+header+"\r\n"+row()+"\r\n"
        val parsed=CsvImport.parse(csv)
        assertEquals(1,parsed.size)
        assertEquals("RIC-000321",parsed[0].sku)
        assertEquals(3.5,parsed[0].quantity,0.0)
        assertEquals(2.5,parsed[0].cost!!,0.0)
        val special=header+"\n"+row(name="Vite, M8") + "\n"
        assertEquals("Vite, M8",CsvImport.parse(special).single().name)
        assertEquals("ELE-000321",CsvImport.parse(header+"\n"+row(sku="ELE-000321")).single().sku)
    }
    @Test fun rejectsDuplicateRowsIncorrectHeaderAndMalformedQuotes() {
        rejects(header+"\n"+row().replace("\"Vite\"","\"Vite\"spazzatura"))
        rejects("wrong;header\n"+row())
        rejects(header+"\n"+row()+"\n"+row()+"\n")
        rejects(header+"\n"+row().replace("RIC-000321","ABC-000321"))
        rejects(header+"\n"+row().dropLast(1))
        rejects(header+"\n"+row().replace("3.5","-9"))
    }
    @Test fun formulaApostropheAddedOnExportIsRemovedOnImport() {
        val values=listOf("RIC-000321","'=Vite","Ricambi","3","1","pz","'@Scaffale","'-10% sconto","2","'+Fornitore","5")
        val csv=header+"\n"+values.joinToString(";"){"\""+it+"\""}+"\n"
        val parsed=CsvImport.parse(csv).single()
        assertEquals("=Vite",parsed.name)
        assertEquals("@Scaffale",parsed.location)
        assertEquals("-10% sconto",parsed.note)
        assertEquals("+Fornitore",parsed.supplier)
        assertEquals("'normale",CsvImport.unformula("'normale"))
        assertEquals("'",CsvImport.unformula("'"))
    }
}
