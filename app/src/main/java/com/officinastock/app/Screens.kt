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

fun amount(d:Double):String= if(d%1.0==0.0) d.toLong().toString() else String.format(Locale.ITALY,"%.2f",d)
fun date(ts:Long):String=SimpleDateFormat("dd MMM · HH:mm",Locale.ITALY).format(Date(ts))

@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier,shape=RoundedCornerShape(22.dp),color=PanelBg,border=BorderStroke(1.dp,Color.White.copy(alpha=.06f))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
/** Identita visiva Ubicora. Il marchio non modifica i codici QR o il database. */
@Composable fun UbicoraWordmark(compact:Boolean=false) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(color=Color(0xFF143D48),shape=RoundedCornerShape(if(compact)14.dp else 22.dp)) {
            androidx.compose.foundation.Image(
                painter=painterResource(R.drawable.ic_ubicora_u_selected),
                contentDescription="Simbolo Ubicora",
                modifier=Modifier.size(if(compact)50.dp else 76.dp))
        }
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("Ubicora",color=TextPrimary,fontSize=if(compact)23.sp else 32.sp,
                fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.8).sp)
            Text("INVENTARIO  ·  SPAZI  ·  ANALISI",color=Mint,fontSize=if(compact)8.sp else 9.sp,
                fontWeight=FontWeight.Bold,letterSpacing=0.7.sp)
        }
    }
}

@Composable fun SectionTitle(title:String,subtitle:String="",modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,color=TextPrimary,fontSize=20.sp,fontWeight=FontWeight.Bold)
        if(subtitle.isNotEmpty()) Text(subtitle,color=TextSecondary,fontSize=12.sp)
    }
}
@Composable fun Input(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier,number:Boolean=false,password:Boolean=false) {
    OutlinedTextField(value,onValueChange=onChange,label={Text(label)},modifier=modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Number else KeyboardType.Text),
        visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable fun PrimaryAction(text:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {Text(text,fontWeight=FontWeight.SemiBold)}
}

@Composable fun NewProfileScreen(model:StockViewModel,first:Boolean) {
    var name by remember{mutableStateOf("")};var pin by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.Center) {
        UbicoraWordmark()
        Spacer(Modifier.height(34.dp))
        Text("Il tuo spazio.\nTutto sotto controllo.",fontSize=35.sp,color=TextPrimary,lineHeight=39.sp,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Text("Crea un ambiente locale privato o professionale. Nessun account online richiesto.",color=TextSecondary)
        Spacer(Modifier.height(28.dp))
        Panel {
            Input(name,{name=it},"Nome profilo (es. Personale)")
            Input(pin,{pin=it.filter(Char::isDigit).take(8)},"PIN locale · 4–8 cifre",number=true,password=true)
            Text("Il PIN limita l'accesso nell'app, ma non cifra il database del dispositivo.",fontSize=12.sp,color=TextSecondary)
            PrimaryAction("Crea profilo",{model.createProfile(name,pin)},name.isNotBlank()&&pin.length>=4&&!model.ui.busy,Modifier.fillMaxWidth())
            model.ui.message?.let{Text(it,color=Danger)}
        }
    }
}

@Composable fun ProfileChooserScreen(model:StockViewModel) {
    var creating by remember { mutableStateOf(false) }
    var picked by remember{mutableStateOf<Profile?>(null)}
    var pin by remember{mutableStateOf("")}
    if(creating){
        androidx.activity.compose.BackHandler { creating=false }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick={creating=false}) { Text("Torna ai profili") }
            Box(Modifier.weight(1f)) { NewProfileScreen(model,false) }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Spacer(Modifier.height(36.dp))
        UbicoraWordmark(compact=true)
        Spacer(Modifier.height(14.dp))
        SectionTitle("Scegli il tuo profilo","I magazzini di ogni profilo rimangono separati")
        Spacer(Modifier.height(24.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
            items(model.ui.profiles,key={it.id}) { profile ->
                Panel(Modifier.fillMaxWidth().clickable {picked=profile;pin=""}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PersonOutline,null,tint=Mint,modifier=Modifier.size(29.dp))
                        Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) {
                            Text(profile.name,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                            Text("Archivio locale",color=TextSecondary,fontSize=12.sp)
                        };Icon(Icons.Outlined.ChevronRight,null,tint=TextSecondary)
                    }
                }
            }
        }
        OutlinedButton(onClick={creating=true},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Nuovo profilo")}
        model.ui.message?.let {Text(it,color=Danger)}
    }
    picked?.let { p ->
        AlertDialog(onDismissRequest={picked=null},title={Text("Accedi a ${p.name}")},text={Input(pin,{pin=it.filter(Char::isDigit).take(8)},"PIN",number=true,password=true)},
            confirmButton={TextButton(onClick={model.unlock(p,pin);picked=null},enabled=pin.isNotEmpty()){Text("Accedi")}},
            dismissButton={TextButton(onClick={picked=null}){Text("Annulla")}})
    }
}
@Composable fun WarehouseChooserScreen(model:StockViewModel) {
    var show by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("IL TUO AMBIENTE",color=Mint,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp)
        Spacer(Modifier.height(12.dp))
        SectionTitle("Dove lavoriamo?","Profilo ${model.ui.profile?.name}")
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(model.ui.warehouses,key={it.id}) { w ->
                Panel(Modifier.fillMaxWidth().clickable{model.selectWarehouse(w)}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warehouse,null,tint=Mint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {Text(w.name,fontWeight=FontWeight.Bold);Text(w.location.ifBlank{"Nessuna località"},color=TextSecondary,fontSize=12.sp)}
                        Icon(Icons.Outlined.ArrowForwardIos,null,tint=TextSecondary,modifier=Modifier.size(16.dp))
                    }
                }
            }
        }
        PrimaryAction("+  Crea nuovo magazzino",{show=true},modifier=Modifier.fillMaxWidth())
        TextButton(onClick=model::signOut){Text("Cambia profilo")}
        model.ui.message?.let{Text(it,color=Danger)}
    }
    if(show) AlertDialog(onDismissRequest={show=false},title={Text("Nuovo magazzino")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Input(name,{name=it},"Nome magazzino");Input(location,{location=it},"Località / descrizione")}
    },confirmButton={TextButton(onClick={model.createWarehouse(name,location);show=false},enabled=name.isNotBlank()){Text("Crea")}},
        dismissButton={TextButton(onClick={show=false}){Text("Annulla")}})
}

