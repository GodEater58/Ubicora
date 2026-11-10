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

private enum class EditorTool { VIEW, WALL, FURNITURE }
/** Colour layers answering warehouse questions at a glance. */
private enum class MapLayer(val label: String, val legend: String) {
    NORMAL("Normale", ""),
    CRITICAL("Critici", "Articoli sotto scorta minima per struttura"),
    VALUE("Valore", "Valore delle scorte (quantità × costo)"),
    ACTIVITY("Più usati", "Movimenti negli ultimi 30 giorni"),
    EMPTY("Vuote", "Strutture senza articoli assegnati")
}
private val FloorBackground = Color(0xFF0C1B2A)
private val FloorFill = Color(0xFF152C3A)
private val ShelfTeal = Color(0xFF267C80)
private val CabinetBlue = Color(0xFF48668D)
private val BenchGold = Color(0xFFA47745)
private val DrawingCyan = Color(0xFF7DE8E3)
private val ClosingGreen = Color(0xFF6EE7A0)

/** Hook radius for wall corners: about a fingertip on screen, never more than 35 cm or 45% of the stroke. */
private fun snapTolerance(hookPx: Float, scale: Float, from: PointMetres?, raw: PointMetres): Float {
    var t = minOf(hookPx / scale.coerceAtLeast(1f), .35f)
    if (from != null) t = minOf(t, PlanGeometry.distance(from, raw) * .45f)
    return t
}
private val DoorBrown = Color(0xFF8B6B4A)
private val ColumnGrey = Color(0xFF59646E)
private val Idle = Color(0xFF2B3A47)

private fun numberM(value: Float):String = String.format(Locale.ITALY,"%.2f m", value)
private fun floorColor(kind: String): Color = when(kind) {
    "Armadio" -> CabinetBlue
    "Banco" -> BenchGold
    "Cassetto" -> Color(0xFF66719F)
    "Area" -> Color(0xFF456D67)
    "Porta" -> DoorBrown
    "Colonna" -> ColumnGrey
    "Corridoio" -> Mint.copy(alpha=.12f)
    else -> ShelfTeal
}
/** 0 = calm (mint), 0.5 = attention (amber), 1 = hot (red). */
private fun heat(t: Float): Color {
    val v=t.coerceIn(0f,1f)
    return if(v<.5f) lerp(Mint,Amber,v*2f) else lerp(Amber,Danger,(v-.5f)*2f)
}

