package com.officinastock.app

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import java.util.Locale

// 4.19.1: moved out of MapScreen.kt without changes — the dialogs and small controls of the map.

@Composable internal fun SmallFloatingControl(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,enabled:Boolean=true,onClick:()->Unit){
    Surface(shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.97f),border=BorderStroke(1.dp,Hairline.copy(alpha=.1f))){
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(38.dp)){Icon(icon,description,tint=if(enabled)TextPrimary else TextSecondary.copy(alpha=.4f),modifier=Modifier.size(18.dp))}
    }
}

/** Compact −/+ control; [none] shows "—" for value 0 when the field is optional. */
@Composable fun Stepper(label:String,value:Int,range:IntRange,none:Boolean=false,onChange:(Int)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=12.sp,color=TextSecondary,modifier=Modifier.weight(1f))
        IconButton(onClick={onChange((value-1).coerceIn(range))},enabled=value>range.first) {Icon(Icons.Outlined.Remove,"Diminuisci $label")}
        // 4.19.3: "Non indicato" instead of a dash, which looked like a broken field.
        if(none&&value==0) Text("Non indicato",fontSize=12.sp,color=TextSecondary,modifier=Modifier.widthIn(min=28.dp))
        else Text(value.toString(),fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.widthIn(min=28.dp))
        IconButton(onClick={onChange((value+1).coerceIn(range))},enabled=value<range.last) {Icon(Icons.Outlined.Add,"Aumenta $label")}
    }
}

@Composable internal fun MapDimensionDialog(currentW:Float,currentH:Float,onClose:()->Unit,onSave:(Float,Float)->Unit){
    var w by remember { mutableStateOf("${currentW}") }
    var h by remember { mutableStateOf("${currentH}") }
    val parsedW=w.replace(',','.').toFloatOrNull()
    val parsedH=h.replace(',','.').toFloatOrNull()
    AlertDialog(onDismissRequest=onClose,title={Text("Dimensioni della pianta")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Imposta le dimensioni reali del garage o del magazzino. Modificarle cambia il confine della pianta, senza riscalare gli oggetti esistenti.",fontSize=12.sp,color=TextSecondary)
            Input(w,{w=it},"Larghezza (metri)",number=true)
            Input(h,{h=it},"Profondità (metri)",number=true)
        }
    },confirmButton={TextButton(onClick={onSave(parsedW!!,parsedH!!)},enabled=parsedW!=null&&parsedW in 2f..150f&&parsedH!=null&&parsedH in 2f..150f){Text("Applica")}},dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable internal fun CreatePlacementDialog(onCancel:()->Unit,onCreate:(String,String)->Unit){
    var kind by remember{mutableStateOf("Scaffale")};var label by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onCancel,title={Text("Nuova struttura")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Appare al centro di ciò che stai guardando, con dimensioni modificabili. Trascinala prima di bloccarla.",color=TextSecondary,fontSize=12.sp)
            Text("CONTENGONO ARTICOLI",fontSize=10.sp,color=Mint,fontWeight=FontWeight.Bold)
            PlacementKinds.storage.chunked(3).forEach{ group ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){group.forEach{v ->
                    FilterChip(selected=kind==v,onClick={kind=v},label={Text(v,fontSize=11.sp)})
                }}
            }
            Text("RIFERIMENTI IN PIANTA",fontSize=10.sp,color=Sky,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){PlacementKinds.landmarks.forEach{v ->
                FilterChip(selected=kind==v,onClick={kind=v},label={Text(v,fontSize=11.sp)})
            }}
            OutlinedTextField(value=label,onValueChange={label=it.take(80)},label={Text(if(kind in PlacementKinds.landmarks)"Nome (es. Ingresso)" else "Nome / ubicazione")},singleLine=true,modifier=Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={onCreate(kind,label.trim())},enabled=label.isNotBlank()){Text("Crea")}},dismissButton={TextButton(onClick=onCancel){Text("Annulla")}})
}

