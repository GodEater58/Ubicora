package com.officinastock.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable fun NativeMapScreen(model:StockViewModel,onAr:()->Unit) {
    val u=model.ui
    var showCreate by remember{mutableStateOf(false)}
    var selectedId by remember{mutableStateOf<Long?>(null)}
    var wallMode by remember{mutableStateOf(false)}
    var firstWall by remember{mutableStateOf<Offset?>(null)}
    var showLabels by remember{mutableStateOf(true)}
    var showGrid by remember{mutableStateOf(true)}
    var zoom by remember{mutableFloatStateOf(1f)}
    var panX by remember{mutableFloatStateOf(0f)}
    var panY by remember{mutableFloatStateOf(0f)}
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start=18.dp,end=18.dp,top=16.dp,bottom=8.dp)) {
            Text("PLANIMETRIA OPERATIVA",color=Mint,fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.8.sp)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment=Alignment.CenterVertically) {
                SectionTitle("Mappa magazzino","${u.walls.size} pareti · ${u.placements.size} strutture",Modifier.weight(1f))
                FilledIconButton(onClick={showCreate=true}){Icon(Icons.Outlined.Add,null)}
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onAr,modifier=Modifier.weight(1f)) {Icon(Icons.Outlined.CameraAlt,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("Scansione AR",fontSize=12.sp)}
                OutlinedButton(onClick={wallMode=!wallMode;firstWall=null},modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit,null,modifier=Modifier.size(17.dp));Spacer(Modifier.width(5.dp))
                    Text(if(wallMode)"Fine disegno" else "Disegna pareti",fontSize=12.sp)
                }
            }
            Text(if(wallMode)"Tocca due punti sulla mappa per creare una parete. Ripeti per aggiungerne altre."
                else "Trascina le strutture sbloccate. Tocca una struttura per vedere e assegnare gli articoli.",
                color=TextSecondary,fontSize=11.sp,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0B1C2B))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density=LocalDensity.current
                val w=with(density){maxWidth.toPx()}
                val h=with(density){maxHeight.toPx()}
                Box(Modifier.fillMaxSize()
                    .pointerInput(w,h) { detectTransformGestures { _,pan,magnify,_ ->
                        zoom=(zoom*magnify).coerceIn(1f,3.5f)
                        panX=(panX+pan.x).coerceIn(-w/2,w/2)
                        panY=(panY+pan.y).coerceIn(-h/2,h/2)
                    } }
                ) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX=zoom;scaleY=zoom;translationX=panX;translationY=panY
                    }) {
                        Canvas(Modifier.fillMaxSize().pointerInput(wallMode,firstWall,w,h) {
                            detectTapGestures {tap ->
                                if(wallMode) {
                                    val current=Offset((tap.x/w*100f).coerceIn(0f,100f),(tap.y/h*100f).coerceIn(0f,100f))
                                    val previous=firstWall
                                    if(previous==null)firstWall=current
                                    else {
                                        if((previous-current).getDistance()>=.2f)
                                            model.addWall(floatArrayOf(previous.x,previous.y,current.x,current.y))
                                        firstWall=null
                                    }
                                } else selectedId=null
                            }
                        }) {
                            if(showGrid) {
                                for(n in 1..9) {
                                    val px=size.width*n/10f;val py=size.height*n/10f
                                    drawLine(Color.White.copy(alpha=.045f),Offset(px,0f),Offset(px,size.height),1.dp.toPx())
                                    drawLine(Color.White.copy(alpha=.045f),Offset(0f,py),Offset(size.width,py),1.dp.toPx())
                                }
                            }
                            val lines=u.walls
                            lines.forEach { wall ->
                                drawLine(if(wall.source=="ar")Mint else Sky,
                                    Offset(size.width*wall.x1/100f,size.height*wall.y1/100f),
                                    Offset(size.width*wall.x2/100f,size.height*wall.y2/100f),
                                    strokeWidth=4.dp.toPx(),cap=StrokeCap.Round)
                            }
                            firstWall?.let {p -> drawCircle(color=Amber,radius=7.dp.toPx(),center=Offset(size.width*p.x/100f,size.height*p.y/100f))}
                        }
                        if(!wallMode) u.placements.forEach { p ->
                            MapPin(p=p,items=u.items.count{it.placementId==p.id},
                                labelVisible=showLabels,maxWidth=maxWidth,maxHeight=maxHeight,
                                widthPx=w,heightPx=h,zoom=zoom,
                                selected=(p.id==selectedId),
                                onTap={selectedId=p.id},
                                onDrag={x,y->model.previewMove(p.id,x,y)},
                                onDragEnd={x,y->model.commitMove(p.id,x,y)})
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.TopStart).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Surface(color=Surface2.copy(alpha=.95f),shape=RoundedCornerShape(12.dp)) {
                    Text(if(wallMode)"DISEGNO PARETI" else "MAPPA 2D · LIVE",modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),color=if(wallMode)Amber else Mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                FilledTonalIconButton(onClick={zoom=(zoom*1.2f).coerceAtMost(3.5f)}) {Icon(Icons.Outlined.Add,null)}
                FilledTonalIconButton(onClick={zoom=(zoom/1.2f).coerceAtLeast(1f)}) {Icon(Icons.Outlined.Remove,null)}
                FilledTonalIconButton(onClick={zoom=1f;panX=0f;panY=0f}) {Icon(Icons.Outlined.CenterFocusStrong,null)}
            }
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(color=Surface2.copy(alpha=.94f),shape=RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(Mint,RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(5.dp));Text("AR",color=TextSecondary,fontSize=10.sp)
                        Spacer(Modifier.width(10.dp));Box(Modifier.size(9.dp).background(Sky,RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(5.dp));Text("Manuale",color=TextSecondary,fontSize=10.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(showGrid,onCheckedChange={showGrid=it},modifier=Modifier.size(35.dp));Text("Griglia",fontSize=12.sp)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(showLabels,onCheckedChange={showLabels=it},modifier=Modifier.size(35.dp));Text("Etichette",fontSize=12.sp)
            }
            Text("${(zoom*100).roundToInt()}%",fontSize=12.sp,color=Mint)
        }
        if(u.walls.isNotEmpty()) {
            TextButton(onClick={model.deleteWall(u.walls.last().id)},modifier=Modifier.align(Alignment.End)) {
                Icon(Icons.Outlined.Undo,null,modifier=Modifier.size(15.dp));Spacer(Modifier.width(5.dp));Text("Annulla ultima parete",fontSize=12.sp)
            }
        }
    }
    if(showCreate) CreatePlacementDialog({showCreate=false}){ kind,label ->
        model.addPlacement(kind,label,x=40f,y=40f);showCreate=false
    }
    selectedId?.let {id->
        u.placements.firstOrNull {it.id==id}?.let{p->
            PlacementDetailDialog(p,u.items,model,onDismiss={selectedId=null})
        }
    }
}

