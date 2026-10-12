package com.officinastock.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Runs [action] once the Nearby permissions are granted, asking for them if needed.
 * Returns the launcher function; [onDenied] receives the text to show.
 */
@Composable fun rememberNearbyAccess(onDenied: (String) -> Unit): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val action = pending; pending = null
        if (granted.values.all { it } && TeamLink.missingPermissions(context).isEmpty()) action?.invoke()
        else onDenied("Per trovare gli altri telefoni servono i permessi «Dispositivi nelle vicinanze» e «Posizione» (posizione precisa). " +
            "Se li hai negati, attivali da Impostazioni Android › App › Ubicora › Autorizzazioni.")
    }
    return { action ->
        val missing = TeamLink.missingPermissions(context)
        if (missing.isEmpty()) action() else { pending = action; launcher.launch(missing.toTypedArray()) }
    }
}

/** Full-screen view of the automatic exchange, shown while the network or the search is active. */
@Composable fun TeamLinkDialog(model: StockViewModel) {
    val link = model.link.ui
    if (link.phase == LinkPhase.IDLE) return
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    val central = link.phase == LinkPhase.CENTRAL
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (central) "RETE DEL CENTRALE" else "SINCRONIZZAZIONE", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(when (link.phase) {
                    LinkPhase.CENTRAL -> "In attesa dei satelliti"
                    LinkPhase.SEARCHING -> "Cerco il centrale"
                    LinkPhase.CONNECTING -> "Mi collego a ${link.central ?: "centrale"}"
                    LinkPhase.TRANSFER -> "Scambio con ${link.central ?: "centrale"}"
                    LinkPhase.DONE -> "Sincronizzato"
                    else -> "Non riuscito"
                }, fontWeight = FontWeight.Bold, fontSize = 22.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState = visual(link), label = "linkVisual") { v ->
                    Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                        when (v) {
                            Visual.RADAR -> Radar(if (central) Icons.Outlined.WifiTethering else Icons.Outlined.Search)
                            Visual.TRANSFER -> Transfer()
                            Visual.DONE -> Outcome(true)
                            Visual.ERROR -> Outcome(false)
                        }
                    }
                }
                link.message?.let {
                    Text(it, fontSize = 14.sp, textAlign = TextAlign.Center,
                        color = when (link.phase) { LinkPhase.ERROR -> Danger; LinkPhase.DONE -> Mint; else -> TextSecondary })
                }
                if (central) {
                    if (link.peers.isEmpty()) Text("Sui satelliti: Impostazioni › «Sincronizza con il centrale vicino».",
                        fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    link.peers.asReversed().forEach { PeerRow(it) }
                    val done = link.peers.count { it.phase == PeerPhase.DONE }
                    if (done > 0) Text("$done ${if (done == 1) "satellite sincronizzato" else "satelliti sincronizzati"}. " +
                        "Quando hanno finito tutti, chiudi la rete.", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(8.dp))
                when (link.phase) {
                    LinkPhase.DONE, LinkPhase.ERROR -> PrimaryAction("Chiudi", onClick = { model.stopLink() }, modifier = Modifier.fillMaxWidth())
                    LinkPhase.CENTRAL -> PrimaryAction("Chiudi la rete", onClick = { model.stopLink() }, modifier = Modifier.fillMaxWidth())
                    else -> OutlinedButton(onClick = { model.stopLink() }, modifier = Modifier.fillMaxWidth()) { Text("Annulla") }
                }
            }
        }
    }
}

private enum class Visual { RADAR, TRANSFER, DONE, ERROR }
private fun visual(link: LinkUi) = when (link.phase) {
    LinkPhase.CENTRAL -> if (link.peers.any { it.phase in setOf(PeerPhase.CONNECTING, PeerPhase.RECEIVING, PeerPhase.APPLYING, PeerPhase.SENDING) })
        Visual.TRANSFER else Visual.RADAR
    LinkPhase.SEARCHING -> Visual.RADAR
    LinkPhase.CONNECTING, LinkPhase.TRANSFER -> Visual.TRANSFER
    LinkPhase.DONE -> Visual.DONE
    else -> Visual.ERROR
}

/** Waves leaving the phone: network open or search in progress. */
@Composable private fun Radar(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "wave")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val max = size.minDimension / 2
            for (k in 0..2) {
                val f = (p + k / 3f) % 1f
                drawCircle(Mint.copy(alpha = (1f - f) * .55f), radius = max * (.25f + .75f * f), style = Stroke(3.dp.toPx()))
            }
            drawCircle(Mint.copy(alpha = .16f), radius = max * .3f)
        }
        Icon(icon, null, tint = Mint, modifier = Modifier.size(52.dp))
    }
}

/** Two phones with data flowing between them. */
@Composable private fun Transfer() {
    val t = rememberInfiniteTransition(label = "transfer")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "flow")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.PhoneAndroid, null, tint = Mint, modifier = Modifier.size(64.dp))
        Canvas(Modifier.width(130.dp).height(40.dp)) {
            val n = 6
            for (i in 0 until n) {
                val f = ((i.toFloat() / n) + p) % 1f
                val y = size.height / 2
                drawCircle(Mint.copy(alpha = .25f + .75f * kotlin.math.sin(f * Math.PI).toFloat()), radius = 6.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width * f, y))
            }
        }
        Icon(Icons.Outlined.PhoneAndroid, null, tint = Mint, modifier = Modifier.size(64.dp))
    }
}

/** Big tick (or cross) that pops in when the exchange ends. */
@Composable private fun Outcome(ok: Boolean) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val s by animateFloatAsState(if (shown) 1f else .3f, spring(dampingRatio = .45f, stiffness = 260f), label = "pop")
    val color = if (ok) Mint else Danger
    Box(Modifier.size(150.dp).scale(s).clip(CircleShape).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = color, modifier = Modifier.size(96.dp))
    }
}

@Composable private fun PeerRow(peer: LinkPeer) {
    val color = when (peer.phase) { PeerPhase.DONE -> Mint; PeerPhase.ERROR -> Danger; else -> Amber }
    Surface(color = color.copy(alpha = .10f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                Text(peer.name.substringAfter("Satellite ").take(1).ifBlank { "?" }, fontWeight = FontWeight.Bold, color = color)
            }
            Column(Modifier.weight(1f)) {
                Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                if (peer.detail.isNotEmpty()) Text(peer.detail, fontSize = 12.sp, color = TextSecondary)
            }
            when (peer.phase) {
                PeerPhase.DONE -> Icon(Icons.Outlined.CheckCircle, null, tint = Mint)
                PeerPhase.ERROR -> Icon(Icons.Outlined.ErrorOutline, null, tint = Danger)
                else -> CircularProgressIndicator(Modifier.size(22.dp), color = Amber, strokeWidth = 2.5.dp)
            }
        }
    }
}

