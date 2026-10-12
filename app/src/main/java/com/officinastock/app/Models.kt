package com.officinastock.app

data class Profile(val id: Long, val name: String)
data class Warehouse(val id: Long, val profileId: Long, val name: String, val location: String, val mapWidth: Float = 12f, val mapHeight: Float = 8f)
data class StockItem(
    val id: Long, val warehouseId: Long, val sku: String, val name: String, val category: String,
    val quantity: Double, val minimum: Double, val unit: String, val placementId: Long?, val note: String,
    val photoFile: String? = null,
    val unitCost: Double? = null, val supplier: String = "", val leadDays: Int? = null,
    val shelfLevel: Int? = null, val shelfSlot: Int? = null
) { val lowStock get() = quantity <= minimum }
data class Placement(val id: Long, val warehouseId: Long, val kind: String, val label: String,
    val x: Float, val y: Float, val locked: Boolean, val width: Float = 1.6f, val depth: Float = 0.75f, val rotation: Float = 0f,
    val levels: Int = 1, val slots: Int = 1) {
    /** Doors, columns and corridors orient people on the plan but never hold stock. */
    val holdsStock get() = PlacementKinds.holdsStock(kind)
    val hasPositions get() = levels > 1 || slots > 1
}

object PlacementKinds {
    val storage = listOf("Scaffale","Armadio","Cassetto","Banco","Area")
    val landmarks = listOf("Porta","Colonna","Corridoio")
    val all = storage + landmarks
    fun holdsStock(kind: String) = kind !in landmarks
    const val MAX_LEVELS = 20
    const val MAX_SLOTS = 50
}

/** Human-readable precise location, e.g. "Scaffale A · R2 P3". */
object Locations {
    fun code(level: Int?, slot: Int?): String = buildString {
        if(level != null) append("R").append(level)
        if(slot != null) { if(isNotEmpty()) append(' '); append("P").append(slot) }
    }
    fun describe(placement: Placement?, item: StockItem): String {
        if(placement == null) return "Da assegnare"
        val suffix = code(item.shelfLevel, item.shelfSlot)
        return if(suffix.isEmpty()) placement.label else "${placement.label} · $suffix"
    }
    fun validate(placement: Placement, level: Int?, slot: Int?) {
        require(placement.holdsStock) { "${placement.kind}: non può contenere articoli" }
        require(level == null || level in 1..placement.levels) { "Ripiano fuori dalla struttura (1–${placement.levels})" }
        require(slot == null || slot in 1..placement.slots) { "Posizione fuori dalla struttura (1–${placement.slots})" }
    }
}
data class Wall(val id: Long, val warehouseId: Long, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val source: String)
data class Movement(val id: Long, val itemId: Long, val sku: String, val itemName: String,
    val delta: Double, val note: String, val createdAt: Long)
data class Loan(val id: Long, val itemId: Long, val borrower: String, val quantity: Double,
    val createdAt: Long, val returnedAt: Long?)

object StockRules {
    const val QUANTITY_EPSILON = 0.0000001
    /** Decimal arithmetic avoids rejecting a valid 0.1 + 0.2 receipt. */
    fun difference(a: Double, b: Double): Double {
        require(a.isFinite() && b.isFinite()) { "Quantità non valida" }
        return java.math.BigDecimal.valueOf(a).subtract(java.math.BigDecimal.valueOf(b)).toDouble()
    }
    fun sku(value: String) = value.trim().uppercase(java.util.Locale.ROOT)
    fun validQuantity(value: Double): Boolean = value.isFinite() && value >= 0.0
    fun applyDelta(current: Double, delta: Double): Double {
        require(current.isFinite() && current >= 0.0 && delta.isFinite()) { "Quantità non valida" }
        val next = java.math.BigDecimal.valueOf(current).add(java.math.BigDecimal.valueOf(delta)).toDouble()
        require(next.isFinite() && next >= -QUANTITY_EPSILON) { "Giacenza insufficiente" }
        return next.coerceAtLeast(0.0)
    }
    fun mapPosition(x: Float) = x.coerceIn(0f, 100f)
    fun normalizeWall(raw: FloatArray): FloatArray {
        require(raw.size == 4 && raw.all { it.isFinite() }) { "Coordinate non valide" }
        require(kotlin.math.hypot((raw[2]-raw[0]).toDouble(),(raw[3]-raw[1]).toDouble()) > .15) { "Parete troppo corta" }
        return raw
    }
}

