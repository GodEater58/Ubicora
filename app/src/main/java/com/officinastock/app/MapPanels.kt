package com.officinastock.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.20: results of the map search, above the plan: each article with its shelf, then the shelves by name. */
@Composable fun MapResultsCard(results: MapResults, placements: List<Placement>, model: StockViewModel, query: String,
                               onItem: (StockItem) -> Unit, onShelf: (Placement) -> Unit) {
    val placeById = remember(placements) { placements.associateBy { it.id } }
    val siteHits = model.ui.siteHits
    // Nothing here: the same words are looked for in the other sites of the company.
    LaunchedEffect(query, results.isEmpty) {
        if (results.isEmpty) { kotlinx.coroutines.delay(350); model.searchOtherSites(query) }
    }
    // The hits of the other sites belong to this search: they go with it, so Articoli never shows them for another one.
    DisposableEffect(Unit) { onDispose { model.searchOtherSites("") } }
    Surface(shape = RoundedCornerShape(20.dp), color = PanelBg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            if (results.isEmpty) {
                Text("Nessun articolo o scaffale con «${query.trim()}» in questa sede.", color = TextSecondary, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                siteHits.take(8).forEach { hit ->
                    ResultRow(dot = Sky, outlined = false, title = hit.item.name, detail = "Nella sede ${hit.site.name}",
                        quantity = "${amount(hit.item.quantity)} ${hit.item.unit}") { model.openInSite(hit) }
                }
            }
            results.items.take(40).forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline.copy(alpha = .08f))
                val place = item.placementId?.let { placeById[it] }
                ResultRow(dot = Amber, outlined = place == null, title = item.name,
                    detail = if (place == null) "Senza posto · tocca per vedere dove metterlo" else MapRoute.placeText(place, item),
                    detailColor = if (place == null) TextSecondary else Sky,
                    quantity = "${amount(item.quantity)} ${item.unit}") { onItem(item) }
            }
            if (results.items.size > 40) Text("e altri ${results.items.size - 40}: scrivi qualche lettera in più", color = TextSecondary,
                fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            results.structures.take(10).forEach { shelf ->
                if (results.items.isNotEmpty() || shelf != results.structures.first()) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline.copy(alpha = .08f))
                val inside = model.ui.items.count { it.placementId == shelf.id }
                ResultRow(dot = Mint, outlined = false, title = shelf.label.ifBlank { shelf.kind },
                    detail = "${shelf.kind} · $inside ${if (inside == 1) "articolo" else "articoli"} · tocca per aprirlo",
                    quantity = "") { onShelf(shelf) }
            }
        }
    }
}

@Composable private fun ResultRow(dot: Color, outlined: Boolean, title: String, detail: String, detailColor: Color = TextSecondary,
                                  quantity: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).then(if (outlined) Modifier.border(BorderStroke(2.dp, TextSecondary), RoundedCornerShape(6.dp))
                else Modifier.background(dot, RoundedCornerShape(6.dp))))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 13.sp, fontWeight = if (detailColor == Sky) FontWeight.Bold else FontWeight.Normal, color = detailColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (quantity.isNotEmpty()) Text(quantity, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** 4.20 "Dov'è": the article, its exact place with the shelf seen from the front, and what to do. */
@Composable fun FoundPanel(item: StockItem, place: Placement?, placements: List<Placement>, onPick: () -> Unit, onOpen: () -> Unit,
                           onShelf: () -> Unit, onPlace: () -> Unit) {
    Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (item.photoFile != null) ArticleThumbnail(item.photoFile)
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.sku} · ${item.category}", fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amount(item.quantity), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (item.lowStock) Amber else TextPrimary)
                    Text(item.unit, fontSize = 12.sp, color = TextSecondary)
                }
            }
            if (place == null) {
                Text("Questo articolo non ha ancora un posto. Sceglilo sulla mappa, oppure leggi l'etichetta (o il tag) dello scaffale dove lo metti.",
                    fontSize = 14.sp, color = TextSecondary)
                Button(onClick = onPlace, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Place, null); Spacer(Modifier.width(8.dp)); Text("Scegli il posto", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(MapRoute.placeText(place, item),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Sky, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (place.hasPositions) Surface(onClick = onShelf, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ShelfMiniGrid(place, item, Modifier.fillMaxWidth())
                        Text("Lo scaffale visto di fronte · tocca per aprirlo", fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPick, enabled = item.quantity > 0.0, modifier = Modifier.weight(2f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp)) { Text("Preleva", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) { Text("Scheda") }
                }
            }
        }
    }
}

/** 4.20: the articles still without a place, from the button on the plan: one touch and the map asks where. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UnplacedSheet(items: List<StockItem>, onDismiss: () -> Unit, onChoose: (StockItem) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("Articoli senza posto", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Toccane uno, poi tocca lo scaffale sulla mappa o leggi la sua etichetta.", fontSize = 13.sp, color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(items.sortedBy { it.name.lowercase() }, key = { it.id }) { item ->
                    ResultRow(dot = Amber, outlined = true, title = item.name, detail = "${item.sku} · ${item.category}",
                        quantity = "${amount(item.quantity)} ${item.unit}") { onChoose(item) }
                }
            }
        }
    }
}
