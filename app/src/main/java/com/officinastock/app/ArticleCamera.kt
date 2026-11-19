package com.officinastock.app

import android.net.Uri
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 4.21: the article camera. The preview shows the exact frame of the photo in the article card
 * ([PhotoFiles.CARD_ASPECT]); after the shot the picture is cut to that frame, so what is inside
 * the frame is what the card shows and nobody has to take it again to centre it.
 * [onFallback] opens the phone's own camera when this one cannot start.
 */
@Composable
fun ArticleCameraDialog(onCaptured: (Uri) -> Unit, onFallback: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val ratio = remember {
        ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setResolutionSelector(ratio).build()
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var shooting by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            runCatching {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().setResolutionSelector(ratio).build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                // Same view port for preview and photo: the saved picture covers exactly what the screen showed.
                val group = previewView.viewPort?.let { port ->
                    UseCaseGroup.Builder().setViewPort(port).addUseCase(preview).addUseCase(capture).build() }
                if (group != null) p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                else p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }.onFailure {
                Toast.makeText(context, "Fotocamera con riquadro non disponibile: apro quella del telefono", Toast.LENGTH_LONG).show()
                onFallback()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { provider?.unbindAll() } }
    }

    fun shoot() {
        if (shooting || viewSize == IntSize.Zero) return
        shooting = true
        val (file, uri) = runCatching { PhotoFiles.captureFile(context) }.getOrElse {
            shooting = false
            Toast.makeText(context, "Impossibile preparare la foto", Toast.LENGTH_LONG).show(); return
        }
        previewView.display?.rotation?.let { capture.targetRotation = it }
        val frame = PhotoFiles.frameSize(viewSize.width.toFloat(), viewSize.height.toFloat())
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val cut = withContext(Dispatchers.IO) {
                            runCatching { PhotoFiles.cropToFrame(file, viewSize.width.toFloat(), viewSize.height.toFloat(), frame.first, frame.second) }
                        }
                        shooting = false
                        if (cut.isSuccess) onCaptured(uri) else {
                            file.delete()
                            Toast.makeText(context, "Foto non salvata, riprova", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    shooting = false; file.delete()
                    Toast.makeText(context, "Scatto non riuscito, riprova", Toast.LENGTH_LONG).show()
                }
            })
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
                // Everything outside the frame is darkened: inside is exactly the photo of the card.
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val (fw, fh) = PhotoFiles.frameSize(size.width, size.height)
                    val topLeft = Offset((size.width - fw) / 2f, (size.height - fh) / 2f)
                    drawRect(Color.Black.copy(alpha = .62f))
                    drawRoundRect(Color.Transparent, topLeft, Size(fw, fh), CornerRadius(14.dp.toPx()), blendMode = BlendMode.Clear)
                    drawRoundRect(Mint, topLeft, Size(fw, fh), CornerRadius(14.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
            Text("Inquadra l'articolo dentro il riquadro: è la foto che vedrai nella scheda",
                color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 24.dp, end = 24.dp))
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Chiudi fotocamera", tint = Color.White)
            }
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp).size(76.dp)
                .border(4.dp, Color.White, CircleShape).padding(8.dp)) {
                Button(onClick = { shoot() }, enabled = !shooting, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, disabledContainerColor = Color.Gray),
                    modifier = Modifier.fillMaxSize()) {}
            }
            if (shooting) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Mint)
        }
    }
}
