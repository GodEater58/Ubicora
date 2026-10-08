package com.officinastock.app

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import java.util.Locale

private enum class EditorTool { VIEW, WALL, FURNITURE }
private val FloorBackground = Color(0xFF0C1B2A)
private val FloorFill = Color(0xFF152C3A)
private val ShelfTeal = Color(0xFF267C80)
private val CabinetBlue = Color(0xFF48668D)
private val BenchGold = Color(0xFFA47745)
private val DrawingCyan = Color(0xFF7DE8E3)

private fun numberM(value: Float):String = String.format(Locale.ITALY,"%.2f m", value)
private fun floorColor(kind: String): Color = when(kind) {
    "Armadio" -> CabinetBlue
    "Banco" -> BenchGold
    "Cassetto" -> Color(0xFF66719F)
    "Area" -> Color(0xFF456D67)
    else -> ShelfTeal
}

@Composable
fun NativeMapScreen(model:StockViewModel,onAr:()->Unit) {
    val state=model.ui
    val warehouse=state.warehouse ?: return
    var mode by remember(warehouse.id){mutableStateOf(EditorTool.VIEW)}
    var selectedId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    var wallAnchor by remember(warehouse.id){mutableStateOf<PointMetres?>(null)}
    var draftEnd by remember(warehouse.id){mutableStateOf<PointMetres?>(null)}
    var showNewObject by remember {mutableStateOf(false)}
    var showDimensions by remember {mutableStateOf(false)}
    var showGrid by remember {mutableStateOf(true)}
    var snapGrid by remember {mutableStateOf(true)}
    var orthogonal by remember {mutableStateOf(false)}
    var showLabels by remember {mutableStateOf(true)}
    var zoom by remember(warehouse.id){mutableFloatStateOf(1f)}
    var panX by remember(warehouse.id){mutableFloatStateOf(0f)}
    var panY by remember(warehouse.id){mutableFloatStateOf(0f)}
    var detailsId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    val placement=state.placements.firstOrNull{it.id==selectedId}
    val walls=state.walls
    val livePlacements by rememberUpdatedState(state.placements)
    val liveWalls by rememberUpdatedState(state.walls)
    val manualWalls=walls.filter{it.source=="manual"}
    val roomWidth=warehouse.mapWidth
    val roomHeight=warehouse.mapHeight
    val mapItems=remember(state.items){state.items.groupBy { it.placementId }}

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal=16.dp).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=RoundedCornerShape(12.dp),color=Mint.copy(alpha=.11f)) {
                    Icon(Icons.Outlined.Architecture,"Progettazione",tint=Mint,modifier=Modifier.padding(10.dp).size(23.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("STUDIO PLANIMETRICO",fontSize=9.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.Bold,color=Mint)
                    Text("${warehouse.name} · Piano 2D",fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                FilledTonalIconButton(onClick={showDimensions=true}) {Icon(Icons.Outlined.Straighten,"Dimensioni ambiente")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()) {
                listOf(
                    Triple(EditorTool.VIEW, "Esplora", Icons.Outlined.PanToolAlt),
                    Triple(EditorTool.WALL, "Pareti", Icons.Outlined.Edit),
                    Triple(EditorTool.FURNITURE, "Strutture", Icons.Outlined.GridView)
                ).forEach { (tool,title,icon) ->
                    val active=mode==tool
                    Surface(
                        modifier=Modifier.weight(1f).clickable {mode=tool;wallAnchor=null;draftEnd=null},
                        shape=RoundedCornerShape(14.dp),
                        color=if(active) Mint.copy(alpha=.15f) else Surface2,
                        border=BorderStroke(1.dp,if(active) Mint.copy(alpha=.66f) else Color.White.copy(alpha=.07f))
                    ) {
                        Row(Modifier.padding(vertical=10.dp,horizontal=5.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            Icon(icon,null,tint=if(active) Mint else TextSecondary,modifier=Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(title,color=if(active) TextPrimary else TextSecondary,fontSize=12.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal=12.dp).clipToBounds()) {
            BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(BorderStroke(1.dp,Color.White.copy(alpha=.13f)),RoundedCornerShape(24.dp)).background(FloorBackground,RoundedCornerShape(24.dp))) {
                val density=LocalDensity.current
                val w=with(density){maxWidth.toPx()}
                val h=with(density){maxHeight.toPx()}
                val camera=PlanViewport(w,h,roomWidth,roomHeight,zoom,panX,panY,with(density){34.dp.toPx()})
                val labelPx=with(density){14.sp.toPx()}
                LaunchedEffect(zoom,w,h,roomWidth,roomHeight) {
                    val adjusted=PlanViewport(w,h,roomWidth,roomHeight,zoom,panX,panY,with(density){34.dp.toPx()})
                        .clampedPan(panX,panY)
                    panX=adjusted.x;panY=adjusted.y
                }
                // One gesture owner. Unlike overlapping tap/drag handlers, this cannot steal its own wall strokes.
                Canvas(Modifier.fillMaxSize().clipToBounds().pointerInput(mode,warehouse.id,roomWidth,roomHeight) {
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        val cameraNow=PlanViewport(size.width.toFloat(),size.height.toFloat(),roomWidth,roomHeight,zoom,panX,panY,34.dp.toPx())
                        val initial=PointMetres(down.position.x,down.position.y)
                        val startWorld=cameraNow.bounded(cameraNow.toWorld(initial))
                        val hit=livePlacements.lastOrNull { PlanGeometry.contains(startWorld,it) }
                        val target=if(mode==EditorTool.FURNITURE) hit else null
                        val initialFurniture=target?.let{PointMetres(it.x,it.y)}
                        val originalPanX=panX
                        val originalPanY=panY
                        var last=initial
                        var dragged=false
                        var cancelled=false
                        val minDrag=viewConfiguration.touchSlop
                        while(true){
                            val e=awaitPointerEvent()
                            val change=e.changes.firstOrNull{it.id==down.id}
                            if(change==null){cancelled=true;break}
                            last=PointMetres(change.position.x,change.position.y)
                            if(!dragged && PlanGeometry.distance(initial,last)>minDrag) dragged=true
                            if(dragged && change.pressed){
                                if(mode==EditorTool.WALL){
                                    val snap=cameraNow.bounded(cameraNow.toWorld(last))
                                    val endpoint=if(snapGrid) PlanGeometry.snap(snap) else snap
                                    draftEnd=if(orthogonal) PlanGeometry.orthogonal(wallAnchor?:startWorld,endpoint) else endpoint
                                }else if(mode==EditorTool.FURNITURE && target!=null && !target.locked && initialFurniture!=null){
                                    val dx=(last.x-initial.x)/cameraNow.scale
                                    val dy=(last.y-initial.y)/cameraNow.scale
                                    val pos=cameraNow.bounded(PointMetres(initialFurniture.x+dx,initialFurniture.y+dy))
                                    val mapped=if(snapGrid)PlanGeometry.snap(pos) else pos
                                    model.previewMove(target.id,mapped.x,mapped.y)
                                }else if(mode!=EditorTool.WALL){
                                    val bounded=cameraNow.clampedPan(originalPanX+last.x-initial.x,originalPanY+last.y-initial.y)
                                    panX=bounded.x; panY=bounded.y
                                }
                                change.consume()
                            }
                            if(!change.pressed){break}
                        }
                        if(!cancelled){
                            if(mode==EditorTool.WALL){
                                val from=wallAnchor ?: PlanGeometry.snapToWallEndpoint(if(snapGrid)PlanGeometry.snap(startWorld) else startWorld,liveWalls)
                                val point=cameraNow.bounded(cameraNow.toWorld(last))
                                val clean=if(snapGrid) PlanGeometry.snap(point) else point
                                val finish=PlanGeometry.snapToWallEndpoint(if(orthogonal) PlanGeometry.orthogonal(from,clean) else clean,liveWalls)
                                if(dragged){
                                    if(PlanGeometry.validWall(from,finish))model.addWall(floatArrayOf(from.x,from.y,finish.x,finish.y))
                                    wallAnchor=finish
                                }else if(wallAnchor==null){
                                    wallAnchor=finish
                                }else if(PlanGeometry.validWall(from,finish)){
                                    model.addWall(floatArrayOf(from.x,from.y,finish.x,finish.y))
                                    wallAnchor=finish
                                }
                                draftEnd=null
                            }else if(mode==EditorTool.FURNITURE){
                                if(target!=null){
                                    selectedId=target.id
                                    if(dragged && !target.locked){
                                        val updated=model.ui.placements.firstOrNull{it.id==target.id}
                                        if(updated!=null)model.commitMove(updated.id,updated.x,updated.y)
                                    }
                                }else if(!dragged)selectedId=null
                            }else if(!dragged){
                                selectedId=hit?.id
                                if(hit!=null)detailsId=hit.id
                            }
                        }
                    }
                }) {
                    clipRect {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D2335),FloorBackground)),size=size)
                    val step=.5f
                    if(showGrid){
                        for(i in 0..ceil(roomWidth/step).toInt()) {
                            val x=camera.toScreen(PointMetres(i*step,0f)).x
                            if(x in 0f..size.width)drawLine(Color.White.copy(alpha=if(i%2==0).13f else .055f),Offset(x,0f),Offset(x,size.height),strokeWidth=if(i%2==0)1.1f else .7f)
                        }
                        for(i in 0..ceil(roomHeight/step).toInt()){
                            val y=camera.toScreen(PointMetres(0f,i*step)).y
                            if(y in 0f..size.height)drawLine(Color.White.copy(alpha=if(i%2==0).13f else .055f),Offset(0f,y),Offset(size.width,y),strokeWidth=if(i%2==0)1.1f else .7f)
                        }
                    }
                    val topLeft=camera.toScreen(PointMetres(0f,0f))
                    val sizeFloor=Size(roomWidth*camera.scale,roomHeight*camera.scale)
                    drawRoundRect(Color.Black.copy(alpha=.32f),topLeft=Offset(topLeft.x+4f,topLeft.y+8f),size=sizeFloor,cornerRadius=CornerRadius(7f))
                    drawRoundRect(Brush.verticalGradient(listOf(FloorFill,Color(0xFF102734))),topLeft=Offset(topLeft.x,topLeft.y),size=sizeFloor,cornerRadius=CornerRadius(5f))
                    drawRoundRect(Color.White.copy(alpha=.23f),topLeft=Offset(topLeft.x,topLeft.y),size=sizeFloor,cornerRadius=CornerRadius(5f),style=Stroke(width=1.3f))
                    for(wall in walls){
                        val a=camera.toScreen(PointMetres(wall.x1,wall.y1));val b=camera.toScreen(PointMetres(wall.x2,wall.y2))
                        val start=Offset(a.x,a.y);val end=Offset(b.x,b.y)
                        drawLine(if(wall.source=="ar")DrawingCyan.copy(alpha=.17f) else Sky.copy(alpha=.2f),start,end,strokeWidth=15f,cap=StrokeCap.Round)
                        drawLine(if(wall.source=="ar") DrawingCyan else Color(0xFFDCE8F1),start,end,strokeWidth=5.3f,cap=StrokeCap.Round)
                        drawCircle(Color(0xFFDCE8F1),2.8f,start);drawCircle(Color(0xFFDCE8F1),2.8f,end)
                    }
                    if(wallAnchor!=null){
                        val start=camera.toScreen(wallAnchor!!)
                        drawCircle(Amber.copy(alpha=.2f),14f,Offset(start.x,start.y))
                        drawCircle(Amber,4.2f,Offset(start.x,start.y))
                        draftEnd?.let { finish ->
                            val a=Offset(start.x,start.y);val end=camera.toScreen(finish);val b=Offset(end.x,end.y)
                            drawLine(Amber,a,b,strokeWidth=3.8f,cap=StrokeCap.Round)
                            drawCircle(Amber,5f,b)
                        }
                    }
                    for(f in state.placements){
                        val pos=camera.toScreen(PointMetres(f.x,f.y));val center=Offset(pos.x,pos.y)
                        val fw=f.width*camera.scale;val fh=f.depth*camera.scale
                        val selected=f.id==selectedId
                        rotate(f.rotation,pivot=center){
                            drawRoundRect(Color.Black.copy(alpha=.32f),topLeft=Offset(center.x-fw/2+3f,center.y-fh/2+4f),size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            drawRoundRect(floorColor(f.kind),topLeft=Offset(center.x-fw/2,center.y-fh/2),size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            if(f.kind=="Scaffale") for(i in 1..3){
                                val x=center.x-fw/2+fw*i/4f
                                drawLine(Color.White.copy(alpha=.32f),Offset(x,center.y-fh/2+2f),Offset(x,center.y+fh/2-2f),strokeWidth=1f)
                            }
                            drawRoundRect(if(selected)Amber else Color.White.copy(alpha=if(f.locked).35f else .68f),topLeft=Offset(center.x-fw/2,center.y-fh/2),size=Size(fw,fh),cornerRadius=CornerRadius(5f),style=Stroke(width=if(selected)3f else 1.5f))
                            if(selected)drawRoundRect(Amber.copy(alpha=.17f),topLeft=Offset(center.x-fw/2-6f,center.y-fh/2-6f),size=Size(fw+12,fh+12),cornerRadius=CornerRadius(10f),style=Stroke(width=1.3f))
                        }
                        val items=mapItems[f.id].orEmpty()
                        val count=items.size
                        if(count>0){
                            val bubble=Offset(center.x+fw/2,center.y-fh/2)
                            drawCircle(if(items.any{it.lowStock})Amber else Mint,9f,bubble)
                            drawIntoCanvas { cv ->
                                val paint=AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.BLACK;textSize=11f;typeface=Typeface.DEFAULT_BOLD;textAlign=AndroidPaint.Align.CENTER}
                                cv.nativeCanvas.drawText(count.toString(),bubble.x,bubble.y+4f,paint)
                            }
                        }
                        if(showLabels){
                            drawIntoCanvas { cv ->
                                val paint=AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.WHITE;textSize=if(selected)13f else 11f;typeface=Typeface.DEFAULT_BOLD;textAlign=AndroidPaint.Align.CENTER}
                                cv.nativeCanvas.drawText(f.label.take(19),center.x,center.y+fh/2+17f,paint)
                            }
                        }
                    }
                    drawIntoCanvas { cv ->
                        val paint=AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.rgb(155,184,201);textSize=12f;textAlign=AndroidPaint.Align.CENTER}
                        cv.nativeCanvas.drawText(numberM(roomWidth),topLeft.x+sizeFloor.width/2f,topLeft.y-14f,paint)
                        cv.nativeCanvas.save()
                        cv.nativeCanvas.rotate(-90f,topLeft.x-15f,topLeft.y+sizeFloor.height/2f)
                        cv.nativeCanvas.drawText(numberM(roomHeight),topLeft.x-15f,topLeft.y+sizeFloor.height/2f,paint)
                        cv.nativeCanvas.restore()
                    }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Surface(shape=RoundedCornerShape(11.dp),color=PanelBg.copy(alpha=.96f),border=BorderStroke(1.dp,Mint.copy(alpha=.23f))) {
                        Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Layers,null,modifier=Modifier.size(13.dp),tint=Mint)
                            Spacer(Modifier.width(6.dp))
                            Text("PIANTA IN SCALA",fontSize=9.sp,color=Mint,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                        }
                    }
                    Surface(shape=RoundedCornerShape(11.dp),color=PanelBg.copy(alpha=.96f)) {
                        Text("${state.placements.size} strutture",Modifier.padding(horizontal=9.dp,vertical=9.dp),fontSize=9.sp,color=TextSecondary)
                    }
                }
                Column(Modifier.align(Alignment.CenterEnd).padding(end=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    SmallFloatingControl(Icons.Outlined.Add,"Zoom +",{zoom=(zoom*1.25f).coerceAtMost(4f)})
                    SmallFloatingControl(Icons.Outlined.Remove,"Zoom -",{zoom=(zoom/1.25f).coerceAtLeast(.7f)})
                    SmallFloatingControl(Icons.Outlined.CenterFocusStrong,"Inquadra tutto",{zoom=1f;panX=0f;panY=0f})
                }
                Surface(Modifier.align(Alignment.BottomStart).padding(10.dp),shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.96f)) {
                    Text(when(mode){
                        EditorTool.WALL -> if(wallAnchor==null)"Tocca il punto iniziale oppure trascina" else "Tocca per continuare le pareti · Fine per chiudere"
                        EditorTool.FURNITURE -> "Tocca una struttura; trascina solo se sbloccata"
                        EditorTool.VIEW -> "Sposta la pianta trascinando lo sfondo"
                    },Modifier.padding(horizontal=11.dp,vertical=8.dp),fontSize=10.sp,color=TextSecondary)
                }
            }
        }
        Row(Modifier.padding(horizontal=14.dp),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
            if(mode==EditorTool.WALL){
                FilterChip(selected=snapGrid,onClick={snapGrid=!snapGrid},label={Text("Griglia 25 cm",fontSize=11.sp)},leadingIcon={Icon(Icons.Outlined.GridOn,null,Modifier.size(15.dp))})
                FilterChip(selected=orthogonal,onClick={orthogonal=!orthogonal},label={Text("90°",fontSize=11.sp)})
                if(wallAnchor!=null)TextButton(onClick={wallAnchor=null;draftEnd=null}){Text("Fine",fontSize=12.sp)}
            }else{
                FilterChip(selected=showGrid,onClick={showGrid=!showGrid},label={Text("Griglia",fontSize=11.sp)})
                FilterChip(selected=showLabels,onClick={showLabels=!showLabels},label={Text("Nomi",fontSize=11.sp)})
                Spacer(Modifier.weight(1f))
                Text("${(zoom*100).toInt()}%",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
            }
        }
        if(mode==EditorTool.WALL){
            Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${walls.size} pareti · ${manualWalls.size} disegnate",fontSize=11.sp,color=TextSecondary,modifier=Modifier.weight(1f))
                if(manualWalls.isNotEmpty()) TextButton(onClick={wallAnchor=null;draftEnd=null;model.deleteWall(manualWalls.last().id)}) {
                    Icon(Icons.Outlined.Undo,null,Modifier.size(15.dp));Spacer(Modifier.width(4.dp));Text("Annulla ultima",fontSize=11.sp)
                }
            }
        }else if(mode==EditorTool.FURNITURE){
            if(placement==null){
                Button(onClick={showNewObject=true},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Inserisci struttura nella pianta")
                }
            }else{
                Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp),shape=RoundedCornerShape(16.dp),color=PanelBg,border=BorderStroke(1.dp,Mint.copy(alpha=.24f))){
                    Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(39.dp).background(floorColor(placement.kind).copy(alpha=.27f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
                            Icon(Icons.Outlined.Warehouse,null,tint=Mint,modifier=Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)){
                            Text(placement.label,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
                            Text("${numberM(placement.width)} × ${numberM(placement.depth)} · ${mapItems[placement.id]?.size?:0} articoli",fontSize=10.sp,color=TextSecondary)
                        }
                        IconButton(onClick={model.lockPlacement(placement.id,!placement.locked)}) {Icon(if(placement.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,"Blocco",tint=if(placement.locked)Amber else Mint)}
                        IconButton(onClick={detailsId=placement.id}) {Icon(Icons.Outlined.Tune,"Modifica",tint=Sky)}
                    }
                }
            }
        }else{
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=3.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("",modifier=Modifier.weight(1f))
                OutlinedButton(onClick=onAr,shape=RoundedCornerShape(13.dp)) {
                    Icon(Icons.Outlined.CameraAlt,null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text("Rilievo AR")
                }
            }
        }
    }
    if(showDimensions)MapDimensionDialog(warehouse.mapWidth,warehouse.mapHeight,onClose={showDimensions=false}) {w,h->
        model.resizeMap(w,h);showDimensions=false;zoom=1f;panX=0f;panY=0f
    }
    if(showNewObject)CreatePlacementDialog({showNewObject=false}){kind,label->
        model.addPlacement(kind,label);showNewObject=false
    }
    detailsId?.let { id ->
        state.placements.firstOrNull{it.id==id}?.let { f ->
            PlacementDetailDialog(f,state.items,model,onDismiss={detailsId=null})
        }
    }
}

