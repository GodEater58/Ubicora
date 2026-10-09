package com.officinastock.app

data class Profile(val id: Long, val name: String)
data class Warehouse(val id: Long, val profileId: Long, val name: String, val location: String, val mapWidth: Float = 12f, val mapHeight: Float = 8f)
data class StockItem(
    val id: Long, val warehouseId: Long, val sku: String, val name: String, val category: String,
    val quantity: Double, val minimum: Double, val unit: String, val placementId: Long?, val note: String,
    val photoFile: String? = null,
    val unitCost: Double? = null, val supplier: String = "", val leadDays: Int? = null
) { val lowStock get() = quantity <= minimum }
data class Placement(val id: Long, val warehouseId: Long, val kind: String, val label: String,
    val x: Float, val y: Float, val locked: Boolean, val width: Float = 1.6f, val depth: Float = 0.75f, val rotation: Float = 0f)
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

