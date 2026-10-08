package com.officinastock.app

data class Profile(val id: Long, val name: String)
data class Warehouse(val id: Long, val profileId: Long, val name: String, val location: String)
data class StockItem(
    val id: Long, val warehouseId: Long, val sku: String, val name: String, val category: String,
    val quantity: Double, val minimum: Double, val unit: String, val placementId: Long?, val note: String
) { val lowStock get() = quantity <= minimum }
data class Placement(val id: Long, val warehouseId: Long, val kind: String, val label: String,
    val x: Float, val y: Float, val locked: Boolean)
data class Wall(val id: Long, val warehouseId: Long, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val source: String)
data class Movement(val id: Long, val itemId: Long, val sku: String, val itemName: String,
    val delta: Double, val note: String, val createdAt: Long)
data class Loan(val id: Long, val itemId: Long, val borrower: String, val quantity: Double,
    val createdAt: Long, val returnedAt: Long?)

object StockRules {
    fun sku(value: String) = value.trim().uppercase(java.util.Locale.ROOT)
    fun validQuantity(value: Double): Boolean = value.isFinite() && value >= 0.0
    fun applyDelta(current: Double, delta: Double): Double {
        require(current.isFinite() && current >= 0.0 && delta.isFinite()) { "Quantità non valida" }
        val next = current + delta
        require(next.isFinite() && next >= 0.0) { "Giacenza insufficiente" }
        return next
    }
    fun mapPosition(x: Float) = x.coerceIn(0f, 100f)
    fun normalizeWall(raw: FloatArray): FloatArray {
        require(raw.size == 4 && raw.all { it.isFinite() }) { "Coordinate non valide" }
        require(kotlin.math.hypot((raw[2]-raw[0]).toDouble(),(raw[3]-raw[1]).toDouble()) > .15) { "Parete troppo corta" }
        return raw
    }
}
