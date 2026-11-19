package com.officinastock.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Working offline reservation flow; physical stock changes ONLY when consumed. */
@Composable fun StockReservationsPanel(model:StockViewModel) {
    val ui=model.ui
    var creating by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf<Pair<Long,Boolean>?>(null) }
    val active=ui.reservations.filter{it.status=="ACTIVE"}
    Panel(Modifier.fillMaxWidth()) {
        Text("SCORTE PRENOTATE",fontWeight=FontWeight.Bold,color=Mint,fontSize=14.sp)
        Text("La prenotazione blocca la quantità disponibile senza scaricarla. Al consumo viene registrato il movimento.",
            color=TextSecondary,fontSize=12.sp)
        Text("Prime 35 referenze critiche o prenotate, incluse quelle esaurite.",color=TextSecondary,fontSize=11.sp)
        for(item in ui.items.filter{model.availableStock(it.id).reserved>0 || model.availableStock(it.id).available<=it.minimum}.take(35)){
            val available=model.availableStock(item.id)
            if(available.reserved>0 || item.quantity<=item.minimum) {
                Text("${item.name}: fisiche ${amount(available.physical)} · riservate ${amount(available.reserved)} · disponibili ${amount(available.available)}",
                    fontSize=12.sp,color=TextSecondary)
            }
        }
        OutlinedButton(onClick={
            chosen=null;search="";quantity="";reference="";creating=true
        },enabled=ui.items.isNotEmpty()&&!ui.busy,modifier=Modifier.fillMaxWidth()){
            Text("+ Prenota materiale per attività")
        }
        if(active.isEmpty())Text("Nessuna prenotazione aperta.",color=TextSecondary,fontSize=12.sp)
        active.forEach{r->
            HorizontalDivider()
            Text(ui.items.firstOrNull{it.id==r.itemId}?.name ?: "Articolo #${r.itemId}",
                fontWeight=FontWeight.SemiBold,fontSize=12.sp)
            Text("${amount(r.quantity)} · ${r.reference} · ${r.requestedBy}",
                color=TextSecondary,fontSize=11.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={closing=r.id to true},enabled=!ui.busy){Text("Consuma")}
                TextButton(onClick={closing=r.id to false},enabled=!ui.busy){Text("Libera")}
            }
        }
        Text("Cronologia: ${ui.reservations.count{it.status!="ACTIVE"}} prenotazioni chiuse.",
            color=TextSecondary,fontSize=11.sp)
    }
    if(creating){
        val parsed=quantity.trim().replace(',','.').toDoubleOrNull()
        val available=chosen?.let{model.availableStock(it).available} ?: 0.0
        AlertDialog(onDismissRequest={creating=false},title={Text("Prenota materiale")},
            text={Column(Modifier.heightIn(max=425.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(value=search,onValueChange={search=it},label={Text("Cerca articolo o codice")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                ui.items.filter{it.matchesSearch(search)}
                    .take(8).forEach{it->
                        Row(Modifier.fillMaxWidth().clickable{chosen=it.id},
                            verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=chosen==it.id,onClick={chosen=it.id})
                            Column {
                                Text(it.name,fontSize=12.sp)
                                Text(it.sku+" · disponibili "+amount(model.availableStock(it.id).available),
                                    color=TextSecondary,fontSize=11.sp)
                            }
                        }
                    }
                if(chosen!=null)Text("Massimo disponibile: "+amount(available),color=TextSecondary,fontSize=12.sp)
                OutlinedTextField(quantity,{quantity=it},label={Text("Quantità da riservare")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(reference,{reference=it.take(160)},label={Text("Intervento / commessa di destinazione")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
            }},
            confirmButton={TextButton(onClick={
                model.reserveStock(chosen!!,parsed!!,reference)
                creating=false
            },enabled=chosen!=null && parsed!=null && parsed.isFinite() && parsed>0 &&
                parsed<=available+StockRules.QUANTITY_EPSILON && reference.trim().length>=3 && !ui.busy){Text("Prenota")}},
            dismissButton={TextButton(onClick={creating=false}){Text("Annulla")}}
        )
    }
    closing?.let{(id,consume)->
        AlertDialog(onDismissRequest={closing=null},
            title={Text(if(consume)"Conferma consumo" else "Conferma rilascio")},
            text={Text(if(consume)"La quantità prenotata sarà scaricata realmente e il movimento registrato."
                else "La quantità tornerà disponibile, senza modificare la giacenza fisica.")},
            confirmButton={TextButton(onClick={model.closeReservation(id,consume);closing=null},
                enabled=!ui.busy){Text("Conferma")}},
            dismissButton={TextButton(onClick={closing=null}){Text("Annulla")}})
    }
}


