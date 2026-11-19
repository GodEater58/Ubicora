package com.officinastock.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// 4.15: moved out of Screens.kt without changes — pages of one article: new, details, movements, location.

/**
 * 4.7.1: article screens open as full pages (top bar, scrollable body, main action pinned at the
 * bottom) instead of small dialogs, so long forms and the keyboard have room on a phone.
 */
@Composable fun FullScreenPage(title:String,onClose:()->Unit,subtitle:String="",
    action:(@Composable RowScope.()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onClose,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Ink) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Surface(color=PanelBg) {
                    Row(Modifier.fillMaxWidth().padding(start=4.dp,end=12.dp,top=6.dp,bottom=6.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick=onClose) { Icon(Icons.Outlined.Close,contentDescription="Chiudi") }
                        Column(Modifier.weight(1f)) {
                            Text(title,fontWeight=FontWeight.SemiBold,fontSize=18.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=TextPrimary)
                            if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.sp,color=TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=14.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
                if(action!=null) Surface(color=PanelBg) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),
                        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically,content=action)
                }
            }
        }
    }
}

@Composable fun NewItemDialog(onClose:()->Unit,allowed:List<String> = emptyList(),save:(String,String,Double,Double,String,String,String?,Double?,String,Int?)->Unit) {
    // A satellite with a department only sees its own categories (4.9).
    val categories=ArticleCodes.categoryPrefixes.keys.filter { allowed.isEmpty() || it in allowed }.ifEmpty { ArticleCodes.categoryPrefixes.keys.toList() }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if("Ricambi" in categories) "Ricambi" else categories.first()) }
    var qty by remember { mutableStateOf("0") }
    var min by remember { mutableStateOf("0") }
    var unit by remember { mutableStateOf("pz") }
    var note by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var leadDays by remember { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val valid=name.trim().length in 1..180&&unit.trim().length<=24&&note.length<=3000&&supplier.length<=160&&qty.replace(',','.').toDoubleOrNull()?.let(StockRules::validQuantity)==true&&min.replace(',','.').toDoubleOrNull()?.let(StockRules::validQuantity)==true
            &&(unitCost.isBlank()||unitCost.replace(',','.').toDoubleOrNull()?.let{it.isFinite()&&it>=0.0&&it<=100000000.0}==true)
            &&(leadDays.isBlank()||leadDays.toIntOrNull()?.let{it in 0..3650}==true)
    FullScreenPage("Nuovo articolo",onClose=onClose,action={
        OutlinedButton(onClick=onClose,modifier=Modifier.weight(1f)){Text("Annulla")}
        Button(onClick={save(name,category,qty.replace(',','.').toDouble(),min.replace(',','.').toDouble(),unit,note,photoUri,
            unitCost.takeIf{it.isNotBlank()}?.replace(',','.')?.toDouble(),supplier,leadDays.takeIf{it.isNotBlank()}?.toInt())},
            enabled=valid,modifier=Modifier.weight(1f)){Text("Salva")}
    }) {
            Input(name,{name=it},"Nome articolo")
            Text("Categoria",color=TextSecondary,fontSize=12.sp)
            Column { categories.chunked(3).forEach { group->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    group.forEach { cat ->
                        FilterChip(selected=category==cat,onClick={category=cat;unit=MeasureUnits.afterCategoryChange(cat,unit)},label={Text(cat,fontSize=10.sp)})
                    }
                }
            } }
            Surface(color=Mint.copy(alpha=.10f),shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Codice automatico",color=Mint,fontWeight=FontWeight.SemiBold)
                    Text("${ArticleCodes.prefix(category)}-######  ·  progressivo assegnato al salvataggio",fontSize=12.sp,color=TextSecondary)
                }
            }
            Input(note,{note=it},"Descrizione (facoltativa)")
            Input(qty,{qty=it},"Quantità iniziale",number=true)
            Input(min,{min=it},"Soglia minima",number=true)
            UnitPicker(unit,category,{unit=it})
            Text("DATI PER IL CONTROLLO MANAGERIALE · FACOLTATIVI",fontSize=10.sp,color=Mint,fontWeight=FontWeight.Bold)
            Input(unitCost,{unitCost=it},"Costo unitario (€)",number=true)
            Input(supplier,{supplier=it},"Fornitore")
            Input(leadDays,{leadDays=it},"Giorni di consegna",number=true)
            ArticlePhotoField(savedPhoto=null,selectedUri=photoUri,
                onSelected={photoUri=it},onApply=null,onRemoveSaved=null)
    }
}

