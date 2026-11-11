package com.officinastock.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * 4.19.3: the single scanner of the app, drawn like the rest of Ubicora (it replaces the plain ZXing
 * screen): camera, frame, torch and a close button. It reads one code and closes.
 */
@Composable fun QuickScanScreen(model: StockViewModel, onClose: () -> Unit) {
    val u = model.ui
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }
    val cancel = { if (!done) { done = true; onClose(); model.scanCancelled() } }
    BackHandler { cancel() }
    val waitingFor = u.pickScanItemId?.let { id -> u.items.firstOrNull { it.id == id } }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (granted) BurstCamera(torch) { raw ->
                    if (!done) {
                        done = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (UiPrefs.scanSound) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
                        onClose()
                        model.handleScannedCode(raw)
                    }
                } else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Per leggere le etichette serve il permesso della fotocamera.", color = Color.White, textAlign = TextAlign.Center)
                    Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Consenti la fotocamera") }
                }
                Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, Mint, RoundedCornerShape(24.dp)))
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = { cancel() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "Chiudi lo scanner") }
                    Text("Scansiona", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { torch = !torch }, enabled = granted, modifier = Modifier.size(48.dp)) {
                        Icon(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn, if (torch) "Spegni la torcia" else "Accendi la torcia")
                    }
                }
            }
            Surface(color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (waitingFor != null) {
                        Text("CONFERMA IL PRELIEVO", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text("Leggi l'etichetta di ${waitingFor.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Inquadra un'etichetta", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Di un articolo o di uno scaffale. Anche il QR del telefono centrale, per collegarti alla squadra.",
                            color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
