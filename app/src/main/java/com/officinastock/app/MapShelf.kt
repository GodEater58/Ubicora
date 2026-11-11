package com.officinastock.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * 4.20: a structure seen from the front, shelf by shelf, opened by tapping it on the map or by reading its
 * label or NFC tag. Tapping an article gives the usual actions; tapping a free place puts an article there.
 * While an article is waiting for a place (Sposta / Assegna), every place says "Metti qui".
 */
@Composable fun ShelfFrontScreen(model: StockViewModel) {
    val u = model.ui
    val p = u.placements.firstOrNull { it.id == u.shelfOpenId && it.holdsStock }
    if (p == null) { LaunchedEffect(u.shelfOpenId) { model.closeShelf() }; return }
    BackHandler { model.closeShelf() }
    val context = LocalContext.current
    val hasNfc = remember { runCatching { android.nfc.NfcAdapter.getDefaultAdapter(context) != null }.getOrDefault(false) }
    val view = remember(p, u.items) { ShelfLayout.of(p, u.items) }
    val walk = remember(p, u.placements) { MapRoute.walk(u.placements, p) }
    val placing = u.locationPickItemIds
    val placingNames = placing?.mapNotNull { id -> u.items.firstOrNull { it.id == id }?.name }.orEmpty()
    var actionsFor by remember(p.id) { mutableStateOf<Long?>(null) }
    var quantityFor by remember(p.id) { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var openItem by remember(p.id) { mutableStateOf<Long?>(null) }
    var labelItem by remember(p.id) { mutableStateOf<Long?>(null) }
    var transferFor by remember(p.id) { mutableStateOf<Long?>(null) }
    var freeCell by remember(p.id) { mutableStateOf<ShelfCell?>(null) }
    var showQr by remember(p.id) { mutableStateOf(false) }
    var editing by remember(p.id) { mutableStateOf(false) }
    val canPlace = !model.pickOnly

    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = model::closeShelf, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.ArrowBack, "Torna indietro") }
                Column(Modifier.weight(1f)) {
                    Text(p.label.ifBlank { p.kind }, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.kind, if (p.levels > 1) "${p.levels} ripiani" else null,
                        "${view.count} ${if (view.count == 1) "articolo" else "articoli"}").joinToString(" · "),
                        color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (model.mapEditable) FilledTonalIconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Edit, "Modifica la struttura")
                }
            }
            if (placing != null) Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(14.dp),
                color = Amber.copy(alpha = .16f), border = BorderStroke(1.dp, Amber.copy(alpha = .6f))) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (placingNames.size == 1) "Tocca il posto dove mettere ${placingNames.single()}" else "Tocca il posto per ${placing.size} articoli",
                        fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.cancelLocationPick() }) { Text("Annulla") }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiniPlan(u.warehouse?.mapWidth ?: 1f, u.warehouse?.mapHeight ?: 1f, u.placements, p.id, Modifier.size(width = 64.dp, height = 64.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.warehouse?.name.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(walk?.let { MapRoute.describe(it) } ?: "Sulla pianta della sede", color = TextSecondary, fontSize = 13.sp)
                        }
                        if (placing == null) TextButton(onClick = { model.showOnMap(p.id) }) { Text("Mappa") }
                    }
                }
                view.rows.forEach { row ->
                    ShelfRowView(row, p, highlightItemId = null, placing = placing != null, canPlace = canPlace,
                        onItem = { actionsFor = it.id },
                        onFree = { cell -> freeCell = cell },
                        onPlace = { cell -> model.completeLocationPick(p.id, cell.level, cell.slot) })
                }
                if (view.loose.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RIPIANO NON INDICATO", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    view.loose.forEach { item -> ShelfItemButton(item, false, Modifier.fillMaxWidth()) { actionsFor = item.id } }
                    Text("Sono in questa struttura ma senza ripiano: toccane uno per spostarlo nel posto giusto.", color = TextSecondary, fontSize = 12.sp)
                }
                Text(when {
                    placing != null -> "Il posto scelto vale per ${if (placing.size == 1) "l'articolo" else "tutti gli articoli"} in attesa."
                    canPlace -> "Tocca un articolo per prelevare o caricare. Tocca un posto libero per metterci un articolo."
                    else -> "Tocca un articolo per prelevarlo."
                }, color = TextSecondary, fontSize = 13.sp)
            }
            if (placing == null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { model.startShelfCheck(p.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Verifica", maxLines = 1) }
                OutlinedButton(onClick = { showQr = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Etichetta", maxLines = 1) }
                if (hasNfc) OutlinedButton(onClick = { model.requestNfcWritePlacement(p.id) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text("Tag NFC", maxLines = 1)
                }
            }
        }
    }
    actionsFor?.let { id ->
        val item = u.items.firstOrNull { it.id == id }
        if (item == null) actionsFor = null else ArticleActionsSheet(item, p.takeIf { item.placementId == it.id }, model,
            onDismiss = { actionsFor = null }, onOpen = { openItem = item.id }, onLabel = { labelItem = item.id },
            onTransfer = { transferFor = item.id }, onQuantity = { load -> quantityFor = item.id to load })
    }
    quantityFor?.let { (id, load) ->
        val item = u.items.firstOrNull { it.id == id }
        if (item == null) quantityFor = null else QuickQuantityDialog(item, load, model, onClose = { quantityFor = null })
    }
    openItem?.let { id ->
        val item = u.items.firstOrNull { it.id == id }
        if (item == null) openItem = null else ItemDialog(item, u.placements, model, onDismiss = { openItem = null })
    }
    labelItem?.let { id -> u.items.firstOrNull { it.id == id }?.let { QrLabelDialog(it, onDismiss = { labelItem = null }) } ?: run { labelItem = null } }
    transferFor?.let { id -> u.items.firstOrNull { it.id == id }?.let { TransferDialog(it, model, onClose = { transferFor = null }) } ?: run { transferFor = null } }
    if (showQr) PlacementQrDialog(p, u.items, onDismiss = { showQr = false })
    if (editing) PlacementDetailDialog(p, u.items, model, onQr = { showQr = true }, onDismiss = { editing = false }, startEditing = true)
    freeCell?.let { cell ->
        PutHereDialog(p, cell, u.items, onClose = { freeCell = null }) { itemId ->
            model.assignItem(itemId, p.id, cell.level, cell.slot); freeCell = null
        }
    }
}

/** One shelf of the front view: its title, its places side by side, then what has no place on it. */
@Composable fun ShelfRowView(row: ShelfRow, p: Placement, highlightItemId: Long?, placing: Boolean, canPlace: Boolean,
                             onItem: (StockItem) -> Unit, onFree: (ShelfCell) -> Unit, onPlace: (ShelfCell) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ShelfLayout.rowTitle(row, p.levels).uppercase(Locale.ITALY), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        val wide = row.cells.size > ShelfLayout.WIDE
        Row(Modifier.fillMaxWidth().then(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.cells.forEach { cell ->
                val cellModifier = if (wide) Modifier.width(118.dp) else Modifier.weight(1f)
                Column(cellModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.slots > 1) Text("Posizione ${cell.slot}", color = TextSecondary, fontSize = 11.sp)
                    when {
                        placing -> Button(onClick = { onPlace(cell) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            shape = RoundedCornerShape(12.dp)) { Text("Metti qui", fontWeight = FontWeight.Bold) }
                        cell.items.isEmpty() -> FreePlace(canPlace, Modifier.fillMaxWidth()) { onFree(cell) }
                        else -> cell.items.forEach { item -> ShelfItemButton(item, item.id == highlightItemId, Modifier.fillMaxWidth()) { onItem(item) } }
                    }
                }
            }
        }
        // The edge of the shelf, as on the drawing of the mockup.
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0xFF4A6074), RoundedCornerShape(2.dp)))
        if (row.withoutSlot.isNotEmpty()) {
            Text("Su questo ripiano, posizione non indicata", color = TextSecondary, fontSize = 12.sp)
            row.withoutSlot.forEach { item -> ShelfItemButton(item, item.id == highlightItemId, Modifier.fillMaxWidth()) { onItem(item) } }
        }
    }
}