/** Gestione anagrafica: la quantità si modifica solo tramite Carico/Prelievo tracciato. */
@Composable
private fun EditItemDetailsDialog(
    item: StockItem,
    onClose: () -> Unit,
    review: Boolean = false,
    allowed: List<String> = emptyList(),
    sentBack: String? = null,
    onSave: (String,String,String,Double,String,String,Double?,String,Int?) -> Unit
) {
    // 4.21: the code can change too; review = a maintainer's change, applied after the central validates it.
    var sku by remember(item.id) { mutableStateOf(item.sku) }
    var name by remember(item.id) { mutableStateOf(item.name) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var minimum by remember(item.id) { mutableStateOf(item.minimum.toString()) }
    var unit by remember(item.id) { mutableStateOf(item.unit) }
    var note by remember(item.id) { mutableStateOf(item.note) }
    var cost by remember(item.id) { mutableStateOf(item.unitCost?.toString().orEmpty()) }
    var supplier by remember(item.id) { mutableStateOf(item.supplier) }
    var lead by remember(item.id) { mutableStateOf(item.leadDays?.toString().orEmpty()) }
    val parsedMinimum=minimum.replace(',','.').toDoubleOrNull()
    val parsedCost=cost.takeIf { it.isNotBlank() }?.replace(',','.')?.toDoubleOrNull()
    val parsedDays=lead.takeIf { it.isNotBlank() }?.toIntOrNull()
    val valid=sku.trim().length in 1..40 && name.trim().length in 1..180 && unit.trim().length in 1..24 &&
        note.length<=3000 && supplier.length<=160 &&
        parsedMinimum?.let(StockRules::validQuantity)==true &&
        (cost.isBlank() || parsedCost?.let { it.isFinite() && it>=0.0 && it<=100000000.0 }==true) &&
        (lead.isBlank() || parsedDays?.let { it in 0..3650 }==true)
    FullScreenPage("Modifica articolo",onClose=onClose,subtitle=item.name,action={
        OutlinedButton(onClick=onClose,modifier=Modifier.weight(1f)){Text("Annulla")}
        Button(onClick={onSave(sku.trim(),name,category,parsedMinimum!!,unit,note,parsedCost,supplier,parsedDays)},
            enabled=valid,modifier=Modifier.weight(1f)){Text(if(review)"Invia al centrale" else "Salva modifiche")}
    }) {
            if(sentBack!=null) Surface(color=Danger.copy(alpha=.10f),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
                Text("Il centrale ha rimandato indietro la modifica: $sentBack",Modifier.padding(10.dp),fontSize=13.sp)
            }
            if(review) Text("Sei un manutentore: la modifica va al telefono centrale e la scheda cambia quando la valida.",
                color=Sky,fontSize=12.sp)
            Text("Giacenza e movimenti restano invariati.",color=TextSecondary,fontSize=12.sp)
            Input(sku,{sku=it},"Codice articolo")
            if(sku.trim()!=item.sku) Text("Cambiando il codice va ristampata l'etichetta QR: quella vecchia non sarà più riconosciuta.",
                fontSize=11.sp,color=Amber)
            Input(name,{name=it},"Nome articolo")
            Text("Categoria",color=TextSecondary,fontSize=12.sp)
            ArticleCodes.categoryPrefixes.keys.filter { allowed.isEmpty() || it in allowed || it==item.category }.chunked(3).forEach { group ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    group.forEach { cat -> FilterChip(selected=category==cat,onClick={category=cat},
                        label={Text(cat,fontSize=10.sp)}) }
                }
            }
            Input(minimum,{minimum=it},"Scorta minima",number=true)
            UnitPicker(unit,category,{unit=it})
            if(unit!=item.unit) Text("L'unità si può cambiare solo con giacenza zero e senza operazioni aperte.",fontSize=11.sp,color=Amber)
            Input(note,{note=it},"Descrizione")
            Input(cost,{cost=it},"Costo unitario EUR",number=true)
            Input(supplier,{supplier=it},"Fornitore")
            Input(lead,{lead=it},"Giorni di consegna",number=true)
    }
}

