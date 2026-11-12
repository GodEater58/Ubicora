package com.officinastock.app

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 4.20: the logic behind "where is it" on the map, without any drawing, so it can be tested on the JVM:
 * the search, the walk from the entrance, the front view of a shelf and where the names go on the plan.
 */
data class MapResults(
    /** Matching articles: the ones with a place first, then those still without one. */
    val items: List<StockItem> = emptyList(),
    /** Structures whose own name matches (for example "sc2"). */
    val structures: List<Placement> = emptyList(),
    /** Structure id → how many of [items] are in it. */
    val countByPlacement: Map<Long, Int> = emptyMap(),
) {
    /** Structures to light up on the plan. */
    val lit: Set<Long> get() = countByPlacement.keys + structures.map { it.id }
    val isEmpty: Boolean get() = items.isEmpty() && structures.isEmpty()
}

object MapSearch {
    private fun words(text: String) = text.lowercase(Locale.ITALY).split(' ', '\t', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
    private fun fold(text: String) = text.lowercase(Locale.ITALY)

    /** Every word typed must appear in the name, code, category or description ("m12 90" finds "connettore M12 F 90°"). */
    fun matches(item: StockItem, query: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return false
        val hay = fold(listOf(item.name, item.sku, item.category, item.note).joinToString(" "))
        return wanted.all { it in hay }
    }

    fun matches(placement: Placement, query: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return false
        val hay = fold(placement.label + " " + placement.kind)
        return wanted.all { it in hay }
    }

    fun results(query: String, items: List<StockItem>, placements: List<Placement>): MapResults {
        if (words(query).isEmpty()) return MapResults()
        val placeById = placements.associateBy { it.id }
        // An article pointing to a structure that no longer exists counts as "without a place".
        fun placed(item: StockItem) = item.placementId?.let { placeById[it] } != null
        val found = items.filter { matches(it, query) }
            .sortedWith(compareBy<StockItem>({ !placed(it) }, { it.name.lowercase(Locale.ITALY) }))
        val counts = found.filter { placed(it) }.groupingBy { it.placementId!! }.eachCount()
        val shelves = placements.filter { it.holdsStock && matches(it, query) }.sortedBy { it.label.lowercase(Locale.ITALY) }
        return MapResults(found, shelves, counts)
    }
}

/** Walk on the plan from the entrance to a structure, as a right-angled hint (not a route planner). */
data class MapWalk(val from: Placement, val corner: PointMetres, val to: Placement,
                   /** 4.20.1: where the walk starts: the middle of the opening of [from]. */
                   val start: PointMetres = PointMetres(from.x, from.y)) {
    /** Length of the two legs, in metres. */
    val metres: Float get() = abs(corner.x - start.x) + abs(corner.y - start.y) + abs(to.x - corner.x) + abs(to.y - corner.y)
}

object MapRoute {
    /** The door or gate called "ingresso" (or entrance, entrata), else the one nearest to [target], else null. */
    fun entrance(placements: List<Placement>, target: Placement?): Placement? {
        val doors = placements.filter { PlacementKinds.isPassage(it.kind) && it.id != target?.id }
        if (doors.isEmpty()) return null
        doors.firstOrNull { d -> listOf("ingress", "entrat", "entranc").any { d.label.contains(it, ignoreCase = true) } }?.let { return it }
        if (target == null) return doors.first()
        return doors.minBy { abs(it.x - target.x) + abs(it.y - target.y) }
    }

    fun walk(placements: List<Placement>, target: Placement): MapWalk? {
        val door = entrance(placements, target) ?: return null
        val start = PlanGeometry.passageMid(door)
        val end = PointMetres(target.x, target.y)
        return MapWalk(door, PickRoute.elbow(start, end, placements.filter { it.id != target.id }), target, start)
    }

    /** "Scaffale sc2 · Ripiano 2 · Posizione 1": where the article is, in full words. */
    fun placeText(place: Placement, item: StockItem): String {
        val name = place.label.ifBlank { place.kind }
        val head = if (name.contains(place.kind, ignoreCase = true)) name else "${place.kind} $name"
        val level = item.shelfLevel?.takeIf { place.levels > 1 && it in 1..place.levels }?.let { "Ripiano $it" }
        val slot = item.shelfSlot?.takeIf { place.slots > 1 && it in 1..place.slots }?.let { "Posizione $it" }
        return listOfNotNull(head, level, slot).joinToString(" · ")
    }

