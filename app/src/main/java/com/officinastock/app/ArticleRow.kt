package com.officinastock.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 4.15 article row: swipe right withdraws one, swipe left loads one, a long press opens the quick
 * actions. The name takes two lines, the location stands out and the stock state is written only
 * when it needs attention. [compact] drops photo and code to show more articles per screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun ArticleRow(entry: StockItem, place: Placement?, model: StockViewModel, compact: Boolean,
    onOpen: () -> Unit, onMenu: () -> Unit, onQuantity: (load: Boolean) -> Unit) {
    val busy = model.ui.busy
    val current by rememberUpdatedState(entry)
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.StartToEnd -> if (current.quantity >= 1.0) model.quickMovement(current.id, -1.0)
            SwipeToDismissBoxValue.EndToStart -> model.quickMovement(current.id, 1.0)
            SwipeToDismissBoxValue.Settled -> Unit
        }
        false   // the row always comes back: the swipe is a command, not a removal
    }, positionalThreshold = { width -> width * .35f })   // a clear gesture, not a brush of the thumb while scrolling
    val shape = RoundedCornerShape(if (compact) 16.dp else 22.dp)
    SwipeToDismissBox(state = swipe, modifier = Modifier.fillMaxWidth().clip(shape),
        enableDismissFromStartToEnd = !busy && entry.quantity >= 1.0, enableDismissFromEndToStart = !busy,
        backgroundContent = {
            val pick = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val idle = swipe.dismissDirection == SwipeToDismissBoxValue.Settled
            Row(Modifier.fillMaxSize().background(if (idle) Color.Transparent else if (pick) Mint else Sky).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (pick) Arrangement.Start else Arrangement.End) {
                if (!idle) {
                    Icon(if (pick) Icons.Outlined.Remove else Icons.Outlined.Add, contentDescription = null, tint = Color(0xFF07121F))
                    Spacer(Modifier.width(8.dp))
                    Text(if (pick) "Preleva 1" else "Carica 1", color = Color(0xFF07121F), fontWeight = FontWeight.Bold)
                }
            }
        }) {
        Surface(shape = shape, color = PanelBg, border = BorderStroke(1.dp, Hairline.copy(alpha = .06f)),
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onMenu, onLongClickLabel = "Azioni rapide")) {
            Row(Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!compact) {
                    if (entry.photoFile != null) ArticleThumbnail(entry.photoFile)
                    else Surface(color = if (entry.lowStock) Amber.copy(alpha = .13f) else Mint.copy(alpha = .12f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.Inventory2, null, tint = if (entry.lowStock) Amber else Mint, modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (UiPrefs.isFavorite(entry.warehouseId, entry.id)) {
                            Icon(Icons.Outlined.Star, contentDescription = "Preferito", tint = Amber, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(entry.name, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                            lineHeight = 19.sp)
                    }
                    // 4.21.1: the description right under the name (it is often what was searched for).
                    if (entry.note.isNotBlank()) Text(entry.note, fontSize = 12.sp, color = TextPrimary.copy(alpha = .82f),
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                    // Where to find it comes first: it is what the operator looks for.
                    Text(Locations.describe(place, entry), color = if (place == null) Amber else Sky, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text("${entry.sku}  ·  ${entry.category}", color = TextSecondary, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${amount(entry.quantity)} ${entry.unit}", color = if (entry.lowStock) Amber else TextPrimary, fontWeight = FontWeight.Bold,
                        fontSize = 17.sp)
                    if (entry.lowStock) Text(if (entry.quantity <= 0.0) "Esaurito" else "Scorta bassa · min ${amount(entry.minimum)}",
                        color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (!compact) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        StepButton(Icons.Outlined.Remove, "Preleva 1 ${entry.unit}. Tieni premuto per scegliere la quantità",
                            enabled = !busy && entry.quantity >= 1.0, onClick = { model.quickMovement(entry.id, -1.0) }, onLong = { onQuantity(false) })
                        StepButton(Icons.Outlined.Add, "Carica 1 ${entry.unit}. Tieni premuto per scegliere la quantità",
                            enabled = !busy, onClick = { model.quickMovement(entry.id, 1.0) }, onLong = { onQuantity(true) })
                    }
                }
            }
        }
    }
}

/** Round ±1 button: a tap moves one piece, a long press asks how many. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Surface2.copy(alpha = if (enabled) 1f else .45f))
        .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = if (enabled) TextPrimary else TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** 4.15: quick actions of an article in a panel from the bottom, reachable with one hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ArticleActionsSheet(item: StockItem, place: Placement?, model: StockViewModel, onDismiss: () -> Unit,
    onOpen: () -> Unit, onLabel: () -> Unit, onTransfer: () -> Unit, onQuantity: (load: Boolean) -> Unit) {
    val favorite = UiPrefs.isFavorite(item.warehouseId, item.id)
    val context = LocalContext.current
    val hasNfc = remember { runCatching { android.nfc.NfcAdapter.getDefaultAdapter(context) != null }.getOrDefault(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${amount(item.quantity)} ${item.unit} · ${Locations.describe(place, item)}", color = TextSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            SheetAction(Icons.Outlined.Remove, "Preleva una quantità…", enabled = item.quantity > 0.0) { onDismiss(); onQuantity(false) }
            SheetAction(Icons.Outlined.Add, "Carica una quantità…") { onDismiss(); onQuantity(true) }
            if (place != null) SheetAction(Icons.Outlined.Map, "Dov'è sulla mappa") { onDismiss(); model.showItemOnMap(item.id) }
            SheetAction(Icons.Outlined.Place, if (place == null) "Assegna un'ubicazione sulla mappa" else "Sposta scegliendo sulla mappa") {
                onDismiss(); model.startLocationPick(listOf(item.id))
            }
            if (model.ui.warehouses.size > 1) SheetAction(Icons.Outlined.SwapHoriz, "Sposta in un'altra sede…", enabled = item.quantity > 0.0) { onDismiss(); onTransfer() }
            SheetAction(Icons.Outlined.PlaylistAdd, "Aggiungi alla lista di prelievo") { model.addToPickList(item.id); onDismiss() }
            SheetAction(Icons.Outlined.QrCode2, "Etichetta QR") { onDismiss(); onLabel() }
            if (hasNfc) SheetAction(Icons.Outlined.Nfc, "Scrivi su tag NFC") { onDismiss(); model.requestNfcWrite(item.id) }
            SheetAction(if (favorite) Icons.Outlined.StarOutline else Icons.Outlined.Star,
                if (favorite) "Togli dai preferiti" else "Aggiungi ai preferiti") { UiPrefs.toggleFavorite(item.warehouseId, item.id); onDismiss() }
            SheetAction(Icons.Outlined.Inventory2, "Apri la scheda articolo") { onDismiss(); onOpen() }
        }
    }
}

@Composable private fun SheetAction(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (enabled) Mint else TextSecondary)
        Spacer(Modifier.width(16.dp))
        Text(text, fontSize = 16.sp, color = if (enabled) TextPrimary else TextSecondary)
    }
}

/** 4.15: "how many?" after a long press on − or +. A withdrawal never exceeds what is on the shelf. */
@Composable fun QuickQuantityDialog(item: StockItem, load: Boolean, model: StockViewModel, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val quantity = QuickQuantity.parse(text)
    val valid = QuickQuantity.valid(quantity, load, item.quantity)
    AlertDialog(onDismissRequest = onClose,
        title = { Text(if (load) "Carica · ${item.name}" else "Preleva · ${item.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A scaffale: ${amount(item.quantity)} ${item.unit}", color = TextSecondary, fontSize = 13.sp)
                Input(text, { text = it }, "Quantità (${item.unit})", number = true)
                if (quantity != null && !load && quantity > item.quantity) Text("Più di quanto c'è a scaffale.", color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(enabled = valid && !model.ui.busy, onClick = {
                model.movement(item.id, if (load) quantity!! else -quantity!!, if (load) "Carico rapido" else "Prelievo rapido"); onClose()
            }) { Text(if (load) "Carica" else "Preleva") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } })
}

/** 4.19: how many pieces go to which other site of the company. */
@Composable fun TransferDialog(item: StockItem, model: StockViewModel, onClose: () -> Unit) {
    val others = model.ui.warehouses.filter { it.id != item.warehouseId }
    var site by remember { mutableStateOf(others.singleOrNull()) }
    var text by remember { mutableStateOf("") }
    val quantity = QuickQuantity.parse(text)
    val target = site
    AlertDialog(onDismissRequest = onClose,
        title = { Text("Sposta · ${item.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Qui: ${amount(item.quantity)} ${item.unit}. Esce da questa sede ed entra nell'altra, nello stesso momento.", color = TextSecondary, fontSize = 13.sp)
                others.forEach { other ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { site = other }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = site?.id == other.id, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(other.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Input(text, { text = it }, "Quantità (${item.unit})", number = true)
                if (quantity != null && quantity > item.quantity) Text("Più di quanto c'è a scaffale.", color = Danger, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(enabled = target != null && QuickQuantity.valid(quantity, false, item.quantity) && !model.ui.busy,
                onClick = { model.transferToSite(item.id, target!!, quantity!!); onClose() }) { Text("Sposta") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } })
}

/** Quantity typed by hand: comma or dot, positive, and within the shelf when withdrawing. */
object QuickQuantity {
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    fun valid(quantity: Double?, load: Boolean, available: Double): Boolean = quantity != null && (load || quantity <= available)
}