@Composable fun ItemDialog(item:StockItem,placements:List<Placement>,model:StockViewModel,onDismiss:()->Unit) {
    // 4.19.3: picking is what people do most, so it is the default.
    var action by remember(item.id) {mutableStateOf("prelievo")}
    var quantity by remember(item.id){mutableStateOf("1")}
    var note by remember(item.id){mutableStateOf("")}
    var borrower by remember(item.id){mutableStateOf("")}
    var pickingLocation by remember { mutableStateOf(false) }
    var showQrLabel by remember(item.id) { mutableStateOf(false) }
    var editPurchase by remember(item.id) { mutableStateOf(false) }
    var editDetails by remember(item.id) { mutableStateOf(false) }
    var askDeleteItem by remember(item.id) { mutableStateOf(false) }
    val itemEdits=model.ui.team.edits.filter { it.itemId==item.id }
    val canConfirm=quantity.replace(',','.').toDoubleOrNull()?.let{it.isFinite() && (if(action=="rettifica") it>=0.0 && it!=item.quantity else it>0.0)}==true&&(action!="prestito"||borrower.isNotBlank())&&(action!="rettifica"||note.trim().length in 3..250)
    FullScreenPage(item.name,onClose=onDismiss,subtitle="${item.sku} · ${item.category}",action={
        Button(onClick={
            val q=quantity.replace(',','.').toDouble()
            if(action=="prestito") model.lend(item.id,borrower,q)
            else if(action=="rettifica") model.reconcileQuantity(item.id,q,note)
            else model.movement(item.id,if(action=="carico")q else -q,note)
            onDismiss()
        },enabled=canConfirm,modifier=Modifier.fillMaxWidth()){Text("Conferma ${action}")}
    }) {
            // 4.19.3: quantity, place and the movement first; photo and the card details below.
            Text("Disponibili: ${amount(item.quantity)} ${item.unit}",fontWeight=FontWeight.Bold,fontSize=21.sp,color=Mint)
            val location=placements.firstOrNull{it.id==item.placementId}
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={pickingLocation=true},modifier=Modifier.weight(1f)) {
                    Text("Ubicazione: ${Locations.describe(location,item)}",maxLines=2,overflow=TextOverflow.Ellipsis)
                }
                if(location!=null) FilledTonalIconButton(onClick={model.showItemOnMap(item.id);onDismiss()}) {
                    Icon(Icons.Outlined.Map,contentDescription="Mostra in pianta")
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("prelievo","carico","prestito","rettifica").forEach {
                    FilterChip(selected=action==it,onClick={action=it},label={Text(it.replaceFirstChar(Char::uppercase),fontSize=12.sp)})
                }
            }
            Input(quantity,{quantity=it},if(action=="rettifica")"Quantità contata" else "Quantità",number=true)
            if(action=="prestito") Input(borrower,{borrower=it},"Assegnato a")
            else Input(note,{note=it},"Motivo / riferimento")
            HorizontalDivider(Modifier.padding(vertical=6.dp))
            ArticlePhotoField(savedPhoto=item.photoFile,selectedUri=null,
                onSelected={ },onApply={uri->model.replaceItemPhoto(item.id,uri)},
                onRemoveSaved={model.removeItemPhoto(item.id)},busy=model.ui.busy)
            // 4.21: changes of this card asked by a maintainer: to validate here (central) or sent from here (satellite).
            val central=model.ui.team.role==TeamRole.CENTRAL
            itemEdits.forEach { e ->
                TeamEditCard(e,central,model.ui.busy,showItem=false,
                    onApprove={model.approveTeamEdit(e.uid)},onReject={model.rejectTeamEdit(e.uid,it)},
                    onRedo={editDetails=true},onDismiss={model.dismissTeamEdit(e.uid)})
            }
            OutlinedButton(onClick={editDetails=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit,contentDescription=null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Modifica scheda articolo")
            }
            OutlinedButton(onClick={showQrLabel=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.QrCode2,contentDescription=null)
                Spacer(Modifier.width(8.dp))
                Text("Etichetta QR · Visualizza / Stampa")
            }
            OutlinedButton(onClick={editPurchase=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Analytics,null,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Costo, fornitore e tempi di riordino")
            }
            if(item.note.isNotBlank()) Text(item.note,color=TextSecondary,fontSize=12.sp)
            if(model.canDeleteItems) TextButton(onClick={askDeleteItem=true},enabled=!model.ui.busy,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(6.dp));Text("Elimina articolo",color=Danger)
            }
    }
    if(askDeleteItem) AlertDialog(onDismissRequest={askDeleteItem=false},title={Text("Eliminare ${item.name}?")},
        text={Text("Cancella definitivamente l'articolo ${item.sku} con foto, movimenti e storico. " +
            (if(model.ui.team.role==TeamRole.CENTRAL) "Sparirà anche dai telefoni satellite alla prossima sincronizzazione. " else "") +
            "Non si può annullare.")},
        confirmButton={TextButton(onClick={askDeleteItem=false;model.deleteItem(item.id);onDismiss()}){Text("Elimina",color=Danger)}},
        dismissButton={TextButton(onClick={askDeleteItem=false}){Text("Annulla")}})
    if(showQrLabel) QrLabelDialog(item,onDismiss={showQrLabel=false})
    if(editDetails) {
        val review=model.editNeedsReview(item)
        EditItemDetailsDialog(item,onClose={editDetails=false},review=review,
            allowed=if(review) model.departmentCategories() else emptyList(),
            sentBack=itemEdits.lastOrNull { it.status=="rejected" }?.reason) { sku,name,category,min,unit,note,cost,supplier,days ->
            model.updateItemDetails(item.id,name,category,min,unit,note,cost,supplier,days,sku=sku)
            editDetails=false
        }
    }
    if(editPurchase) PurchasePlanningDialog(item,onClose={editPurchase=false}) { cost,supplier,lead ->
        model.updatePurchase(item.id,cost,supplier,lead)
        editPurchase=false
    }
    if(pickingLocation) LocationPickerDialog(item,placements,
        onMap={model.startLocationPick(listOf(item.id));pickingLocation=false;onDismiss()},
        onClose={pickingLocation=false}) { placementId,level,slot ->
        model.assignItem(item.id,placementId,level,slot);pickingLocation=false
    }
}