    /** "Circa 19 m dall'ingresso", in words for the screen. */
    fun describe(walk: MapWalk): String {
        val m = walk.metres
        val door = walk.from.label.ifBlank { walk.from.kind.lowercase() }
        val name = when {
            door.contains("ingress", ignoreCase = true) -> "dall'ingresso"
            door.equals("cancello", ignoreCase = true) -> "dal cancello"
            door.equals("porta", ignoreCase = true) -> "dalla porta"
            else -> "da $door"
        }
        val by = if (walk.from.kind == "Cancello") "Accanto al cancello" else "Accanto alla porta"
        return if (m < 1.5f) by else "Circa ${m.roundToInt()} m $name"
    }
}

/** One cell of the front view: a position of a shelf with what is in it. */
data class ShelfCell(val level: Int?, val slot: Int?, val items: List<StockItem>)
data class ShelfRow(val level: Int?, val cells: List<ShelfCell>, val withoutSlot: List<StockItem> = emptyList())
/** The shelf seen from the front: [rows] from the top shelf to the bottom one, [loose] = in it but with no shelf given. */
data class ShelfView(val rows: List<ShelfRow>, val loose: List<StockItem>) {
    val count: Int get() = rows.sumOf { r -> r.cells.sumOf { it.items.size } + r.withoutSlot.size } + loose.size
}

object ShelfLayout {
    /** More positions than this are drawn in a row that scrolls sideways. */
    const val WIDE = 4

    fun of(p: Placement, items: List<StockItem>): ShelfView {
        val inside = items.filter { it.placementId == p.id }.sortedBy { it.name.lowercase(Locale.ITALY) }
        if (!p.hasPositions) return ShelfView(listOf(ShelfRow(null, listOf(ShelfCell(null, null, inside)))), emptyList())
        val levels = p.levels.coerceAtLeast(1); val slots = p.slots.coerceAtLeast(1)
        fun levelOf(item: StockItem): Int? = if (levels > 1) item.shelfLevel?.takeIf { it in 1..levels } else 1
        fun slotOf(item: StockItem): Int? = if (slots > 1) item.shelfSlot?.takeIf { it in 1..slots } else 1
        val loose = inside.filter { levelOf(it) == null }
        val rows = (levels downTo 1).map { level ->
            val here = inside.filter { levelOf(it) == level }
            val cells = (1..slots).map { slot -> ShelfCell(if (levels > 1) level else null, if (slots > 1) slot else null, here.filter { slotOf(it) == slot }) }
            ShelfRow(if (levels > 1) level else null, cells, here.filter { slotOf(it) == null })
        }
        return ShelfView(rows, loose)
    }

    /** Title of a row: "Ripiano 2 · in alto". */
    fun rowTitle(row: ShelfRow, levels: Int): String {
        val level = row.level ?: return if (levels > 1) "Ripiano non indicato" else "Contenuto"
        return "Ripiano $level" + when {
            levels <= 1 -> ""
            level == levels -> " · in alto"
            level == 1 -> " · in basso"
            else -> ""
        }
    }
}

/** A rectangle on screen, in pixels. */
data class ScreenBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(o: ScreenBox) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun inside(width: Float, height: Float) = left >= 0f && top >= 0f && right <= width && bottom <= height
}

object MapLabels {
    /** Screen box of a structure of [w]×[h] pixels centred at ([cx],[cy]) and turned by [degrees]. */
    fun footprint(cx: Float, cy: Float, w: Float, h: Float, degrees: Float): ScreenBox {
        val r = Math.toRadians(degrees.toDouble())
        val c = abs(cos(r)).toFloat(); val s = abs(sin(r)).toFloat()
        val ex = (c * w + s * h) / 2f; val ey = (s * w + c * h) / 2f
        return ScreenBox(cx - ex, cy - ey, cx + ex, cy + ey)
    }

    /**
     * Where the name of a structure goes: beside it (right, left, below, above), never over another
     * structure or another name and always inside the screen. Null = no free place: the name is not drawn.
     */
    fun place(structure: ScreenBox, textW: Float, textH: Float, gap: Float, taken: List<ScreenBox>,
              width: Float, height: Float): ScreenBox? {
        val midY = (structure.top + structure.bottom) / 2f
        val midX = (structure.left + structure.right) / 2f
        val options = listOf(
            ScreenBox(structure.right + gap, midY - textH / 2f, structure.right + gap + textW, midY + textH / 2f),
            ScreenBox(structure.left - gap - textW, midY - textH / 2f, structure.left - gap, midY + textH / 2f),
            ScreenBox(midX - textW / 2f, structure.bottom + gap, midX + textW / 2f, structure.bottom + gap + textH),
            ScreenBox(midX - textW / 2f, structure.top - gap - textH, midX + textW / 2f, structure.top - gap),
        )
        return options.firstOrNull { box -> box.inside(width, height) && taken.none { it.intersects(box) } }
    }
}
