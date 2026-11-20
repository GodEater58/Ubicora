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
/** 4.23: every place that holds stock has one colour; colour on the plan means a state (finishing, found), never a kind. */
private val StockNeutral = Color(0xFF2A4256)

private fun numberM(value: Float):String = String.format(Locale.ITALY,"%.2f m", value)
private fun floorColor(kind: String): Color = when(kind) {
    "Armadio" -> CabinetBlue
    "Banco" -> BenchGold
    "Cassetto" -> Color(0xFF66719F)
    "Area" -> Color(0xFF456D67)
    "Porta","Cancello" -> DoorBrown
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
    var qrFor by remember {mutableStateOf<Long?>(null)}
    var showGrid by remember {mutableStateOf(false)}   // 4.20: clean plan at rest; the grid is always on while editing
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
    // 4.20: search, "dov'è" and the plan turned to fill a portrait phone.
    var query by remember(warehouse.id){mutableStateOf("")}
    var foundItemId by remember(warehouse.id){mutableStateOf<Long?>(null)}
    var showUnplaced by remember{mutableStateOf(false)}
    var quickPick by remember{mutableStateOf<Long?>(null)}
    var openItemId by remember{mutableStateOf<Long?>(null)}
    val editing=mode!=EditorTool.VIEW
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    // Decided on the whole screen, not on the map box, so it does not flip while the results list opens.
    val turned=remember(roomWidth,roomHeight,config.screenWidthDp,config.screenHeightDp){
        PlanViewport.shouldTurn(config.screenWidthDp.toFloat(),config.screenHeightDp*.62f,roomWidth,roomHeight,20f)
    }
    fun viewport(w:Float,h:Float,z:Float,px:Float,py:Float,margin:Float)=PlanViewport(w,h,roomWidth,roomHeight,z,px,py,margin,turned)
    val results=remember(query,state.items,state.placements){MapSearch.results(query,state.items,state.placements)}
    val found=state.items.firstOrNull{it.id==foundItemId}
    val foundPlace=found?.placementId?.let{id->state.placements.firstOrNull{it.id==id}}
    val walk=remember(foundPlace,state.placements,state.walls,roomWidth,roomHeight){foundPlace?.let{MapRoute.walk(state.placements,it,state.walls,roomWidth,roomHeight)}}
    val searching=query.isNotBlank() && found==null
    val lit:Set<Long> = when { found!=null -> setOfNotNull(foundPlace?.id); searching -> results.lit; else -> emptySet() }
    val dimming=lit.isNotEmpty()
    val unplaced=remember(state.items,state.placements){
        val ids=state.placements.map{it.id}.toSet()
        state.items.filter{it.placementId==null || it.placementId !in ids}
    }
    androidx.activity.compose.BackHandler(enabled=found!=null) { foundItemId=null }
    androidx.activity.compose.BackHandler(enabled=found==null && query.isNotEmpty()) { query="" }
    androidx.activity.compose.BackHandler(enabled=editing && wallAnchor==null && state.locationPickItemIds==null) { mode=EditorTool.VIEW;selectedId=null;selectedWallId=null }

    LaunchedEffect(warehouse.id,zoom,panX,panY) { model.saveMapCamera(warehouse.id,zoom,panX,panY) }
    // "Mostra in pianta" and shelf QR: center, zoom and pulse the structure.
    LaunchedEffect(state.mapFocusPlacementId,canvasW,canvasH) {
        val focus=state.mapFocusPlacementId ?: return@LaunchedEffect
        if(canvasW<=0f||canvasH<=0f) return@LaunchedEffect
        val p=state.placements.firstOrNull{it.id==focus}
        if(p!=null) {
            mode=EditorTool.VIEW;selectedWallId=null;selectedId=p.id;highlightId=p.id
            val z=2f
            val cam=viewport(canvasW,canvasH,z,0f,0f,marginPx)
            val at=cam.toScreen(PointMetres(p.x,p.y))
            val pan=cam.clampedPan(canvasW/2f-at.x,canvasH/2f-at.y)
            zoom=z;panX=pan.x;panY=pan.y
        }
        model.acknowledgeMapFocus()
    }
    LaunchedEffect(highlightId) { if(highlightId!=null) { delay(5000);highlightId=null } }
    // 4.20: "Dov'è sulla mappa" from an article.
    LaunchedEffect(state.mapFindItemId) {
        val id=state.mapFindItemId ?: return@LaunchedEffect
        mode=EditorTool.VIEW;selectedWallId=null;foundItemId=id
        model.consumeMapFind()
    }
    // 4.20: what is lit (search results, the article found and its door) is framed on the screen.
    var framed by remember(warehouse.id){mutableStateOf(false)}
    LaunchedEffect(lit,walk?.from?.id,canvasW,canvasH,turned) {
        // Search closed: back to the whole plan, as it was before the search framed the results.
        if(lit.isEmpty()) { if(framed) { zoom=1f;panX=0f;panY=0f;framed=false }; return@LaunchedEffect }
        if(canvasW<=0f || canvasH<=0f) return@LaunchedEffect
        val boxes=state.placements.filter{it.id in lit}+listOfNotNull(walk?.from)
        if(boxes.isEmpty()) return@LaunchedEffect
        val pad=.8f
        val fit=viewport(canvasW,canvasH,zoom,panX,panY,marginPx).fitting(
            boxes.minOf{it.x-maxOf(it.width,it.depth)/2f}-pad,boxes.minOf{it.y-maxOf(it.width,it.depth)/2f}-pad,
            boxes.maxOf{it.x+maxOf(it.width,it.depth)/2f}+pad,boxes.maxOf{it.y+maxOf(it.width,it.depth)/2f}+pad,
            maxZoom=if(found!=null) 2.6f else 2f)
        zoom=fit.zoom;panX=fit.panX;panY=fit.panY;framed=true
    }
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
        // 4.20: the shelf seen from the front shows where to put it, place by place.
        if(p.hasPositions) model.openShelf(p.id) else model.completeLocationPick(p.id,null,null)
    }
    fun viewCentre():PointMetres {
        if(canvasW<=0f) return PointMetres(roomWidth/2f,roomHeight/2f)
        val cam=viewport(canvasW,canvasH,zoom,panX,panY,marginPx)
        return cam.bounded(cam.toWorld(PointMetres(canvasW/2f,canvasH/2f)))
    }

    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        // 4.15 "tour mode": during a guided picking the editing tools leave the screen and the title follows the walk.
        val guiding=state.pickList.any{!it.done}
        LaunchedEffect(guiding) { if(guiding) { mode=EditorTool.VIEW;layer=MapLayer.NORMAL;wallAnchor=null;draftEnd=null;selectedWallId=null } }
        if(guiding) GuidedMapHeader(state.pickList,onClose=model::clearPickList)
        if(!guiding && editing) Column(Modifier.padding(horizontal=16.dp).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            // 4.20: drawing tools live here, behind "Modifica": the map at rest shows only the plan and the search.
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={mode=EditorTool.VIEW;selectedId=null;selectedWallId=null;wallAnchor=null;draftEnd=null},
                    modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Fine")
                }
                Column(Modifier.weight(1f)) {
                    Text("MODIFICA PIANTA",fontSize=10.sp,letterSpacing=1.4.sp,fontWeight=FontWeight.Bold,color=Mint,maxLines=1)
                    Text(warehouse.name,fontSize=18.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                var editMenu by remember { mutableStateOf(false) }
                Box {
                    FilledTonalIconButton(onClick={editMenu=true},modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.MoreVert,"Altri strumenti della pianta")}
                    DropdownMenu(expanded=editMenu,onDismissRequest={editMenu=false}) {
                        DropdownMenuItem(text={Text("Dimensioni dell'ambiente")},leadingIcon={Icon(Icons.Outlined.Straighten,null)},onClick={
                            editMenu=false;showDimensions=true
                        })
                        DropdownMenuItem(text={Text("Rilievo AR delle pareti")},leadingIcon={Icon(Icons.Outlined.CameraAlt,null)},onClick={
                            editMenu=false;onAr()
                        })
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                listOf(
                    Triple(EditorTool.FURNITURE, "Strutture", Icons.Outlined.GridView),
                    Triple(EditorTool.WALL, "Pareti", Icons.Outlined.Edit)
                ).forEach { (tool,title,icon) ->
                    val active=mode==tool
                    Surface(
                        modifier=Modifier.weight(1f).heightIn(min=48.dp).clickable {mode=tool;wallAnchor=null;draftEnd=null;selectedWallId=null},
                        shape=RoundedCornerShape(14.dp),
                        color=if(active) Mint.copy(alpha=.15f) else Surface2,
                        border=BorderStroke(1.dp,if(active) Mint.copy(alpha=.66f) else Hairline.copy(alpha=.07f))
                    ) {
                        Row(Modifier.padding(vertical=12.dp,horizontal=5.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            Icon(icon,null,tint=if(active) Mint else TextSecondary,modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(title,color=if(active) TextPrimary else TextSecondary,fontSize=14.sp,fontWeight=if(active) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if(!guiding && !editing) Column(Modifier.padding(horizontal=16.dp).padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(found!=null) {
                // 4.20 "Dov'è": back to the results (or to the plan), and how far it is.
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(onClick={foundItemId=null},modifier=Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.ArrowBack,if(query.isNotBlank()) "Torna ai risultati" else "Torna alla mappa")
                    }
                    Column(Modifier.weight(1f)) {
                        val index=results.items.indexOfFirst{it.id==found.id}
                        Text(if(query.isNotBlank() && index>=0) "Risultato ${index+1} di ${results.items.size} per «${query.trim()}»" else "Dov'è",
                            fontSize=13.sp,color=TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(when { foundPlace==null -> "Non ha ancora un posto"; walk!=null -> MapRoute.describe(walk); else -> foundPlace.label },
                            fontSize=15.sp,fontWeight=FontWeight.Bold,color=Amber,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            } else {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    OutlinedTextField(query,onValueChange={query=it.take(60)},singleLine=true,
                        placeholder={Text("Cerca un articolo o uno scaffale",maxLines=1,overflow=TextOverflow.Ellipsis)},
                        leadingIcon={Icon(Icons.Outlined.Search,null)},
                        trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"Cancella la ricerca")} },
                        keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={keyboard?.hide()}),
                        shape=RoundedCornerShape(26.dp),modifier=Modifier.weight(1f))
                    if(model.mapEditable) FilledTonalIconButton(onClick={mode=EditorTool.FURNITURE;selectedId=null;query=""},modifier=Modifier.size(52.dp)) {
                        Icon(Icons.Outlined.Edit,"Modifica la pianta")
                    }
                }
                if(query.isBlank()) Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(warehouse.name,fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,fill=false))
                    Spacer(Modifier.width(8.dp))
                    Text("${numberM(roomWidth)} × ${numberM(roomHeight)}".replace(" m ×"," ×"),fontSize=13.sp,color=TextSecondary,maxLines=1,modifier=Modifier.weight(1f))
                    var layersMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick={layersMenu=true},modifier=Modifier.heightIn(min=44.dp)) {
                            Icon(Icons.Outlined.Layers,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))
                            Text(if(layer==MapLayer.NORMAL) "Strati" else layer.label)
                        }
                        DropdownMenu(expanded=layersMenu,onDismissRequest={layersMenu=false}) {
                            MapLayer.entries.forEach { option ->
                                DropdownMenuItem(text={Text(option.label,fontWeight=if(option==layer) FontWeight.Bold else FontWeight.Normal)},
                                    leadingIcon={ if(option==layer) Icon(Icons.Outlined.Check,null,tint=Mint) else Spacer(Modifier.size(24.dp)) },
                                    onClick={layer=option;layersMenu=false})
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text={Text("Griglia")},leadingIcon={ if(showGrid) Icon(Icons.Outlined.Check,null,tint=Mint) else Spacer(Modifier.size(24.dp)) },
                                onClick={showGrid=!showGrid;layersMenu=false})
                            DropdownMenuItem(text={Text("Nomi delle strutture")},leadingIcon={ if(showLabels) Icon(Icons.Outlined.Check,null,tint=Mint) else Spacer(Modifier.size(24.dp)) },
                                onClick={showLabels=!showLabels;layersMenu=false})
                            HorizontalDivider()
                            DropdownMenuItem(text={Text("Esporta pianta in PDF")},leadingIcon={Icon(Icons.Outlined.PictureAsPdf,null)},onClick={
                                layersMenu=false;pdfLauncher.launch("Ubicora_pianta_${warehouse.id}.pdf")
                            })
                            DropdownMenuItem(text={Text("Etichette QR di tutte le strutture")},leadingIcon={Icon(Icons.Outlined.QrCode2,null)},
                                enabled=state.placements.any{it.holdsStock},onClick={
                                layersMenu=false
                                val labels=state.placements.filter{it.holdsStock}.map{LabelSpec.placement(it)}
                                try { QrLabels.printSheet(context,"Ubicazioni ${warehouse.name}",labels) }
                                catch(e:Exception) { Toast.makeText(context,e.localizedMessage ?: "Stampa non disponibile",Toast.LENGTH_LONG).show() }
                            })
                            DropdownMenuItem(text={Text("Impostazioni")},leadingIcon={Icon(Icons.Outlined.Settings,null)},onClick={layersMenu=false;onSettings()})
                        }
                    }
                }
                if(searching) MapResultsCard(results,state.placements,model,query,
                    onItem={ item -> foundItemId=item.id },
                    onShelf={ shelf -> model.openShelf(shelf.id) })
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
                val camera=viewport(w,h,zoom,panX,panY,marginPx)
                // Screen-space typography: never shrink text with the map camera.
                val labelPx=with(density){14.sp.toPx()}
                val selectedLabelPx=with(density){16.sp.toPx()}
                val labelHeightPx=with(density){22.dp.toPx()}
                val labelGapPx=with(density){7.dp.toPx()}
                val badgePx=with(density){12.dp.toPx()}
                val badgeTextPx=with(density){11.sp.toPx()}
                val rulerPx=with(density){11.sp.toPx()}
                val measurePx=with(density){13.sp.toPx()}
                val wallTapPx=with(density){18.dp.toPx()}
                // Paints are allocated once per composition, not once per structure per frame.
                val labelPaint=remember(labelPx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.WHITE;typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);textAlign=AndroidPaint.Align.CENTER
                    // 4.20: no box behind the names any more: a soft shadow keeps them readable on the floor and the grid.
                    setShadowLayer(4f,0f,1f,android.graphics.Color.argb(220,7,18,31))
                }}
                val codePx=with(density){20.sp.toPx()}
                val codePaint=remember(codePx){AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    textSize=codePx;typeface=Typeface.create("sans-serif",Typeface.BOLD);textAlign=AndroidPaint.Align.CENTER
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
                    val adjusted=viewport(w,h,zoom,panX,panY,marginPx)
                        .clampedPan(panX,panY)
                    panX=adjusted.x;panY=adjusted.y
                }
                // One gesture owner. Unlike overlapping tap/drag handlers, this cannot steal its own wall strokes.
                Canvas(Modifier.fillMaxSize().clipToBounds().pointerInput(mode,warehouse.id,roomWidth,roomHeight,turned) {
                    // 4.15: double tap on the empty floor.
                    var lastTapAt=0L
                    var lastTapPos=PointMetres(0f,0f)
                    awaitEachGesture {
                        val down=awaitFirstDown(requireUnconsumed=false)
                        val cameraNow=viewport(size.width.toFloat(),size.height.toFloat(),zoom,panX,panY,34.dp.toPx())
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
                                val current=viewport(size.width.toFloat(),size.height.toFloat(),zoom,panX,panY,34.dp.toPx())
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
                                    // 4.20: the finger moves on the screen; on a turned plan that is another direction in the room.
                                    val delta=cameraNow.toWorldDelta(last.x-initial.x,last.y-initial.y)
                                    val pos=cameraNow.bounded(PointMetres(initialFurniture.x+delta.x,initialFurniture.y+delta.y))
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
                        if((transforming || cancelled) && target!=null && initialFurniture!=null) model.previewMove(target.id,initialFurniture.x,initialFurniture.y)
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
                                    val current=viewport(size.width.toFloat(),size.height.toFloat(),zoom,panX,panY,34.dp.toPx())
                                    val next=current.transformed(1.8f,initial,PointMetres(0f,0f))
                                    zoom=next.zoom;panX=next.panX;panY=next.panY
                                }
                                return@awaitEachGesture
                            }
                            lastTapAt=now;lastTapPos=initial
                        }
                        // 4.20: walls are measured while editing; exploring, a tap on the floor does nothing.
                        val tappedWall=if(!dragged && hit==null && mode==EditorTool.FURNITURE)
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
                            selectedWallId=null;selectedId=null
                            // 4.20: a structure opens seen from the front; doors and columns only say what they are.
                            if(hit!=null) {
                                if(hit.holdsStock) model.openShelf(hit.id)
                                else Toast.makeText(context,"${hit.label.ifBlank{hit.kind}} · ${hit.kind}",Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) {
                    clipRect {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D2335),FloorBackground)),size=size)
                    val step=.5f
                    // 4.20: the grid is a drawing aid: always while editing, on request while exploring.
                    if(showGrid || editing){
                        for(i in 0..ceil(camera.shownWidth/step).toInt()) {
                            val x=camera.originX+i*step*camera.scale
                            if(x in 0f..size.width)drawLine(Color.White.copy(alpha=if(i%2==0).13f else .055f),Offset(x,0f),Offset(x,size.height),strokeWidth=if(i%2==0)1.1f else .7f)
                        }
                        for(i in 0..ceil(camera.shownHeight/step).toInt()){
                            val y=camera.originY+i*step*camera.scale
                            if(y in 0f..size.height)drawLine(Color.White.copy(alpha=if(i%2==0).13f else .055f),Offset(0f,y),Offset(size.width,y),strokeWidth=if(i%2==0)1.1f else .7f)
                        }
                    }
                    val topLeft=PointMetres(camera.originX,camera.originY)
                    val sizeFloor=Size(camera.shownWidth*camera.scale,camera.shownHeight*camera.scale)
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
                    // 4.20: every structure's place on the screen first, so that no name is written over one of them.
                    val footprints=state.placements.associate { f ->
                        val c=camera.toScreen(PointMetres(f.x,f.y))
                        val deg=f.rotation+camera.turnDegrees
                        // 4.20.2: a gate takes also the track it slides along, so no name is written over it.
                        if(f.kind=="Cancello") {
                            val r=Math.toRadians(deg.toDouble()); val shift=.35f*f.width*camera.scale
                            f.id to MapLabels.footprint(c.x+shift*kotlin.math.cos(r).toFloat(),c.y+shift*kotlin.math.sin(r).toFloat(),
                                1.7f*f.width*camera.scale,maxOf(f.depth*camera.scale,12f),deg)
                        } else f.id to MapLabels.footprint(c.x,c.y,f.width*camera.scale,f.depth*camera.scale,deg)
                    }
                    for(f in state.placements.sortedBy { if(it.id==selectedId || it.id in lit) 2 else if(it.kind=="Corridoio") 0 else 1 }){
                        val pos=camera.toScreen(PointMetres(f.x,f.y));val center=Offset(pos.x,pos.y)
                        val fw=f.width*camera.scale;val fh=f.depth*camera.scale
                        val selected=f.id==selectedId
                        val items=mapItems[f.id].orEmpty()
                        val isLit=f.id in lit
                        val faded=dimming && !isLit && f.id!=walk?.from?.id   // the door where the walk starts stays visible
                        val fill=when {
                            isLit -> Color(0xFF4A3A1A)
                            faded -> Color(0xFF1B2D40)
                            !f.holdsStock -> floorColor(f.kind)
                            layer==MapLayer.NORMAL -> StockNeutral
                            layer==MapLayer.EMPTY -> if(items.isEmpty()) Amber.copy(alpha=.85f) else Idle
                            else -> (layerValues[f.id] ?: 0f).let { v -> if(v<=0f) Idle else heat(v/layerMax) }
                        }
                        rotate(f.rotation+camera.turnDegrees,pivot=center){
                            val box=Offset(center.x-fw/2,center.y-fh/2)
                            val drawnAsLine=f.kind=="Cancello"   // 4.20.1: the gate is a line across the opening, not a block
                            if(f.kind!="Corridoio" && !faded && !drawnAsLine)drawRoundRect(Color.Black.copy(alpha=.32f),topLeft=Offset(box.x+3f,box.y+4f),size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            if(!drawnAsLine || faded) drawRoundRect(if(drawnAsLine) fill.copy(alpha=.35f) else fill,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                            if(f.id in guide.done) {
                                drawRoundRect(Mint.copy(alpha=.30f),topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                                drawRoundRect(Mint,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),style=Stroke(width=3f))
                            }
                            if(f.id==guide.next?.id || isLit) {
                                if(!isLit) drawRoundRect(Amber.copy(alpha=.30f),topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f))
                                drawRoundRect(Amber,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),style=Stroke(width=4f))
                                drawRoundRect(Amber.copy(alpha=.5f),topLeft=Offset(box.x-9f,box.y-9f),size=Size(fw+18f,fh+18f),cornerRadius=CornerRadius(12f),style=Stroke(width=2f))
                            }
                            if(!faded) when(f.kind) {
                                // 4.20.1: a sliding gate: the gate across the opening and, dashed, the track it slides along.
                                "Cancello" -> {
                                    val midY=box.y+fh/2f
                                    drawLine(DoorBrown,Offset(box.x,midY),Offset(box.x+fw,midY),strokeWidth=(fh*.6f).coerceIn(3f,8f),cap=StrokeCap.Round)
                                    drawLine(Color.White.copy(alpha=.5f),Offset(box.x+fw,midY),Offset(box.x+fw*1.7f,midY),strokeWidth=1.5f,
                                        pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,5f)))
                                    val tip=box.x+fw*1.7f; val arrow=(fh*1.4f).coerceIn(5f,12f)
                                    drawLine(Color.White.copy(alpha=.5f),Offset(tip,midY),Offset(tip-arrow,midY-arrow*.6f),strokeWidth=1.5f)
                                    drawLine(Color.White.copy(alpha=.5f),Offset(tip,midY),Offset(tip-arrow,midY+arrow*.6f),strokeWidth=1.5f)
                                }
                                "Porta" -> drawArc(Color.White.copy(alpha=.5f),270f,90f,false,topLeft=Offset(box.x-fw,box.y+fh-fw),size=Size(fw*2,fw*2),
                                    style=Stroke(width=1.2f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(6f,5f))))
                                "Colonna" -> { drawLine(Color.White.copy(alpha=.4f),box,Offset(box.x+fw,box.y+fh),1.2f);drawLine(Color.White.copy(alpha=.4f),Offset(box.x+fw,box.y),Offset(box.x,box.y+fh),1.2f) }
                                else -> if(f.holdsStock && editing) {
                                    // 4.20: the positions of a shelf are drawn only while editing; at rest the plan stays clean.
                                    val dividers=if(f.slots>1) f.slots else 1
                                    for(i in 1 until dividers){
                                        val x=box.x+fw*i/dividers
                                        drawLine(Color.White.copy(alpha=.32f),Offset(x,box.y+2f),Offset(x,box.y+fh-2f),strokeWidth=1f)
                                    }
                                }
                            }
                            if(editing || selected || f.id in colliding) {
                                val outline=when { selected -> Amber; f.id in colliding -> Danger; else -> Color.White.copy(alpha=if(f.locked).35f else .68f) }
                                drawRoundRect(outline,topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),
                                    style=Stroke(width=if(selected||f.id in colliding)3f else 1.5f,
                                        pathEffect=if(f.kind=="Corridoio") PathEffect.dashPathEffect(floatArrayOf(10f,7f)) else null))
                            } else if(f.kind=="Corridoio") drawRoundRect(Color.White.copy(alpha=.25f),topLeft=box,size=Size(fw,fh),cornerRadius=CornerRadius(5f),
                                style=Stroke(width=1.2f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,7f))))
                            if(selected)drawRoundRect(Amber.copy(alpha=.17f),topLeft=Offset(box.x-6f,box.y-6f),size=Size(fw+12,fh+12),cornerRadius=CornerRadius(10f),style=Stroke(width=1.3f))
                            if(f.id==highlightId) drawRoundRect(Amber.copy(alpha=.25f+.5f*pulse),topLeft=Offset(box.x-10f-8f*pulse,box.y-10f-8f*pulse),
                                size=Size(fw+20f+16f*pulse,fh+20f+16f*pulse),cornerRadius=CornerRadius(14f),style=Stroke(width=3f))
                        }
                    }
                    // 4.20: names beside the structures, without boxes, never over a structure or another name.
                    // A name with no free place is left out (it appears zooming in), except the ones that matter now.
                    val solid=state.placements.filter{it.kind!="Corridoio"}.mapNotNull{f->footprints[f.id]?.let{f.id to it}}
                    val labelBoxes=mutableListOf<ScreenBox>()
                    val guideIds=setOfNotNull(guide.next?.id,walk?.from?.id)+guide.done
                    val badgeBoxes=mutableListOf<ScreenBox>()
                    // 4.23: at rest every structure shows how many of its articles are finishing.
                    val showLow=!guiding && !editing && !dimming && layer==MapLayer.NORMAL
                    if(guiding || showLow) for(f in state.placements) {
                        val low=mapItems[f.id].orEmpty().count{it.lowStock}
                        val count=if(guiding) mapItems[f.id].orEmpty().size else low
                        val visited=guiding && f.id in guide.done
                        if(count==0 && !visited) continue
                        val fp=footprints[f.id] ?: continue
                        val bubble=Offset(camera.insetCoordinate(fp.right,size.width,badgePx),camera.insetCoordinate(fp.top,size.height,badgePx))
                        drawCircle(if(visited) Mint else if(low>0)Amber else Mint,badgePx,bubble)
                        badgeBoxes.add(ScreenBox(bubble.x-badgePx,bubble.y-badgePx,bubble.x+badgePx,bubble.y+badgePx))
                        drawIntoCanvas { cv ->
                            cv.nativeCanvas.drawText(if(visited) "\u2713" else count.toString(),bubble.x,bubble.y-(badgePaint.ascent()+badgePaint.descent())/2f,badgePaint)
                        }
                    }
                    if(showLabels && size.width>60f && size.height>60f) {
                        val order=state.placements.sortedBy { f -> when { f.id in lit || f.id==selectedId || f.id==highlightId || f.id in guideIds -> 0; f.holdsStock -> 1; else -> 2 } }
                        for(f in order) {
                            val fp=footprints[f.id] ?: continue
                            if(fp.right<0f || fp.left>size.width || fp.bottom<0f || fp.top>size.height) continue
                            val important=f.id in lit || f.id==selectedId || f.id==highlightId || f.id in guideIds
                            val strong=important && !(f.id in guide.done && f.id !in lit)
                            labelPaint.textSize=if(strong) selectedLabelPx else labelPx
                            labelPaint.color=when { strong -> android.graphics.Color.rgb(255,201,120); dimming -> android.graphics.Color.rgb(132,151,169); else -> android.graphics.Color.WHITE }
                            val original=f.label.ifBlank { f.kind }
                            val maxTextWidth=(size.width*.45f).coerceAtLeast(24f)
                            var cut=original.length
                            while(cut>1 && labelPaint.measureText(if(cut<original.length)original.take(cut)+"…" else original)>maxTextWidth) cut--
                            val label=if(cut<original.length)original.take(cut)+"…" else original
                            val count=if(f.id in lit && searching) results.countByPlacement[f.id] ?: 0 else 0
                            val second=if(count>0) "$count ${if(count==1) "articolo" else "articoli"}" else null
                            val textW=maxOf(labelPaint.measureText(label),second?.let{labelPaint.measureText(it)*.85f} ?: 0f)
                            val textH=if(second!=null) labelHeightPx*1.8f else labelHeightPx
                            // Inside the structure when it is big and square to the screen, else beside it.
                            val square=((f.rotation+camera.turnDegrees)%90f+90f)%90f<.5f
                            val inside=ScreenBox((fp.left+fp.right)/2f-textW/2f,(fp.top+fp.bottom)/2f-textH/2f,(fp.left+fp.right)/2f+textW/2f,(fp.top+fp.bottom)/2f+textH/2f)
                            val others=solid.filter{it.first!=f.id}.map{it.second}+badgeBoxes+labelBoxes
                            val fitsInside=square && !PlacementKinds.isPassage(f.kind) && fp.right-fp.left>textW+12f && fp.bottom-fp.top>textH+6f &&
                                others.none{it.intersects(inside)}
                            val taken=solid.map{it.second}+badgeBoxes+labelBoxes
                            val spot=if(fitsInside) inside
                                else MapLabels.place(fp,textW,textH,labelGapPx,taken,size.width,size.height)
                                    ?: if(important) MapLabels.place(fp,textW,textH,labelGapPx,emptyList(),size.width,size.height) ?: inside else null
                            // 4.23: the short code ("A" of "A · Ricambi") stays inside the block, so the name beside it is never mistaken.
                            val code=original.substringBefore(" · ",missingDelimiterValue="").trim()
                            if(!fitsInside && code.length in 1..3 && !PlacementKinds.isPassage(f.kind) && fp.right-fp.left>codePx && fp.bottom-fp.top>codePx)
                                drawIntoCanvas { cv ->
                                    codePaint.color=if(dimming && f.id !in lit) android.graphics.Color.rgb(132,151,169) else android.graphics.Color.WHITE
                                    cv.nativeCanvas.drawText(code,(fp.left+fp.right)/2f,(fp.top+fp.bottom)/2f-(codePaint.ascent()+codePaint.descent())/2f,codePaint)
                                }
                            if(spot==null) continue
                            labelBoxes.add(spot)
                            drawIntoCanvas { cv ->
                                val x=(spot.left+spot.right)/2f
                                val firstY=if(second==null) (spot.top+spot.bottom)/2f else spot.top+labelHeightPx/2f
                                cv.nativeCanvas.drawText(label,x,firstY-(labelPaint.ascent()+labelPaint.descent())/2f,labelPaint)
                                if(second!=null) {
                                    val size2=labelPaint.textSize; val color2=labelPaint.color
                                    labelPaint.textSize=size2*.8f; labelPaint.color=android.graphics.Color.rgb(240,247,255)
                                    cv.nativeCanvas.drawText(second,x,spot.bottom-labelHeightPx*.4f-(labelPaint.ascent()+labelPaint.descent())/2f,labelPaint)
                                    labelPaint.textSize=size2; labelPaint.color=color2
                                }
                            }
                        }
                    }
                    // 4.20 "Dov'è": the walk from the entrance to the structure of the article.
                    walk?.let { route ->
                        // 4.20.2: one dotted line along the whole way (around structures and walls).
                        val pts=route.path.map{camera.toScreen(it)}
                        val line=androidx.compose.ui.graphics.Path().apply {
                            moveTo(pts.first().x,pts.first().y); pts.drop(1).forEach { lineTo(it.x,it.y) }
                        }
                        drawPath(line,Amber,style=Stroke(width=5f,cap=StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round,
                            pathEffect=PathEffect.dashPathEffect(floatArrayOf(3f,16f))))
                        val a=pts.first(); val b=pts.last()
                        drawCircle(Color(0xFF07121F),11f,Offset(a.x,a.y));drawCircle(Sky,8f,Offset(a.x,a.y))
                        // Zoomed in: the entrance or the article out of sight get an arrow on the edge pointing to them.
                        val edge=with(density){30.dp.toPx()}
                        fun pointer(at:PointMetres,color:Color,text:String) {
                            if(at.x in 0f..size.width && at.y in 0f..size.height) return
                            val cx=size.width/2f; val cy=size.height/2f
                            val dx=at.x-cx; val dy=at.y-cy
                            val k=minOf(if(dx!=0f) (size.width/2f-edge)/kotlin.math.abs(dx) else Float.MAX_VALUE,if(dy!=0f) (size.height/2f-edge)/kotlin.math.abs(dy) else Float.MAX_VALUE)
                            val m=Offset(cx+dx*k,cy+dy*k)
                            val ang=kotlin.math.atan2(dy,dx)
                            drawCircle(Color(0xFF07121F).copy(alpha=.9f),edge*.62f,m)
                            drawCircle(color,edge*.62f,m,style=Stroke(width=2.5f))
                            val tip=Offset(m.x+kotlin.math.cos(ang)*edge*.38f,m.y+kotlin.math.sin(ang)*edge*.38f)
                            val back=edge*.30f; val side=edge*.24f
                            val bx=tip.x-kotlin.math.cos(ang)*back; val by=tip.y-kotlin.math.sin(ang)*back
                            val arrow=androidx.compose.ui.graphics.Path().apply {
                                moveTo(tip.x,tip.y)
                                lineTo(bx-kotlin.math.sin(ang)*side,by+kotlin.math.cos(ang)*side)
                                lineTo(bx+kotlin.math.sin(ang)*side,by-kotlin.math.cos(ang)*side); close()
                            }
                            drawPath(arrow,color)
                            labelPaint.textSize=labelPx*.85f; labelPaint.color=android.graphics.Color.WHITE
                            val tx=m.x.coerceIn(labelPaint.measureText(text)/2f+4f,size.width-labelPaint.measureText(text)/2f-4f)
                            val ty=if(m.y>size.height/2f) m.y-edge*.9f else m.y+edge*1.2f
                            drawIntoCanvas { cv -> cv.nativeCanvas.drawText(text,tx,ty,labelPaint) }
                        }
                        pointer(a,Sky,route.from.label.ifBlank{route.from.kind})
                        pointer(b,Amber,route.to.label.ifBlank{route.to.kind})
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
                        cv.nativeCanvas.drawText(numberM(camera.shownWidth),topLeft.x+sizeFloor.width/2f,topLeft.y-14f,rulerPaint)
                        cv.nativeCanvas.save()
                        cv.nativeCanvas.rotate(-90f,topLeft.x-15f,topLeft.y+sizeFloor.height/2f)
                        cv.nativeCanvas.drawText(numberM(camera.shownHeight),topLeft.x-15f,topLeft.y+sizeFloor.height/2f,rulerPaint)
                        cv.nativeCanvas.restore()
                    }
                    }
                }
                // 4.20: at rest only what is needed: the active layer (with its legend), the articles without a place
                // and "inquadra tutto". Undo, zoom buttons, overlaps and hints belong to editing.
                if(layer!=MapLayer.NORMAL && !editing && !dimming) Surface(Modifier.align(Alignment.TopStart).padding(10.dp),shape=RoundedCornerShape(12.dp),
                    color=PanelBg.copy(alpha=.96f),border=BorderStroke(1.dp,Mint.copy(alpha=.23f))) {
                    Row(Modifier.padding(start=12.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(layer.legend+if(layer!=MapLayer.EMPTY) " · rosso = massimo" else "",fontSize=11.sp,color=TextSecondary,modifier=Modifier.widthIn(max=230.dp))
                        IconButton(onClick={layer=MapLayer.NORMAL}) {Icon(Icons.Outlined.Close,"Torna alla pianta normale",Modifier.size(18.dp))}
                    }
                }
                if(editing && colliding.isNotEmpty()) Surface(Modifier.align(Alignment.TopStart).padding(12.dp),shape=RoundedCornerShape(11.dp),color=Danger.copy(alpha=.2f)) {
                    Text("${colliding.size} sovrapposte",Modifier.padding(horizontal=9.dp,vertical=9.dp),fontSize=11.sp,color=Danger)
                }
                if(editing) Row(Modifier.align(Alignment.TopEnd).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    SmallFloatingControl(Icons.Outlined.Undo,"Annulla ${state.mapUndoLabel ?: ""}",enabled=state.mapUndoLabel!=null && !state.busy,onClick=model::undoMap)
                    SmallFloatingControl(Icons.Outlined.Redo,"Ripeti ${state.mapRedoLabel ?: ""}",enabled=state.mapRedoLabel!=null && !state.busy,onClick=model::redoMap)
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(end=10.dp,bottom=if(editing || state.locationPickItemIds!=null) 56.dp else 12.dp),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(editing) {
                        SmallFloatingControl(Icons.Outlined.Add,"Zoom +",onClick={zoom=(zoom*1.25f).coerceAtMost(4f)})
                        SmallFloatingControl(Icons.Outlined.Remove,"Zoom -",onClick={zoom=(zoom/1.25f).coerceAtLeast(.7f)})
                    }
                    Surface(shape=RoundedCornerShape(24.dp),color=PanelBg.copy(alpha=.97f),border=BorderStroke(1.dp,Hairline.copy(alpha=.12f))) {
                        IconButton(onClick={zoom=1f;panX=0f;panY=0f},modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.CenterFocusStrong,"Inquadra tutta la pianta")}
                    }
                }
                if(!editing && !guiding && found==null && !searching && state.locationPickItemIds==null && unplaced.isNotEmpty())
                    Surface(onClick={showUnplaced=true},modifier=Modifier.align(Alignment.BottomStart).padding(12.dp).heightIn(min=48.dp),
                        shape=RoundedCornerShape(24.dp),color=PanelBg.copy(alpha=.97f),border=BorderStroke(1.dp,Amber.copy(alpha=.45f))) {
                        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(Amber,RoundedCornerShape(5.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text("${unplaced.size} ${if(unplaced.size==1) "articolo senza posto" else "articoli senza posto"}",fontSize=14.sp,fontWeight=FontWeight.Medium)
                        }
                    }
                // 4.20.2: the hint goes away once something is selected, so it never covers the bottom of the plan while working there.
                if(state.locationPickItemIds!=null || (editing && selectedId==null && selectedWallId==null && (mode!=EditorTool.WALL || wallAnchor==null)))
                    Surface(Modifier.align(Alignment.BottomStart).padding(10.dp),shape=RoundedCornerShape(12.dp),color=PanelBg.copy(alpha=.96f)) {
                    Text(when{
                        state.locationPickItemIds!=null -> "Trascina per spostarti, tocca una struttura (o leggi la sua etichetta)"
                        mode==EditorTool.WALL -> if(wallAnchor==null)"Tocca il punto iniziale oppure trascina" else if(draftCloses) "Rilascia: la forma si chiude" else "Tocca per continuare · vicino a un angolo la forma si chiude"
                        else -> "Tocca una struttura o una parete; trascina solo se sbloccata"
                    },Modifier.padding(horizontal=11.dp,vertical=8.dp).widthIn(max=260.dp),fontSize=11.sp,color=TextSecondary)
                }
            }
        }
        if(selectedWall!=null && mode==EditorTool.FURNITURE) {
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
        if(!guiding && editing) Row(Modifier.padding(horizontal=14.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.CenterVertically) {
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
                EditorTool.VIEW -> Unit
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
        }else if(!guiding && found!=null){
            FoundPanel(found,foundPlace,state.placements,
                onPick={quickPick=found.id},
                onOpen={openItemId=found.id},
                onShelf={ foundPlace?.let { model.openShelf(it.id) } },
                onPlace={model.startLocationPick(listOf(found.id));foundItemId=null})
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
    quickPick?.let { id ->
        val item=state.items.firstOrNull{it.id==id}
        if(item==null) quickPick=null else QuickQuantityDialog(item,false,model,onClose={quickPick=null})
    }
    openItemId?.let { id ->
        val item=state.items.firstOrNull{it.id==id}
        if(item==null) openItemId=null else ItemDialog(item,state.placements,model,onDismiss={openItemId=null})
    }
    if(showUnplaced) UnplacedSheet(unplaced,onDismiss={showUnplaced=false}) { item ->
        showUnplaced=false;model.startLocationPick(listOf(item.id))
    }
    qrFor?.let { id -> state.placements.firstOrNull{it.id==id}?.let { p -> PlacementQrDialog(p,state.items,onDismiss={qrFor=null}) } }
    detailsId?.let { id ->
        state.placements.firstOrNull{it.id==id}?.let { f ->
            PlacementDetailDialog(f,state.items,model,onQr={qrFor=f.id},onDismiss={detailsId=null})
        }
    }
}