@Composable fun DashboardScreen(
    model:StockViewModel,goStock:()->Unit,goMap:()->Unit,goMoves:()->Unit,goManager:()->Unit,
    onStockFocus:(StockFocus)->Unit,
    onActivityFocus:(ActivityFocus,Int)->Unit,
    onItemFocus:(Long)->Unit
) {
    val u=model.ui
    val count=u.items.size;val critical=u.items.count{it.lowStock}
    val recently=u.movements.take(4)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(17.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
        item {
            Column {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.ic_ubicora_u_selected),contentDescription=null,modifier=Modifier.size(23.dp))
                    Text("UBICORA  /  CONTROLLO OPERATIVO",color=Mint,letterSpacing=1.2.sp,fontWeight=FontWeight.Bold,fontSize=10.sp)
                }
                Spacer(Modifier.height(5.dp))
                Text(u.warehouse?.name ?: "Magazzino",fontSize=30.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${u.warehouse?.location?.ifBlank{"Area di lavoro"}}  ·  ${u.profile?.name}",color=TextSecondary,fontSize=13.sp)
            }
        }
        item {
            Surface(shape=RoundedCornerShape(26.dp),color=Color.Transparent,
                modifier=Modifier.fillMaxWidth().clickable {
                    onStockFocus(if(critical>0)StockFocus.CRITICAL else StockFocus.ALL)
                }) {
                Column(Modifier.background(HeroBrush).padding(23.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.QueryStats,null,tint=Mint)
                        Spacer(Modifier.width(10.dp));Text("STATO DEL MAGAZZINO",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                    }
                    Spacer(Modifier.height(17.dp))
                    Text(if(critical==0) "Tutto sotto controllo" else "$critical articoli da verificare",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text(if(critical==0) "Nessuna scorta sotto la soglia impostata." else "Controlla le giacenze per evitare fermi e mancanze.",color=TextSecondary,fontSize=13.sp)
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(progress={ if(count==0)0f else (1f-critical.toFloat()/count).coerceIn(0f,1f) },
                        modifier=Modifier.fillMaxWidth().height(7.dp),color=Mint,trackColor=Color.White.copy(alpha=.12f))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("${count-critical} di $count referenze sopra la soglia",fontSize=11.sp,color=TextSecondary,modifier=Modifier.weight(1f))
                        Text("Apri inventario ›",fontSize=11.sp,color=Mint,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                SmallMetric("REFERENZE",count.toString(),"A catalogo",Mint,Modifier.weight(1f),onClick={onStockFocus(StockFocus.ALL)})
                SmallMetric("DA RIORDINARE",critical.toString(),"Sotto soglia",Amber,Modifier.weight(1f),onClick={onStockFocus(StockFocus.CRITICAL)})
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                SmallMetric("UBICAZIONI",u.placements.size.toString(),"Nella planimetria",Sky,Modifier.weight(1f),onClick=goMap)
                SmallMetric("IN UTILIZZO",u.loans.size.toString(),"Prestiti aperti",TextPrimary,Modifier.weight(1f),onClick={onActivityFocus(ActivityFocus.LOANS,0)})
            }
        }
        if(u.businessMode) item {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AssignmentTurnedIn,contentDescription=null,tint=Mint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Centro operativo",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text("Acquisti · conteggi · trasferimenti · manutenzioni",fontSize=12.sp,color=TextSecondary)
                    }
                }
                val pending=u.orders.count{it.status in listOf("DRAFT","APPROVED","ORDERED")}+
                    u.stockCounts.count{it.status=="PENDING"}+
                    u.stockTransfers.count{it.status in listOf("REQUESTED","IN_TRANSIT")}+
                    u.maintenanceWork.count{it.status=="OPEN"}
                Text(if(pending==0)"Nessuna azione aperta" else "$pending attività da seguire",
                    color=if(pending>0)Amber else Mint,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                PrimaryAction("Apri gestione aziendale",goManager,modifier=Modifier.fillMaxWidth())
            }
        }
        if(u.businessMode) item { ManagerAnalyticsPanel(u.items,u.manager,
            onStockFocus=onStockFocus,onActivityFocus=onActivityFocus,onItemFocus=onItemFocus) }
        item { SectionTitle("Accesso rapido","Azioni frequenti, senza menu inutili") }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                QuickAction("Inventario",Icons.Outlined.Inventory2,goStock,Modifier.weight(1f))
                QuickAction("Mappa",Icons.Outlined.Map,goMap,Modifier.weight(1f))
                QuickAction("Storico",Icons.Outlined.History,goMoves,Modifier.weight(1f))
            }
        }
        item {SectionTitle("Ultime attività")}
        if(recently.isEmpty()) item { Panel(Modifier.fillMaxWidth()){Text("Non ci sono ancora movimenti. Aggiungi il primo articolo.",color=TextSecondary)} }
        items(recently,key={it.id}) { move -> MovementRow(move,onClick={onItemFocus(move.itemId)}) }
        item{Spacer(Modifier.height(8.dp))}
    }
}
@Composable fun SmallMetric(label:String,value:String,subtitle:String,color:Color,modifier:Modifier=Modifier,onClick:(()->Unit)?=null) {
    Panel(if(onClick!=null) modifier.clickable(onClick=onClick) else modifier){
        Text(label,color=TextSecondary,fontSize=10.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Text(value,color=color,fontSize=27.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(subtitle,color=TextSecondary,fontSize=11.sp,modifier=Modifier.weight(1f),maxLines=2)
            if(onClick!=null) Icon(Icons.Outlined.ChevronRight,"Apri dettaglio",tint=color,modifier=Modifier.size(17.dp))
        }
    }
}
@Composable fun QuickAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick=click,modifier=modifier,shape=RoundedCornerShape(18.dp),color=Surface2) {
        Column(Modifier.padding(vertical=20.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,null,tint=Mint,modifier=Modifier.size(28.dp));Spacer(Modifier.height(8.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable fun MovementRow(move:Movement,onClick:(()->Unit)?=null) {
    Panel(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(if(move.delta>=0) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,null,tint=if(move.delta>=0)Mint else Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(move.itemName,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                Text("${move.sku} · ${move.note} · ${date(move.createdAt)}",fontSize=11.sp,color=TextSecondary,maxLines=2)
            }
            Text("${if(move.delta>0)"+" else ""}${amount(move.delta)}",color=if(move.delta>=0)Mint else Amber,fontWeight=FontWeight.Bold)
            if(onClick!=null) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.ChevronRight,"Apri attività",tint=TextSecondary,modifier=Modifier.size(17.dp))
            }
        }
    }
}

@Composable fun InventoryScreen(model:StockViewModel,onScan:()->Unit,requestedFocus:StockFocus=StockFocus.ALL) {
    val u=model.ui
    var query by remember(u.warehouse?.id){mutableStateOf("")}
    var activeFocus by remember(u.warehouse?.id){mutableStateOf(requestedFocus)}
    val idleIds=remember(u.manager.usage) {
        u.manager.usage.filter { it.consumed90>0.0 }.map { it.itemId }.toSet()
    }
    LaunchedEffect(requestedFocus){activeFocus=requestedFocus}
    var add by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<StockItem?>(null)}
    var labelItem by remember { mutableStateOf<StockItem?>(null) }
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
                model.acknowledgeNewItem()
            }
        }
    }
    val filtered=remember(u.items,query,activeFocus,idleIds) { u.items.filter { item ->
        val matches=when(activeFocus){
            StockFocus.ALL -> true
            StockFocus.CRITICAL -> item.lowStock
            StockFocus.EMPTY -> item.quantity==0.0
            StockFocus.PRICED -> item.unitCost!=null
            StockFocus.UNLOCATED -> item.placementId==null
            StockFocus.IDLE_90 -> item.id !in idleIds
        }
        matches && (query.isBlank()||listOf(item.name,item.sku,item.category).any{v->v.contains(query,true)})
    } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {SectionTitle("Inventario","${u.items.size} referenze registrate")}
            FilledTonalIconButton(onClick=onScan) {
                Icon(Icons.Outlined.QrCodeScanner,contentDescription="Scansiona QR articolo")
            }
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick={add=true},enabled=!u.busy) {Icon(Icons.Outlined.Add,"Nuovo articolo")}
        }
        Column(Modifier.padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,onValueChange={query=it},label={Text("Cerca codice, nome o categoria")},singleLine=true,
                leadingIcon={Icon(Icons.Outlined.Search,null)},modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(
                    StockFocus.ALL to "Tutti",StockFocus.CRITICAL to "Critici",
                    StockFocus.EMPTY to "Esauriti",StockFocus.PRICED to "Con costo",
                    StockFocus.UNLOCATED to "Senza ubicazione",
                    StockFocus.IDLE_90 to "Fermi 90 gg"
                ).forEach { (option,label) ->
                    FilterChip(selected=activeFocus==option,onClick={activeFocus=option},label={Text(label)})
                }
            }
        }
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(filtered,key={it.id}) { entry ->
                Panel(Modifier.fillMaxWidth().clickable{selected=entry}) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        if(entry.photoFile!=null) ArticleThumbnail(entry.photoFile)
                        else Surface(color=if(entry.lowStock)Amber.copy(alpha=.13f) else Mint.copy(alpha=.12f),shape=RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.Inventory2,null,tint=if(entry.lowStock)Amber else Mint,modifier=Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                            Text("${entry.sku}  ·  ${entry.category}",color=TextSecondary,fontSize=11.sp)
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text("${amount(entry.quantity)} ${entry.unit}",color=if(entry.lowStock)Amber else TextPrimary,fontWeight=FontWeight.Bold)
                            Text(if(entry.lowStock)"Scorta bassa" else "Disponibile",color=if(entry.lowStock)Amber else Mint,fontSize=11.sp)
                        }
                    }
                }
            }
            if(filtered.isEmpty()) item {Panel(Modifier.fillMaxWidth()){Text("Nessun articolo corrispondente.",color=TextSecondary)}}
        }
    }
    if(add) NewItemDialog(onClose={add=false},save={name,cat,qty,min,unit,note,photoUri,cost,supplier,leadDays->
        model.addItem(name,cat,qty,min,unit,note,photoUri,cost,supplier,leadDays);add=false
    })
    labelItem?.let { current ->
        QrLabelDialog(current,onDismiss={labelItem=null})
    }
    selected?.let { item ->
        val current=u.items.firstOrNull{it.id==item.id}
        if(current==null) selected=null else ItemDialog(current,u.placements,model,onDismiss={selected=null})
    }
}