@Composable internal fun PlacementRowDialog(p:Placement,onClose:()->Unit,onCreate:(Int,Float,Boolean)->Unit){
    var count by remember{mutableIntStateOf(3)}
    var gap by remember{mutableStateOf("1")}
    var vertical by remember{mutableStateOf(false)}
    val gapValue=gap.replace(',','.').toFloatOrNull()
    var preview=p.label
    val names=(1..count).map{ preview=PlanGeometry.nextLabel(preview);preview }
    AlertDialog(onDismissRequest=onClose,title={Text("Crea in fila")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Copie di «${p.label}» con stessa forma e ripiani, una dopo l'altra.",fontSize=12.sp,color=TextSecondary)
            Stepper("Copie",count,1..50){count=it}
            Input(gap,{gap=it},"Distanza tra le strutture (m)",number=true)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=!vertical,onClick={vertical=false},label={Text("Verso destra")})
                FilterChip(selected=vertical,onClick={vertical=true},label={Text("Verso il basso")})
            }
            Text("Nomi: ${names.take(4).joinToString()}${if(names.size>4)" … ${names.last()}" else ""}",fontSize=11.sp,color=TextSecondary)
        }
    },confirmButton={TextButton(onClick={onCreate(count,gapValue!!,vertical)},enabled=gapValue!=null&&gapValue in 0f..20f){Text("Crea ${count}")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable internal fun WallLengthDialog(wall:Wall,onClose:()->Unit,onSave:(Float)->Unit){
    var value by remember{mutableStateOf(String.format(Locale.ITALY,"%.2f",PlanGeometry.wallLength(wall)))}
    val parsed=value.replace(',','.').toFloatOrNull()
    AlertDialog(onDismissRequest=onClose,title={Text("Lunghezza parete")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Il punto iniziale e la direzione restano invariati.",fontSize=12.sp,color=TextSecondary)
            Input(value,{value=it},"Lunghezza (metri)",number=true)
        }
    },confirmButton={TextButton(onClick={onSave(parsed!!)},enabled=parsed!=null&&parsed in .2f..150f){Text("Applica")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

/** Level/position choice; 0 = not specified. */
@Composable fun PositionDialog(p:Placement,title:String,onClose:()->Unit,initialLevel:Int?=null,initialSlot:Int?=null,onConfirm:(Int?,Int?)->Unit){
    // Opens on the current shelf/position when the item is already in this structure.
    var level by remember(p.id){mutableIntStateOf((initialLevel ?: 0).coerceIn(0,p.levels))}
    var slot by remember(p.id){mutableIntStateOf((initialSlot ?: 0).coerceIn(0,p.slots))}
    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(p.label,fontWeight=FontWeight.Bold)
            if(p.levels>1) Stepper("Ripiano",level,0..p.levels,none=true){level=it}
            if(p.slots>1) Stepper("Posizione",slot,0..p.slots,none=true){slot=it}
            Text("«—» = solo la struttura, senza ripiano o posizione precisa.",fontSize=11.sp,color=TextSecondary)
        }
    },confirmButton={TextButton(onClick={onConfirm(level.takeIf{it>0},slot.takeIf{it>0})}){Text("Conferma")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable internal fun PlacementDetailDialog(p:Placement,items:List<StockItem>,model:StockViewModel,onQr:()->Unit,onDismiss:()->Unit){
    var articleQuery by remember(p.id){mutableStateOf("")}
    var askDelete by remember {mutableStateOf(false)}
    var name by remember(p.id,p.label){mutableStateOf(p.label)}
    var w by remember(p.id,p.width){mutableStateOf("${p.width}")}
    var d by remember(p.id,p.depth){mutableStateOf("${p.depth}")}
    var angle by remember(p.id,p.rotation){mutableFloatStateOf(p.rotation)}
    var levels by remember(p.id,p.levels){mutableIntStateOf(p.levels)}
    var slots by remember(p.id,p.slots){mutableIntStateOf(p.slots)}
    var chosen by remember(p.id){mutableStateOf(setOf<Long>())}
    var assignLevel by remember(p.id){mutableIntStateOf(0)}
    var assignSlot by remember(p.id){mutableIntStateOf(0)}
    val wVal=w.replace(',','.').toFloatOrNull()
    val dVal=d.replace(',','.').toFloatOrNull()
    val valid=wVal!=null&&wVal in .2f..15f&&dVal!=null&&dVal in .2f..15f
    fun matches(item:StockItem)=item.name.contains(articleQuery,true)||item.sku.contains(articleQuery,true)
    val inside=items.filter{it.placementId==p.id && matches(it)}
        .sortedWith(compareBy({it.shelfLevel ?: 0},{it.shelfSlot ?: 0},{it.name.lowercase(Locale.ITALY)}))
    val free=items.filter{it.placementId==null && matches(it)}
    // 4.19.3: two tabs. "Contenuto" (what people need while exploring) opens first; size, shelves and deletion are in "Modifica".
    var editing by remember(p.id){mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(p.label.ifBlank{p.kind},maxLines=1,overflow=TextOverflow.Ellipsis)},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=!editing,onClick={editing=false},label={Text(if(p.holdsStock)"Contenuto" else "Informazioni")})
                FilterChip(selected=editing,onClick={editing=true},label={Text("Modifica")},
                    leadingIcon={Icon(Icons.Outlined.Edit,null,modifier=Modifier.size(16.dp))})
            }
            if(!editing) {
            if(!p.holdsStock) {
                Text("${p.kind}: elemento di riferimento, non contiene articoli.",fontSize=12.sp,color=TextSecondary)
            } else {
                Text("ARTICOLI QUI · ${items.count{it.placementId==p.id}}",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp,letterSpacing=1.sp)
                if(items.count{it.placementId==p.id}>6 || free.size>6) Input(articleQuery,{articleQuery=it},"Cerca articolo o codice")
                if(inside.size>100 || free.size>100) Text("Mostrati i primi 100 risultati per elenco. Affina la ricerca.",fontSize=12.sp)
                if(inside.isEmpty())Text("Nessun articolo in questa struttura",color=TextSecondary,fontSize=12.sp)
                inside.take(100).forEach{item ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(item.name,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(listOf(item.sku,"${amount(item.quantity)} ${item.unit}",Locations.code(item.shelfLevel,item.shelfSlot))
                                .filter{it.isNotBlank()}.joinToString(" · "),fontSize=11.sp,color=if(item.lowStock)Amber else TextSecondary)
                        }
                        IconButton(onClick={model.assignItem(item.id,null)}){Icon(Icons.Outlined.RemoveCircleOutline,"Scollega",tint=Amber)}
                    }
                }
                if(free.isNotEmpty()){
                    HorizontalDivider()
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("METTI QUI GLI ARTICOLI SENZA POSTO",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.weight(1f))
                        TextButton(onClick={chosen=if(chosen.size==free.take(100).size) emptySet() else free.take(100).map{it.id}.toSet()}) {
                            Text(if(chosen.isNotEmpty()&&chosen.size==free.take(100).size)"Nessuno" else "Tutti",fontSize=11.sp)
                        }
                    }
                    free.take(100).forEach{item->
                        Row(Modifier.fillMaxWidth().clickable{chosen=if(item.id in chosen) chosen-item.id else chosen+item.id},verticalAlignment=Alignment.CenterVertically){
                            Checkbox(checked=item.id in chosen,onCheckedChange={on->chosen=if(on) chosen+item.id else chosen-item.id})
                            Text("${item.name} · ${item.sku}",fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    if(chosen.isNotEmpty()) {
                        if(p.levels>1) Stepper("Ripiano",assignLevel,0..p.levels,none=true){assignLevel=it}
                        if(p.slots>1) Stepper("Posizione",assignSlot,0..p.slots,none=true){assignSlot=it}
                    }
                    Button(onClick={
                        model.assignItems(chosen.toList(),p.id,assignLevel.takeIf{it>0},assignSlot.takeIf{it>0});chosen=emptySet()
                    },enabled=chosen.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                        Text(when{chosen.isEmpty()->"Scegli gli articoli da mettere qui";chosen.size==1->"Metti qui 1 articolo";else->"Metti qui ${chosen.size} articoli"})
                    }
                }
                OutlinedButton(onClick=onQr,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCode2,null);Spacer(Modifier.width(8.dp));Text("Etichetta QR della struttura")
                }
            }
            } else {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name,{name=it.take(80)},label={Text("Nome")},singleLine=true,modifier=Modifier.weight(1f))
                TextButton(onClick={model.renamePlacement(p.id,name)},enabled=name.isNotBlank()&&name.trim()!=p.label){Text("Rinomina")}
            }
            Surface(color=Surface2,shape=RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(p.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,null,tint=if(p.locked)Amber else Mint)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Blocca la posizione",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        Text(if(p.locked)"Bloccata: non si sposta trascinandola" else "Sbloccata: si può trascinare sulla pianta",fontSize=11.sp,color=TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked=p.locked,onCheckedChange={model.lockPlacement(p.id,it)})
                }
            }
            Text("INGOMBRO IN PIANTA",fontWeight=FontWeight.Bold,color=Mint,fontSize=10.sp,letterSpacing=1.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Input(w,{w=it},"Larghezza m",Modifier.weight(1f),number=true)
                Input(d,{d=it},"Profondità m",Modifier.weight(1f),number=true)
            }
            Text("Rotazione ${angle.toInt()}°",fontSize=12.sp,color=TextSecondary)
            Slider(value=angle,onValueChange={angle=it},valueRange=0f..270f,steps=2)
            val changed=wVal!=p.width||dVal!=p.depth||angle!=p.rotation
            if(changed) Button(onClick={model.resizePlacement(p.id,wVal!!,dVal!!,angle)},enabled=valid,modifier=Modifier.fillMaxWidth()) {Text("Aggiorna dimensioni e rotazione")}
            if(p.holdsStock) {
                Text("RIPIANI E POSIZIONI",fontWeight=FontWeight.Bold,color=Mint,fontSize=10.sp,letterSpacing=1.sp)
                Stepper("Ripiani (dal basso)",levels,1..PlacementKinds.MAX_LEVELS){levels=it}
                Stepper("Posizioni per ripiano",slots,1..PlacementKinds.MAX_SLOTS){slots=it}
                if(levels!=p.levels||slots!=p.slots) Button(onClick={model.updatePlacementLayout(p.id,levels,slots)},modifier=Modifier.fillMaxWidth()) {
                    Text("Applica ${levels} × ${slots}")
                }
            }
            // 4.19.3: deletion well apart from the other buttons.
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            TextButton(onClick={askDelete=true}) {Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(5.dp));Text("Elimina struttura",color=Danger)}
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
    if(askDelete)AlertDialog(onDismissRequest={askDelete=false},title={Text("Eliminare ${p.label}?")},text={Text("Gli articoli rimangono nell'inventario senza ubicazione. Puoi annullare con il pulsante ↶ sulla mappa.")},confirmButton={TextButton(onClick={model.deletePlacement(p.id);askDelete=false;onDismiss()}){Text("Elimina",color=Danger)}},dismissButton={TextButton(onClick={askDelete=false}){Text("Annulla")}})
}
