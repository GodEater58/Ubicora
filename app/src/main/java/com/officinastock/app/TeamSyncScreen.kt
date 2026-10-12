package com.officinastock.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 4.8 Settings panel: central phone and satellites exchanging encrypted files, no server. */
@Composable fun TeamSyncPanel(model: StockViewModel) {
    val u = model.ui
    val t = u.team
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var askCentral by remember { mutableStateOf(false) }
    var askJoin by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    var showConflicts by remember { mutableStateOf(false) }
    var editScope by remember { mutableStateOf<String?>(null) }
    var scopeChoice by remember { mutableStateOf(setOf<String>()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf("B") }
    fun run(block: suspend () -> String) = scope.launch {
        result = try { block() } catch (e: Exception) { e.message ?: "Operazione non riuscita" }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) run { model.teamExport(uri) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) run { model.teamImport(uri) }
    }
    val joiner = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { val p = password; val l = letter; run { model.teamJoin(uri, p, l) } }
        password = ""
    }
    val nearby = rememberNearbyAccess { result = it }
    val day = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ITALY).format(Date())
    val synced = t.warehouseId != null && t.warehouseId == u.warehouse?.id

    Panel(Modifier.fillMaxWidth()) {
        Text("SINCRONIZZAZIONE TRA TELEFONI", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
        when (t.role) {
            TeamRole.NONE -> {
                Text("Più persone, nessun server", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Un telefono centrale tiene l'archivio; gli altri (satelliti) lavorano offline e si scambiano file cifrati " +
                    "con il centrale, via WhatsApp, email o USB. Le giacenze si uniscono sommando i movimenti.",
                    fontSize = 12.sp, color = TextSecondary)
                PrimaryAction("Rendi questo il telefono centrale", onClick = { password = ""; confirm = ""; askCentral = true },
                    enabled = !u.busy && u.warehouse != null, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { password = ""; letter = "B"; askJoin = true }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Collega a un telefono centrale")
                }
            }
            TeamRole.CENTRAL -> {
                Text("Telefono centrale", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (synced) "Magazzino sincronizzato: ${u.warehouse?.name}" else "Il magazzino sincronizzato è un altro: aprilo per gestire gli scambi.",
                    fontSize = 12.sp, color = if (synced) TextSecondary else Amber)
                if (synced) {
                    PrimaryAction("Apri la rete per i satelliti vicini", onClick = { nearby { model.startCentralLink() } },
                        enabled = !u.busy, modifier = Modifier.fillMaxWidth())
                    Text("Oppure con i file (WhatsApp, email…):", fontSize = 11.sp, color = TextSecondary)
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Importa dai satelliti")
                    }
                    OutlinedButton(onClick = { exporter.launch("Ubicora_aggiornamento_$day.ubisync") }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Invia aggiornamento a tutti")
                    }
                }
                if (synced) {
                    HorizontalDivider()
                    Text("REPARTI DEGLI OPERATORI", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Fuori dal suo reparto un operatore vede gli articoli e può solo prelevare: non crea, non carica, non sposta.",
                        fontSize = 11.sp, color = TextSecondary)
                    listOf("B", "C", "D", "E", "F").forEach { l ->
                        val cats = t.scopes[l].orEmpty()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(l, fontWeight = FontWeight.Bold, color = Mint, modifier = Modifier.width(24.dp))
                            Text(if (cats.isEmpty()) "Tutti i reparti" else cats.joinToString(", "), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scopeChoice = cats.toSet(); editScope = l }, enabled = !u.busy) { Text("Modifica") }
                        }
                    }
                    HorizontalDivider()
                }
                if (t.peers.isEmpty()) Text("Nessun satellite ancora: invia l'aggiornamento e fallo importare con «Collega a un telefono centrale».",
                    fontSize = 11.sp, color = TextSecondary)
                t.peers.forEach { p ->
                    Text("Satellite ${p.letter} · ultima importazione ${if (p.lastImport > 0) date(p.lastImport) else "—"} · ${p.processedSeq} modifiche ricevute",
                        fontSize = 12.sp)
                }
            }
            TeamRole.SATELLITE -> {
                Text("Satellite ${t.letter}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (synced) "Magazzino sincronizzato: ${u.warehouse?.name} · la mappa la modifica solo il centrale"
                    else "Il magazzino sincronizzato è un altro: aprilo per gestire gli scambi.",
                    fontSize = 12.sp, color = if (synced) TextSecondary else Amber)
                Text("Reparto: " + (if (t.scope.isEmpty()) "tutti" else t.scope.joinToString(", ") + " · fuori reparto puoi solo prelevare"),
                    fontSize = 12.sp, color = if (t.scope.isEmpty()) TextSecondary else Mint)
                Surface(color = (if (t.pending > 0) Amber else Mint).copy(alpha = .12f), shape = MaterialTheme.shapes.medium) {
                    Text(if (t.pending > 0) "${t.pending} modifiche da inviare al centrale" else "Tutto inviato al centrale",
                        Modifier.padding(10.dp).fillMaxWidth(), color = if (t.pending > 0) Amber else Mint, fontWeight = FontWeight.SemiBold)
                }
                if (synced) {
                    PrimaryAction("Sincronizza con il centrale vicino", onClick = { nearby { model.startSatelliteLink() } },
                        enabled = !u.busy, modifier = Modifier.fillMaxWidth())
                    Text("Oppure con i file (WhatsApp, email…):", fontSize = 11.sp, color = TextSecondary)
                    OutlinedButton(onClick = { exporter.launch("Ubicora_${t.letter}_$day.ubisync") }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Invia al centrale")
                    }
                    OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Importa aggiornamento dal centrale")
                    }
                }
            }
        }
        if (t.lastSync > 0) Text("Ultimo scambio: ${date(t.lastSync)}", fontSize = 11.sp, color = TextSecondary)
        if (t.conflicts.isNotEmpty()) OutlinedButton(onClick = { showConflicts = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Amber); Spacer(Modifier.width(8.dp)); Text("${t.conflicts.size} conflitti da controllare", color = Amber)
        }
        if (t.role != TeamRole.NONE) TextButton(onClick = { askLeave = true }, enabled = !u.busy) { Text("Scollega questo telefono", color = TextSecondary) }
    }

    if (askCentral) AlertDialog(onDismissRequest = { askCentral = false }, title = { Text("Telefono centrale") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Il magazzino «${u.warehouse?.name}» diventa quello condiviso. Scegli la password della squadra: serve a cifrare i file e la useranno tutti i telefoni.",
                fontSize = 13.sp)
            Input(password, { password = it }, "Password della squadra (min. 8)", password = true)
            Input(confirm, { confirm = it }, "Ripeti la password", password = true)
            Text("Consiglio: prima fai un backup completo.", fontSize = 11.sp, color = TextSecondary)
        }
    }, confirmButton = { TextButton(onClick = { model.becomeTeamCentral(password); askCentral = false; password = ""; confirm = "" },
        enabled = password.length >= 8 && password == confirm) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = { askCentral = false }) { Text("Annulla") } })

    if (askJoin) AlertDialog(onDismissRequest = { askJoin = false }, title = { Text("Collega al centrale") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avvicinati al telefono centrale con la rete aperta, oppure usa il file «aggiornamento» che ti ha inviato. " +
                "Verrà creato un nuovo magazzino con i suoi articoli e la sua mappa.", fontSize = 13.sp)
            Input(password, { password = it }, "Password della squadra", password = true)
            Text("Lettera di questo telefono (diversa per ogni persona)", fontSize = 12.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("B", "C", "D", "E", "F").forEach { l -> FilterChip(selected = letter == l, onClick = { letter = l }, label = { Text(l) }) }
            }
            Text("I codici degli articoli creati qui avranno la lettera: es. RIC-${letter}000001.", fontSize = 11.sp, color = TextSecondary)
        }
    }, confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { askJoin = false; val p = password; val l = letter; password = ""; nearby { model.joinNearby(p, l) } },
                enabled = password.length >= 8) { Text("Cerca il centrale vicino") }
            TextButton(onClick = { askJoin = false; joiner.launch(arrayOf("*/*")) }, enabled = password.length >= 8) { Text("Usa un file") }
            TextButton(onClick = { askJoin = false; password = "" }) { Text("Annulla") }
        }
    })

    val unsent = t.role == TeamRole.SATELLITE && t.pending > 0
    if (askLeave) AlertDialog(onDismissRequest = { askLeave = false }, title = { Text("Scollegare il telefono?") }, text = {
        Text(when {
            t.role == TeamRole.CENTRAL -> "Gli articoli restano su questo telefono, ma i satelliti non potranno più scambiare file con lui."
            unsent -> "Ci sono ${t.pending} modifiche non ancora inviate al centrale. Se scolleghi ora restano solo su questo telefono e il centrale non le riceverà. " +
                "Fallo solo se il centrale ha rifiutato i tuoi file (per esempio lettera già usata)."
            else -> "Gli articoli restano su questo telefono."
        }, fontSize = 13.sp)
    }, confirmButton = { TextButton(onClick = { model.leaveTeam(force = unsent); askLeave = false }) {
        Text(if (unsent) "Scollega comunque" else "Scollega", color = Danger) } },
        dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Annulla") } })

    if (showConflicts) AlertDialog(onDismissRequest = { showConflicts = false }, title = { Text("Conflitti") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Modifiche dei satelliti non applicate (per esempio un prelievo superiore alla giacenza). Controlla l'articolo e, se serve, fai una rettifica.",
                fontSize = 12.sp, color = TextSecondary)
            t.conflicts.forEach { c ->
                Column { Text("${c.letter} · ${c.item}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(c.detail, fontSize = 12.sp, color = TextSecondary); Text(date(c.createdAt), fontSize = 10.sp, color = TextSecondary) }
            }
        }
    }, confirmButton = { TextButton(onClick = { model.dismissTeamConflicts(); showConflicts = false }) { Text("Ho controllato, cancella elenco") } },
        dismissButton = { TextButton(onClick = { showConflicts = false }) { Text("Chiudi") } })

    TeamLinkDialog(model)

    editScope?.let { l -> AlertDialog(onDismissRequest = { editScope = null }, title = { Text("Reparto dell'operatore $l") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Categorie in cui può creare, caricare e spostare articoli. Nessuna spuntata = tutte. " +
                "Arriva al telefono $l al prossimo scambio.", fontSize = 12.sp, color = TextSecondary)
            ArticleCodes.categoryPrefixes.keys.forEach { c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = c in scopeChoice, onCheckedChange = { on -> scopeChoice = if (on) scopeChoice + c else scopeChoice - c })
                    Text(c)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { model.setTeamScope(l, scopeChoice.toList()); editScope = null }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = { editScope = null }) { Text("Annulla") } }) }

    result?.let { text -> AlertDialog(onDismissRequest = { result = null }, title = { Text("Sincronizzazione") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } }) }
}