@Composable private fun MapPin(p:Placement,items:Int,labelVisible:Boolean,maxWidth:androidx.compose.ui.unit.Dp,maxHeight:androidx.compose.ui.unit.Dp,
    widthPx:Float,heightPx:Float,zoom:Float,selected:Boolean,onTap:()->Unit,onDrag:(Float,Float)->Unit,onDragEnd:(Float,Float)->Unit) {
    val borderColor=if(selected)Amber else if(p.locked)Color(0xFF556B79) else Mint
    Column(Modifier
        .offset(x=maxWidth*(p.x/100f)-46.dp,y=maxHeight*(p.y/100f)-27.dp)
        .width(92.dp)
        .pointerInput(p.id,p.locked,widthPx,heightPx,zoom){
            var dragX=p.x;var dragY=p.y
            detectDragGestures(onDragEnd={if(!p.locked)onDragEnd(dragX,dragY)}){change,amount->
                if(!p.locked){
                    change.consume()
                    dragX=(dragX+amount.x/widthPx*100f).coerceIn(0f,100f)
                    dragY=(dragY+amount.y/heightPx*100f).coerceIn(0f,100f)
                    onDrag(dragX,dragY)
                }
            }
        }
        .clickable(onClick=onTap),horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=RoundedCornerShape(13.dp),color=Surface2.copy(alpha=.98f),
            border=BorderStroke(if(selected)2.dp else 1.dp,borderColor)) {
            Row(Modifier.padding(horizontal=9.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(when(p.kind){"Scaffale"->Icons.Outlined.Storage;"Cassetto"->Icons.Outlined.Inventory;"Armadio"->Icons.Outlined.MeetingRoom;else->Icons.Outlined.Warehouse},
                    null,tint=borderColor,modifier=Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp));Text(items.toString(),fontSize=10.sp,color=TextPrimary)
            }
        }
        if(labelVisible) Text(p.label,color=TextPrimary,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.background(Ink.copy(alpha=.85f),RoundedCornerShape(6.dp)).padding(horizontal=5.dp,vertical=2.dp))
    }
}

