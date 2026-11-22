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

// 4.15: moved out of Screens.kt without changes — the list of the articles with search, filters and quick actions.

@Composable fun InventoryScreen(model:StockViewModel,onScan:()->Unit,requestedFocus:StockFocus=StockFocus.ALL) {
    val u=model.ui
    var query by remember(u.warehouse?.id){mutableStateOf("")}
    var sort by rememberSaveable { mutableStateOf(StockSort.NAME) }
    var sortMenu by remember { mutableStateOf(false) }
    val placementById=remember(u.placements) { u.placements.associateBy { it.id } }
    val lastMoved=remember(u.movements) { buildMap<Long,Long> { u.movements.forEach { m -> if(m.createdAt>(get(m.itemId) ?: 0L)) put(m.itemId,m.createdAt) } } }
    var activeFocus by remember(u.warehouse?.id){mutableStateOf(requestedFocus)}
    val idleIds=remember(u.manager.usage) {
        u.manager.usage.filter { it.consumed90>0.0 }.map { it.itemId }.toSet()
    }
    LaunchedEffect(requestedFocus){activeFocus=requestedFocus}
    var menuItemId by remember{mutableStateOf<Long?>(null)}
    var quantityFor by remember{mutableStateOf<Pair<Long,Boolean>?>(null)}
    var transferFor by remember{mutableStateOf<Long?>(null)}
    transferFor?.let { id ->
        val current=model.ui.items.firstOrNull{it.id==id}
        if(current==null) transferFor=null else TransferDialog(current,model,onClose={transferFor=null})
    }
    var selected by remember{mutableStateOf<StockItem?>(null)}
    var labelItem by remember { mutableStateOf<StockItem?>(null) }
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(u.scannedItemId,u.items) {
        u.scannedItemId?.let { id ->
            u.items.firstOrNull { it.id==id }?.let { match ->
                activeFocus=StockFocus.ALL
                query=""
                selected=match
                model.acknowledgeScannedItem()
            }
        }
    }
    LaunchedEffect(u.createdItemId,u.items) {
        u.createdItemId?.let { id ->
            u.items.firstOrNull { it.id == id }?.let { saved ->
                labelItem=saved
                justSaved=true
                model.acknowledgeNewItem()
            }
        }
    }
    val favorites=UiPrefs.favorites
    LaunchedEffect(query,u.warehouse?.id) { kotlinx.coroutines.delay(300); model.searchOtherSites(query) }
    val filtered=remember(u.items,query,activeFocus,idleIds,sort,lastMoved,favorites) { sortStock(u.items.filter { item ->
        val matches=when(activeFocus){
            StockFocus.ALL -> true
            StockFocus.CRITICAL -> item.lowStock
            StockFocus.EMPTY -> item.quantity==0.0
            StockFocus.PRICED -> item.unitCost!=null
            StockFocus.UNLOCATED -> item.placementId==null
            StockFocus.IDLE_90 -> item.id !in idleIds
            StockFocus.FAVORITES -> UiPrefs.isFavorite(item.warehouseId,item.id)
        }
        matches && item.matchesSearch(query)
    },sort,lastMoved).sortedByDescending { UiPrefs.isFavorite(it.warehouseId,it.id) } }   // 4.10: favourites first, order kept
    Column(Modifier.fillMaxSize()) {
        // 4.26: the title is in StockSections above; here search with «+» beside it, then the tools and filters,
        // then a thin bar with order and view right above the list they change.
        Column(Modifier.padding(start=18.dp,end=18.dp,top=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(query,onValueChange={query=it},label={Text("Cerca articolo",maxLines=1)},placeholder={Text("Nome, codice o descrizione",maxLines=1)},singleLine=true,
                    leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.weight(1f))
                // 4.19.2: a pick-only phone cannot create articles: no form that would be refused at the end.
                if(!model.pickOnly) FilledIconButton(onClick={model.openNewItem()},enabled=!u.busy,modifier=Modifier.size(56.dp)) {Icon(Icons.Outlined.Add,"Nuovo articolo")}
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                // 4.10: scan → quantity → next article, until "Fine".
                AssistChip(onClick=model::startBurst,enabled=!u.busy,label={Text("Prelievo a raffica")},
                    leadingIcon={Icon(Icons.Outlined.DocumentScanner,contentDescription=null,modifier=Modifier.size(18.dp))})
                // 4.15: sweep a structure and see what is missing or misplaced.
                AssistChip(onClick={model.startShelfCheck()},enabled=!u.busy,label={Text("Verifica scaffale")},
                    leadingIcon={Icon(Icons.Outlined.FactCheck,contentDescription=null,modifier=Modifier.size(18.dp))})
                listOf(
                    StockFocus.ALL to "Tutti",StockFocus.FAVORITES to "★ Preferiti",StockFocus.CRITICAL to "Da riordinare",
                    StockFocus.EMPTY to "Esauriti",StockFocus.PRICED to "Con costo",
                    StockFocus.UNLOCATED to "Senza posto",
                    StockFocus.IDLE_90 to "Fermi 90 gg"
                ).forEach { (option,label) ->
                    FilterChip(selected=activeFocus==option,onClick={activeFocus=option},label={Text(label)})
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box {
                    TextButton(onClick={sortMenu=true}) {
                        Icon(Icons.Outlined.Sort,contentDescription=null,modifier=Modifier.size(18.dp),tint=TextSecondary)
                        Spacer(Modifier.width(6.dp));Text("Ordina: ${sort.label}",color=TextSecondary)
                    }
                    DropdownMenu(expanded=sortMenu,onDismissRequest={sortMenu=false}) {
                        StockSort.entries.forEach { option ->
                            DropdownMenuItem(text={Text(option.label,fontWeight=if(option==sort) FontWeight.Bold else FontWeight.Normal)},
                                onClick={sort=option;sortMenu=false})
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // 4.19.3: the compact view is a button always in sight, not a chip at the end of the row.
                IconButton(onClick={UiPrefs.updateCompactList(!UiPrefs.compactList)}) {
                    Icon(if(UiPrefs.compactList) Icons.Outlined.ViewAgenda else Icons.Outlined.ViewHeadline,
                        if(UiPrefs.compactList) "Vista con foto" else "Vista compatta",tint=TextSecondary)
                }
            }
        }
        LazyColumn(contentPadding=PaddingValues(start=18.dp,top=4.dp,end=18.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(if(UiPrefs.compactList) 6.dp else 10.dp)) {
            // 4.19.3: the hint stays until it is closed once.
            if(!UiPrefs.swipeHintSeen) item {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Scorri a destra per prelevare, a sinistra per caricare. Tieni premuto per le altre azioni.",color=TextSecondary,fontSize=11.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick=UiPrefs::hideSwipeHint){Text("Ho capito")}
                }
            }
            items(filtered,key={it.id}) { entry ->
                // 4.15: swipe, long press and compact rows live in ArticleRow.
                ArticleRow(entry,entry.placementId?.let { placementById[it] },model,UiPrefs.compactList,query=query,
                    onOpen={selected=entry},onMenu={menuItemId=entry.id},onQuantity={load->quantityFor=entry.id to load})
            }
            if(filtered.isEmpty()) item {Panel(Modifier.fillMaxWidth()){Text(if(u.siteHits.isEmpty()) "Nessun articolo corrispondente." else "In questa sede non c'è.",color=TextSecondary)}}
            // 4.17: the same search in the other sites of the company.
            if(u.siteHits.isNotEmpty()) {
                item { Text("NELLE ALTRE SEDI",color=TextSecondary,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp,modifier=Modifier.padding(top=8.dp)) }
                items(u.siteHits,key={"site${it.site.id}-${it.item.id}"}) { hit ->
                    Surface(onClick={model.openInSite(hit)},enabled=!u.busy,shape=RoundedCornerShape(16.dp),color=PanelBg,modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.heightIn(min=60.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(hit.item.name,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                if(hit.item.note.isNotBlank()) Text(hit.item.note,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(hit.site.name,color=Sky,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Text("${amount(hit.item.quantity)} ${hit.item.unit}",fontWeight=FontWeight.Bold,color=if(hit.item.quantity>0.0) TextPrimary else TextSecondary)
                        }
                    }
                }
            }
        }
    }
    labelItem?.let { current ->
        QrLabelDialog(current,onDismiss={labelItem=null;justSaved=false},
            onAnother=if(justSaved && !model.pickOnly) ({ labelItem=null;justSaved=false;model.openNewItem() }) else null)
    }
    menuItemId?.let { id ->
        val current=u.items.firstOrNull{it.id==id}
        if(current==null) menuItemId=null else ArticleActionsSheet(current,current.placementId?.let { placementById[it] },model,
            onDismiss={menuItemId=null},onOpen={selected=current},onLabel={labelItem=current},onTransfer={transferFor=current.id},onQuantity={load->quantityFor=current.id to load})
    }
    quantityFor?.let { (id,load) ->
        val current=u.items.firstOrNull{it.id==id}
        if(current==null) quantityFor=null else QuickQuantityDialog(current,load,model,onClose={quantityFor=null})
    }
    selected?.let { item ->
        val current=u.items.firstOrNull{it.id==item.id}
        if(current==null) selected=null else ItemDialog(current,u.placements,model,onDismiss={selected=null})
    }
}