@Composable private fun SmallFloatingControl(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,onClick:()->Unit){
    Surface(shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.97f),border=BorderStroke(1.dp,Color.White.copy(alpha=.1f))){
        IconButton(onClick=onClick,modifier=Modifier.size(38.dp)){Icon(icon,description,tint=TextPrimary,modifier=Modifier.size(18.dp))}
    }
}

@Composable private fun MapDimensionDialog(currentW:Float,currentH:Float,onClose:()->Unit,onSave:(Float,Float)->Unit){
    var w by remember { mutableStateOf("${currentW}") }
    var h by remember { mutableStateOf("${currentH}") }
    val parsedW=w.replace(',','.').toFloatOrNull()
    val parsedH=h.replace(',','.').toFloatOrNull()
    AlertDialog(onDismissRequest=onClose,title={Text("Dimensioni della pianta")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Imposta le dimensioni reali del garage o del magazzino. Modificarle cambia il confine della pianta, senza riscalare gli oggetti esistenti.",fontSize=12.sp,color=TextSecondary)
            OutlinedTextField(value=w,onValueChange={w=it},label={Text("Larghezza (metri)")},singleLine=true)
            OutlinedTextField(value=h,onValueChange={h=it},label={Text("Profondità (metri)")},singleLine=true)
        }
    },confirmButton={TextButton(onClick={onSave(parsedW!!,parsedH!!)},enabled=parsedW!=null&&parsedW in 2f..150f&&parsedH!=null&&parsedH in 2f..150f){Text("Applica")}},dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable private fun CreatePlacementDialog(onCancel:()->Unit,onCreate:(String,String)->Unit){
    var kind by remember{mutableStateOf("Scaffale")};var label by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onCancel,title={Text("Nuova struttura")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Scegli l'elemento. Appare al centro della pianta con dimensioni modificabili e puoi trascinarlo prima di bloccarlo.",color=TextSecondary,fontSize=12.sp)
            listOf("Scaffale","Armadio","Cassetto","Banco","Area").chunked(3).forEach{ group ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){group.forEach{v ->
                    FilterChip(selected=kind==v,onClick={kind=v},label={Text(v,fontSize=11.sp)})
                }}
            }
            OutlinedTextField(value=label,onValueChange={label=it},label={Text("Nome / ubicazione")},singleLine=true,modifier=Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={onCreate(kind,label.trim())},enabled=label.isNotBlank()){Text("Crea")}},dismissButton={TextButton(onClick=onCancel){Text("Annulla")}})
}

