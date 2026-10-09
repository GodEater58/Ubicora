package com.officinastock.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val sections=listOf("Decisioni","Acquisti","Inventari","Trasferimenti","Manutenzioni","Registro","Anagrafiche","Prenotazioni","Lotti e seriali")
private fun statusLabel(status:String)=when(status) {
    "DRAFT"->"Da approvare";"APPROVED"->"Approvato";"ORDERED"->"Ordinato"
    "RECEIVED"->"Ricevuto";"CANCELLED"->"Annullato";"PENDING"->"Da verificare"
    "APPLIED"->"Confermato";"REQUESTED"->"Richiesto";"IN_TRANSIT"->"In transito"
    "OPEN"->"Aperto";"DONE"->"Completato";else->status
}

@Composable fun OperationsScreen(model:StockViewModel,onBack:()->Unit,onCsvExport:()->Unit,onPdfExport:()->Unit) {
    val ui=model.ui
    var section by remember {mutableIntStateOf(0)}
    var create by remember {mutableStateOf<String?>(null)}
    var purchaseItemId by remember {mutableStateOf<Long?>(null)}
    var receivingOrder by remember {mutableStateOf<PurchaseRequest?>(null)}
    val report=ui.operationalReports[30] ?: OperationalReport()
    val openOrders=report.openOrders
    val pendingCounts=report.pendingCounts
    val activeTransfers=report.activeTransfers
    val openWork=report.openWork
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,contentDescription="Torna alla panoramica")}
            Column(Modifier.weight(1f)) {
                Text("UBICORA · AZIENDALE",color=Mint,fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text("Centro operativo",fontSize=22.sp,fontWeight=FontWeight.Bold)
            }
            IconButton(onClick=onPdfExport){Icon(Icons.Outlined.PictureAsPdf,contentDescription="Esporta report PDF")}
            IconButton(onClick=onCsvExport){Icon(Icons.Outlined.Download,contentDescription="Esporta report CSV")}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            sections.forEachIndexed {idx,name ->
                FilterChip(selected=section==idx,onClick={section=idx},label={Text(name)})
            }
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(section==0) {
                item{Text("LE TUE PRIORITÀ",color=Mint,fontSize=11.sp,fontWeight=FontWeight.Bold)}
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SmallMetric("ACQUISTI",openOrders.toString(),"Da seguire",Amber,Modifier.weight(1f),onClick={section=1})
                        SmallMetric("CONTEGGI",pendingCounts.toString(),"Da confermare",Sky,Modifier.weight(1f),onClick={section=2})
                    }
                }
                item{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SmallMetric("TRASFERIMENTI",activeTransfers.toString(),"Da completare",Mint,Modifier.weight(1f),onClick={section=3})
                        SmallMetric("INTERVENTI",openWork.toString(),"Aperti",Amber,Modifier.weight(1f),onClick={section=4})
                    }
                }
                item { OperationalCharts(ui.operationalReports,ui.monthlyBudget,onSection={section=it}) }
                item{Panel(Modifier.fillMaxWidth()) {
                    Text("Scorte da riordinare",fontWeight=FontWeight.Bold)
                    val risks=ui.items.filter{model.availableStock(it.id).available<=it.minimum}.take(8)
                    if(risks.isEmpty())Text("Nessuna referenza sotto soglia.",color=TextSecondary)
                    risks.forEach{item ->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=13.sp)
                                Text(item.sku+" · disponibile "+amount(model.availableStock(item.id).available)+" / minimo "+amount(item.minimum),
                                    color=TextSecondary,fontSize=11.sp)
                            }
                            TextButton(onClick={purchaseItemId=item.id;create="purchase"}){Text("Acquista")}
                        }
                    }
                    Text("Il riordino richiede approvazione e conferma di ricezione: non modifica le scorte quando crei una richiesta.",
                        color=TextSecondary,fontSize=11.sp)
                }}
                item {Panel(Modifier.fillMaxWidth()) {
                    Text("Fornitori · ordini recenti",fontWeight=FontWeight.Bold)
                    val grouped=ui.orders.filter{it.supplier.isNotBlank()}.groupBy{it.supplier}
                        .toList().sortedByDescending{it.second.size}.take(5)
                    if(grouped.isEmpty())Text("Nessun fornitore con ordini registrati.",color=TextSecondary,fontSize=12.sp)
                    grouped.forEach{(supplier,orders)->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(supplier,modifier=Modifier.weight(1f),maxLines=1,
                                overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                            Text("Ricevuti "+orders.count{it.status=="RECEIVED"}+" / "+orders.size,
                                color=Sky,fontSize=11.sp)
                        }
                    }
                    Text("Analisi limitata alle ultime ${ui.operationsLimit} richieste caricate. La puntualità richiederà date di consegna effettive.",
                        color=TextSecondary,fontSize=11.sp)
                }}
                item{Panel(Modifier.fillMaxWidth()) {
                    Text("Responsabilità e accessi",fontWeight=FontWeight.Bold)
                    Text("Le operazioni sono attribuite al profilo locale corrente. La separazione tra persone, ruoli e approvazioni su dispositivi diversi richiede account verificati e sincronizzazione: non è simulata con un semplice campo di testo.",
                        color=TextSecondary,fontSize=12.sp)
                }}
            }
            if(section==1) {
                item{PrimaryAction("+ Richiesta d'acquisto",{create="purchase"},enabled=ui.items.isNotEmpty(),modifier=Modifier.fillMaxWidth())}
                if(ui.orders.isEmpty())item{EmptyOperation("Nessun ordine. Crea una richiesta scegliendo articolo e quantità.")}
                items(ui.orders.size){idx->
                    val o=ui.orders[idx]
                    Panel(Modifier.fillMaxWidth()) {
                        OperationHeading("Ordine #"+o.id,o.itemName,statusLabel(o.status))
                        Text(amount(o.quantity)+" · "+o.supplier.ifBlank{"Fornitore non indicato"}+" · "+o.owner,
                            color=TextSecondary,fontSize=12.sp)
                        o.centerId?.let { id ->
                            ui.costCenters.firstOrNull{it.id==id}?.let{c->
                                Text("Centro di costo: "+c.code+" · "+c.name,color=Sky,fontSize=11.sp)
                            }
                        }
                        Text("Ricevuti "+amount(o.received)+" / "+amount(o.quantity)+
                            " · Da ricevere "+amount((o.quantity-o.received).coerceAtLeast(0.0)),
                            fontSize=12.sp,color=TextSecondary)
                        o.unitPrice?.let{Text("Valore previsto: "+String.format(java.util.Locale.ITALY,"%.2f €",o.quantity*it),fontSize=12.sp)}
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                            when(o.status) {
                                "DRAFT"->{
                                    TextButton(onClick={model.advancePurchase(o.id,"APPROVE")}){Text("Approva")}
                                    TextButton(onClick={model.advancePurchase(o.id,"CANCEL")}){Text("Annulla")}
                                }
                                "APPROVED"->{
                                    TextButton(onClick={model.advancePurchase(o.id,"PLACE")}){Text("Segna ordinato")}
                                    TextButton(onClick={model.advancePurchase(o.id,"CANCEL")}){Text("Annulla")}
                                }
                                "ORDERED"->{
                                    TextButton(onClick={receivingOrder=o}){Text("Registra consegna")}
                                    TextButton(onClick={model.advancePurchase(o.id,"CANCEL")}){Text("Annulla")}
                                }
                            }
                        }
                    }
                }
            }
            if(section==2) {
                item{PrimaryAction("+ Nuovo conteggio",{create="count"},enabled=ui.items.isNotEmpty(),modifier=Modifier.fillMaxWidth())}
                if(ui.stockCounts.isEmpty())item{EmptyOperation("Nessun inventario ciclico avviato.")}
                items(ui.stockCounts.size){idx->
                    val c=ui.stockCounts[idx]
                    Panel(Modifier.fillMaxWidth()) {
                        OperationHeading("Conteggio #"+c.id,c.itemName,statusLabel(c.status))
                        Text("Sistema: "+amount(c.expected)+" · Rilevati: "+amount(c.counted)+
                            " · Differenza: "+amount(c.counted-c.expected),fontSize=12.sp)
                        Text(c.reason,color=TextSecondary,fontSize=12.sp)
                        if(c.status=="PENDING") Row {
                            TextButton(onClick={model.decideCycleCount(c.id,true)}){Text("Conferma rettifica")}
                            TextButton(onClick={model.decideCycleCount(c.id,false)}){Text("Scarta")}
                        }
                    }
                }
            }
            if(section==3) {
                item{PrimaryAction("+ Trasferisci tra magazzini",{create="transfer"},
                    enabled=ui.items.isNotEmpty()&&ui.warehouses.size>1,modifier=Modifier.fillMaxWidth())}
                if(ui.warehouses.size<2)item{EmptyOperation("Crea un altro magazzino nello stesso profilo per eseguire trasferimenti.")}
                if(ui.stockTransfers.isEmpty())item{EmptyOperation("Nessun trasferimento registrato.")}
                items(ui.stockTransfers.size){idx->
                    val t=ui.stockTransfers[idx]
                    Panel(Modifier.fillMaxWidth()) {
                        OperationHeading("Trasferimento #"+t.id,t.itemName,statusLabel(t.status))
                        val source=ui.warehouses.firstOrNull{it.id==t.fromId}?.name ?: "Magazzino "+t.fromId
                        val dest=ui.warehouses.firstOrNull{it.id==t.toId}?.name ?: "Magazzino "+t.toId
                        Text(source+" → "+dest+" · "+amount(t.quantity),color=TextSecondary,fontSize=12.sp)
                        if(t.status=="REQUESTED"&&ui.warehouse?.id==t.fromId)Row {
                            TextButton(onClick={model.processTransfer(t.id,"SHIP")}){Text("Conferma spedizione")}
                            TextButton(onClick={model.processTransfer(t.id,"CANCEL")}){Text("Annulla")}
                        }
                        if(t.status=="IN_TRANSIT")Row {
                            if(ui.warehouse?.id==t.toId)TextButton(onClick={model.processTransfer(t.id,"RECEIVE")}){Text("Ricevi articoli")}
                            if(ui.warehouse?.id==t.fromId)TextButton(onClick={model.processTransfer(t.id,"CANCEL")}){Text("Annulla e rientra")}
                        }
                    }
                }
                item{Text("La spedizione scarica l'origine; la ricezione carica la destinazione. Per confermare l'arrivo seleziona il magazzino destinatario.",
                    color=TextSecondary,fontSize=11.sp)}
            }
            if(section==4) {
                item{PrimaryAction("+ Intervento di manutenzione",{create="maintenance"},modifier=Modifier.fillMaxWidth())}
                if(ui.maintenanceWork.isEmpty())item{EmptyOperation("Nessun intervento di manutenzione inserito.")}
                items(ui.maintenanceWork.size){idx->
                    val w=ui.maintenanceWork[idx]
                    Panel(Modifier.fillMaxWidth()) {
                        OperationHeading("Intervento #"+w.id,w.machine,statusLabel(w.status))
                        Text(w.description,color=TextSecondary,fontSize=12.sp)
                        w.assetId?.let { asset ->
                            ui.businessAssets.firstOrNull{it.id==asset}?.let{
                                Text("Impianto: "+it.code+" · "+it.name,color=Sky,fontSize=11.sp)
                            }
                        }
                        if(w.status=="DONE") {
                            val laborCost=if(w.laborHours==0.0)0.0 else w.hourlyRate?.let{it*w.laborHours}
                            val total=if(laborCost!=null && w.partsCost!=null)laborCost+w.partsCost else null
                            Text("Costo ricambi: "+(w.partsCost?.let{amount(it)+" €"} ?: "non noto")+
                                " · Manodopera: "+(laborCost?.let{amount(it)+" €"} ?: "non nota"),
                                color=TextSecondary,fontSize=11.sp)
                            if(total!=null)Text("Consuntivo: "+amount(total)+" €",color=Mint,fontSize=12.sp)
                        }
                        if(w.itemId!=null)Text("Ricambio: "+(ui.items.firstOrNull{it.id==w.itemId}?.name ?: "Articolo rimosso")+
                            " · "+amount(w.quantity),fontSize=12.sp)
                        if(w.status=="OPEN")Row {
                            TextButton(onClick={model.finishWorkOrder(w.id,true)}){Text("Completa")}
                            TextButton(onClick={model.finishWorkOrder(w.id,false)}){Text("Annulla")}
                        }
                    }
                }
                item{Text("La chiusura dell'intervento scarica automaticamente i ricambi indicati e registra il movimento.",
                    color=TextSecondary,fontSize=11.sp)}
            }
            if(section==5) {
                item{Text("REGISTRO LOCALE",fontWeight=FontWeight.Bold,color=Mint)}
                if(ui.operationsEvents.isEmpty())item{EmptyOperation("Nessuna operazione registrata.")}
                items(ui.operationsEvents.size){idx->
                    val event=ui.operationsEvents[idx]
                    Panel(Modifier.fillMaxWidth()) {
                        Text(event.action.replace('_',' '),fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(event.detail,fontSize=12.sp)
                        Text(event.actor+" · "+date(event.at),color=TextSecondary,fontSize=11.sp)
                    }
                }
            }
            if(section==6) item {EnterpriseDirectoryPanel(model)}
            if(section==7) item {StockReservationsPanel(model)}
            if(section==8) item {StockTracePanel(model)}
            val loaded=when(section) {
                1->ui.orders.size;2->ui.stockCounts.size;3->ui.stockTransfers.size
                4->ui.maintenanceWork.size;5->ui.operationsEvents.size;7->ui.reservations.size
                8->maxOf(ui.stockLots.size,ui.stockSerials.size);else->0
            }
            if(section!=0 && loaded>=ui.operationsLimit && ui.operationsLimit<1000000) item {
                OutlinedButton(onClick={model.loadMoreOperations()},enabled=!ui.busy,modifier=Modifier.fillMaxWidth()) {
                    Text("Carica altre 250 operazioni")
                }
            }
            item{Spacer(Modifier.height(14.dp))}
        }
    }
    create?.let { kind ->
        NewOperationDialog(kind,model,preselectedItemId=if(kind=="purchase") purchaseItemId else null,
            onClose={create=null;purchaseItemId=null})
    }
    receivingOrder?.let { order ->
        var quantity by remember(order.id){mutableStateOf("")}
        val value=quantity.trim().replace(',','.').toDoubleOrNull()
        val remaining=order.quantity-order.received
        AlertDialog(onDismissRequest={receivingOrder=null},title={Text("Ricevimento ordine #"+order.id)},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(order.itemName+" · ancora da ricevere: "+amount(remaining),fontSize=13.sp)
                Text("Carica soltanto la merce arrivata. L'ordine resta aperto fino al saldo.",
                    fontSize=12.sp,color=TextSecondary)
                OutlinedTextField(quantity,{quantity=it},label={Text("Quantità ricevuta")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
            }},
            confirmButton={TextButton(onClick={
                model.receivePurchase(order.id,value!!)
                receivingOrder=null
            },enabled=value!=null && value.isFinite() && value>0.0 && value<=remaining && !ui.busy){
                Text("Registra arrivo")
            }},
            dismissButton={TextButton(onClick={receivingOrder=null}){Text("Annulla")}}
        )
    }
}

