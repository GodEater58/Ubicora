package com.officinastock.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.10: sync status on the home screen with a one-tap exchange. */
@Composable fun SyncCard(model: StockViewModel) {
    val u = model.ui
    val t = u.team
    var denied by remember { mutableStateOf<String?>(null) }
    val nearby = rememberNearbyAccess { denied = it }
    val stale = t.lastSync == 0L || System.currentTimeMillis() - t.lastSync > 2 * 86_400_000L
    val warn = (t.role == TeamRole.SATELLITE && (t.pending > 0 || stale)) || t.conflicts.isNotEmpty()
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sync, null, tint = if (warn) Amber else Mint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (t.role == TeamRole.CENTRAL) "Telefono centrale" else "Satellite ${t.letter}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(when (t.role) {
                    TeamRole.CENTRAL -> "${t.peers.size} ${if (t.peers.size == 1) "satellite" else "satelliti"} · ultimo scambio ${ago(t.lastSync)}" +
                        (if (t.conflicts.isNotEmpty()) " · ${t.conflicts.size} conflitti" else "")
                    else -> (if (t.pending > 0) "${t.pending} modifiche da inviare" else "Tutto inviato") + " · ultimo scambio ${ago(t.lastSync)}"
                }, fontSize = 12.sp, color = if (warn) Amber else TextSecondary)
            }
        }
        PrimaryAction(if (t.role == TeamRole.CENTRAL) "Apri la rete per i satelliti" else "Sincronizza ora",
            onClick = { nearby { if (t.role == TeamRole.CENTRAL) model.startCentralLink() else model.startSatelliteLink() } },
            enabled = !u.busy, modifier = Modifier.fillMaxWidth())
    }
    denied?.let { text -> AlertDialog(onDismissRequest = { denied = null }, title = { Text("Permessi") }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { denied = null }) { Text("OK") } }) }
}

/** 4.10: articles under minimum, shared as text (WhatsApp, email…). */
@Composable fun ReorderShareButton(model: StockViewModel, modifier: Modifier = Modifier) {
    // A launcher (not startActivity) keeps the session open while the user is in the other app.
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val critical = model.ui.items.count { it.lowStock }
    OutlinedButton(onClick = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Da ordinare · ${model.ui.warehouse?.name.orEmpty()}")
            putExtra(Intent.EXTRA_TEXT, model.reorderText())
        }
        share.launch(Intent.createChooser(send, "Invia la lista da ordinare"))
    }, enabled = critical > 0, modifier = modifier) {
        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text(if (critical > 0) "Condividi lista da ordinare ($critical)" else "Niente da ordinare")
    }
}

/** 4.10 Settings: how the app looks on this phone. */
@Composable fun SecurityPanel(model: StockViewModel) {
    val encrypted = remember { model.archiveEncrypted }
    Panel(Modifier.fillMaxWidth()) {
        Text("SICUREZZA DELL'ARCHIVIO", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (encrypted) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null, tint = if (encrypted) Mint else Amber)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (encrypted) "Archivio cifrato su questo telefono" else "Archivio non cifrato", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (encrypted) "AES-256: anche copiando i file dal telefono i dati non si leggono senza la chiave, custodita dal portachiavi sicuro di Android."
                    else (model.archiveProblem ?: "Questo telefono non offre un portachiavi sicuro: i dati restano protetti dal blocco schermo di Android."),
                    fontSize = 11.sp, color = TextSecondary)
            }
        }
        Text("Il backup completo resta il modo per portare i dati su un altro telefono: la chiave dell'archivio non lascia mai questo.",
            fontSize = 11.sp, color = TextSecondary)
        // 4.14: change the PIN of the profile in use.
        var changing by remember { mutableStateOf(false) }
        var done by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { changing = true; done = false }, enabled = !model.ui.busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Password, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Cambia PIN di ${model.ui.profile?.name.orEmpty()}")
        }
        if (done) Text("PIN cambiato. Ricorda: un backup fatto prima contiene ancora il PIN vecchio.", fontSize = 11.sp, color = Mint)
        if (changing) ChangePinDialog(model, onClose = { changing = false }, onChanged = { changing = false; done = true })
    }
}

@Composable private fun ChangePinDialog(model: StockViewModel, onClose: () -> Unit, onChanged: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    fun digits(v: String) = v.filter(Char::isDigit).take(8)
    val validNew = fresh.length in 4..8
    LaunchedEffect(Unit) { model.dismissMessage() }
    AlertDialog(onDismissRequest = onClose, title = { Text("Cambia PIN") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Input(current, { current = digits(it) }, "PIN attuale", number = true, password = true)
            Input(fresh, { fresh = digits(it) }, "Nuovo PIN (4–8 cifre)", number = true, password = true)
            Input(repeat, { repeat = digits(it) }, "Ripeti il nuovo PIN", number = true, password = true)
            if (repeat.isNotEmpty() && repeat != fresh) Text("I due PIN nuovi non coincidono", color = Danger, fontSize = 12.sp)
            model.ui.message?.let { Text(it, color = Danger, fontSize = 12.sp) }
        }
    }, confirmButton = {
        TextButton(onClick = { model.changePin(current, fresh) { onChanged() } },
            enabled = current.length >= 4 && validNew && repeat == fresh && !model.ui.busy) { Text("Cambia PIN") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Annulla") } })
}

@Composable fun DisplayPanel() {
    Panel(Modifier.fillMaxWidth()) {
        Text("VISUALIZZAZIONE", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
        Text("Solo su questo telefono", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Toggle("Tema chiaro", "Più leggibile con luce forte o all'aperto", UiPrefs.light, UiPrefs::updateLight)
        Toggle("Modalità guanti", "Pulsanti e testi più grandi, facili da premere anche con i guanti", UiPrefs.glove, UiPrefs::updateGlove)
        Toggle("Consenti screenshot", "Spento: le schermate dell'app non si possono fotografare né registrare (protegge i dati)",
            UiPrefs.screenshots, UiPrefs::updateScreenshots)
    }
}

@Composable private fun Toggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