@Composable private fun ShelfItemButton(item: StockItem, highlighted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp),
        color = if (highlighted) Amber.copy(alpha = .22f) else PanelBg,
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, if (highlighted) Amber else Hairline.copy(alpha = .12f))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(amount(item.quantity), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (item.lowStock) Amber else TextPrimary)
                Spacer(Modifier.width(4.dp))
                Text(item.unit, fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable private fun FreePlace(canPlace: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.heightIn(min = 64.dp).clip(shape)
        .then(if (canPlace) Modifier.clickable(role = Role.Button, onClickLabel = "Metti un articolo in questo posto", onClick = onClick) else Modifier)
        .border(BorderStroke(1.dp, Color(0xFF4A6074)), shape), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canPlace) { Icon(Icons.Outlined.Add, null, tint = TextSecondary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
            Text("Libero", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Choose an article for a free place: the ones without a place first, then all the others of the site. */
@Composable private fun PutHereDialog(p: Placement, cell: ShelfCell, items: List<StockItem>, onClose: () -> Unit, onChoose: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    val where = listOfNotNull(p.label, cell.level?.let { "ripiano $it" }, cell.slot?.let { "posizione $it" }).joinToString(" · ")
    val candidates = remember(items, query, p.id, cell) {
        items.filter { !(it.placementId == p.id && it.shelfLevel == cell.level && it.shelfSlot == cell.slot) }
            .filter { query.isBlank() || MapSearch.matches(it, query) }
            .sortedWith(compareBy<StockItem>({ it.placementId != null }, { it.name.lowercase(Locale.ITALY) }))
    }
    AlertDialog(onDismissRequest = onClose, title = { Text("Cosa metti qui?") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(where, color = TextSecondary, fontSize = 13.sp)
            if (items.size > 6) Input(query, { query = it }, "Cerca articolo o codice")
            candidates.take(60).forEach { item ->
                Surface(onClick = { onChoose(item.id) }, color = Color.Transparent, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(item.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (item.placementId == null) "Senza posto · ${amount(item.quantity)} ${item.unit}" else "Ora altrove: verrà spostato qui",
                            fontSize = 12.sp, color = if (item.placementId == null) Amber else TextSecondary)
                    }
                }
            }
            if (candidates.isEmpty()) Text("Nessun articolo trovato.", color = TextSecondary, fontSize = 13.sp)
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Annulla") } })
}

/** The whole room in a small box, with one structure lit: where the shelf is in the site. */
@Composable fun MiniPlan(roomWidth: Float, roomHeight: Float, placements: List<Placement>, litId: Long?, modifier: Modifier) {
    val desc = placements.firstOrNull { it.id == litId }?.let { "Posizione di ${it.label} nella pianta" } ?: "Pianta della sede"
    Canvas(modifier.semantics { contentDescription = desc }) {
        val margin = 4f
        val turned = PlanViewport.shouldTurn(size.width, size.height, roomWidth, roomHeight, margin)
        val cam = PlanViewport(size.width, size.height, roomWidth, roomHeight, marginPx = margin, turned = turned)
        val tl = cam.toScreen(PointMetres(0f, 0f)).let { a -> cam.toScreen(PointMetres(roomWidth, roomHeight)).let { b ->
            Offset(minOf(a.x, b.x), minOf(a.y, b.y)) } }
        drawRoundRect(Color(0xFF0C1A28), topLeft = tl, size = Size(cam.shownWidth * cam.scale, cam.shownHeight * cam.scale), cornerRadius = CornerRadius(3f))
        drawRoundRect(Color(0xFF4A6074), topLeft = tl, size = Size(cam.shownWidth * cam.scale, cam.shownHeight * cam.scale),
            cornerRadius = CornerRadius(3f), style = Stroke(width = 2f))
        placements.filter { it.kind != "Corridoio" }.forEach { f ->
            val c = cam.toScreen(PointMetres(f.x, f.y)); val w = (f.width * cam.scale).coerceAtLeast(3f); val h = (f.depth * cam.scale).coerceAtLeast(3f)
            rotate(f.rotation + cam.turnDegrees, pivot = Offset(c.x, c.y)) {
                drawRoundRect(if (f.id == litId) Amber else Color(0xFF2B3A47), topLeft = Offset(c.x - w / 2, c.y - h / 2), size = Size(w, h), cornerRadius = CornerRadius(1.5f))
            }
        }
    }
}

/** The places of a shelf as small boxes, with the one of [item] lit: used under "Dov'è" on the map. */
@Composable fun ShelfMiniGrid(p: Placement, item: StockItem, modifier: Modifier = Modifier) {
    val view = remember(p, item) { ShelfLayout.of(p, listOf(item)) }
    val label = Locations.describe(p, item)
    Column(modifier.semantics { contentDescription = "$label, vista di fronte" }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        view.rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val here = row.cells.any { c -> c.items.isNotEmpty() } || row.withoutSlot.isNotEmpty()
                Text(row.level?.let { "Ripiano $it" } ?: "", fontSize = 12.sp, color = if (here) TextPrimary else TextSecondary,
                    fontWeight = if (here) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(72.dp))
                row.cells.take(8).forEach { cell ->
                    val lit = cell.items.isNotEmpty()
                    Box(Modifier.weight(1f).height(26.dp).background(if (lit) Amber.copy(alpha = .28f) else Surface2, RoundedCornerShape(6.dp))
                        .then(if (lit) Modifier.border(BorderStroke(2.dp, Amber), RoundedCornerShape(6.dp)) else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (lit) Text("È qui", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber)
                    }
                }
                if (row.cells.size > 8) Text("+${row.cells.size - 8}", fontSize = 11.sp, color = TextSecondary)
            }
        }
        if (view.loose.isNotEmpty()) Text("Ripiano non indicato", fontSize = 12.sp, color = Amber)
    }
}