@Composable fun NewItemDialog(onClose:()->Unit,save:(String,String,Double,Double,String,String,String?,Double?,String,Int?)->Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Ricambi") }
    var qty by remember { mutableStateOf("0") }
    var min by remember { mutableStateOf("0") }
    var unit by remember { mutableStateOf("pz") }
    var note by remember { mutableStateOf("") }
    var unitCost by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var leadDays by remember { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val categories=ArticleCodes.categoryPrefixes.keys.toList()
    AlertDialog(onDismissRequest=onClose,title={Text("Nuovo articolo")},text={
        Column(Modifier.heightIn(max=470.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Input(name,{name=it},"Nome articolo")
            Text("Categoria",color=TextSecondary,fontSize=12.sp)
            Column { categories.chunked(3).forEach { group->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    group.forEach { cat ->
                        FilterChip(selected=category==cat,onClick={category=cat},label={Text(cat,fontSize=10.sp)})
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
            Input(unit,{unit=it},"Unità (pz, m, kg...)")
            Text("DATI PER IL CONTROLLO MANAGERIALE · FACOLTATIVI",fontSize=10.sp,color=Mint,fontWeight=FontWeight.Bold)
            Input(unitCost,{unitCost=it},"Costo unitario (€)",number=true)
            Input(supplier,{supplier=it},"Fornitore")
            Input(leadDays,{leadDays=it},"Giorni di consegna",number=true)
            ArticlePhotoField(savedPhoto=null,selectedUri=photoUri,
                onSelected={photoUri=it},onApply=null,onRemoveSaved=null)
        }
    },confirmButton={TextButton(onClick={save(name,category,qty.replace(',','.').toDouble(),min.replace(',','.').toDouble(),unit,note,photoUri,
        unitCost.takeIf{it.isNotBlank()}?.replace(',','.')?.toDouble(),supplier,leadDays.takeIf{it.isNotBlank()}?.toInt())},
        enabled=name.trim().length in 1..180&&unit.trim().length<=24&&note.length<=3000&&supplier.length<=160&&qty.replace(',','.').toDoubleOrNull()?.let(StockRules::validQuantity)==true&&min.replace(',','.').toDoubleOrNull()?.let(StockRules::validQuantity)==true
            &&(unitCost.isBlank()||unitCost.replace(',','.').toDoubleOrNull()?.let{it.isFinite()&&it>=0.0&&it<=100000000.0}==true)
            &&(leadDays.isBlank()||leadDays.toIntOrNull()?.let{it in 0..3650}==true)){Text("Salva")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

/** Gestione anagrafica: la quantità si modifica solo tramite Carico/Prelievo tracciato. */
@Composable
private fun EditItemDetailsDialog(
    item: StockItem,
    onClose: () -> Unit,
    onSave: (String,String,Double,String,String,Double?,String,Int?) -> Unit
) {
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
    val valid=name.trim().length in 1..180 && unit.trim().length in 1..24 &&
        note.length<=3000 && supplier.length<=160 &&
        parsedMinimum?.let(StockRules::validQuantity)==true &&
        (cost.isBlank() || parsedCost?.let { it.isFinite() && it>=0.0 && it<=100000000.0 }==true) &&
        (lead.isBlank() || parsedDays?.let { it in 0..3650 }==true)
    AlertDialog(onDismissRequest=onClose,title={Text("Modifica articolo")},text={
        Column(Modifier.heightIn(max=490.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Codice ${item.sku}: non cambia. Giacenza e movimenti restano invariati.",
                color=TextSecondary,fontSize=12.sp)
            Input(name,{name=it},"Nome articolo")
            Text("Categoria",color=TextSecondary,fontSize=12.sp)
            ArticleCodes.categoryPrefixes.keys.chunked(3).forEach { group ->
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    group.forEach { cat -> FilterChip(selected=category==cat,onClick={category=cat},
                        label={Text(cat,fontSize=10.sp)}) }
                }
            }
            Input(minimum,{minimum=it},"Scorta minima",number=true)
            Input(unit,{unit=it},"Unità di misura")
            Input(note,{note=it},"Descrizione")
            Input(cost,{cost=it},"Costo unitario EUR",number=true)
            Input(supplier,{supplier=it},"Fornitore")
            Input(lead,{lead=it},"Giorni di consegna",number=true)
        }
    },confirmButton={TextButton(
        onClick={onSave(name,category,parsedMinimum!!,unit,note,parsedCost,supplier,parsedDays)},
        enabled=valid){Text("Salva modifiche")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable fun ItemDialog(item:StockItem,placements:List<Placement>,model:StockViewModel,onDismiss:()->Unit) {
    var action by remember(item.id) {mutableStateOf("carico")}
    var quantity by remember(item.id){mutableStateOf("1")}
    var note by remember(item.id){mutableStateOf("")}
    var borrower by remember(item.id){mutableStateOf("")}
    var pickingLocation by remember { mutableStateOf(false) }
    var showQrLabel by remember(item.id) { mutableStateOf(false) }
    var editPurchase by remember(item.id) { mutableStateOf(false) }
    var editDetails by remember(item.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(item.name)},text={
        Column(Modifier.heightIn(max=510.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            ArticlePhotoField(savedPhoto=item.photoFile,selectedUri=null,
                onSelected={ },onApply={uri->model.replaceItemPhoto(item.id,uri)},
                onRemoveSaved={model.removeItemPhoto(item.id)},busy=model.ui.busy)
            Text("Codice ${item.sku} · ${item.category}",color=TextSecondary)
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
            Text("Disponibili: ${amount(item.quantity)} ${item.unit}",fontWeight=FontWeight.Bold,fontSize=21.sp,color=Mint)
            OutlinedButton(onClick={editPurchase=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Analytics,null,modifier=Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Costo, fornitore e tempi di riordino")
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("carico","prelievo","prestito","rettifica").forEach {
                    FilterChip(selected=action==it,onClick={action=it},label={Text(it.replaceFirstChar(Char::uppercase),fontSize=12.sp)})
                }
            }
            Input(quantity,{quantity=it},if(action=="rettifica")"Quantità contata" else "Quantità",number=true)
            if(action=="prestito") Input(borrower,{borrower=it},"Assegnato a")
            else Input(note,{note=it},"Motivo / riferimento")
            OutlinedButton(onClick={pickingLocation=true},modifier=Modifier.fillMaxWidth()) {
                Text("Ubicazione: ${placements.firstOrNull{it.id==item.placementId}?.label ?: "Da assegnare"}")
            }
            if(item.note.isNotBlank()) Text(item.note,color=TextSecondary,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick={
        val q=quantity.replace(',','.').toDouble()
        if(action=="prestito") model.lend(item.id,borrower,q)
        else if(action=="rettifica") model.reconcileQuantity(item.id,q,note)
        else model.movement(item.id,if(action=="carico")q else -q,note)
        onDismiss()
    },enabled=quantity.replace(',','.').toDoubleOrNull()?.let{it.isFinite() && (if(action=="rettifica") it>=0.0 && it!=item.quantity else it>0.0)}==true&&(action!="prestito"||borrower.isNotBlank())&&(action!="rettifica"||note.trim().length in 3..250)){Text("Conferma")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
    if(showQrLabel) QrLabelDialog(item,onDismiss={showQrLabel=false})
    if(editDetails) EditItemDetailsDialog(item,onClose={editDetails=false}) { name,category,min,unit,note,cost,supplier,days ->
        model.updateItemDetails(item.id,name,category,min,unit,note,cost,supplier,days)
        editDetails=false
    }
    if(editPurchase) PurchasePlanningDialog(item,onClose={editPurchase=false}) { cost,supplier,lead ->
        model.updatePurchase(item.id,cost,supplier,lead)
        editPurchase=false
    }
    if(pickingLocation) AlertDialog(onDismissRequest={pickingLocation=false},title={Text("Assegna ubicazione")},text={
        Column(Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick={model.assignItem(item.id,null);pickingLocation=false}){Text("Nessuna ubicazione")}
            placements.forEach {p->TextButton(onClick={model.assignItem(item.id,p.id);pickingLocation=false}){Text("${p.label} · ${p.kind}")}}
        }
    },confirmButton={TextButton(onClick={pickingLocation=false}){Text("Chiudi")}})
}

@Composable fun MovementsScreen(
    model:StockViewModel,
    requestedFocus:ActivityFocus=ActivityFocus.ALL,
    requestedDays:Int=0
) {
    val u=model.ui
    var focus by remember(u.warehouse?.id){mutableStateOf(requestedFocus)}
    var days by remember(u.warehouse?.id){mutableIntStateOf(requestedDays)}
    LaunchedEffect(requestedFocus,requestedDays) { focus=requestedFocus;days=requestedDays }
    val warehouseId=u.warehouse?.id
    val newestMovementId=u.movements.firstOrNull()?.id ?: 0L
    // Non caricare tutto lo storico in RAM: i filtri sono eseguiti dal database, prima della paginazione.
    var page by remember(warehouseId,focus,days,newestMovementId) { mutableIntStateOf(0) }
    var history by remember(warehouseId,focus,days,newestMovementId) { mutableStateOf<List<Movement>>(emptyList()) }
    var moreAvailable by remember(warehouseId,focus,days,newestMovementId) { mutableStateOf(false) }
    var loading by remember(warehouseId,focus,days,newestMovementId) { mutableStateOf(true) }
    var historyError by remember(warehouseId,focus,days,newestMovementId) { mutableStateOf<String?>(null) }
    var retryToken by remember(warehouseId,focus,days,newestMovementId) { mutableIntStateOf(0) }
    LaunchedEffect(warehouseId,focus,days,newestMovementId,page,retryToken) {
        if(warehouseId==null || focus==ActivityFocus.LOANS) return@LaunchedEffect
        loading=true
        historyError=null
        try {
            val entries=model.historyPage(warehouseId,focus,days,page)
            history=if(page==0)entries.take(100) else history+entries.take(100)
            moreAvailable=entries.size>100
        } catch(e:Exception) {
            historyError=e.message ?: "Impossibile caricare la cronologia"
        } finally {
            loading=false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            SectionTitle("Attività","Movimenti registrati e utensili in uso",Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal=18.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(ActivityFocus.ALL to "Tutti",ActivityFocus.INBOUND to "Entrate",
                   ActivityFocus.OUTBOUND to "Uscite",ActivityFocus.LOANS to "Prestiti (${u.loans.size})").forEach{(option,label)->
                FilterChip(selected=focus==option,onClick={focus=option},label={Text(label)})
            }
        }
        if(focus!=ActivityFocus.LOANS) {
            Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(0 to "Sempre",7 to "7 gg",30 to "30 gg",90 to "90 gg").forEach{(d,label)->
                    FilterChip(selected=days==d,onClick={days=d},label={Text(label)})
                }
            }
        }
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(focus==ActivityFocus.LOANS) {
                if(u.loans.isEmpty()) item{Panel(Modifier.fillMaxWidth()){Text("Nessun utensile attualmente assegnato.",color=TextSecondary)}}
                items(u.loans,key={it.id}) { loan ->
                    Panel(Modifier.fillMaxWidth()) {
                        Text(u.items.firstOrNull{it.id==loan.itemId}?.name ?: "Articolo",fontWeight=FontWeight.Bold)
                        Text("Assegnato a: ${loan.borrower}",color=TextSecondary)
                        Text("Quantità ${amount(loan.quantity)} · ${date(loan.createdAt)}",fontSize=12.sp,color=TextSecondary)
                        PrimaryAction("Registra restituzione",{model.returnLoan(loan.id)},modifier=Modifier.fillMaxWidth())
                    }
                }
            } else {
                item { Text("${history.size} operazioni caricate",color=TextSecondary,fontSize=12.sp) }
                if(history.isEmpty() && !loading && historyError==null) item {
                    Panel(Modifier.fillMaxWidth()){Text("Nessun movimento per questi filtri.",color=TextSecondary)}
                }
                items(history,key={it.id}) {MovementRow(it)}
                if(loading) item { CircularProgressIndicator(Modifier.padding(12.dp)) }
                if(historyError!=null) item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(historyError.orEmpty(),color=Danger)
                        TextButton(onClick={retryToken++}){Text("Riprova caricamento")}
                    }
                }
                if(moreAvailable && !loading && historyError==null) item {
                    OutlinedButton(onClick={page++},modifier=Modifier.fillMaxWidth()) {
                        Text("Mostra altre operazioni")
                    }
                }
            }
        }
    }
}

@Composable fun SettingsScreen(model:StockViewModel,onCsv:()->Unit,onCsvImport:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit) {
    val archiveScope=rememberCoroutineScope()
    var archivePins by remember { mutableStateOf<Map<Long,String>>(emptyMap()) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    var newWarehouse by remember{mutableStateOf(false)}
    var confirmDemoRemoval by remember{mutableStateOf(false)}
    var confirmDemoRefresh by remember{mutableStateOf(false)}
    var showLegal by remember{mutableStateOf(false)}
    var editBudget by remember{mutableStateOf(false)}
    var budgetText by remember{mutableStateOf("")}
    var backupDialog by remember{mutableStateOf(false)}
    var restoreDialog by remember{mutableStateOf(false)}
    var secret by remember{mutableStateOf("")}
    var secretConfirm by remember{mutableStateOf("")}
    var understood by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")}
    LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionTitle("Impostazioni","Ambienti, profili, demo e sicurezza dei dati")}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("PROFILO CORRENTE",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text(model.ui.profile?.name ?: "",fontWeight=FontWeight.Bold,fontSize=19.sp)
            OutlinedButton(onClick=model::signOut,modifier=Modifier.fillMaxWidth()){Text("Cambia profilo / Logout")}
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("MODALITÀ D'USO",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(model.ui.businessMode)"Aziendale" else "Personale",fontWeight=FontWeight.Bold,fontSize=17.sp)
                    Text(if(model.ui.businessMode) "Acquisti, approvazioni, inventari e manutenzioni" else
                        "Inventario e planimetria, senza moduli amministrativi",color=TextSecondary,fontSize=11.sp)
                }
                Switch(checked=model.ui.businessMode,onCheckedChange=model::changeManagerMode,
                    enabled=!model.ui.busy)
            }
            if(model.ui.businessMode) {
                OutlinedButton(onClick={budgetText=model.ui.monthlyBudget?.toString().orEmpty();editBudget=true},
                    modifier=Modifier.fillMaxWidth()) {
                    Text("Budget acquisti mensile: "+(model.ui.monthlyBudget?.let{amount(it)+" €"} ?: "non impostato"))
                }
            }
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("MAGAZZINI",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text(model.ui.warehouse?.name ?: "",fontWeight=FontWeight.Bold,fontSize=19.sp)
            OutlinedButton(onClick={model.leaveWarehouse()},modifier=Modifier.fillMaxWidth()){Text("Cambia magazzino")}
            OutlinedButton(onClick={newWarehouse=true},modifier=Modifier.fillMaxWidth()){Text("Crea un altro magazzino")}
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("BACKUP COMPLETO CIFRATO",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text("Esporta profili, articoli, foto, movimenti, prestiti, magazzini, codici e planimetrie in un unico file protetto da password.",fontSize=12.sp,color=TextSecondary)
            PrimaryAction("Esporta backup completo",onClick={secret="";secretConfirm="";archivePins=emptyMap();archiveError=null;backupDialog=true},enabled=!model.ui.busy,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={secret="";understood=false;archivePins=emptyMap();archiveError=null;restoreDialog=true},enabled=!model.ui.busy,modifier=Modifier.fillMaxWidth()) {Text("Ripristina da backup")}
            Text("Conserva password e file al sicuro. Senza la password il backup non è recuperabile. Il ripristino sostituisce tutti i dati locali.",fontSize=11.sp,color=TextSecondary)
        }}
        if(model.ui.demoCount>0) {
            item {Panel(Modifier.fillMaxWidth()) {
                Text("DATI DIMOSTRATIVI",color=Amber,fontSize=11.sp,letterSpacing=1.sp)
                Text("${model.ui.demoCount} elementi di esempio in questo magazzino. La rimozione preserva gli elementi creati da te.",fontSize=12.sp,color=TextSecondary)
                OutlinedButton(onClick={confirmDemoRefresh=true},modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh,contentDescription=null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rigenera demo completa (4.1.3)")
                }
                OutlinedButton(onClick={confirmDemoRemoval=true},modifier=Modifier.fillMaxWidth()) {Text("Rimuovi soltanto la demo",color=Danger)}
            }}
        } else {
            item {Panel(Modifier.fillMaxWidth()) {
                Text("DEMO GUIDATA",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
                Text("Carica 42 articoli fittizi con costi, fornitori, foto illustrative, scorte critiche, prelievi degli ultimi 90 giorni, prestiti, ubicazioni e planimetria. Puoi farlo anche in un magazzino con dati reali: la demo rimane separata.",fontSize=12.sp,color=TextSecondary)
                PrimaryAction("Carica dati dimostrativi",model::seedDemo,modifier=Modifier.fillMaxWidth())
            }}
        }
        item {Panel(Modifier.fillMaxWidth()) {
            Text("ESPORTAZIONE EXCEL / CSV",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            PrimaryAction("Esporta inventario CSV",onCsv,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick=onCsvImport,modifier=Modifier.fillMaxWidth()){Text("Importa articoli da CSV")}
            Text("I duplicati bloccano l’importazione. Occorre confermare l’anteprima.",fontSize=11.sp,color=TextSecondary)
            Text("Importazione: se il CSV contiene ubicazioni, crea prima nella Planimetria le strutture con gli stessi nomi. Una sede nuova non ha scaffali già configurati.",fontSize=11.sp,color=TextSecondary)
            Text("Il CSV è solo un elenco in chiaro: NON permette di ripristinare l'app. Usa il backup cifrato per salvare tutti i dati.",fontSize=11.sp,color=TextSecondary)
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("PRIVACY E LICENZE",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text("Informazioni sul trattamento locale dei dati, sull'uso di Google ARCore e sulle librerie incluse.",fontSize=12.sp,color=TextSecondary)
            OutlinedButton(onClick={showLegal=true},modifier=Modifier.fillMaxWidth()) {
                Text("Apri privacy e licenze di terze parti")
            }
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            UbicoraWordmark(compact=true)
            Text("Versione ${BuildConfig.VERSION_NAME} · Alpha",color=TextSecondary,fontSize=12.sp)
            Text("Inventario offline, mappa metrica, scanner QR, statistiche manageriali e backup cifrato.",color=TextSecondary,fontSize=12.sp)
        }}
    }
    if(editBudget) AlertDialog(onDismissRequest={editBudget=false},title={Text("Budget acquisti mensile")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Input(budgetText,{budgetText=it},"Importo disponibile (€)",number=true)
            Text("Lascia vuoto per nessun budget. Il controllo segnala impegni noti, non blocca acquisti approvati.",
                color=TextSecondary,fontSize=11.sp)
        }
    },confirmButton={TextButton(onClick={
        model.changeMonthlyBudget(budgetText.trim().replace(',','.').takeIf{it.isNotEmpty()}?.toDoubleOrNull())
        editBudget=false
    },enabled=budgetText.isBlank()||(budgetText.trim().replace(',','.').toDoubleOrNull()?.let{it.isFinite()&&it>=0.0}==true)){Text("Salva")}},
    dismissButton={TextButton(onClick={editBudget=false}){Text("Annulla")}})
    if(showLegal) LegalInfoDialog(onDismiss={showLegal=false})
    model.ui.csvPreviewCount?.let { count ->
        AlertDialog(onDismissRequest=model::cancelCsvImport,
            title={Text("Conferma importazione CSV")},
            text={Text("Aggiungere " + count + " articoli a questo magazzino? Non saranno modificati quelli esistenti. La quantità iniziale genera un movimento. Le fotografie non sono comprese.")},
            confirmButton={TextButton(onClick=model::confirmCsvImport,enabled=!model.ui.busy){Text("Importa")}},
            dismissButton={TextButton(onClick=model::cancelCsvImport){Text("Annulla")}})
    }
    if(newWarehouse) AlertDialog(onDismissRequest={newWarehouse=false},title={Text("Nuovo magazzino")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Input(name,{name=it},"Nome");Input(location,{location=it},"Località")}
    },confirmButton={TextButton(onClick={model.createWarehouse(name,location);newWarehouse=false},enabled=name.isNotBlank()){Text("Crea")}},
        dismissButton={TextButton(onClick={newWarehouse=false}){Text("Annulla")}})
    if(confirmDemoRefresh) AlertDialog(onDismissRequest={confirmDemoRefresh=false},title={Text("Rigenerare la demo completa?")},text={
        Text("Sostituisce tutti gli elementi marcati DEMO, comprese eventuali prove fatte su quegli articoli. Non elimina gli articoli creati personalmente. È consigliato esportare un backup prima.")
    },confirmButton={TextButton(onClick={model.refreshDemo();confirmDemoRefresh=false}){Text("Rigenera demo")}},
        dismissButton={TextButton(onClick={confirmDemoRefresh=false}){Text("Annulla")}})
    if(confirmDemoRemoval) AlertDialog(onDismissRequest={confirmDemoRemoval=false},title={Text("Eliminare la demo?")},text={
        Text("Rimuove soltanto articoli, strutture e pareti marcati come dimostrativi in questo magazzino. Gli articoli personali non verranno cancellati.")
    },confirmButton={TextButton(onClick={model.removeDemo();confirmDemoRemoval=false}){Text("Rimuovi demo",color=Danger)}},
        dismissButton={TextButton(onClick={confirmDemoRemoval=false}){Text("Annulla")}})
    if(backupDialog) AlertDialog(onDismissRequest={backupDialog=false},title={Text("Backup cifrato")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("Scegli una password di almeno 8 caratteri e conservala: protegge tutto l'archivio, inclusi PIN e fotografie.",fontSize=12.sp,color=TextSecondary)
            ArchivePins(model.ui.profiles,archivePins,{id,pin->archivePins=archivePins+(id to pin)})
            archiveError?.let{Text(it,color=Danger)}
            OutlinedTextField(secret,onValueChange={secret=it},label={Text("Password del backup")},
                visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(secretConfirm,onValueChange={secretConfirm=it},label={Text("Conferma password")},
                visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        }
    },confirmButton={TextButton(onClick={archiveScope.launch {
        try {model.authorizeArchive(archivePins);onBackup(secret);backupDialog=false;secret="";secretConfirm="";archivePins=emptyMap()}
        catch(e:Exception){archiveError=e.message}
    }},
        enabled=secret.length>=8 && secret==secretConfirm && !model.ui.busy){Text("Scegli dove salvare")}},
        dismissButton={TextButton(onClick={backupDialog=false}){Text("Annulla")}})
    if(restoreDialog) AlertDialog(onDismissRequest={restoreDialog=false},title={Text("Ripristino completo")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("ATTENZIONE: il ripristino sostituisce TUTTI i profili, magazzini, articoli, foto e movimenti esistenti su questo telefono.",color=Amber,fontSize=12.sp)
            ArchivePins(model.ui.profiles,archivePins,{id,pin->archivePins=archivePins+(id to pin)})
            archiveError?.let{Text(it,color=Danger)}
            OutlinedTextField(secret,onValueChange={secret=it},label={Text("Password del backup")},
                visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=understood,onCheckedChange={understood=it})
                Text("Ho capito: i dati attuali saranno sostituiti",fontSize=12.sp)
            }
        }
    },confirmButton={TextButton(onClick={archiveScope.launch {
        try {model.authorizeArchive(archivePins);onRestore(secret);restoreDialog=false;secret="";archivePins=emptyMap()}
        catch(e:Exception){archiveError=e.message}
    }},
        enabled=secret.length>=8 && understood && !model.ui.busy){Text("Seleziona backup",color=Amber)}},
        dismissButton={TextButton(onClick={restoreDialog=false}){Text("Annulla")}})
}

@Composable private fun ArchivePins(profiles:List<Profile>,pins:Map<Long,String>,onChange:(Long,String)->Unit) {
    Text("L'operazione riguarda tutti i profili: verifica ciascun PIN locale.",fontSize=12.sp,color=TextSecondary)
    profiles.forEach { p -> Input(pins[p.id].orEmpty(),{onChange(p.id,it.filter(Char::isDigit).take(8))},"PIN ${p.name}",number=true,password=true) }
}