@Composable
fun NativeMapScreen(model:StockViewModel,onAr:()->Unit,onSettings:()->Unit={}) {
    val state=model.ui
    val warehouse=state.warehouse ?: return
    val context=LocalContext.current
    val haptics=LocalHapticFeedback.current
    val scope=rememberCoroutineScope()
    var mode by remember(warehouse.id){mutableStateOf(EditorTool.VIEW)}
    var layer by remember(warehouse.id){mutableStateOf(MapLayer.NORMAL)}
    var selectedId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    var selectedWallId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    var highlightId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    // 4.15 guided picking: structures already visited, the one to reach and the stretch between them.
    val guide=remember(state.pickList,state.items,state.placements){ PickRoute.guide(state.pickList,state.items,state.placements) }
    var wallAnchor by remember(warehouse.id){mutableStateOf<PointMetres?>(null)}
    var draftEnd by remember(warehouse.id){mutableStateOf<PointMetres?>(null)}
    var draftCloses by remember(warehouse.id){mutableStateOf(false)}
    // 4.7.2: Back while drawing ends the current chain of walls instead of leaving the map.
    androidx.activity.compose.BackHandler(enabled=wallAnchor!=null) { wallAnchor=null;draftEnd=null;draftCloses=false }
    // Back while choosing a location on the map cancels the choice instead of leaving it pending.
    androidx.activity.compose.BackHandler(enabled=state.locationPickItemIds!=null) { model.cancelLocationPick() }
    var showNewObject by remember {mutableStateOf(false)}
    var showDimensions by remember {mutableStateOf(false)}
    var showRowFor by remember {mutableStateOf<Long?>(null)}
    var wallLengthFor by remember {mutableStateOf<Long?>(null)}
    var pickPositionFor by remember {mutableStateOf<Long?>(null)}
    var qrFor by remember {mutableStateOf<Long?>(null)}
    var showGrid by remember {mutableStateOf(true)}
    var snapGrid by remember {mutableStateOf(true)}
    var magnet by remember {mutableStateOf(true)}
    var orthogonal by remember {mutableStateOf(false)}
    var showLabels by remember {mutableStateOf(true)}
    val savedCamera=remember(warehouse.id){model.mapCamera(warehouse.id)}
    var zoom by remember(warehouse.id){mutableFloatStateOf(savedCamera.first)}
    var panX by remember(warehouse.id){mutableFloatStateOf(savedCamera.second)}
    var panY by remember(warehouse.id){mutableFloatStateOf(savedCamera.third)}
    var canvasW by remember{mutableFloatStateOf(0f)}
    var canvasH by remember{mutableFloatStateOf(0f)}
    var detailsId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    val placement=state.placements.firstOrNull{it.id==selectedId}
    val selectedWall=state.walls.firstOrNull{it.id==selectedWallId}
    val walls=state.walls
    val livePlacements by rememberUpdatedState(state.placements)
    val liveWalls by rememberUpdatedState(state.walls)
    val pickIds by rememberUpdatedState(state.locationPickItemIds)
    val manualWalls=walls.filter{it.source=="manual"}
    val roomWidth=warehouse.mapWidth
    val roomHeight=warehouse.mapHeight
    val marginPx=with(LocalDensity.current){34.dp.toPx()}
    val mapItems=remember(state.items){state.items.groupBy { it.placementId }}
    val colliding=remember(state.placements){
        state.placements.filter{PlanGeometry.collisions(it,state.placements).isNotEmpty()}.map{it.id}.toSet()
    }
    val layerValues=remember(layer,state.items,state.placements,state.placementActivity){
        state.placements.filter{it.holdsStock}.associate { p ->
            val inside=mapItems[p.id].orEmpty()
            p.id to when(layer) {
                MapLayer.NORMAL -> 0f
                MapLayer.CRITICAL -> inside.count{it.lowStock}.toFloat()
                MapLayer.VALUE -> inside.sumOf{it.quantity*(it.unitCost ?: 0.0)}.toFloat()
                MapLayer.ACTIVITY -> (state.placementActivity[p.id] ?: 0).toFloat()
                MapLayer.EMPTY -> if(inside.isEmpty()) 1f else 0f
            }
        }
    }
    val layerMax=layerValues.values.maxOrNull()?.takeIf{it>0f} ?: 1f

    LaunchedEffect(warehouse.id,zoom,panX,panY) { model.saveMapCamera(warehouse.id,zoom,panX,panY) }
    // "Mostra in pianta" and shelf QR: center, zoom and pulse the structure.
    LaunchedEffect(state.mapFocusPlacementId,canvasW,canvasH) {
        val focus=state.mapFocusPlacementId ?: return@LaunchedEffect
        if(canvasW<=0f||canvasH<=0f) return@LaunchedEffect
        val p=state.placements.firstOrNull{it.id==focus}
        if(p!=null) {
            mode=EditorTool.VIEW;selectedWallId=null;selectedId=p.id;highlightId=p.id
            val z=2f
            val cam=PlanViewport(canvasW,canvasH,roomWidth,roomHeight,z,0f,0f,marginPx)
            val at=cam.toScreen(PointMetres(p.x,p.y))
            val pan=cam.clampedPan(canvasW/2f-at.x,canvasH/2f-at.y)
            zoom=z;panX=pan.x;panY=pan.y
        }
        model.acknowledgeMapFocus()
    }
    LaunchedEffect(highlightId) { if(highlightId!=null) { delay(5000);highlightId=null } }
    // Animate only while a structure is highlighted: no continuous redraw otherwise.
    val pulse=if(highlightId!=null) {
        val value by rememberInfiniteTransition(label="focus").animateFloat(0f,1f,
            infiniteRepeatable(tween(900),RepeatMode.Reverse),label="pulse")
        value
    } else 0f
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) scope.launch {
            val failure=withContext(Dispatchers.IO) { runCatching {
                val out=context.contentResolver.openOutputStream(uri) ?: error("Impossibile aprire il file")
                out.use { MapPdf.write(model.ui,it) }
            }.exceptionOrNull()?.message }
            Toast.makeText(context,if(failure==null)"Pianta esportata in PDF" else "Esportazione non riuscita: $failure",Toast.LENGTH_LONG).show()
        }
    }
    fun pick(p:Placement) {
        if(!p.holdsStock) { Toast.makeText(context,"${p.kind}: scegli uno scaffale, armadio o area",Toast.LENGTH_SHORT).show();return }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if(p.hasPositions) pickPositionFor=p.id else model.completeLocationPick(p.id,null,null)
    }
    fun viewCentre():PointMetres {
        if(canvasW<=0f) return PointMetres(roomWidth/2f,roomHeight/2f)
        val cam=PlanViewport(canvasW,canvasH,roomWidth,roomHeight,zoom,panX,panY,marginPx)
        return cam.bounded(cam.toWorld(PointMetres(canvasW/2f,canvasH/2f)))
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        // 4.15 "tour mode": during a guided picking the editing tools leave the screen and the title follows the walk.
        val guiding=state.pickList.any{!it.done}
        LaunchedEffect(guiding) { if(guiding) { mode=EditorTool.VIEW;layer=MapLayer.NORMAL;wallAnchor=null;draftEnd=null;selectedWallId=null } }
        if(guiding) GuidedMapHeader(state.pickList,onClose=model::clearPickList)
        if(!guiding) Column(Modifier.padding(horizontal=16.dp).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Surface(shape=RoundedCornerShape(12.dp),color=Mint.copy(alpha=.11f)) {
                    Icon(Icons.Outlined.Architecture,"Progettazione",tint=Mint,modifier=Modifier.padding(10.dp).size(23.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("STUDIO PLANIMETRICO",fontSize=9.sp,letterSpacing=1.7.sp,fontWeight=FontWeight.Bold,color=Mint)
                    Text("${warehouse.name} · Piano 2D",fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick={pdfLauncher.launch("Ubicora_pianta_${warehouse.id}.pdf")}) {Icon(Icons.Outlined.PictureAsPdf,"Esporta pianta in PDF",tint=Sky)}
                IconButton(onClick={
                    val labels=state.placements.filter{it.holdsStock}.map{LabelSpec.placement(it)}
                    try { QrLabels.printSheet(context,"Ubicazioni ${warehouse.name}",labels) }
                    catch(e:Exception) { Toast.makeText(context,e.localizedMessage ?: "Stampa non disponibile",Toast.LENGTH_LONG).show() }
                },enabled=state.placements.any{it.holdsStock}) {Icon(Icons.Outlined.QrCode2,"Etichette QR di tutte le strutture",tint=Sky)}
                FilledTonalIconButton(onClick={showDimensions=true}) {Icon(Icons.Outlined.Straighten,"Dimensioni ambiente")}
                FilledTonalIconButton(onClick=onSettings) {Icon(Icons.Outlined.Settings,"Impostazioni")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp),modifier=Modifier.fillMaxWidth()) {
                listOf(
                    Triple(EditorTool.VIEW, "Esplora", Icons.Outlined.PanToolAlt),
                    Triple(EditorTool.WALL, "Pareti", Icons.Outlined.Edit),
                    Triple(EditorTool.FURNITURE, "Strutture", Icons.Outlined.GridView)
                ).forEach { (tool,title,icon) ->
                    val active=mode==tool
                    Surface(
                        modifier=Modifier.weight(1f).clickable {mode=tool;wallAnchor=null;draftEnd=null;selectedWallId=null},
                        shape=RoundedCornerShape(14.dp),
                        color=if(active) Mint.copy(alpha=.15f) else Surface2,
                        border=BorderStroke(1.dp,if(active) Mint.copy(alpha=.66f) else Hairline.copy(alpha=.07f))
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
        state.locationPickItemIds?.let { ids ->
            Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp),shape=RoundedCornerShape(14.dp),color=Amber.copy(alpha=.16f),border=BorderStroke(1.dp,Amber.copy(alpha=.6f))) {
                Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TouchApp,null,tint=Amber)
                    Spacer(Modifier.width(8.dp))
                    Text(if(ids.size==1) "Tocca la struttura dove mettere l'articolo" else "Tocca la struttura per ${ids.size} articoli",
                        fontSize=12.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick=model::cancelLocationPick){Text("Annulla")}
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal=12.dp).clipToBounds()) {
            BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).border(BorderStroke(1.dp,Color.White.copy(alpha=.13f)),RoundedCornerShape(24.dp)).background(FloorBackground,RoundedCornerShape(24.dp))) {
                val density=LocalDensity.current
                val w=with(density){maxWidth.toPx()}
                val h=with(density){maxHeight.toPx()}
                SideEffect { canvasW=w;canvasH=h }
                val camera=PlanViewport(w,h,roomWidth,roomHeight,zoom,panX,panY,marginPx)
                // Screen-space typography: never shrink text with the map camera.
                val labelPx=with(density){14.sp.toPx()}
                val selectedLabelPx=with(density){16.sp.toPx()}
                val labelHeightPx=with(density){28.dp.toPx()}
                val labelPadPx=with(density){9.dp.toPx()}
                val labelGapPx=with(density){7.dp.toPx()}
                val badgePx=with(density){12.dp.toPx()}
                val badgeTextPx=with(density){11.sp.toPx()}
                val rulerPx=with(density){11.sp.toPx()}
                val measurePx=with(density){13.sp.toPx()}
                val wallTapPx=with(density){18.dp.toPx()}
                // Paints are allocated once per composition, not once per structure per frame.
                val labelPaint=remember(labelPx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.WHITE;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);textAlign=AndroidPaint.Align.CENTER
                }}
                val badgePaint=remember(badgeTextPx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.BLACK;textSize=badgeTextPx;typeface=Typeface.DEFAULT_BOLD;textAlign=AndroidPaint.Align.CENTER
                }}
                val rulerPaint=remember(rulerPx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.rgb(155,184,201);textSize=rulerPx;textAlign=AndroidPaint.Align.CENTER
                }}
                val measurePaint=remember(measurePx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.rgb(255,201,120);textSize=measurePx;typeface=Typeface.DEFAULT_BOLD;textAlign=AndroidPaint.Align.CENTER
                }}
                LaunchedEffect(zoom,w,h,roomWidth,roomHeight) {
                    val adjusted=PlanViewport(w,h,roomWidth,roomHeight,zoom,panX,panY,marginPx)
                        .clampedPan(panX,panY)
                    panX=adjusted.x;panY=adjusted.y
                }
                // One gesture owner. Unlike overlapping tap/drag handlers, this cannot steal its own wall strokes.
                Canvas(Modifier.fillMaxSize().clipToBounds().pointerInput(mode,warehouse.id,roomWidth,roomHeight) {
                    // 4.15: double tap on the empty floor.
                    var lastTapAt=0L
                    var lastTapPos=PointMetres(0f,0f)
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        val cameraNow=PlanViewport(size.width.toFloat(),size.height.toFloat(),roomWidth,roomHeight,zoom,panX,panY,34.dp.toPx())
                        val initial=PointMetres(down.position.x,down.position.y)
                        val startWorld=cameraNow.bounded(cameraNow.toWorld(initial))
                        val hit=livePlacements.lastOrNull { PlanGeometry.contains(startWorld,it) }
                        val picking=pickIds!=null
                        val target=if(mode==EditorTool.FURNITURE && !picking) hit else null
                        val initialFurniture=target?.let{PointMetres(it.x,it.y)}
                        val originalPanX=panX
                        val originalPanY=panY
                        var last=initial
                        var dragged=false
                        var cancelled=false
                        var transforming=false
                        val minDrag=viewConfiguration.touchSlop
                        while(true){
                            val e=awaitPointerEvent()
                            if(e.changes.count{it.pressed}>1) {
                                transforming=true
                                draftEnd=null
                                draftCloses=false
                                val pivot=e.calculateCentroid(useCurrent=false)
                                val shift=e.calculatePan()
                                val current=PlanViewport(size.width.toFloat(),size.height.toFloat(),roomWidth,roomHeight,zoom,panX,panY,34.dp.toPx())
                                val next=current.transformed(e.calculateZoom(),PointMetres(pivot.x,pivot.y),PointMetres(shift.x,shift.y))
                                zoom=next.zoom;panX=next.panX;panY=next.panY
                                e.changes.forEach{it.consume()}
                                continue
                            }
                            if(transforming) {
                                e.changes.forEach{it.consume()}
                                if(e.changes.none{it.pressed})break
                                continue
                            }
                            val change=e.changes.firstOrNull{it.id==down.id}
                            if(change==null){cancelled=true;break}
                            last=PointMetres(change.position.x,change.position.y)
                            if(!dragged && PlanGeometry.distance(initial,last)>minDrag) dragged=true
                            if(dragged && change.pressed){
                                if(mode==EditorTool.WALL && !picking){
                                    if(wallAnchor==null) wallAnchor=PlanGeometry.wallPoint(startWorld,null,liveWalls,snapTolerance(14.dp.toPx(),cameraNow.scale,null,startWorld),snapGrid,false).point
                                    val raw=cameraNow.bounded(cameraNow.toWorld(last))
                                    val landed=PlanGeometry.wallPoint(raw,wallAnchor,liveWalls,snapTolerance(14.dp.toPx(),cameraNow.scale,wallAnchor,raw),snapGrid,orthogonal)
                                    draftEnd=landed.point
                                    draftCloses=landed.closes
                                }else if(target!=null && !target.locked && initialFurniture!=null){
                                    val dx=(last.x-initial.x)/cameraNow.scale
                                    val dy=(last.y-initial.y)/cameraNow.scale
                                    val pos=cameraNow.bounded(PointMetres(initialFurniture.x+dx,initialFurniture.y+dy))
                                    val mapped=if(snapGrid)PlanGeometry.snap(pos) else pos
                                    model.previewMove(target.id,mapped.x,mapped.y)
                                }else if(mode!=EditorTool.WALL || picking){
                                    val bounded=cameraNow.clampedPan(originalPanX+last.x-initial.x,originalPanY+last.y-initial.y)
                                    panX=bounded.x; panY=bounded.y
                                }
                                change.consume()
                            }
                            if(!change.pressed){break}
                        }
                        if(transforming && target!=null && initialFurniture!=null) model.previewMove(target.id,initialFurniture.x,initialFurniture.y)
                        if(cancelled || transforming) return@awaitEachGesture
                        if(picking) {
                            if(!dragged && hit!=null) pick(hit)
                            return@awaitEachGesture
                        }
                        // 4.15: a second tap on the same spot of the empty floor zooms there; when already close, back to the whole plan.
                        if(!dragged && hit==null && mode==EditorTool.VIEW) {
                            val now=down.uptimeMillis
                            if(now-lastTapAt<320L && PlanGeometry.distance(initial,lastTapPos)<minDrag*4f) {
                                lastTapAt=0L
                                if(zoom>=3.2f) { zoom=1f;panX=0f;panY=0f }
                                else {
                                    val current=PlanViewport(size.width.toFloat(),size.height.toFloat(),roomWidth,roomHeight,zoom,panX,panY,34.dp.toPx())
                                    val next=current.transformed(1.8f,initial,PointMetres(0f,0f))
                                    zoom=next.zoom;panX=next.panX;panY=next.panY
                                }
                                return@awaitEachGesture
                            }
                            lastTapAt=now;lastTapPos=initial
                        }
                        val tappedWall=if(!dragged && hit==null && mode!=EditorTool.WALL)
                            liveWalls.minByOrNull{PlanGeometry.distanceToWall(startWorld,it)}
                                ?.takeIf{PlanGeometry.distanceToWall(startWorld,it)*cameraNow.scale<=wallTapPx} else null
                        if(mode==EditorTool.WALL){
                            val from=wallAnchor ?: PlanGeometry.wallPoint(startWorld,null,liveWalls,snapTolerance(14.dp.toPx(),cameraNow.scale,null,startWorld),snapGrid,false).point
                            val point=cameraNow.bounded(cameraNow.toWorld(last))
                            val first=wallAnchor==null && !dragged
                            val landed=if(first) PlanGeometry.wallPoint(point,null,liveWalls,snapTolerance(14.dp.toPx(),cameraNow.scale,null,point),snapGrid,false)
                                else PlanGeometry.wallPoint(point,from,liveWalls,snapTolerance(14.dp.toPx(),cameraNow.scale,from,point),snapGrid,orthogonal)
                            val finish=landed.point
                            if(first){
                                wallAnchor=finish
                            }else if(PlanGeometry.validWall(from,finish)){
                                model.addWall(floatArrayOf(from.x,from.y,finish.x,finish.y))
                                // Landing on an existing corner or wall closes the shape: the next tap starts a new one.
                                wallAnchor=if(landed.closes) null else finish
                                if(landed.closes) Toast.makeText(context,"Forma chiusa",Toast.LENGTH_SHORT).show()
                            }
                            draftEnd=null
                            draftCloses=false
                        }else if(tappedWall!=null){
                            selectedWallId=tappedWall.id;selectedId=null
                        }else if(mode==EditorTool.FURNITURE){
                            selectedWallId=null
                            if(target!=null){
                                selectedId=target.id
                                if(dragged && !target.locked && initialFurniture!=null){
                                    val updated=livePlacements.firstOrNull{it.id==target.id}
                                    if(updated!=null){
                                        val snapped=if(magnet) PlanGeometry.snapToNeighbors(updated,livePlacements,liveWalls) else PointMetres(updated.x,updated.y)
                                        model.commitMove(updated.id,initialFurniture.x,initialFurniture.y,snapped.x,snapped.y)
                                    }
                                }
                            }else if(!dragged)selectedId=null
                        }else if(!dragged){
                            selectedWallId=null
                            selectedId=hit?.id
                            if(hit!=null)detailsId=hit.id
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
                        val chosen=wall.id==selectedWallId
                        drawLine(if(chosen)Amber.copy(alpha=.3f) else if(wall.source=="ar")DrawingCyan.copy(alpha=.17f) else Sky.copy(alpha=.2f),start,end,strokeWidth=if(chosen)22f else 15f,cap=StrokeCap.Round)
                        drawLine(if(chosen)Amber else if(wall.source=="ar") DrawingCyan else Color(0xFFDCE8F1),start,end,strokeWidth=5.3f,cap=StrokeCap.Round)
                        drawCircle(Color(0xFFDCE8F1),2.8f,start);drawCircle(Color(0xFFDCE8F1),2.8f,end)
                        if(chosen) drawIntoCanvas { cv ->
                            cv.nativeCanvas.drawText(numberM(PlanGeometry.wallLength(wall)),(a.x+b.x)/2f,(a.y+b.y)/2f-12f,measurePaint)
                        }
                    }
                    if(wallAnchor!=null){
                        val start=camera.toScreen(wallAnchor!!)
                        drawCircle(Amber.copy(alpha=.2f),14f,Offset(start.x,start.y))
                        drawCircle(Amber,4.2f,Offset(start.x,start.y))
                        draftEnd?.let { finish ->
                            val a=Offset(start.x,start.y);val end=camera.toScreen(finish);val b=Offset(end.x,end.y)
                            drawLine(Amber,a,b,strokeWidth=3.8f,cap=StrokeCap.Round)
                            if(draftCloses){
                                // The end will hook onto existing geometry and close the shape.
                                drawCircle(ClosingGreen.copy(alpha=.25f),18f,b)
                                drawCircle(ClosingGreen,6.5f,b)
                            } else drawCircle(Amber,5f,b)
                            // Live measurement while drawing.
                            drawIntoCanvas { cv ->
                                cv.nativeCanvas.drawText(numberM(PlanGeometry.distance(wallAnchor!!,finish)),(a.x+b.x)/2f,(a.y+b.y)/2f-14f,measurePaint)
                            }
                        }
                    }
                    val occupiedLabelBoxes=mutableListOf<android.graphics.RectF>()
                    for(f in state.placements.sortedBy { if(it.id==selectedId) 2 else if(it.kind=="Corridoio") 0 else 1 }){
                        val pos=camera.toScreen(PointMetres(f.x,f.y));val center=Offset(pos.x,pos.y)
                        val fw=f.width*camera.scale;val fh=f.depth*camera.scale
                        val selected=f.id==selectedId
                        val items=mapItems[f.id].orEmpty()
                        val fill=when {
                            !f.holdsStock || layer==MapLayer.NORMAL -> floorColor(f.kind)
                            layer==MapLayer.EMPTY -> if(items.isEmpty()) Amber.copy(alpha=.85f) else Idle
                            else -> (layerValues[f.id] ?: 0f).let { v -> if(v<=0f) Idle else heat(v/layerMax) }
                        }
                        rotate(f.rotation,pivot=center){
                            val box=Offset(center.x-fw/2,center.y-fh/2)
                            if(f.kind!="Corridoio")drawRoundRect(Color.Black.copy(alpha=.32f),topLeft=Offset(box.x+3f,box.y+4f),size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            drawRoundRect(fill,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            if(f.id in guide.done) {
                                drawRoundRect(Mint.copy(alpha=.30f),topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                                drawRoundRect(Mint,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),style=Stroke(width=3f))
                            }
                            if(f.id==guide.next?.id) {
                                drawRoundRect(Amber.copy(alpha=.30f),topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                                drawRoundRect(Amber,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),style=Stroke(width=4f))
                                drawRoundRect(Amber.copy(alpha=.5f),topLeft=Offset(box.x-9f,box.y-9f),size=Size(fw+18f,fh+18f),cornerRadius=CornerRadius(12f),style=Stroke(width=2f))
                            }
                            when(f.kind) {
                                "Porta" -> drawArc(Color.White.copy(alpha=.5f),270f,90f,false,topLeft=Offset(box.x-fw,box.y+fh-fw),size=Size(fw*2,fw*2),
                                    style=Stroke(width=1.2f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,5f))))
                                "Colonna" -> { drawLine(Color.White.copy(alpha=.4f),box,Offset(box.x+fw,box.y+fh),1.2f);drawLine(Color.White.copy(alpha=.4f),Offset(box.x+fw,box.y),Offset(box.x,box.y+fh),1.2f) }
                                else -> if(f.holdsStock) {
                                    val dividers=if(f.slots>1) f.slots else if(f.kind=="Scaffale") 4 else 1
                                    for(i in 1 until dividers){
                                        val x=box.x+fw*i/dividers
                                        drawLine(Color.White.copy(alpha=.32f),Offset(x,box.y+2f),Offset(x,box.y+fh-2f),strokeWidth=1f)
                                    }
                                }
                            }
                            val outline=when { selected -> Amber; f.id in colliding -> Danger; else -> Color.White.copy(alpha=if(f.locked).35f else .68f) }
                            drawRoundRect(outline,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),
                                style=Stroke(width=if(selected||f.id in colliding)3f else 1.5f,
                                    pathEffect=if(f.kind=="Corridoio") PathEffect.dashPathEffect(floatArrayOf(10f,7f)) else null))
                            if(selected)drawRoundRect(Amber.copy(alpha=.17f),topLeft=Offset(box.x-6f,box.y-6f),size=Size(fw+12,fh+12),cornerRadius=CornerRadius(10f),style=Stroke(width=1.3f))
                            if(f.id==highlightId) drawRoundRect(Amber.copy(alpha=.25f+.5f*pulse),topLeft=Offset(box.x-10f-8f*pulse,box.y-10f-8f*pulse),
                                size=Size(fw+20f+16f*pulse,fh+20f+16f*pulse),cornerRadius=CornerRadius(14f),style=Stroke(width=3f))
                        }
                        val count=items.size
                        val visited=f.id in guide.done
                        if(count>0 || visited){
                            val bubble=Offset(
                                camera.insetCoordinate(center.x+fw/2,size.width,badgePx),
                                camera.insetCoordinate(center.y-fh/2,size.height,badgePx)
                            )
                            drawCircle(if(visited) Mint else if(items.any{it.lowStock})Amber else Mint,badgePx,bubble)
                            // 4.15: labels keep clear of the counters instead of being cut by them.
                            occupiedLabelBoxes.add(android.graphics.RectF(bubble.x-badgePx,bubble.y-badgePx,bubble.x+badgePx,bubble.y+badgePx))
                            drawIntoCanvas { cv ->
                                // 4.15: a check mark replaces the count on the structures already visited by the guided picking.
                                cv.nativeCanvas.drawText(if(visited) "\u2713" else count.toString(),bubble.x,
                                    bubble.y-(badgePaint.ascent()+badgePaint.descent())/2f,badgePaint)
                            }
                        }
                        if(showLabels && size.width>60f && size.height>60f){
                            labelPaint.textSize=if(selected)selectedLabelPx else labelPx
                            val original=f.label.ifBlank { f.kind }+(if(f.levels>1)" · ${f.levels}R" else "")
                            val maxTextWidth=(size.width-2f*labelPadPx-18f).coerceAtLeast(24f)
                            var cut=original.length
                            while(cut>1 && labelPaint.measureText(
                                if(cut<original.length)original.take(cut)+"…" else original
                            )>maxTextWidth) cut--
                            val label=if(cut<original.length)original.take(cut)+"…" else original
                            val bubbleW=(labelPaint.measureText(label)+2f*labelPadPx)
                                .coerceAtMost(size.width-12f)
                            val centerX=center.x.coerceIn(bubbleW/2f+6f,size.width-bubbleW/2f-6f)
                            val below=center.y+fh/2f+labelGapPx+labelHeightPx/2f
                            val above=center.y-fh/2f-labelGapPx-labelHeightPx/2f
                            val labelY=listOf(below,above).map { camera.insetCoordinate(it,size.height,labelHeightPx/2f+6f) }
                            val clearY=labelY.firstOrNull { cy ->
                                val rect=android.graphics.RectF(centerX-bubbleW/2f,cy-labelHeightPx/2f,
                                    centerX+bubbleW/2f,cy+labelHeightPx/2f)
                                occupiedLabelBoxes.none { android.graphics.RectF.intersects(it,rect) }
                            }
                            if(clearY!=null || selected || f.id==highlightId) {
                            val centerY=clearY ?: labelY.first()
                            val origin=Offset(centerX-bubbleW/2f,centerY-labelHeightPx/2f)
                            occupiedLabelBoxes.add(android.graphics.RectF(origin.x,origin.y,origin.x+bubbleW,origin.y+labelHeightPx))
                            drawRoundRect(
                                Color(0xF20B2332),topLeft=origin,
                                size=Size(bubbleW,labelHeightPx),cornerRadius=CornerRadius(8f)
                            )
                            drawRoundRect(
                                if(selected)Amber.copy(alpha=.75f) else Mint.copy(alpha=.38f),
                                topLeft=origin,size=Size(bubbleW,labelHeightPx),
                                cornerRadius=CornerRadius(8f),style=Stroke(width=1.25f)
                            )
                            drawIntoCanvas { cv ->
                                cv.nativeCanvas.drawText(label,centerX,
                                    centerY-(labelPaint.ascent()+labelPaint.descent())/2f,labelPaint)
                            }
                            }
                        }
                    }
                    // 4.15: dotted stretch from the last structure visited to the next one.
                    val guideFrom=guide.from;val guideNext=guide.next
                    if(guideFrom!=null && guideNext!=null) {
                        val a=camera.toScreen(PointMetres(guideFrom.x,guideFrom.y));val b=camera.toScreen(PointMetres(guideNext.x,guideNext.y))
                        // Right-angled stretch through the corner that crosses fewer structures.
                        val corner=camera.toScreen(PickRoute.elbow(guideFrom,guideNext,state.placements))
                        val dots=PathEffect.dashPathEffect(floatArrayOf(3f,16f))
                        drawLine(Amber,Offset(a.x,a.y),Offset(corner.x,corner.y),strokeWidth=5f,cap=androidx.compose.ui.graphics.StrokeCap.Round,pathEffect=dots)
                        drawLine(Amber,Offset(corner.x,corner.y),Offset(b.x,b.y),strokeWidth=5f,cap=androidx.compose.ui.graphics.StrokeCap.Round,pathEffect=dots)
                        drawCircle(Color(0xFF07121F),11f,Offset(a.x,a.y));drawCircle(Sky,8f,Offset(a.x,a.y))
                    }
                    drawIntoCanvas { cv ->
                        cv.nativeCanvas.drawText(numberM(roomWidth),topLeft.x+sizeFloor.width/2f,topLeft.y-14f,rulerPaint)
                        cv.nativeCanvas.save()
                        cv.nativeCanvas.rotate(-90f,topLeft.x-15f,topLeft.y+sizeFloor.height/2f)
                        cv.nativeCanvas.drawText(numberM(roomHeight),topLeft.x-15f,topLeft.y+sizeFloor.height/2f,rulerPaint)
                        cv.nativeCanvas.restore()
                    }
                    }
                }
                Row(Modifier.align(Alignment.TopStart).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Surface(shape=RoundedCornerShape(11.dp),color=PanelBg.copy(alpha=.96f),border=BorderStroke(1.dp,Mint.copy(alpha=.23f))) {
                        Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                            Icon(Icons.Outlined.Layers,null,modifier=Modifier.size(13.dp),tint=Mint)
                            Spacer(Modifier.width(6.dp))
                            Text(if(layer==MapLayer.NORMAL)"PIANTA IN SCALA" else layer.label.uppercase(Locale.ITALY),fontSize=9.sp,color=Mint,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                        }
                    }
                    if(colliding.isNotEmpty()) Surface(shape=RoundedCornerShape(11.dp),color=Danger.copy(alpha=.2f)) {
                        Text("${colliding.size} sovrapposte",Modifier.padding(horizontal=9.dp,vertical=9.dp),fontSize=9.sp,color=Danger)
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    SmallFloatingControl(Icons.Outlined.Undo,"Annulla ${state.mapUndoLabel ?: ""}",enabled=state.mapUndoLabel!=null && !state.busy,onClick=model::undoMap)
                    SmallFloatingControl(Icons.Outlined.Redo,"Ripeti ${state.mapRedoLabel ?: ""}",enabled=state.mapRedoLabel!=null && !state.busy,onClick=model::redoMap)
                }
                Column(Modifier.align(Alignment.CenterEnd).padding(end=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    SmallFloatingControl(Icons.Outlined.Add,"Zoom +",onClick={zoom=(zoom*1.25f).coerceAtMost(4f)})
                    SmallFloatingControl(Icons.Outlined.Remove,"Zoom -",onClick={zoom=(zoom/1.25f).coerceAtLeast(.7f)})
                    SmallFloatingControl(Icons.Outlined.CenterFocusStrong,"Inquadra tutto",onClick={zoom=1f;panX=0f;panY=0f})
                }
                Surface(Modifier.align(Alignment.BottomStart).padding(10.dp),shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.96f)) {
                    Text(when{
                        state.locationPickItemIds!=null -> "Trascina per spostarti, tocca una struttura per sceglierla"
                        layer!=MapLayer.NORMAL && mode==EditorTool.VIEW -> layer.legend+if(layer!=MapLayer.EMPTY) " · rosso = massimo" else ""
                        mode==EditorTool.WALL -> if(wallAnchor==null)"Tocca il punto iniziale oppure trascina" else if(draftCloses) "Rilascia: la forma si chiude" else "Tocca per continuare · vicino a un angolo la forma si chiude"
                        mode==EditorTool.FURNITURE -> "Tocca una struttura; trascina solo se sbloccata"
                        else -> "Tocca una struttura per il contenuto, una parete per misurarla"
                    },Modifier.padding(horizontal=11.dp,vertical=8.dp),fontSize=10.sp,color=TextSecondary)
                }
            }
        }
        if(selectedWall!=null && mode!=EditorTool.WALL) {
            Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp),shape=RoundedCornerShape(16.dp),color=PanelBg,border=BorderStroke(1.dp,Amber.copy(alpha=.4f))) {
                Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Parete · ${numberM(PlanGeometry.wallLength(selectedWall))}",fontSize=13.sp,fontWeight=FontWeight.Bold)
                        Text(if(selectedWall.source=="ar")"Rilevata con AR" else "Disegnata a mano",fontSize=10.sp,color=TextSecondary)
                    }
                    TextButton(onClick={wallLengthFor=selectedWall.id}){Text("Lunghezza")}
                    IconButton(onClick={model.deleteWall(selectedWall.id);selectedWallId=null}){Icon(Icons.Outlined.DeleteOutline,"Elimina parete",tint=Danger)}
                    IconButton(onClick={selectedWallId=null}){Icon(Icons.Outlined.Close,"Chiudi")}
                }
            }
        }
        if(!guiding) Row(Modifier.padding(horizontal=14.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
            when(mode) {
                EditorTool.WALL -> {
                    FilterChip(selected=snapGrid,onClick={snapGrid=!snapGrid},label={Text("Griglia 25 cm",fontSize=11.sp)},leadingIcon={Icon(Icons.Outlined.GridOn,null,Modifier.size(15.dp))})
                    FilterChip(selected=orthogonal,onClick={orthogonal=!orthogonal},label={Text("90°",fontSize=11.sp)})
                    if(wallAnchor!=null)TextButton(onClick={wallAnchor=null;draftEnd=null}){Text("Fine",fontSize=12.sp)}
                }
                EditorTool.FURNITURE -> {
                    FilterChip(selected=snapGrid,onClick={snapGrid=!snapGrid},label={Text("Griglia",fontSize=11.sp)})
                    FilterChip(selected=magnet,onClick={magnet=!magnet},label={Text("Magnete",fontSize=11.sp)})
                    FilterChip(selected=showLabels,onClick={showLabels=!showLabels},label={Text("Nomi",fontSize=11.sp)})
                }
                EditorTool.VIEW -> {
                    MapLayer.entries.forEach { option ->
                        if(option==MapLayer.NORMAL) FilterChip(selected=layer==option,onClick={layer=option},label={Text(option.label,fontSize=11.sp)})
                        else FilterChip(selected=layer==option,onClick={layer=option},label={Text(option.label,fontSize=11.sp)},
                            leadingIcon={Icon(Icons.Outlined.Palette,null,Modifier.size(14.dp))})
                    }
                    FilterChip(selected=showGrid,onClick={showGrid=!showGrid},label={Text("Griglia",fontSize=11.sp)})
                    FilterChip(selected=showLabels,onClick={showLabels=!showLabels},label={Text("Nomi",fontSize=11.sp)})
                }
            }
            Text("${(zoom*100).toInt()}%",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
        }
        if(mode==EditorTool.WALL){
            Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${walls.size} pareti · ${manualWalls.size} disegnate",fontSize=11.sp,color=TextSecondary,modifier=Modifier.weight(1f))
                if(manualWalls.isNotEmpty()) TextButton(onClick={wallAnchor=null;draftEnd=null;model.deleteWall(manualWalls.last().id)}) {
                    Icon(Icons.Outlined.Undo,null,Modifier.size(15.dp));Spacer(Modifier.width(4.dp));Text("Elimina ultima",fontSize=11.sp)
                }
            }
        }else if(mode==EditorTool.FURNITURE){
            if(placement==null){
                Button(onClick={showNewObject=true},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),shape=RoundedCornerShape(14.dp)){
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Inserisci struttura dove stai guardando")
                }
            }else{
                val overlaps=PlanGeometry.collisions(placement,state.placements)
                Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp),shape=RoundedCornerShape(16.dp),color=PanelBg,border=BorderStroke(1.dp,(if(overlaps.isEmpty())Mint else Danger).copy(alpha=.3f))){
                    Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Column(Modifier.weight(1f)){
                                Text(placement.label,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text("${placement.kind} · ${numberM(placement.width)} × ${numberM(placement.depth)}"+
                                    (if(placement.holdsStock)" · ${mapItems[placement.id]?.size?:0} articoli" else ""),fontSize=10.sp,color=TextSecondary)
                            }
                            IconButton(onClick={model.lockPlacement(placement.id,!placement.locked)}) {Icon(if(placement.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,"Blocco",tint=if(placement.locked)Amber else Mint)}
                            IconButton(onClick={model.duplicatePlacement(placement.id)}) {Icon(Icons.Outlined.ContentCopy,"Duplica",tint=Sky)}
                            IconButton(onClick={showRowFor=placement.id}) {Icon(Icons.Outlined.ViewWeek,"Crea in fila",tint=Sky)}
                            IconButton(onClick={detailsId=placement.id}) {Icon(Icons.Outlined.Tune,"Modifica",tint=Sky)}
                        }
                        if(overlaps.isNotEmpty()) Text("⚠ Si sovrappone a ${overlaps.joinToString{it.label}}",fontSize=11.sp,color=Danger)
                    }
                }
            }
        }else{
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=3.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${state.placements.count{it.holdsStock}} strutture · ${state.items.count{it.placementId==null}} articoli senza ubicazione",
                    fontSize=11.sp,color=TextSecondary,modifier=Modifier.weight(1f))
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
        val at=viewCentre()
        model.addPlacement(kind,label,at.x,at.y);showNewObject=false
    }
    showRowFor?.let { id -> state.placements.firstOrNull{it.id==id}?.let { p ->
        PlacementRowDialog(p,onClose={showRowFor=null}) { count,gap,vertical -> model.createPlacementRow(p.id,count,gap,vertical);showRowFor=null }
    } }
    wallLengthFor?.let { id -> state.walls.firstOrNull{it.id==id}?.let { wall ->
        WallLengthDialog(wall,onClose={wallLengthFor=null}) { length -> model.setWallLength(wall.id,length);wallLengthFor=null }
    } }
    pickPositionFor?.let { id -> state.placements.firstOrNull{it.id==id}?.let { p ->
        PositionDialog(p,"Scegli la posizione",onClose={pickPositionFor=null}) { level,slot ->
            model.completeLocationPick(p.id,level,slot);pickPositionFor=null
        }
    } }
    qrFor?.let { id -> state.placements.firstOrNull{it.id==id}?.let { p -> PlacementQrDialog(p,state.items,onDismiss={qrFor=null}) } }
    detailsId?.let { id ->
        state.placements.firstOrNull{it.id==id}?.let { f ->
            PlacementDetailDialog(f,state.items,model,onQr={qrFor=f.id},onDismiss={detailsId=null})
        }
    }
}