@Composable private fun CreatePlacementDialog(onCancel:()->Unit,onCreate:(String,String)->Unit) {
    var kind by remember{mutableStateOf("Scaffale")};var label by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onCancel,title={Text("Aggiungi struttura")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Trascinala sulla planimetria e blocca la posizione quando è corretta.",color=TextSecondary,fontSize=12.sp)
            listOf("Scaffale","Armadio","Cassetto","Area","Banco").chunked(3).forEach {group->
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    group.forEach {v->FilterChip(selected=kind==v,onClick={kind=v},label={Text(v,fontSize=11.sp)})}
                }
            }
            Input(label,{label=it},"Nome (es. Scaffale A3)")
        }
    },confirmButton={TextButton(onClick={onCreate(kind,label)},enabled=label.isNotBlank()){Text("Aggiungi")}},
        dismissButton={TextButton(onClick=onCancel){Text("Annulla")}})
}

@Composable private fun PlacementDetailDialog(p:Placement,items:List<StockItem>,model:StockViewModel,onDismiss:()->Unit) {
    var confirmDelete by remember {mutableStateOf(false)}
    val inside=items.filter{it.placementId==p.id}
    val available=items.filter{it.placementId==null}
    AlertDialog(onDismissRequest=onDismiss,title={Text("${p.label} · ${p.kind}")},text={
        Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(p.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,null,tint=Mint)
                Text(if(p.locked)"Posizione bloccata" else "Trascinamento consentito",fontSize=12.sp,color=TextSecondary)
                Spacer(Modifier.weight(1f))
                Switch(checked=p.locked,onCheckedChange={model.lockPlacement(p.id,it)})
            }
            Text("CONTENUTO · ${inside.size} ARTICOLI",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
            if(inside.isEmpty()) Text("Nessun articolo assegnato.",color=TextSecondary,fontSize=12.sp)
            Column(Modifier.heightIn(max=170.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                inside.forEach {item->
                    Surface(color=Surface2,shape=RoundedCornerShape(8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                                Text("${item.sku} · ${amount(item.quantity)} ${item.unit}",color=TextSecondary,fontSize=10.sp)
                            }
                            IconButton(onClick={model.assignItem(item.id,null)},modifier=Modifier.size(33.dp)) {Icon(Icons.Outlined.RemoveCircleOutline,"Scollega",tint=Amber)}
                        }
                    }
                }
            }
            if(available.isNotEmpty()) {
                Text("ASSEGNA UN ARTICOLO",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Column(Modifier.heightIn(max=170.dp).verticalScroll(rememberScrollState())) {
                    available.take(80).forEach {it->
                        TextButton(onClick={model.assignItem(it.id,p.id)},modifier=Modifier.fillMaxWidth()) {
                            Text("+  ${it.name} (${it.sku})",maxLines=1,modifier=Modifier.weight(1f))
                        }
                    }
                }
            }
            OutlinedButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(6.dp));Text("Elimina struttura",color=Danger)
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Eliminare ${p.label}?")},
        text={Text("Gli articoli non verranno cancellati: perderanno soltanto l'ubicazione.")},
        confirmButton={TextButton(onClick={model.deletePlacement(p.id);confirmDelete=false;onDismiss()}){Text("Elimina",color=Danger)}},
        dismissButton={TextButton(onClick={confirmDelete=false}){Text("Annulla")}})
}