@Composable private fun PlacementDetailDialog(p:Placement,items:List<StockItem>,model:StockViewModel,onDismiss:()->Unit){
    var askDelete by remember {mutableStateOf(false)}
    var w by remember(p.id,p.width){mutableStateOf("${p.width}")}
    var d by remember(p.id,p.depth){mutableStateOf("${p.depth}")}
    var angle by remember(p.id,p.rotation){mutableFloatStateOf(p.rotation)}
    val wVal=w.replace(',','.').toFloatOrNull()
    val dVal=d.replace(',','.').toFloatOrNull()
    val valid=wVal!=null&&wVal in .2f..15f&&dVal!=null&&dVal in .2f..15f
    val inside=items.filter{it.placementId==p.id}
    val free=items.filter{it.placementId==null}
    AlertDialog(onDismissRequest=onDismiss,title={Text(p.label)},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(color=Surface2,shape=RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(p.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,null,tint=if(p.locked)Amber else Mint)
                    Spacer(Modifier.width(8.dp));Text(if(p.locked)"Posizione bloccata" else "Trascinamento attivo",fontSize=12.sp,modifier=Modifier.weight(1f))
                    Switch(checked=p.locked,onCheckedChange={model.lockPlacement(p.id,it)})
                }
            }
            Text("INGOMBRO IN PIANTA",fontWeight=FontWeight.Bold,color=Mint,fontSize=10.sp,letterSpacing=1.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(w,{w=it},label={Text("Larghezza m")},singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(d,{d=it},label={Text("Profondità m")},singleLine=true,modifier=Modifier.weight(1f))
            }
            Text("Rotazione ${angle.toInt()}°",fontSize=12.sp,color=TextSecondary)
            Slider(value=angle,onValueChange={angle=it},valueRange=0f..270f,steps=2)
            Button(onClick={model.resizePlacement(p.id,wVal!!,dVal!!,angle)},enabled=valid,modifier=Modifier.fillMaxWidth()) {Text("Aggiorna dimensioni e rotazione")}
            Text("ARTICOLI COLLEGATI · ${inside.size}",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp)
            if(inside.isEmpty())Text("Ancora nessun articolo in questa ubicazione",color=TextSecondary,fontSize=12.sp)
            inside.forEach{item ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text(item.name,fontSize=12.sp,maxLines=1)
                        Text("${item.sku} · ${amount(item.quantity)} ${item.unit}",fontSize=10.sp,color=TextSecondary)
                    }
                    IconButton(onClick={model.assignItem(item.id,null)}){Icon(Icons.Outlined.RemoveCircleOutline,"Scollega",tint=Amber)}
                }
            }
            if(free.isNotEmpty()){
                Text("ASSEGNA ARTICOLI",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp)
                free.take(30).forEach{item->
                    TextButton(onClick={model.assignItem(item.id,p.id)},modifier=Modifier.fillMaxWidth()){
                        Text("+ ${item.name}",maxLines=1,modifier=Modifier.weight(1f))
                    }
                }
            }
            TextButton(onClick={askDelete=true}) {Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(5.dp));Text("Elimina struttura",color=Danger)}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
    if(askDelete)AlertDialog(onDismissRequest={askDelete=false},title={Text("Eliminare ${p.label}?")},text={Text("Gli articoli rimangono nell'inventario senza ubicazione.")},confirmButton={TextButton(onClick={model.deletePlacement(p.id);askDelete=false;onDismiss()}){Text("Elimina",color=Danger)}},dismissButton={TextButton(onClick={askDelete=false}){Text("Annulla")}})
}