@Composable private fun EmptyOperation(message:String) {
    Panel(Modifier.fillMaxWidth()) {Text(message,color=TextSecondary,fontSize=12.sp)}
}
@Composable private fun OperationHeading(id:String,name:String,state:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(id,color=Mint,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
            Text(name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Text(state,color=Amber,fontWeight=FontWeight.SemiBold,fontSize=11.sp)
    }
}
@Composable private fun NewOperationDialog(kind:String,model:StockViewModel,preselectedItemId:Long?=null,onClose:()->Unit) {
    val ui=model.ui
    var search by remember(kind,preselectedItemId){mutableStateOf(preselectedItemId?.let{id->ui.items.firstOrNull{it.id==id}?.name}.orEmpty())}
    var selected by remember(kind,preselectedItemId){mutableStateOf(preselectedItemId)}
    var amountInput by remember(kind){mutableStateOf("")}
    var reason by remember(kind){mutableStateOf("")}
    var supplier by remember(kind){mutableStateOf("")}
    var centerSearch by remember(kind){mutableStateOf("")}
    var supplierSearch by remember(kind){mutableStateOf("")}
    var assetSearch by remember(kind){mutableStateOf("")}
    var selectedCenter by remember(kind){mutableStateOf<Long?>(null)}
    var selectedSupplier by remember(kind){mutableStateOf<Long?>(null)}
    var person by remember(kind){mutableStateOf(ui.profile?.name.orEmpty())}
    var machine by remember(kind){mutableStateOf("")}
    var selectedAsset by remember(kind){mutableStateOf<Long?>(null)}
    var laborHoursText by remember(kind){mutableStateOf("")}
    var hourlyRateText by remember(kind){mutableStateOf("")}
    var target by remember(kind){mutableStateOf<Long?>(null)}
    val parsed=amountInput.replace(',','.').trim().toDoubleOrNull()
    val needsItem=kind!="maintenance" || parsed!=null && parsed>0
    val validQuantity=parsed!=null && parsed.isFinite() && parsed>0.0
    val hours=laborHoursText.trim().replace(',','.').toDoubleOrNull()
    val rate=hourlyRateText.trim().replace(',','.').toDoubleOrNull()
    val validLabor=(laborHoursText.isBlank() || (hours!=null && hours.isFinite() && hours>=0 && hours<=10000)) &&
        (hourlyRateText.isBlank() || (rate!=null && rate.isFinite() && rate>=0 && rate<=1e7))
    val valid=when(kind) {
        "purchase" -> selected!=null && validQuantity && person.trim().length>=2
        "count" -> selected!=null && parsed!=null && StockRules.validQuantity(parsed) && reason.trim().length>=3
        "transfer" -> selected!=null && validQuantity && target!=null
        "maintenance" -> machine.trim().length>=2 && reason.trim().length>=3 && validLabor &&
            (amountInput.isBlank() || (parsed!=null && StockRules.validQuantity(parsed) && (parsed==0.0 || selected!=null)))
        else -> false
    }
    val title=when(kind){"purchase"->"Nuovo acquisto";"count"->"Inventario ciclico";
        "transfer"->"Trasferimento";else->"Intervento manutenzione"}
    AlertDialog(onDismissRequest=onClose,title={Text(title)},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            if(kind=="maintenance") {
                Input(machine,{machine=it;selectedAsset=null},"Macchina / impianto")
                if(ui.businessAssets.isNotEmpty()){
                    Text("Collega a un impianto registrato (facoltativo)",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Input(assetSearch,{assetSearch=it},"Cerca impianto / codice")
                    ui.businessAssets.filter{it.name.contains(assetSearch,true)||it.code.contains(assetSearch,true)}.take(35).forEach{ asset ->
                        FilterChip(selected=selectedAsset==asset.id,onClick={
                            selectedAsset=if(selectedAsset==asset.id)null else asset.id
                            if(selectedAsset!=null)machine=asset.name
                        },label={Text(asset.code+" · "+asset.name,fontSize=11.sp)})
                    }
                }
                Input(reason,{reason=it},"Descrizione intervento")
                Input(laborHoursText,{laborHoursText=it},"Ore di manodopera",number=true)
                Input(hourlyRateText,{hourlyRateText=it},"Costo orario € (facoltativo)",number=true)
                Text("Il costo ricambi viene fissato alla chiusura dell'intervento. Se manca un prezzo, il totale resta non noto.",
                    color=TextSecondary,fontSize=11.sp)
            }
            if(kind=="count")Text("Confronta il conteggio fisico con il dato a sistema. Nessuna rettifica avviene prima della conferma.",fontSize=12.sp,color=TextSecondary)
            if(kind=="transfer")Text("Scegli prima l'articolo nel magazzino attuale e poi la destinazione.",fontSize=12.sp,color=TextSecondary)
            if(kind!="maintenance")Input(search,{search=it},"Cerca articolo / codice")
            if(kind=="maintenance")Input(search,{search=it},"Ricambio facoltativo (cerca)")
            val candidates=ui.items.filter {
                search.isBlank() || it.name.contains(search,true) || it.sku.contains(search,true)
            }.take(6)
            candidates.forEach{item->
                Row(Modifier.fillMaxWidth().clickable{selected=item.id},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=selected==item.id,onClick={selected=item.id})
                    Column(Modifier.weight(1f)) {
                        Text(item.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                        Text(item.sku+" · giacenza "+amount(item.quantity),color=TextSecondary,fontSize=10.sp)
                    }
                }
            }
            Input(amountInput,{amountInput=it},when(kind) {
                "count"->"Quantità fisicamente contata"
                "maintenance"->"Ricambi consumati (lascia vuoto se nessuno)"
                else->"Quantità"
            },number=true)
            if(kind=="purchase") {
                if(ui.costCenters.isNotEmpty()) {
                    Text("Centro di costo (facoltativo)",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Input(centerSearch,{centerSearch=it},"Cerca centro di costo / codice")
                    ui.costCenters.filter{it.name.contains(centerSearch,true)||it.code.contains(centerSearch,true)}.take(30).forEach {c->
                        FilterChip(selected=selectedCenter==c.id,
                            onClick={selectedCenter=if(selectedCenter==c.id)null else c.id},
                            label={Text(c.code+" · "+c.name,fontSize=11.sp)})
                    }
                }
                if(ui.businessSuppliers.isNotEmpty()) {
                    Text("Fornitore da anagrafica (facoltativo)",fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    Input(supplierSearch,{supplierSearch=it},"Cerca fornitore in anagrafica")
                    ui.businessSuppliers.filter{it.name.contains(supplierSearch,true)}.take(30).forEach{f->
                        FilterChip(selected=selectedSupplier==f.id,onClick={
                            selectedSupplier=if(selectedSupplier==f.id)null else f.id
                            if(selectedSupplier!=null)supplier=f.name
                        },label={Text(f.name,fontSize=11.sp)})
                    }
                }
                Input(supplier,{supplier=it;selectedSupplier=null},"Fornitore (testo libero)")
                Input(person,{person=it},"Responsabile")
            }
            if(kind=="count")Input(reason,{reason=it},"Motivo / riferimento inventario")
            if(kind=="transfer") {
                Text("Magazzino di destinazione",fontWeight=FontWeight.SemiBold,fontSize=12.sp)
                ui.warehouses.filter{it.id!=ui.warehouse?.id}.forEach {wh->
                    Row(Modifier.fillMaxWidth().clickable{target=wh.id},verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=target==wh.id,onClick={target=wh.id})
                        Text(wh.name,fontSize=12.sp)
                    }
                }
            }
            if(kind=="maintenance")Text("Senza ricambi puoi lasciare vuota la quantità. Per scaricarli seleziona articolo e quantità.",fontSize=11.sp,color=TextSecondary)
        }
    },confirmButton={TextButton(onClick={
        when(kind) {
            "purchase"->model.requestPurchase(selected!!,parsed!!,supplier,person,selectedCenter,selectedSupplier)
            "count"->model.requestCycleCount(selected!!,parsed!!,reason)
            "transfer"->model.requestTransfer(selected!!,target!!,parsed!!)
            "maintenance"->model.createWorkOrder(machine,reason,selected,parsed?:0.0,selectedAsset,hours?:0.0,rate)
        }
        onClose()
    },enabled=valid&&!ui.busy){Text("Salva")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}