@Composable private fun SmallFloatingControl(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,enabled:Boolean=true,onClick:()->Unit){
    Surface(shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.97f),border=BorderStroke(1.dp,Hairline.copy(alpha=.1f))){
        IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(38.dp)){Icon(icon,description,tint=if(enabled)TextPrimary else TextSecondary.copy(alpha=.4f),modifier=Modifier.size(18.dp))}
    }
}

/** Compact −/+ control; [none] shows "—" for value 0 when the field is optional. */
@Composable fun Stepper(label:String,value:Int,range:IntRange,none:Boolean=false,onChange:(Int)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=12.sp,color=TextSecondary,modifier=Modifier.weight(1f))
        IconButton(onClick={onChange((value-1).coerceIn(range))},enabled=value>range.first) {Icon(Icons.Outlined.Remove,"Diminuisci $label")}
        Text(if(none&&value==0)"—" else value.toString(),fontWeight=FontWeight.Bold,fontSize=16.sp,modifier=Modifier.widthIn(min=28.dp))
        IconButton(onClick={onChange((value+1).coerceIn(range))},enabled=value<range.last) {Icon(Icons.Outlined.Add,"Aumenta $label")}
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
            Input(w,{w=it},"Larghezza (metri)",number=true)
            Input(h,{h=it},"Profondità (metri)",number=true)
        }
    },confirmButton={TextButton(onClick={onSave(parsedW!!,parsedH!!)},enabled=parsedW!=null&&parsedW in 2f..150f&&parsedH!=null&&parsedH in 2f..150f){Text("Applica")}},dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable private fun CreatePlacementDialog(onCancel:()->Unit,onCreate:(String,String)->Unit){
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

@Composable private fun PlacementRowDialog(p:Placement,onClose:()->Unit,onCreate:(Int,Float,Boolean)->Unit){
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

@Composable private fun WallLengthDialog(wall:Wall,onClose:()->Unit,onSave:(Float)->Unit){
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

@Composable private fun PlacementDetailDialog(p:Placement,items:List<StockItem>,model:StockViewModel,onQr:()->Unit,onDismiss:()->Unit){
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
    AlertDialog(onDismissRequest=onDismiss,title={Text(p.label)},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name,{name=it.take(80)},label={Text("Nome")},singleLine=true,modifier=Modifier.weight(1f))
                TextButton(onClick={model.renamePlacement(p.id,name)},enabled=name.isNotBlank()&&name.trim()!=p.label){Text("Rinomina")}
            }
            Surface(color=Surface2,shape=RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(p.locked)Icons.Outlined.Lock else Icons.Outlined.LockOpen,null,tint=if(p.locked)Amber else Mint)
                    Spacer(Modifier.width(8.dp));Text(if(p.locked)"Posizione bloccata" else "Trascinamento attivo",fontSize=12.sp,modifier=Modifier.weight(1f))
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
            Button(onClick={model.resizePlacement(p.id,wVal!!,dVal!!,angle)},enabled=valid,modifier=Modifier.fillMaxWidth()) {Text("Aggiorna dimensioni e rotazione")}
            if(!p.holdsStock) {
                Text("${p.kind}: elemento di riferimento, non contiene articoli.",fontSize=12.sp,color=TextSecondary)
            } else {
                Text("RIPIANI E POSIZIONI",fontWeight=FontWeight.Bold,color=Mint,fontSize=10.sp,letterSpacing=1.sp)
                Stepper("Ripiani (dal basso)",levels,1..PlacementKinds.MAX_LEVELS){levels=it}
                Stepper("Posizioni per ripiano",slots,1..PlacementKinds.MAX_SLOTS){slots=it}
                if(levels!=p.levels||slots!=p.slots) Button(onClick={model.updatePlacementLayout(p.id,levels,slots)},modifier=Modifier.fillMaxWidth()) {
                    Text("Applica ${levels} × ${slots}")
                }
                OutlinedButton(onClick=onQr,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.QrCode2,null);Spacer(Modifier.width(8.dp));Text("Etichetta QR della struttura")
                }
                Text("ARTICOLI COLLEGATI · ${items.count{it.placementId==p.id}}",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp)
                Input(articleQuery,{articleQuery=it},"Cerca articolo o codice")
                if(inside.size>100 || free.size>100) Text("Mostrati i primi 100 risultati per elenco. Affina la ricerca.",fontSize=12.sp)
                if(inside.isEmpty())Text("Nessun articolo in questa ubicazione",color=TextSecondary,fontSize=12.sp)
                inside.take(100).forEach{item ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(item.name,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(listOf(item.sku,"${amount(item.quantity)} ${item.unit}",Locations.code(item.shelfLevel,item.shelfSlot))
                                .filter{it.isNotBlank()}.joinToString(" · "),fontSize=10.sp,color=if(item.lowStock)Amber else TextSecondary)
                        }
                        IconButton(onClick={model.assignItem(item.id,null)}){Icon(Icons.Outlined.RemoveCircleOutline,"Scollega",tint=Amber)}
                    }
                }
                if(free.isNotEmpty()){
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("ASSEGNA ARTICOLI SENZA UBICAZIONE",color=Mint,fontWeight=FontWeight.Bold,fontSize=10.sp,modifier=Modifier.weight(1f))
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
                    if(p.levels>1) Stepper("Ripiano",assignLevel,0..p.levels,none=true){assignLevel=it}
                    if(p.slots>1) Stepper("Posizione",assignSlot,0..p.slots,none=true){assignSlot=it}
                    Button(onClick={
                        model.assignItems(chosen.toList(),p.id,assignLevel.takeIf{it>0},assignSlot.takeIf{it>0});chosen=emptySet()
                    },enabled=chosen.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                        Text(if(chosen.size<=1)"Assegna articolo" else "Assegna ${chosen.size} articoli")
                    }
                }
            }
            TextButton(onClick={askDelete=true}) {Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(5.dp));Text("Elimina struttura",color=Danger)}
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
    if(askDelete)AlertDialog(onDismissRequest={askDelete=false},title={Text("Eliminare ${p.label}?")},text={Text("Gli articoli rimangono nell'inventario senza ubicazione. Puoi annullare con il pulsante ↶ sulla mappa.")},confirmButton={TextButton(onClick={model.deletePlacement(p.id);askDelete=false;onDismiss()}){Text("Elimina",color=Danger)}},dismissButton={TextButton(onClick={askDelete=false}){Text("Annulla")}})
}
