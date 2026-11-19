package com.officinastock.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 4.21: one change of an article card asked by a maintainer. On the central: before/after and the two
 * answers (approve, send back with what to do again). On the satellite: where it stands.
 * [showItem]: the article's name, when the card is shown outside the article page.
 */
@Composable fun TeamEditCard(edit: TeamEdit, central: Boolean, busy: Boolean, showItem: Boolean,
                             onApprove: () -> Unit, onReject: (String) -> Unit, onRedo: (() -> Unit)?, onDismiss: () -> Unit) {
    var rejecting by remember(edit.uid) { mutableStateOf(false) }
    var reason by remember(edit.uid) { mutableStateOf("") }
    val tone = when {
        central -> Amber
        edit.status == "rejected" -> Danger
        edit.status == "approved" -> Mint
        else -> Sky
    }
    Surface(color = tone.copy(alpha = .10f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(when {
                central -> "Modifica della scheda dal telefono ${edit.letter} · da validare"
                edit.status == "sending" -> "Modifica della scheda · da inviare al centrale"
                edit.status == "pending" -> "Modifica della scheda · in attesa di validazione"
                edit.status == "approved" -> "Modifica della scheda approvata dal centrale"
                else -> "Modifica della scheda rimandata indietro dal centrale"
            }, color = tone, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (showItem) Text(edit.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            edit.fields.forEach { f ->
                Column {
                    Text(f.label, fontSize = 11.sp, color = TextSecondary)
                    Text(f.before, fontSize = 12.sp, color = TextSecondary, textDecoration = TextDecoration.LineThrough, maxLines = 3)
                    Text(f.after, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 6)
                }
            }
            if (edit.fields.any { it.key == "sku" })
                Text("Cambia il codice: l'etichetta QR va ristampata.", fontSize = 11.sp, color = Amber)
            Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(edit.createdAt)), fontSize = 10.sp, color = TextSecondary)
            when {
                central -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { rejecting = true }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Rimanda indietro") }
                    Button(onClick = onApprove, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Approva") }
                }
                edit.status == "rejected" -> {
                    Text("Da correggere: ${edit.reason}", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Lascia così") }
                        if (onRedo != null) Button(onClick = onRedo, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Rifai la modifica") }
                    }
                }
                edit.status == "approved" -> TextButton(onClick = onDismiss, enabled = !busy) { Text("OK") }
            }
        }
    }
    if (rejecting) AlertDialog(onDismissRequest = { rejecting = false }, title = { Text("Rimanda indietro") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La scheda non cambia. Il telefono ${edit.letter} vedrà la tua nota al prossimo scambio e potrà rifare la modifica.",
                fontSize = 12.sp, color = TextSecondary)
            OutlinedTextField(reason, { if (it.length <= 300) reason = it }, label = { Text("Cosa deve correggere") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
        }
    }, confirmButton = { TextButton(onClick = { onReject(reason); rejecting = false }) { Text("Rimanda indietro", color = Danger) } },
        dismissButton = { TextButton(onClick = { rejecting = false }) { Text("Annulla") } })
}

/** 4.21: the card changes of the team in the sync panel: to validate (central) or sent by this phone (satellite). */
@Composable fun TeamEditsSection(model: StockViewModel) {
    val t = model.ui.team
    if (t.edits.isEmpty()) return
    val central = t.role == TeamRole.CENTRAL
    HorizontalDivider()
    Text(if (central) "MODIFICHE ALLE SCHEDE DA VALIDARE · ${t.edits.size}" else "LE TUE MODIFICHE ALLE SCHEDE",
        color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
    t.edits.forEach { e ->
        TeamEditCard(e, central, model.ui.busy, showItem = true,
            onApprove = { model.approveTeamEdit(e.uid) }, onReject = { model.rejectTeamEdit(e.uid, it) },
            onRedo = e.itemId?.let { id -> { model.openItemDetails(id) } }, onDismiss = { model.dismissTeamEdit(e.uid) })
    }
}
