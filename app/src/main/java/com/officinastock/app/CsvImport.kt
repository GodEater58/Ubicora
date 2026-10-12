package com.officinastock.app

/** CSV Ubicora: semicolon delimiter, UTF-8, escaped quotes and quoted newlines. */
data class CsvImportRow(
    val sku: String, val name: String, val category: String, val quantity: Double,
    val minimum: Double, val unit: String, val location: String, val note: String,
    val cost: Double?, val supplier: String, val leadDays: Int?,
    val shelfLevel: Int? = null, val shelfSlot: Int? = null
)

object CsvImport {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_ITEMS = 20000
    private val textColumns=setOf(1,6,7,9) // descrizione, ubicazione, note, fornitore
    private val legacyHeader=listOf("Codice","Descrizione","Categoria","Quantità","Minimo",
        "Unità","Ubicazione","Note","Costo unitario EUR","Fornitore","Consegna giorni")
    /** 4.7.1 adds shelf level and position at the end; files from earlier versions (11 columns) still import. */
    val header=legacyHeader+listOf("Ripiano","Posizione")

    /** Undo the apostrophe that StockDb.csv() / the web export prepend to neutralise spreadsheet formulas. */
    internal fun unformula(value: String): String =
        if(value.length>1 && value[0]=='\'' &&
            (value.substring(1).trimStart().firstOrNull() in listOf('=','+','-','@') || value[1]=='\t' || value[1]=='\r'))
            value.substring(1) else value

    /** The importer accepts only the known export layout. No silent guesses or partial data. */
    fun parse(source: String): List<CsvImportRow> {
        require(source.length<=MAX_BYTES) { "CSV superiore al limite di 8 MiB" }
        val text=source.removePrefix("\uFEFF")
        val records=ArrayList<List<String>>()
        val fields=ArrayList<String>()
        val field=StringBuilder()
        var inQuotes=false
        var fieldStarted=false
        var quoteClosed=false
        var i=0
        fun finishField() {
            require(field.length<=4000) { "Campo CSV troppo lungo" }
            fields.add(field.toString())
            field.clear()
            fieldStarted=false
            quoteClosed=false
        }
        fun finishRow() {
            finishField()
            if(fields.size!=1 || fields[0].isNotEmpty()) records.add(fields.toList())
            fields.clear()
            require(records.size<=MAX_ITEMS+1) { "CSV: massimo 20.000 articoli" }
        }
        while(i<text.length) {
            val ch=text[i]
            if(inQuotes) {
                if(ch=='"') {
                    if(i+1<text.length && text[i+1]=='"') {field.append('"');i++}
                    else {inQuotes=false;quoteClosed=true}
                } else field.append(ch)
            } else {
                require(!quoteClosed || ch in charArrayOf(';','\r','\n')) { "Caratteri dopo la chiusura del campo CSV" }
                when(ch) {
                '"' -> {
                    require(!fieldStarted) { "Virgolette CSV non valide" }
                    inQuotes=true
                    fieldStarted=true
                }
                ';' -> finishField()
                '\r','\n' -> {
                    finishRow()
                    if(ch=='\r' && i+1<text.length && text[i+1]=='\n')i++
                }
                else -> {field.append(ch);fieldStarted=true}
            }
            }
            require(field.length<=4000) { "Campo CSV troppo lungo" }
            i++
        }
        require(!inQuotes) { "CSV non completo: virgolette aperte" }
        if(field.isNotEmpty() || fieldStarted || fields.isNotEmpty()) finishRow()
        require(records.isNotEmpty() && (records[0]==header || records[0]==legacyHeader)) {
            "Intestazioni non riconosciute: esporta un CSV da Ubicora per ottenere il modello"
        }
        require(records.size>1) { "Il CSV non contiene articoli" }
        val seen=HashSet<String>()
        return records.drop(1).mapIndexed { index, raw ->
            val v=raw.mapIndexed { column, text -> if(column in textColumns) unformula(text) else text }
            require(v.size==records[0].size) { "Riga ${index+2}: colonne CSV non valide" }
            val sku=v[0].trim().uppercase(java.util.Locale.ROOT)
            val prefix=sku.substringBefore('-')
            val serial=sku.substringAfter('-', "").toLongOrNull()
            require(ArticleCodes.categoryPrefixes.containsKey(v[2]) && prefix in ArticleCodes.categoryPrefixes.values && serial!=null && serial in 1..999999999L &&
                Regex("^[A-Z]{3}-[0-9]{6,9}$").matches(sku)) { "Riga ${index+2}: codice o categoria non validi" }
            require(seen.add(sku)) { "Codice duplicato nel CSV" }
            val qty=v[3].replace(',','.').toDoubleOrNull()
            val min=v[4].replace(',','.').toDoubleOrNull()
            val cost=if(v[8].isBlank())null else v[8].replace(',','.').toDoubleOrNull()
            val lead=if(v[10].isBlank())null else v[10].toIntOrNull()
            require(v[1].trim().length in 1..180 && v[5].trim().length in 1..24 &&
                v[7].length<=3000 && v[9].length<=160 &&
                qty!=null && StockRules.validQuantity(qty) &&
                min!=null && StockRules.validQuantity(min) &&
                (v[8].isBlank() || (cost!=null && cost.isFinite() && cost in 0.0..100000000.0)) &&
                (v[10].isBlank() || (lead!=null && lead in 0..3650))) {
                "Riga ${index+2}: dati non validi"
            }
            fun shelf(column:Int,max:Int,label:String):Int? {
                val raw=v.getOrNull(column)?.trim().orEmpty()
                if(raw.isEmpty()) return null
                val n=raw.toIntOrNull()
                require(n!=null && n in 1..max) { "Riga ${index+2}: $label non valido (1–$max)" }
                return n
            }
            val level=shelf(11,20,"ripiano")
            val slot=shelf(12,50,"posizione")
            require((level==null && slot==null) || v[6].isNotBlank()) { "Riga ${index+2}: ripiano senza ubicazione" }
            CsvImportRow(sku,v[1].trim(),v[2],qty!!,min!!,v[5].trim(),v[6],v[7],
                cost,v[9],lead,level,slot)
        }
    }
}