/** Searchable list of storage structures, with optional shelf/position and a map shortcut. */
@Composable fun LocationPickerDialog(item:StockItem,placements:List<Placement>,onMap:()->Unit,onClose:()->Unit,
                                     onChoose:(Long?,Int?,Int?)->Unit) {
    var query by remember { mutableStateOf("") }
    var positionFor by remember { mutableStateOf<Placement?>(null) }
    val storage=placements.filter{it.holdsStock && (query.isBlank() || it.label.contains(query,true) || it.kind.contains(query,true))}
        .sortedBy{it.label.lowercase(Locale.ITALY)}
    AlertDialog(onDismissRequest=onClose,title={Text("Assegna ubicazione")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick=onMap,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Map,null);Spacer(Modifier.width(8.dp));Text("Scegli sulla mappa")
            }
            if(placements.count{it.holdsStock}>6) Input(query,{query=it},"Cerca struttura")
            TextButton(onClick={onChoose(null,null,null)}){Text("Nessuna ubicazione")}
            storage.forEach { p ->
                TextButton(onClick={if(p.hasPositions) positionFor=p else onChoose(p.id,null,null)},modifier=Modifier.fillMaxWidth()) {
                    Text("${p.label} · ${p.kind}"+(if(p.hasPositions)" · ${p.levels}×${p.slots}" else ""),
                        fontWeight=if(p.id==item.placementId)FontWeight.Bold else FontWeight.Normal,modifier=Modifier.weight(1f))
                }
            }
            if(storage.isEmpty()) Text("Nessuna struttura trovata. Creala nella Mappa → Strutture.",fontSize=12.sp,color=TextSecondary)
        }
    },confirmButton={TextButton(onClick=onClose){Text("Chiudi")}})
    positionFor?.let { p -> PositionDialog(p,"Ripiano e posizione",onClose={positionFor=null},
        initialLevel=item.shelfLevel.takeIf{p.id==item.placementId},initialSlot=item.shelfSlot.takeIf{p.id==item.placementId}) { level,slot ->
        positionFor=null;onChoose(p.id,level,slot)
    } }
}
