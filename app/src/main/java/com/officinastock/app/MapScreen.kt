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

private fun screenToMap(point: Offset, widthPx: Float, heightPx: Float, zoom: Float, panX: Float, panY: Float): Offset {
    val localX = ((point.x - panX) / zoom).coerceIn(0f, widthPx)
    val localY = ((point.y - panY) / zoom).coerceIn(0f, heightPx)
    return Offset((localX / widthPx * 100f).coerceIn(0f, 100f), (localY / heightPx * 100f).coerceIn(0f, 100f))
}
private fun mapToScreen(point: Offset, widthPx: Float, heightPx: Float): Offset =
    Offset(widthPx * point.x / 100f, heightPx * point.y / 100f)


@Composable fun NativeMapScreen(model:StockViewModel,onAr:()->Unit) {
    val u=model.ui
    var showCreate by remember{mutableStateOf(false)}
    var selectedId by remember{mutableStateOf<Long?>(null)}
    var wallMode by remember{mutableStateOf(false)}
    var draftWallStart by remember{mutableStateOf<Offset?>(null)}
    var draftWallEnd by remember{mutableStateOf<Offset?>(null)}
    var showLabels by remember{mutableStateOf(true)}
    var showGrid by remember{mutableStateOf(true)}
    var zoom by remember{mutableFloatStateOf(1f)}
    var panX by remember{mutableFloatStateOf(0f)}
    var panY by remember{mutableFloatStateOf(0f)}
    val manualWalls = remember(u.walls) { u.walls.filter { it.source == "manual" } }
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
                OutlinedButton(onClick=onAr,modifier=Modifier.weight(1f)) {
                    Icon(Icons.Outlined.CameraAlt,null,modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Scansione AR",fontSize=12.sp)
                }
                Button(onClick={wallMode=!wallMode;draftWallStart=null;draftWallEnd=null},modifier=Modifier.weight(1f),
                    colors=ButtonDefaults.buttonColors(containerColor=if(wallMode) Amber.copy(alpha=.22f) else Surface2,contentColor=if(wallMode) Amber else TextPrimary)) {
                    Icon(Icons.Outlined.Edit,null,modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if(wallMode)"Termina disegno" else "Disegno manuale",fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(color=Surface2.copy(alpha=.94f),shape=RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if(wallMode)"Modalità disegno attiva" else "Modalità selezione e spostamento",color=if(wallMode)Amber else Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text(if(wallMode)"Trascina il dito per disegnare una parete in modo naturale. Zoom e pan sono bloccati per evitare tocchi sbagliati."
                            else "Trascina le strutture sbloccate. Usa + / - per lo zoom e tocca una struttura per aprire i dettagli.",
                            color=TextSecondary,fontSize=11.sp)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0B1C2B))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density=LocalDensity.current
                val mapMaxWidth=maxWidth
                val mapMaxHeight=maxHeight
                val w=with(density){mapMaxWidth.toPx()}
                val h=with(density){mapMaxHeight.toPx()}
                val gestureModifier = if (wallMode) {
                    Modifier.pointerInput(w,h,zoom,panX,panY,wallMode) {
                        detectDragGestures(
                            onDragStart={ start ->
                                val mapped=screenToMap(start,w,h,zoom,panX,panY)
                                draftWallStart=mapped
                                draftWallEnd=mapped
                                selectedId=null
                            },
                            onDrag={ change, _ ->
                                change.consume()
                                draftWallEnd=screenToMap(change.position,w,h,zoom,panX,panY)
                            },
                            onDragEnd={
                                val a=draftWallStart
                                val b=draftWallEnd
                                if(a!=null && b!=null && (a-b).getDistance() >= 2.2f) {
                                    model.addWall(floatArrayOf(a.x,a.y,b.x,b.y))
                                }
                                draftWallStart=null
                                draftWallEnd=null
                            },
                            onDragCancel={ draftWallStart=null; draftWallEnd=null }
                        )
                    }
                } else {
                    Modifier.pointerInput(w,h) {
                        detectTransformGestures { _,pan,magnify,_ ->
                            zoom=(zoom*magnify).coerceIn(1f,3.5f)
                            panX=(panX+pan.x).coerceIn(-w/2,w/2)
                            panY=(panY+pan.y).coerceIn(-h/2,h/2)
                        }
                    }
                }
                Box(Modifier.fillMaxSize().then(gestureModifier)) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX=zoom;scaleY=zoom;translationX=panX;translationY=panY
                    }) {
                        Canvas(Modifier.fillMaxSize().pointerInput(wallMode) {
                            if(!wallMode) detectTapGestures(onTap={ _ -> selectedId=null })
                        }) {
                            if(showGrid) {
                                for(n in 1..9) {
                                    val px=size.width*n/10f
                                    val py=size.height*n/10f
                                    drawLine(Color.White.copy(alpha=.045f),Offset(px,0f),Offset(px,size.height),1.dp.toPx())
                                    drawLine(Color.White.copy(alpha=.045f),Offset(0f,py),Offset(size.width,py),1.dp.toPx())
                                }
                            }
                            val lines=u.walls
                            lines.forEach { wall ->
                                drawLine(if(wall.source=="ar")Mint else Sky,
                                    Offset(size.width*wall.x1/100f,size.height*wall.y1/100f),
                                    Offset(size.width*wall.x2/100f,size.height*wall.y2/100f),
                                    strokeWidth=if(wall.source=="ar") 4.dp.toPx() else 5.dp.toPx(),cap=StrokeCap.Round)
                            }
                            val a = draftWallStart
                            val b = draftWallEnd
                            if(a!=null && b!=null) {
                                val pa=mapToScreen(a,size.width,size.height)
                                val pb=mapToScreen(b,size.width,size.height)
                                drawLine(Amber,pa,pb,strokeWidth=4.dp.toPx(),cap=StrokeCap.Round)
                                drawCircle(color=Amber,radius=7.dp.toPx(),center=pa)
                                drawCircle(color=Amber,radius=7.dp.toPx(),center=pb)
                            }
                        }
                        if(!wallMode) u.placements.forEach { p ->
                            MapPin(p=p,items=u.items.count{it.placementId==p.id},
                                labelVisible=showLabels,maxWidth=mapMaxWidth,maxHeight=mapMaxHeight,
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
                    Text(if(wallMode)"DISEGNO PARETI · TRASCINA" else "MAPPA 2D · LIVE",modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),color=if(wallMode)Amber else Mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
                }
                Surface(color=Surface2.copy(alpha=.95f),shape=RoundedCornerShape(12.dp)) {
                    Text("Manuali ${manualWalls.size} · AR ${u.walls.size - manualWalls.size}",modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp),color=TextSecondary,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                FilledTonalIconButton(onClick={zoom=(zoom*1.2f).coerceAtMost(3.5f)}, enabled=!wallMode) {Icon(Icons.Outlined.Add,null)}
                FilledTonalIconButton(onClick={zoom=(zoom/1.2f).coerceAtLeast(1f)}, enabled=!wallMode) {Icon(Icons.Outlined.Remove,null)}
                FilledTonalIconButton(onClick={zoom=1f;panX=0f;panY=0f}) {Icon(Icons.Outlined.CenterFocusStrong,null)}
            }
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Surface(color=Surface2.copy(alpha=.94f),shape=RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(Mint,RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(5.dp));Text("AR",color=TextSecondary,fontSize=10.sp)
                        Spacer(Modifier.width(10.dp));Box(Modifier.size(9.dp).background(Sky,RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(5.dp));Text("Manuale",color=TextSecondary,fontSize=10.sp)
                        if(wallMode) {
                            Spacer(Modifier.width(10.dp));Box(Modifier.size(9.dp).background(Amber,RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(5.dp));Text("Anteprima",color=TextSecondary,fontSize=10.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(showGrid,onCheckedChange={showGrid=it},modifier=Modifier.size(35.dp))
                Text("Griglia",fontSize=12.sp)
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(showLabels,onCheckedChange={showLabels=it},modifier=Modifier.size(35.dp),enabled=!wallMode)
                Text("Etichette",fontSize=12.sp,color=if(wallMode) TextSecondary else TextPrimary)
            }
            Text("${(zoom*100).roundToInt()}%",fontSize=12.sp,color=Mint)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.End) {
            if(draftWallStart != null) {
                TextButton(onClick={draftWallStart=null;draftWallEnd=null}) {
                    Icon(Icons.Outlined.Close,null,modifier=Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Annulla tratto",fontSize=12.sp)
                }
            }
            if(manualWalls.isNotEmpty()) {
                TextButton(onClick={model.deleteWall(manualWalls.last().id)}) {
                    Icon(Icons.Outlined.Undo,null,modifier=Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Annulla ultima manuale",fontSize=12.sp)
                }
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
                    dragX=(dragX+amount.x/(widthPx*zoom)*100f).coerceIn(0f,100f)
                    dragY=(dragY+amount.y/(heightPx*zoom)*100f).coerceIn(0f,100f)
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
