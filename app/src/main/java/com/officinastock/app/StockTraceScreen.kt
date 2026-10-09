package com.officinastock.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Instant

/** Registrations are assignments of existing stock, not receipts. */
@Composable fun StockTracePanel(model:StockViewModel) {
    val ui=model.ui
    var dialog by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<Long?>(null)}
    var search by remember{mutableStateOf("")}
    var code by remember{mutableStateOf("")}
    var quantity by remember{mutableStateOf("")}
    var expiry by remember{mutableStateOf("")}
    var consumingLot by remember{mutableStateOf<StockLot?>(null)}
    var issuingSerial by remember{mutableStateOf<StockSerial?>(null)}
    fun open(kind:String){selected=null;search="";code="";quantity="";expiry="";dialog=kind}
    val today=LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant().toEpochMilli()
    Panel(Modifier.fillMaxWidth()){
        Text("TRACCIABILITÀ MATERIALI",fontWeight=FontWeight.Bold,fontSize=14.sp,color=Mint)
        Text("Lotti, scadenze e numeri di serie su quantità già presenti. Registrare un codice non crea nuove scorte.",
            color=TextSecondary,fontSize=12.sp)
        Text("Lotti con materiale: ${ui.stockLots.count{it.quantity>0}} · seriali disponibili: ${ui.stockSerials.count{it.state=="IN_STOCK"}}",
            color=TextSecondary,fontSize=12.sp)
        OutlinedButton(onClick={open("LOT")},modifier=Modifier.fillMaxWidth(),enabled=ui.items.isNotEmpty()&&!ui.busy){
            Text("+ Registra lotto e scadenza")
        }
        OutlinedButton(onClick={open("SERIAL")},modifier=Modifier.fillMaxWidth(),enabled=ui.items.isNotEmpty()&&!ui.busy){
            Text("+ Registra numero di serie")
        }
        if(ui.stockLots.isEmpty() && ui.stockSerials.isEmpty())
            Text("Nessun materiale tracciato.",color=TextSecondary,fontSize=12.sp)
        ui.stockLots.filter{it.quantity>0}.take(40).forEach{lot->
            HorizontalDivider()
            Text(lot.code+" · "+(ui.items.firstOrNull{it.id==lot.itemId}?.name ?: "Articolo #${lot.itemId}"),
                fontWeight=FontWeight.SemiBold,fontSize=12.sp)
            val expired=lot.expiresAt?.let{it<today} ?: false
            Text("In stock: "+amount(lot.quantity)+
                (lot.expiresAt?.let{" · scadenza "+Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()} ?: "")+
                if(expired)" · SCADUTO" else "",color=if(expired)Danger else TextSecondary,fontSize=11.sp)
            TextButton(onClick={consumingLot=lot},enabled=!ui.busy){Text("Registra consumo del lotto")}
        }
        ui.stockSerials.filter{it.state=="IN_STOCK"}.take(40).forEach{serial->
            HorizontalDivider()
            Text(serial.code+" · "+(ui.items.firstOrNull{it.id==serial.itemId}?.name ?: "Articolo #${serial.itemId}"),
                fontSize=12.sp,fontWeight=FontWeight.SemiBold)
            TextButton(onClick={issuingSerial=serial},enabled=!ui.busy){Text("Registra assegnazione del seriale")}
        }
        Text("Lotti esauriti e seriali assegnati restano nello storico locale. I movimenti di magazzino sono registrati allo scarico.",
            color=TextSecondary,fontSize=11.sp)
    }
    dialog?.let{kind->
        val qty=quantity.trim().replace(',','.').toDoubleOrNull()
        val chosen=selected?.let{id->ui.items.firstOrNull{it.id==id}}
        val lotDate=if(expiry.isBlank())null else runCatching{
            LocalDate.parse(expiry.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val validDate=expiry.isBlank()||lotDate!=null
        val valid=chosen!=null && code.trim().length>=(if(kind=="LOT")2 else 3) &&
            (kind=="SERIAL" || (qty!=null && qty.isFinite() && qty>0)) && validDate
        AlertDialog(onDismissRequest={dialog=null},title={Text(if(kind=="LOT")"Nuovo lotto" else "Nuovo seriale")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(search,{search=it},label={Text("Cerca articolo / codice")},singleLine=true,
                    modifier=Modifier.fillMaxWidth())
                ui.items.filter{search.isBlank() || it.sku.contains(search,true)||it.name.contains(search,true)}
                    .take(8).forEach{item->
                        Row(Modifier.fillMaxWidth().clickable{selected=item.id},verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected=selected==item.id,onClick={selected=item.id})
                            Text(item.name+" · "+amount(item.quantity)+" "+item.unit,fontSize=12.sp)
                        }
                    }
                chosen?.let{
                    Text("Giacenza fisica ${amount(it.quantity)} ${it.unit}. Il lotto o seriale identifica materiale esistente.",
                        color=TextSecondary,fontSize=11.sp)
                }
                OutlinedTextField(code,{code=it.take(100)},label={Text(if(kind=="LOT")"Codice lotto" else "Numero di serie")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                if(kind=="LOT") {
                    OutlinedTextField(quantity,{quantity=it},label={Text("Quantità da etichettare")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(expiry,{expiry=it.take(10)},label={Text("Scadenza (AAAA-MM-GG), facoltativa")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    if(!validDate)Text("Inserisci una data valida, es. 2027-03-31",color=Danger,fontSize=11.sp)
                } else Text("Un seriale equivale a una unità, solo per articoli con unità 'pz'.",fontSize=11.sp,color=TextSecondary)
            }},
            confirmButton={TextButton(onClick={
                if(kind=="LOT")model.createStockLot(chosen!!.id,code,qty!!,lotDate)
                else model.createStockSerial(chosen!!.id,code)
                dialog=null
            },enabled=valid&&!ui.busy){Text("Registra")}},
            dismissButton={TextButton(onClick={dialog=null}){Text("Annulla")}})
    }
    consumingLot?.let{lot->
        var amountInput by remember(lot.id){mutableStateOf("")}
        val qty=amountInput.replace(',','.').trim().toDoubleOrNull()
        AlertDialog(onDismissRequest={consumingLot=null},title={Text("Consuma lotto "+lot.code)},
            text={Column {
                Text("Disponibile sul lotto: "+amount(lot.quantity),fontSize=12.sp)
                OutlinedTextField(amountInput,{amountInput=it},label={Text("Quantità da consumare")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
            }},
            confirmButton={TextButton(onClick={
                model.consumeStockLot(lot.id,qty!!)
                consumingLot=null
            },enabled=qty!=null&&qty.isFinite()&&qty>0&&qty<=lot.quantity&&!ui.busy){Text("Scarica")}},
            dismissButton={TextButton(onClick={consumingLot=null}){Text("Annulla")}})
    }
    issuingSerial?.let{serial->
        AlertDialog(onDismissRequest={issuingSerial=null},title={Text("Assegna seriale "+serial.code)},
            text={Text("Il dispositivo sarà segnato come assegnato e verrà scaricata una unità fisica dall'inventario.")},
            confirmButton={TextButton(onClick={model.issueStockSerial(serial.id);issuingSerial=null},
                enabled=!ui.busy){Text("Conferma scarico")}},
            dismissButton={TextButton(onClick={issuingSerial=null}){Text("Annulla")}})
    }
}