/** Codifica assegnata esclusivamente dal database al momento del salvataggio. */
object ArticleCodes {
    val categoryPrefixes = linkedMapOf(
        "Ricambi" to "RIC", "Utensili" to "UTE", "Elettrico" to "ELE",
        "Pneumatica" to "PNE", "Idraulica" to "IDR", "Consumabili" to "CON",
        "DPI" to "DPI", "Meccanica" to "MEC", "Altro" to "ALT"
    )
    fun prefix(category: String): String = categoryPrefixes[category]
        ?: throw IllegalArgumentException("Categoria non riconosciuta")
    fun format(prefix: String, serial: Long): String {
        require(prefix in categoryPrefixes.values && serial in 1..999999999L) { "Codice articolo non valido" }
        return "%s-%06d".format(java.util.Locale.ROOT, prefix, serial)
    }
    fun payload(warehouseId: Long, sku: String): String =
        "OFFICINA-STOCK:1:W$warehouseId:$sku"

    data class ScanTarget(val warehouseId: Long?, val sku: String)
    /** QR stuck on a shelf: opens the structure and its contents, never moves stock. */
    fun locationPayload(warehouseId: Long, placementId: Long): String =
        "UBICORA-LOC:1:W$warehouseId:P$placementId"
    data class LocationTarget(val warehouseId: Long, val placementId: Long)
    fun parseLocationScan(raw: String): LocationTarget? {
        val match=Regex("^UBICORA-LOC:1:W([0-9]{1,18}):P([0-9]{1,18})$").matchEntire(raw.trim().uppercase(java.util.Locale.ROOT)) ?: return null
        val warehouseId=match.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val placementId=match.groupValues[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return LocationTarget(warehouseId,placementId)
    }
    /** Accetta i QR generati dall'app e, per compatibilità, i vecchi QR con solo codice. */
    fun parseScan(raw: String): ScanTarget? {
        val input=raw.trim().uppercase(java.util.Locale.ROOT)
        if(input.length !in 1..128) return null
        val prefixed=Regex("^OFFICINA-STOCK:1:W([0-9]+):([A-Z0-9][A-Z0-9._/-]{0,63})$").matchEntire(input)
        if(prefixed != null) {
            val warehouseId=prefixed.groupValues[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            return ScanTarget(warehouseId,prefixed.groupValues[2])
        }
        if(input.startsWith("OFFICINA-STOCK:")) return null
        if(!Regex("^[A-Z0-9][A-Z0-9._/-]{0,63}$").matches(input)) return null
        return ScanTarget(null,input)
    }
}


/**
 * 4.7.4: units are chosen from a list instead of typed, so "pz", "Pz" and "pezzi" can no longer
 * coexist. Each category shows its likely units first; every unit stays available for the rest.
 */
object MeasureUnits {
    /** Stored code to label shown in the menu. Codes are what the database and CSV keep. */
    val all: LinkedHashMap<String, String> = linkedMapOf(
        "pz" to "pezzi", "paia" to "paia", "set" to "set / kit", "conf" to "confezioni",
        "scatole" to "scatole", "rotoli" to "rotoli", "m" to "metri", "cm" to "centimetri",
        "mm" to "millimetri", "m²" to "metri quadri", "kg" to "chilogrammi", "g" to "grammi",
        "l" to "litri", "ml" to "millilitri"
    )
    private val byCategory = mapOf(
        "Ricambi" to listOf("pz", "set", "paia", "conf"),
        "Utensili" to listOf("pz", "set"),
        "Elettrico" to listOf("pz", "m", "rotoli", "conf"),
        "Pneumatica" to listOf("pz", "m", "set", "conf"),
        "Idraulica" to listOf("pz", "m", "l", "set"),
        "Consumabili" to listOf("conf", "pz", "kg", "l", "rotoli", "g", "ml", "m"),
        "DPI" to listOf("paia", "pz", "conf"),
        "Meccanica" to listOf("pz", "kg", "m", "set"),
        "Altro" to listOf("pz")
    )
    /** Units suggested for [category], most likely first. */
    fun suggested(category: String): List<String> = byCategory[category] ?: listOf("pz")
    /** Suggested units first, then all the others. A legacy free-text unit is kept at the top. */
    fun options(category: String, current: String = ""): List<String> {
        val first = suggested(category)
        val rest = all.keys.filter { it !in first }
        val legacy = current.trim().takeIf { it.isNotEmpty() && it !in all.keys }
        return listOfNotNull(legacy) + first + rest
    }
    fun label(code: String): String = all[code]?.let { "$code · $it" } ?: code
    /** When the category changes, keep the unit if it still fits, otherwise use the category default. */
    fun afterCategoryChange(category: String, current: String): String =
        if (current in suggested(category)) current else suggested(category).first()
}
