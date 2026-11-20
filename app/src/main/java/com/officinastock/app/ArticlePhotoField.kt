package com.officinastock.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In creazione mantiene una foto provvisoria. Su un articolo esistente la sostituzione va confermata. */
@Composable
fun ArticlePhotoField(
    savedPhoto: String?, selectedUri: String?, onSelected: (String?) -> Unit,
    onApply: ((String) -> Unit)?, onRemoveSaved: (() -> Unit)?,busy: Boolean=false
) {
    val context=LocalContext.current
    val editingExisting=(onApply!=null)
    var stagedUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pending=if(editingExisting) stagedUri else selectedUri
    var newCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var appliedOld by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedPhoto) {
        if (appliedOld != null && appliedOld != savedPhoto) {
            stagedUri=null
            appliedOld=null
        }
    }
    fun stage(uri: String?) {
        if(pending!=uri && pending!=null) PhotoFiles.removeCapturedTemp(context,Uri.parse(pending))
        if(editingExisting) stagedUri=uri else onSelected(uri)
    }
    val capture=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri=newCaptureUri
        if(ok && uri!=null) stage(uri)
        else if(uri!=null) PhotoFiles.removeCapturedTemp(context,Uri.parse(uri))
        newCaptureUri=null
    }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if(uri!=null) stage(uri.toString())
    }
    // 4.21: the app's own camera, with the frame of the card photo; the phone's camera only as a fallback.
    var framedCamera by remember { mutableStateOf(false) }
    fun launchCamera() { framedCamera=true }
    fun launchSystemCamera() {
        runCatching {
            val uri=PhotoFiles.captureUri(context)
            newCaptureUri=uri.toString()
            capture.launch(uri)
        }.onFailure {
            newCaptureUri?.let{uri->PhotoFiles.removeCapturedTemp(context,Uri.parse(uri))}
            newCaptureUri=null
            Toast.makeText(context,"Fotocamera non disponibile",Toast.LENGTH_LONG).show()
        }
    }
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) launchCamera()
        else Toast.makeText(context,"Permesso fotocamera non concesso",Toast.LENGTH_SHORT).show()
    }
    if(framedCamera) ArticleCameraDialog(
        onCaptured={ uri -> framedCamera=false; stage(uri.toString()) },
        onFallback={ framedCamera=false; launchSystemCamera() },
        onClose={ framedCamera=false })
    val shownPhoto=produceState<android.graphics.Bitmap?>(initialValue=null,pending,savedPhoto) {
        value=withContext(Dispatchers.IO) { PhotoFiles.preview(context,pending,savedPhoto) }
    }.value

    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("FOTO ARTICOLO",color=Mint,fontWeight=FontWeight.Bold,fontSize=11.sp)
        if(shownPhoto!=null) {
            Image(shownPhoto.asImageBitmap(),contentDescription="Foto dell'articolo",
                modifier=Modifier.fillMaxWidth().aspectRatio(PhotoFiles.CARD_ASPECT).clip(RoundedCornerShape(14.dp)),
                contentScale=ContentScale.Crop)
        } else {
            Surface(color=Surface2,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AddAPhoto,contentDescription=null,tint=TextSecondary,modifier=Modifier.size(32.dp))
                    Text("Nessuna foto",fontSize=12.sp,color=TextSecondary)
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedButton(onClick={
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
                    launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },enabled=!busy,modifier=Modifier.weight(1f)) {
                Icon(Icons.Outlined.CameraAlt,null,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text(if(shownPhoto!=null)"Rifai foto" else "Scatta foto",fontSize=12.sp)
            }
            OutlinedButton(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                enabled=!busy,modifier=Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoLibrary,null,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Galleria",fontSize=12.sp)
            }
        }
        if(pending!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={stage(null)},enabled=!busy) { Text(if(editingExisting)"Scarta nuovo scatto" else "Rimuovi foto") }
                if(editingExisting) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick={
                        appliedOld=savedPhoto ?: "__NO_OLD_PHOTO__"
                        onApply?.invoke(pending)
                    },enabled=!busy) { Text("Usa questa foto") }
                }
            }
            Text(if(editingExisting) "La foto precedente resta salvata finché non confermi la sostituzione."
                else "La foto viene salvata insieme all'articolo.",fontSize=11.sp,color=TextSecondary)
        } else if(editingExisting && savedPhoto!=null) {
            TextButton(onClick={confirmDelete=true},enabled=!busy) {
                Icon(Icons.Outlined.DeleteOutline,null,tint=Danger,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Elimina foto",color=Danger)
            }
        }
    }
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},
        title={Text("Eliminare questa foto?")},
        text={Text("La foto sarà rimossa dall'articolo. Nome, codice, giacenza e movimenti resteranno invariati.")},
        confirmButton={TextButton(onClick={onRemoveSaved?.invoke();confirmDelete=false}){Text("Elimina foto",color=Danger)}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Annulla")}})
}

@Composable
fun ArticleThumbnail(filename: String, modifier: Modifier = Modifier.size(46.dp), edge: Int = 140) {
    val context=LocalContext.current
    val bitmap=produceState<android.graphics.Bitmap?>(initialValue=null,filename,edge) {
        value=withContext(Dispatchers.IO) { PhotoFiles.preview(context,null,filename,edge) }
    }.value
    if(bitmap!=null) Image(bitmap.asImageBitmap(),contentDescription="Foto articolo",
        modifier=modifier.clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
    else Icon(Icons.Outlined.Inventory2,null,tint=Mint,modifier=modifier)
}
