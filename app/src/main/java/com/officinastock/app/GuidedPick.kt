package com.officinastock.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.15 guided picking: one line of the list ([skipped] = "later", walked at the end; [done] = already taken). */
data class PickLine(val itemId: Long, val quantity: Double, val skipped: Boolean = false, val done: Boolean = false)
data class PickStop(val line: PickLine, val item: StockItem, val place: Placement?)
/** A list where everything has been taken is over. */
object PickLists { fun finished(lines: List<PickLine>): List<PickLine> = if (lines.none { !it.done }) emptyList() else lines }
/** What the map draws: structures already visited, the one to reach and where the walk comes from. */
data class PickGuide(val done: Set<Long> = emptySet(), val next: Placement? = null, val from: Placement? = null)

/**
 * Order of the walk. Structures are visited from the first by name to the nearest not yet visited
 * (the plan has no entrance, so the name decides where to start); inside a structure by shelf and
 * position. Articles without a location come after, skipped ones last.
 */
object PickRoute {
    fun order(lines: List<PickLine>, items: List<StockItem>, placements: List<Placement>): List<PickStop> {
        val itemById = items.associateBy { it.id }
        val placeById = placements.associateBy { it.id }
        val stops = lines.filter { !it.done }.mapNotNull { line ->
            itemById[line.itemId]?.let { item -> PickStop(line, item, item.placementId?.let { placeById[it] }) }
        }
        return walk(stops.filter { !it.line.skipped }) + walk(stops.filter { it.line.skipped })
    }

    /** Done lines are kept, in the order they were taken, until the list is finished. */
    fun guide(lines: List<PickLine>, items: List<StockItem>, placements: List<Placement>): PickGuide {
        if (lines.isEmpty()) return PickGuide()
        val itemById = items.associateBy { it.id }
        val placeById = placements.associateBy { it.id }
        fun placeOf(line: PickLine) = itemById[line.itemId]?.placementId?.let { placeById[it] }
        val next = order(lines, items, placements).firstOrNull()?.place
        val waiting = lines.filter { !it.done }.mapNotNull { placeOf(it)?.id }.toSet()
        val done = lines.filter { it.done }.mapNotNull { placeOf(it)?.id }.toSet() - waiting
        val from = lines.lastOrNull { it.done }?.let { placeOf(it) }?.takeIf { it.id != next?.id }
        return PickGuide(done, next, from)
    }

    /**
     * Corner of the right-angled stretch between two structures: of the two possible corners, the
     * one whose legs cross fewer other structures (it is a hint on the plan, not a route planner).
     */
    fun elbow(from: Placement, to: Placement, placements: List<Placement>): PointMetres {
        val others = placements.filter { it.id != from.id && it.id != to.id && it.kind != "Corridoio" && it.kind != "Porta" }
        fun crossed(corner: PointMetres): Int {
            var hits = 0
            for (other in others) {
                var inside = false
                for (step in 0..12) {
                    val t = step / 12f
                    val first = PointMetres(from.x + (corner.x - from.x) * t, from.y + (corner.y - from.y) * t)
                    val second = PointMetres(corner.x + (to.x - corner.x) * t, corner.y + (to.y - corner.y) * t)
                    if (PlanGeometry.contains(first, other, 0f) || PlanGeometry.contains(second, other, 0f)) { inside = true; break }
                }
                if (inside) hits++
            }
            return hits
        }
        val horizontalFirst = PointMetres(to.x, from.y)
        val verticalFirst = PointMetres(from.x, to.y)
        return if (crossed(verticalFirst) < crossed(horizontalFirst)) verticalFirst else horizontalFirst
    }

