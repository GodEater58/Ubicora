package com.officinastock.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.delay

/**
 * 4.15 burst picking on one screen: camera on top, always on; article, quantity and confirmation
 * below, within reach of the thumb. Every reading answers with a sound and a vibration, so the
 * operator can keep looking at the shelf.
 */
@Composable fun BurstScanScreen(model: StockViewModel) {
    val u = model.ui
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val item = u.burstItemId?.let { id -> u.items.firstOrNull { it.id == id } }
    val place = item?.placementId?.let { id -> u.placements.firstOrNull { it.id == id } }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    val gate = remember { ScanGate() }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { runCatching { tone?.release() } } }
    // null = waiting, true = read, false = refused: colours the frame for a moment.
    var flash by remember { mutableStateOf<Boolean?>(null) }
    val signal = u.scanSignal
    LaunchedEffect(signal?.token) {
        if (signal != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (UiPrefs.scanSound) runCatching {
                tone?.startTone(if (signal.ok) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 150)
            }
            flash = signal.ok
            delay(700)
            flash = null
            model.consumeScanSignal(signal.token)
        }
    }
    // null = closed; otherwise the name being typed for "Segna come prestito".
    var lendTo by remember(u.burstItemId) { mutableStateOf<String?>(null) }
    val lendName = lendTo
    if (lendName != null && item != null) AlertDialog(onDismissRequest = { lendTo = null },
        title = { Text("Prestito · ${item.name}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Input(lendName, { lendTo = it }, "Chi lo prende") },
        confirmButton = { Button(enabled = lendName.isNotBlank() && !u.busy, onClick = { model.burstLend(item.id, u.burstQty, lendName); lendTo = null }) {
            Text("Presta ${amount(u.burstQty)} ${item.unit}") } },
        dismissButton = { TextButton(onClick = { lendTo = null }) { Text("Annulla") } })
    BackHandler { model.endBurst() }
    Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (granted) BurstCamera(torch) { raw ->
                    if (gate.accept(raw, SystemClock.elapsedRealtime())) model.handleScannedCode(raw)
                } else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Per leggere le etichette serve il permesso della fotocamera.", color = Color.White, textAlign = TextAlign.Center)
                    Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Consenti la fotocamera") }
                }
                val frame = when (flash) { true -> Mint; false -> Danger; null -> Color.White.copy(alpha = .85f) }
                Box(Modifier.align(Alignment.Center).size(220.dp).border(if (flash == null) 2.dp else 5.dp, frame, RoundedCornerShape(22.dp)))
                if (flash != null) Surface(color = frame, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.align(Alignment.Center).offset(y = (-136).dp)) {
                    Text(if (flash == true) "${item?.sku ?: "Etichetta"} letto" else "Non valido",
                        color = Color(0xFF07121F), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = model::endBurst, modifier = Modifier.height(48.dp)) { Text("Fine", fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.weight(1f))
                    if (u.burstCount > 0) Surface(color = Ink, shape = RoundedCornerShape(18.dp)) {
                        Text("${u.burstCount} ${if (u.burstCount == 1) "prelievo" else "prelievi"}", color = Mint, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { UiPrefs.updateScanSound(!UiPrefs.scanSound) }, modifier = Modifier.size(48.dp)) {
                        Icon(if (UiPrefs.scanSound) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                            if (UiPrefs.scanSound) "Spegni il suono di lettura" else "Accendi il suono di lettura")
                    }
                    FilledTonalIconButton(onClick = { torch = !torch }, enabled = granted, modifier = Modifier.size(48.dp)) {
                        Icon(if (torch) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            if (torch) "Spegni la torcia" else "Accendi la torcia")
                    }
                }
            }
            Surface(color = PanelBg, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item == null) {
                        Text("Inquadra l'etichetta di un articolo", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(u.message ?: "Riscansiona la stessa etichetta per aggiungere un pezzo.",
                            color = if (u.message != null) Danger else TextSecondary, fontSize = 14.sp)
                    } else {
                        val max = item.quantity
                        val qty = u.burstQty
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(Locations.describe(place, item), color = if (place == null) Amber else Sky, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Disponibili: ${amount(max)} ${item.unit} · ${item.sku}", fontSize = 13.sp,
                                color = if (item.lowStock) Amber else TextSecondary)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalIconButton(onClick = { model.burstAdjust(false) }, enabled = qty > 1.0, modifier = Modifier.size(68.dp)) {
                                Icon(Icons.Outlined.Remove, "Uno in meno", modifier = Modifier.size(30.dp))
                            }
                            Text(amount(qty), fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { model.burstAdjust(true) }, enabled = qty + 1 <= max, modifier = Modifier.size(68.dp)) {
                                Icon(Icons.Outlined.Add, "Uno in più", modifier = Modifier.size(30.dp))
                            }
                        }
                        if (max <= 0.0) Text("Esaurito: salta questo articolo.", color = Danger, fontSize = 13.sp)
                        Button(onClick = { model.burstPick(item.id, qty) }, enabled = !u.busy && qty > 0.0 && qty <= max,
                            modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
                            Text("Preleva ${amount(qty)} ${item.unit}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = model::burstSkip, enabled = !u.busy, modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp)) { Text("Salta") }
                            OutlinedButton(onClick = { lendTo = "" }, enabled = !u.busy && qty > 0.0 && qty <= max, modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(16.dp)) { Text("Segna come prestito", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    u.movementUndo?.let { undo ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ultimo: ${undo.label}", color = TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = model::undoMovement, enabled = !u.busy) { Text("Annulla", color = Mint, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

/** Camera preview that keeps reading QR codes; it follows the activity so the camera is released in the background. */
@Composable private fun BurstCamera(torch: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val current by rememberUpdatedState(onCode)
    val view = remember {
        BarcodeView(context).apply {
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult) { result.text?.let { current(it) } }
                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
            })
        }
    }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.resume()
                Lifecycle.Event.ON_PAUSE -> view.pause()
                else -> Unit
            }
        }
        if (lifecycle != null) lifecycle.addObserver(observer) else view.resume()
        onDispose { lifecycle?.removeObserver(observer); view.pause() }
    }
    LaunchedEffect(torch) { runCatching { view.setTorch(torch) } }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
}
