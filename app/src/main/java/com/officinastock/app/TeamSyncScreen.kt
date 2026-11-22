package com.officinastock.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 4.8 Settings panel: central phone and satellites exchanging encrypted files, no server. */
/** [inTeamScreen]: the Squadra tab already shows the role and the main button in its card above, so they are not repeated. */
@Composable fun TeamSyncPanel(model: StockViewModel, inTeamScreen: Boolean = false) {
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
    var letter by remember { mutableStateOf("B") }
    var invitedTo by remember { mutableStateOf<String?>(null) }
    var viaCode by remember { mutableStateOf(false) }
    var askPassword by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    // 4.10: the central's QR fills password and a free letter.
    LaunchedEffect(u.teamQr) {
        u.teamQr?.let { invite ->
            password = invite.secret; viaCode = invite.viaCode; letter = invite.suggestedLetter; invitedTo = invite.warehouse
            askJoin = true; model.consumeTeamQr()
        }
    }
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
    val synced = !t.covers(null) && t.covers(u.warehouse?.id)
    // 4.19.3: the team works on all the shared sites, not on one warehouse.
    val sharedSites = u.warehouses.filter { t.covers(it.id) }.joinToString(", ") { it.name }.ifBlank { u.warehouse?.name.orEmpty() }
    val compact = inTeamScreen && t.role != TeamRole.NONE && synced
    // 4.26: a central with no satellite yet: one clear first step (the QR), the file exchange folded away.
    val firstLink = t.role == TeamRole.CENTRAL && synced && t.peers.isEmpty()
    var showFiles by remember { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth()) {
        Text(if (firstLink) "COLLEGA IL PRIMO TELEFONO" else if (compact) "ALTRI MODI DI SCAMBIO" else "SINCRONIZZAZIONE TRA TELEFONI", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
        if (t.role != TeamRole.NONE && t.needsPassword) {
            // 4.11: the password is sealed by this phone's keystore; after a restore on another phone it is typed again.
            Surface(color = Amber.copy(alpha = .12f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(10.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Reinserisci la password della squadra", color = Amber, fontWeight = FontWeight.SemiBold)
                    Text("Per sicurezza non viaggia nei backup in chiaro: su questo telefono va scritta una volta.", fontSize = 11.sp, color = TextSecondary)
                    OutlinedButton(onClick = { password = ""; askPassword = true }, enabled = !u.busy) { Text("Inserisci password") }
                }
            }
        }
        when (t.role) {
            TeamRole.NONE -> {
                Text("Più persone, nessun server", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Un telefono centrale tiene l'archivio; gli altri (satelliti) lavorano offline e si scambiano file cifrati " +
                    "con il centrale, via WhatsApp, email o USB. Le giacenze si uniscono sommando i movimenti.",
                    fontSize = 12.sp, color = TextSecondary)
                PrimaryAction("Rendi questo il telefono centrale", onClick = { password = TeamSync.newPassword(); askCentral = true },
                    enabled = !u.busy && u.warehouse != null, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { model.requestScan() }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Collega con il QR del centrale")
                }
                OutlinedButton(onClick = { password = ""; letter = "B"; invitedTo = null; viaCode = false; askJoin = true }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Collega a un telefono centrale")
                }
            }
            TeamRole.CENTRAL -> {
                if (!compact) {
                    Text("Telefono centrale", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (synced) "Sedi della squadra: $sharedSites" else "Questa sede non è condivisa con la squadra: aprine una condivisa per gestire gli scambi.",
                        fontSize = 12.sp, color = if (synced) TextSecondary else Amber)
                }
                if (synced) {
                    if (!compact && !firstLink) PrimaryAction("Apri la rete per i satelliti vicini", onClick = { nearby { model.startCentralLink() } },
                        enabled = !u.busy, modifier = Modifier.fillMaxWidth())
                    if (firstLink) {
                        Text("Sul nuovo telefono tocca «Collega con il QR del centrale» e inquadra questo QR.", fontSize = 12.sp, color = TextSecondary)
                        PrimaryAction("QR per collegare un nuovo telefono", onClick = { showInvite = true }, enabled = !u.busy, modifier = Modifier.fillMaxWidth())
                    } else OutlinedButton(onClick = { showInvite = true }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.QrCode2, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("QR per collegare un nuovo telefono")
                    }
                    if (firstLink && !showFiles) TextButton(onClick = { showFiles = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Altri modi: con i file (WhatsApp, email…)", color = TextSecondary)
                    } else {
                        Text("Oppure con i file (WhatsApp, email…):", fontSize = 11.sp, color = TextSecondary)
                        OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Importa dai satelliti")
                        }
                        OutlinedButton(onClick = { exporter.launch("Ubicora_aggiornamento_$day.ubisync") }, enabled = !u.busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Invia aggiornamento a tutti")
                        }
                    }
                }
                // 4.19.3: only the satellites that are really linked, not a fixed list of letters.
                val linked = (t.peers.map { it.letter } + t.scopes.keys).filter { it.isNotBlank() }.distinct().sorted()
                // 4.26: the permissions section appears with the first linked phone (before, it only said «they appear here»).
                if (synced && linked.isNotEmpty()) {
                    HorizontalDivider()
                    Text("PERMESSI DEI TELEFONI", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
                    Text("Fuori dal suo reparto un telefono vede gli articoli e può solo prelevare. Le schede modificate da un manutentore le validi tu.",
                        fontSize = 11.sp, color = TextSecondary)
                    linked.forEach { l ->
                        val cats = t.scopes[l].orEmpty()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(l, fontWeight = FontWeight.Bold, color = Mint, modifier = Modifier.width(24.dp))
                            Text(permissionLabel(cats), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scopeChoice = cats.toSet(); editScope = l }, enabled = !u.busy) { Text("Modifica") }
                        }
                    }
                    HorizontalDivider()
                }
                t.peers.forEach { p ->
                    Text("Satellite ${p.letter} · ultima importazione ${if (p.lastImport > 0) date(p.lastImport) else "—"} · ${p.processedSeq} modifiche ricevute",
                        fontSize = 12.sp)
                }
            }
            TeamRole.SATELLITE -> {
                if (!compact) Text("Satellite ${t.letter}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (synced) "Sedi della squadra: $sharedSites · la mappa la modifica solo il centrale"
                    else "Questa sede non è condivisa con la squadra: aprine una condivisa per gestire gli scambi.",
                    fontSize = 12.sp, color = if (synced) TextSecondary else Amber)
                Text("Permessi: " + permissionLabel(t.scope) + (if (t.department.isNotEmpty()) " · fuori reparto puoi solo prelevare" else ""),
                    fontSize = 12.sp, color = if (t.scope.isEmpty()) TextSecondary else Mint)
                Surface(color = (if (t.pending > 0) Amber else Mint).copy(alpha = .12f), shape = MaterialTheme.shapes.medium) {
                    Text(if (t.pending > 0) "${t.pending} modifiche da inviare al centrale" else "Tutto inviato al centrale",
                        Modifier.padding(10.dp).fillMaxWidth(), color = if (t.pending > 0) Amber else Mint, fontWeight = FontWeight.SemiBold)
                }
                if (synced) {
                    if (!compact) PrimaryAction("Sincronizza con il centrale vicino", onClick = { nearby { model.startSatelliteLink() } },
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
        if (t.role != TeamRole.NONE && synced) {
            // 4.13: automatic exchange while the app is open.
            val context = androidx.compose.ui.platform.LocalContext.current
            val permitted = TeamLink.missingPermissions(context).isEmpty()
            val link = model.link.ui
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sincronizzazione automatica", fontWeight = FontWeight.SemiBold)
                    Text(if (t.role == TeamRole.CENTRAL) "Con Ubicora aperta la rete resta aperta: i satelliti vicini si aggiornano da soli"
                        else "Con Ubicora aperta cerca il centrale all'avvio e ogni 10 minuti, senza bloccare lo schermo",
                        fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = UiPrefs.autoSync && permitted, enabled = !u.busy,
                    onCheckedChange = { on -> if (on) nearby { model.setAutoSync(true) } else model.setAutoSync(false) })
            }
            if (link.silent && link.phase == LinkPhase.CENTRAL) {
                val done = link.peers.count { it.phase == PeerPhase.DONE }
                Text("Rete automatica aperta" + (if (done > 0) " · $done ${if (done == 1) "satellite sincronizzato" else "satelliti sincronizzati"}" else ""),
                    fontSize = 11.sp, color = Mint)
            }
        }
        if (t.lastSync > 0) Text("Ultimo scambio: ${date(t.lastSync)}", fontSize = 11.sp, color = TextSecondary)
        TeamEditsSection(model)
        if (t.conflicts.isNotEmpty()) OutlinedButton(onClick = { showConflicts = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Amber); Spacer(Modifier.width(8.dp)); Text("${t.conflicts.size} conflitti da controllare", color = Amber)
        }
        if (t.role != TeamRole.NONE) TextButton(onClick = { askLeave = true }, enabled = !u.busy) { Text("Scollega questo telefono", color = TextSecondary) }
    }

    if (askCentral) AlertDialog(onDismissRequest = { askCentral = false }, title = { Text("Telefono centrale") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Il magazzino «${u.warehouse?.name}» diventa quello condiviso. Questa è la password della squadra: cifra i file tra i telefoni. " +
                "Annotala: serve per collegare un telefono con i file e dopo un ripristino. Con il QR non serve.", fontSize = 13.sp)
            Input(password, { password = it.trim() }, "Password della squadra (min. ${TeamSync.NEW_PASSWORD_MIN})")
            Text("Consiglio: prima fai un backup completo.", fontSize = 11.sp, color = TextSecondary)
        }
    }, confirmButton = { TextButton(onClick = { model.becomeTeamCentral(password); askCentral = false; password = "" },
        enabled = password.length >= TeamSync.NEW_PASSWORD_MIN) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = { askCentral = false }) { Text("Annulla") } })

    if (askJoin) AlertDialog(onDismissRequest = { askJoin = false }, title = { Text("Collega al centrale") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            invitedTo?.let { Text(if (viaCode) "QR letto: magazzino «$it». Codice valido ${TeamSync.INVITE_MINUTES} minuti, non serve la password."
                else "QR letto: magazzino «$it». Password già inserita.", fontSize = 12.sp, color = Mint) }
            Text("Avvicinati al telefono centrale con la rete aperta, oppure usa il file «aggiornamento» che ti ha inviato. " +
                "Verrà creato un nuovo magazzino con i suoi articoli e la sua mappa.", fontSize = 13.sp)
            if (!viaCode) Input(password, { password = it }, "Password della squadra", password = true)
            Text("Lettera di questo telefono (diversa per ogni persona)", fontSize = 12.sp, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("B", "C", "D", "E", "F").forEach { l -> FilterChip(selected = letter == l, onClick = { letter = l }, label = { Text(l) }) }
            }
            Text("I codici degli articoli creati qui avranno la lettera: es. RIC-${letter}000001.", fontSize = 11.sp, color = TextSecondary)
        }
    }, confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { askJoin = false; val p = password; val l = letter; password = ""; val code = viaCode; nearby { model.joinNearby(p, l, code) } },
                enabled = password.length >= 8) { Text("Cerca il centrale vicino") }
            // The file exchange needs the real team password (the QR code works only over the nearby network).
            if (!viaCode) TextButton(onClick = { askJoin = false; joiner.launch(arrayOf("*/*")) }, enabled = password.length >= 8) { Text("Usa un file") }
            TextButton(onClick = { askJoin = false; password = "" }) { Text("Annulla") }
        }
    })

    val unsent = t.role == TeamRole.SATELLITE && t.pending > 0
    if (askPassword) AlertDialog(onDismissRequest = { askPassword = false }, title = { Text("Password della squadra") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La stessa usata da tutti i telefoni della squadra.", fontSize = 13.sp)
            Input(password, { password = it }, "Password della squadra", password = true)
        }
    }, confirmButton = { TextButton(onClick = { model.restoreTeamPassword(password); password = ""; askPassword = false },
        enabled = password.length >= 8) { Text("Salva") } },
        dismissButton = { TextButton(onClick = { askPassword = false; password = "" }) { Text("Annulla") } })

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


    if (showInvite) {
        // Database + Keystore + QR bitmap: off the main thread, not during composition.
        // null = still being prepared; a Result holding null = no QR could be made.
        val picture by produceState<Result<androidx.compose.ui.graphics.ImageBitmap?>?>(null, t.peers, u.warehouse?.name) {
            value = withContext(Dispatchers.IO) {
                runCatching { model.teamInvite()?.let { QrLabels.bitmap(it, 640).asImageBitmap() } }
            }
        }
        AlertDialog(onDismissRequest = { showInvite = false }, title = { Text("Collega un nuovo telefono") }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val ready = picture
                if (ready == null) Text("Preparo il QR…", color = TextSecondary)
                else ready.getOrNull()?.let { androidx.compose.foundation.Image(it, "QR di collegamento", Modifier.size(240.dp)) }
                    ?: Text("QR non disponibile", color = Danger)
                Text("Sull'altro telefono: Impostazioni › Sincronizzazione › «Collega con il QR del centrale», poi «Cerca il centrale vicino» " +
                    "mentre qui la rete è aperta.", fontSize = 12.sp, color = TextSecondary)
                Text("Il QR contiene un codice usa e getta valido ${TeamSync.INVITE_MINUTES} minuti, non la password: dopo scade da solo.",
                    fontSize = 12.sp, color = TextSecondary)
            }
        }, confirmButton = { TextButton(onClick = { showInvite = false }) { Text("Chiudi") } })
    }

    editScope?.let { l -> AlertDialog(onDismissRequest = { editScope = null }, title = { Text("Permessi del telefono $l") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // 4.19: three levels, from the most limited to the freest.
            val pickOnly = TeamSync.PICK_ONLY in scopeChoice
            Text("Cosa può fare il telefono $l. Arriva al prossimo scambio.", fontSize = 12.sp, color = TextSecondary)
            // 4.21: the maintainer has a department too, and changes the cards in it (validated by the central).
            val maintainer = !pickOnly && TeamSync.isMaintainer(scopeChoice)
            val chosen = TeamSync.department(scopeChoice)
            listOf(Triple("Solo prelievo", "Preleva e presta. Non crea, non carica, non sposta.", 0),
                Triple("Operatore di reparto", "Tutto nelle categorie spuntate; fuori solo prelievo.", 1),
                Triple("Manutentore", "Come l'operatore, e in più modifica le schede degli articoli del suo reparto: le modifiche le validi tu.", 2)).forEach { (title, hint, level) ->
                val selected = when (level) { 0 -> pickOnly; 1 -> !pickOnly && !maintainer; else -> maintainer }
                Row(Modifier.fillMaxWidth().clickable {
                    scopeChoice = when (level) {
                        0 -> setOf(TeamSync.PICK_ONLY)
                        1 -> chosen.toSet().ifEmpty { setOf(ArticleCodes.categoryPrefixes.keys.first()) }
                        else -> setOf(TeamSync.MAINTAINER) + chosen
                    }
                }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Column { Text(title, fontWeight = FontWeight.SemiBold); Text(hint, fontSize = 12.sp, color = TextSecondary) }
                }
            }
            if (!pickOnly) {
                Text(if (maintainer) "Reparto del manutentore (nessuna spunta = tutte le categorie):" else "Categorie del reparto:",
                    fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 24.dp, top = 4.dp))
                ArticleCodes.categoryPrefixes.keys.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = c in chosen, onCheckedChange = { on ->
                            val next = if (on) chosen + c else chosen - c
                            // An operator keeps at least one category: with none he would become a maintainer of everything.
                            if (maintainer) scopeChoice = setOf(TeamSync.MAINTAINER) + next
                            else if (next.isNotEmpty()) scopeChoice = next.toSet()
                        })
                        Text(c)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = {
            if (TeamSync.PICK_ONLY in scopeChoice) model.setTeamPickOnly(l)
            else model.setTeamScope(l, TeamSync.department(scopeChoice), maintainer = TeamSync.isMaintainer(scopeChoice))
            editScope = null }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = { editScope = null }) { Text("Annulla") } }) }

    result?.let { text -> AlertDialog(onDismissRequest = { result = null }, title = { Text("Sincronizzazione") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } }) }
}

/** 4.21: what a phone may do, in words: "Solo prelievo", "Operatore · Elettrico", "Manutentore · Meccanica, Pneumatica". */
fun permissionLabel(scope: List<String>): String {
    val department = TeamSync.department(scope)
    return when {
        scope == listOf(TeamSync.PICK_ONLY) -> "Solo prelievo e prestito"
        TeamSync.isMaintainer(scope) -> "Manutentore · " + (department.joinToString(", ").ifEmpty { "tutte le categorie" })
        else -> "Operatore · " + department.joinToString(", ")
    }
}
