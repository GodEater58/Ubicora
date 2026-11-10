package com.officinastock.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.15 guided picking: one line of the list ([skipped] = "later", walked at the end). */
data class PickLine(val itemId: Long, val quantity: Double, val skipped: Boolean = false)
data class PickStop(val line: PickLine, val item: StockItem, val place: Placement?)

/**
 * Order of the walk. Structures are visited from the first by name to the nearest not yet visited
 * (the plan has no entrance, so the name decides where to start); inside a structure by shelf and
 * position. Articles without a location come after, skipped ones last.
 */
object PickRoute {
    fun order(lines: List<PickLine>, items: List<StockItem>, placements: List<Placement>): List<PickStop> {
        val itemById = items.associateBy { it.id }
        val placeById = placements.associateBy { it.id }
        val stops = lines.mapNotNull { line ->
            itemById[line.itemId]?.let { item -> PickStop(line, item, item.placementId?.let { placeById[it] }) }
        }
        return walk(stops.filter { !it.line.skipped }) + walk(stops.filter { it.line.skipped })
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
 * 4.15: bar of the guided picking, above the bottom bar on every section. It names the next
 * article and where it is; on the map the structure to reach is highlighted by itself.
 */
@Composable fun GuidedPickBar(model: StockViewModel, onMapTab: Boolean) {
    val u = model.ui
    if (u.pickList.isEmpty()) return
    val route = remember(u.pickList, u.items, u.placements) { PickRoute.order(u.pickList, u.items, u.placements) }
    val next = route.firstOrNull() ?: return
    val placeId = next.place?.id
    LaunchedEffect(next.item.id, onMapTab) { if (onMapTab && placeId != null) model.showOnMap(placeId) }
    val take = minOf(next.line.quantity, next.item.quantity)
    Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PRELIEVO GUIDATO · ${route.size} ${if (route.size == 1) "articolo" else "articoli"}", color = Amber, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = model::clearPickList) { Text("Svuota lista", fontSize = 12.sp) }
            }
            Text(next.item.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Locations.describe(next.place, next.item), color = if (next.place == null) Amber else Sky, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next.item.quantity < next.line.quantity) Text(
                "Ne servono ${amount(next.line.quantity)}, a scaffale ${amount(next.item.quantity)} ${next.item.unit}", color = Danger, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { model.confirmPick(next.item.id) }, enabled = !u.busy && take > 0.0,
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (take > 0.0) "Prelevato ${amount(take)} ${next.item.unit}" else "Esaurito", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (placeId != null && !onMapTab) OutlinedButton(onClick = { model.showOnMap(placeId) }, modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("In pianta") }
                if (take > 0.0 && route.size > 1) TextButton(onClick = { model.skipPick(next.item.id) }, modifier = Modifier.height(52.dp)) { Text("Dopo") }
                else if (take <= 0.0) TextButton(onClick = { model.removeFromPickList(next.item.id) }, modifier = Modifier.height(52.dp)) { Text("Togli") }
            }
        }
    }
}