    private fun walk(stops: List<PickStop>): List<PickStop> {
        val byPlace = LinkedHashMap<Long, MutableList<PickStop>>()
        val places = HashMap<Long, Placement>()
        val nowhere = ArrayList<PickStop>()
        for (stop in stops) {
            val place = stop.place
            if (place == null) nowhere += stop
            else { byPlace.getOrPut(place.id) { ArrayList() } += stop; places[place.id] = place }
        }
        val out = ArrayList<PickStop>()
        var here: Placement? = places.values.minWithOrNull(compareBy<Placement>({ it.label.lowercase() }, { it.id }))
        while (here != null) {
            val at: Placement = here
            out += byPlace.remove(at.id).orEmpty()
                .sortedWith(compareBy<PickStop>({ it.item.shelfLevel ?: 0 }, { it.item.shelfSlot ?: 0 }, { it.item.name.lowercase() }))
            places.remove(at.id)
            here = places.values.minWithOrNull(compareBy<Placement>({ (it.x - at.x) * (it.x - at.x) + (it.y - at.y) * (it.y - at.y) }, { it.id }))
        }
        return out + nowhere.sortedBy { it.item.name.lowercase() }
    }
}

/**
 * 4.15 guided picking. On the map it is the large panel of the walk: progress, next article, where
 * it is, how many, confirmation by scanning its label. On the other sections it shrinks to a bar
 * that leads back to the map. The structure to reach is highlighted on the plan by itself.
 */
@Composable fun GuidedPickBar(model: StockViewModel, onMapTab: Boolean) {
    val u = model.ui
    if (u.pickList.isEmpty()) return
    val route = remember(u.pickList, u.items, u.placements) { PickRoute.order(u.pickList, u.items, u.placements) }
    val next = route.firstOrNull() ?: return
    val placeId = next.place?.id
    LaunchedEffect(next.item.id, onMapTab) { if (onMapTab && placeId != null) model.showOnMap(placeId) }
    val total = u.pickList.size
    val done = u.pickList.count { it.done }
    val take = minOf(next.line.quantity, next.item.quantity)
    Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        if (!onMapTab) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("PRELIEVO GUIDATO · $done di $total", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(next.item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Locations.describe(next.place, next.item), color = if (next.place == null) Amber else Sky, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { if (placeId != null) model.showOnMap(placeId) else model.openGuidedMap() }, modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Vai", fontWeight = FontWeight.Bold) }
        } else Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Title and progress are at the top of the map (GuidedMapHeader): here only what to take.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("PROSSIMO", color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(next.item.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
                    Text(Locations.describe(next.place, next.item), color = if (next.place == null) Amber else Sky, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amount(take), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (take < next.line.quantity) Danger else TextPrimary)
                    Text("${next.item.unit} da prendere", color = TextSecondary, fontSize = 12.sp)
                }
            }
            if (take < next.line.quantity) Text("Ne servono ${amount(next.line.quantity)}, a scaffale ${amount(next.item.quantity)} ${next.item.unit}.",
                color = Danger, fontSize = 12.sp)
            Button(onClick = { model.scanToConfirmPick(next.item.id) }, enabled = !u.busy && take > 0.0,
                modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Scansiona per confermare", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { model.skipPick(next.item.id) }, enabled = route.size > 1, modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Salta") }
                OutlinedButton(onClick = { model.removeFromPickList(next.item.id) }, modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Manca a scaffale", maxLines = 1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (route.size > 1) "Poi: " + route.drop(1).take(2).joinToString(" · ") { it.item.name + (it.place?.let { p -> " (${p.label})" } ?: "") }
                    else "Ultimo articolo della lista", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            // Only after a reading that failed or was closed: a worn or missing label must not block the walk.
            if (u.pickScanMissedId == next.item.id && take > 0.0) TextButton(onClick = { model.confirmPick(next.item.id) }, enabled = !u.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Etichetta illeggibile? Conferma a mano") }
        }
    }
}

/** 4.15: title of the map during a guided picking, with one segment per article (taken, next, to take). */
@Composable fun GuidedMapHeader(lines: List<PickLine>, onClose: () -> Unit) {
    val total = lines.size
    val done = lines.count { it.done }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Chiudi il prelievo guidato") }
            Column(Modifier.weight(1f)) {
                Text("Prelievo guidato", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$done di $total fatti", color = TextSecondary, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total.coerceAtMost(24)) { index ->
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (index < done) Mint else if (index == done) Amber else Surface2))
            }
        }
    }
}
