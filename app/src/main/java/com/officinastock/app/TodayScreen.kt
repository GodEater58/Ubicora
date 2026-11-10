package com.officinastock.app

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.15: what the first screen asks the operator to do (plain logic, no Android). */
object TodayTasks {
    const val LOAN_DAYS = 7
    /** Open loans older than [days]: tools that are probably forgotten somewhere. */
    fun overdueLoans(loans: List<Loan>, now: Long, days: Int = LOAN_DAYS): Int =
        loans.count { it.returnedAt == null && now - it.createdAt > days * 86_400_000L }
    /** "B · 2 ore fa": the phone that made the movement, when known. */
    fun who(actor: String?, time: String): String = if (actor.isNullOrBlank()) time else "$actor · $time"
    val LOAN_DAY_CHOICES = listOf(3, 7, 14, 30)
    fun signed(delta: Double): String = (if (delta > 0) "+" else "−") + amount(kotlin.math.abs(delta))
}

/**
 * 4.15 first screen ("Oggi"): no longer a wall of counters but the list of what needs attention
 * now, the state of the team in one line and the last movements. Settings open from the gear.
 */
@Composable fun DashboardScreen(
    model: StockViewModel, goStock: () -> Unit, goMap: () -> Unit, goMoves: () -> Unit, goManager: () -> Unit,
    goSettings: () -> Unit, goTeam: () -> Unit,
    onStockFocus: (StockFocus) -> Unit, onActivityFocus: (ActivityFocus, Int) -> Unit, onItemFocus: (Long) -> Unit
) {
    val u = model.ui
    val count = u.items.size
    val critical = u.items.count { it.lowStock }
    val unlocated = u.items.count { it.placementId == null }
    val loanDays = UiPrefs.loanDays
    val overdue = remember(u.loans, loanDays) { TodayTasks.overdueLoans(u.loans, System.currentTimeMillis(), loanDays) }
    var askLoanDays by remember { mutableStateOf(false) }
    // Business mode: the indicators stay one tap away, closed by default, so the screen is the same for everyone.
    var showIndicators by rememberSaveable { mutableStateOf(false) }
    val team = u.team.takeIf { it.role != TeamRole.NONE && it.warehouseId == u.warehouse?.id }
    val conflicts = team?.conflicts?.size ?: 0
    val managerPending = if (!u.businessMode) 0 else u.orders.count { it.status in listOf("DRAFT", "APPROVED", "ORDERED") } +
        u.stockCounts.count { it.status == "PENDING" } + u.stockTransfers.count { it.status in listOf("REQUESTED", "IN_TRANSIT") } +
        u.maintenanceWork.count { it.status == "OPEN" }
    val recent = u.movements.take(4)
    if (askLoanDays) LoanDaysDialog(loanDays) { askLoanDays = false }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.warehouse?.name ?: "Magazzino", fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${u.warehouse?.location?.ifBlank { "Area di lavoro" }}  ·  ${u.profile?.name}", color = TextSecondary, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SettingsGear(goSettings)
            }
        }
        if (team != null) item { TeamStatusRow(team, goTeam) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TodayNumber(count.toString(), "Referenze", TextPrimary, Modifier.weight(1f)) { onStockFocus(StockFocus.ALL) }
                TodayNumber(critical.toString(), "Da riordinare", Amber, Modifier.weight(1f)) { onStockFocus(StockFocus.CRITICAL) }
                TodayNumber(u.loans.size.toString(), "In prestito", Sky, Modifier.weight(1f)) { onActivityFocus(ActivityFocus.LOANS, 0) }
            }
        }
        item { TodayLabel("DA FARE ADESSO") }
        if (critical > 0) item {
            TodayTask(critical, Amber, if (critical == 1) "Articolo sotto scorta" else "Articoli sotto scorta", "Apri la lista da ordinare") { onStockFocus(StockFocus.CRITICAL) }
        }
        if (overdue > 0) item {
            TodayTask(overdue, Sky, if (overdue == 1) "Utensile fuori da più di $loanDays giorni" else "Utensili fuori da più di $loanDays giorni",
                "Vedi chi li ha · tieni premuto per cambiare i giorni", onLong = { askLoanDays = true }) { onActivityFocus(ActivityFocus.LOANS, 0) }
        }
        if (unlocated > 0) item {
            TodayTask(unlocated, TextPrimary, if (unlocated == 1) "Articolo senza ubicazione" else "Articoli senza ubicazione", "Assegna sulla mappa") { onStockFocus(StockFocus.UNLOCATED) }
        }
        if (conflicts > 0) item {
            TodayTask(conflicts, Danger, if (conflicts == 1) "Conflitto dalla squadra" else "Conflitti dalla squadra", "Rivedi cosa non è stato applicato", onClick = goTeam)
        }
        if (managerPending > 0) item {
            TodayTask(managerPending, Mint, if (managerPending == 1) "Attività di gestione aperta" else "Attività di gestione aperte",
                "Acquisti, conteggi, trasferimenti, manutenzioni", onClick = goManager)
        }
        if (critical == 0 && overdue == 0 && unlocated == 0 && conflicts == 0 && managerPending == 0) item {
            Surface(color = PanelBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (count == 0) "Aggiungi il primo articolo dalla sezione Articoli." else "Niente da fare adesso: tutto sotto controllo.",
                    color = TextSecondary, modifier = Modifier.padding(16.dp))
            }
        }
        if (critical > 0) item { ReorderShareButton(model, Modifier.fillMaxWidth()) }
        // 4.15: the management indicators live in the Gestione section; here the screen is the same for everyone.
        if (u.businessMode) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = goManager) { Text("Gestione aziendale") }
                TextButton(onClick = { showIndicators = !showIndicators }) { Text(if (showIndicators) "Nascondi indicatori" else "Mostra indicatori") }
            }
        }
        if (u.businessMode && showIndicators) item {
            ManagerAnalyticsPanel(u.items, u.manager, onStockFocus = onStockFocus, onActivityFocus = onActivityFocus, onItemFocus = onItemFocus)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { TodayLabel("ULTIMI MOVIMENTI") }
                TextButton(onClick = goMoves) { Text("Tutti") }
            }
        }
        if (recent.isEmpty()) item { Text("Non ci sono ancora movimenti.", color = TextSecondary, fontSize = 14.sp) }
        items(recent, key = { it.id }) { move ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onItemFocus(move.itemId) }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(TodayTasks.signed(move.delta), color = if (move.delta >= 0) Mint else Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.widthIn(min = 52.dp))
                Text(move.itemName, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(TodayTasks.who(move.actor, ago(move.createdAt)), color = TextSecondary, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable private fun TodayLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable private fun TodayNumber(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = PanelBg) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun TodayTask(number: Int, color: Color, title: String, subtitle: String, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onClick, onLongClick = onLong),
        shape = RoundedCornerShape(16.dp), color = PanelBg) {
        Row(Modifier.heightIn(min = 68.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text(if (number > 99) "99+" else number.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

/** 4.15: after how many days a tool on loan is worth a reminder. */
@Composable private fun LoanDaysDialog(current: Int, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Utensili in prestito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avvisami quando un utensile è fuori da più di:", color = TextSecondary, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodayTasks.LOAN_DAY_CHOICES.forEach { days ->
                        FilterChip(selected = days == current, onClick = { UiPrefs.updateLoanDays(days); onClose() }, label = { Text("$days gg") })
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Chiudi") } })
}

/** State of the team in one line; the whole line opens the Squadra section. */
@Composable private fun TeamStatusRow(team: TeamSyncStatus, onClick: () -> Unit) {
    val central = team.role == TeamRole.CENTRAL
    val warn = team.conflicts.isNotEmpty() || (!central && team.pending > 0)
    val text = when {
        team.conflicts.isNotEmpty() -> "${team.conflicts.size} ${if (team.conflicts.size == 1) "conflitto" else "conflitti"} da rivedere"
        central -> "Telefono centrale · ${team.peers.size} ${if (team.peers.size == 1) "satellite" else "satelliti"}"
        team.pending > 0 -> "Satellite ${team.letter} · ${team.pending} ${if (team.pending == 1) "modifica" else "modifiche"} da inviare"
        else -> "Satellite ${team.letter} · tutto inviato"
    }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = PanelBg) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (warn) Amber else Mint))
            Text(text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ago(team.lastSync), color = TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/** 4.15: the same gear, top right, in every section of the bottom bar. */
@Composable fun SettingsGear(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Settings, "Impostazioni") }
}

/**
 * 4.15: Articoli holds two pages, the stock and its history ("Giacenze" / "Movimenti"): who looks
 * for what happened to a piece starts from the list of the pieces.
 */
@Composable fun StockSections(movements: Boolean, onSelect: (movements: Boolean) -> Unit, goSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "Giacenze", true to "Movimenti").forEach { (target, label) ->
            val active = target == movements
            Surface(onClick = { onSelect(target) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                color = if (active) Mint.copy(alpha = .16f) else Surface2) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) TextPrimary else TextSecondary, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        SettingsGear(goSettings)
    }
}

/** 4.15: the team has its own section of the bottom bar (it used to be inside Settings only). */
@Composable fun TeamScreen(model: StockViewModel, goSettings: () -> Unit) {
    val u = model.ui
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionTitle("Squadra", "Sincronizzazione tra i telefoni, senza internet") }
                SettingsGear(goSettings)
            }
        }
        if (u.team.role != TeamRole.NONE && u.team.warehouseId == u.warehouse?.id) item { SyncCard(model) }
        item { TeamSyncPanel(model) }
    }
}
