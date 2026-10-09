package com.officinastock.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Operational queue; no automatic stock adjustment or purchase order is generated. */
@Composable fun ManagerOperationsPanel(model:StockViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf("PURCHASE") }
    var title by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var assignee by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    val actions=model.ui.managerActions
    val open=actions.filter{it.status=="OPEN"}
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Assignment,null,tint=Mint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("CENTRO DECISIONALE",fontWeight=FontWeight.Bold,fontSize=16.sp)
                Text("${open.size} attività aperte · Magazzino attuale",color=TextSecondary,fontSize=12.sp)
            }
            IconButton(onClick={expanded=!expanded}) {
                Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Espandi attività")
            }
        }
        if(expanded) {
            Text("Richieste, conteggi da verificare e incarichi. Una richiesta non genera ordini né cambia le giacenze.",color=TextSecondary,fontSize=12.sp)
            OutlinedButton(onClick={adding=true},modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add,null)
                Spacer(Modifier.width(8.dp))
                Text("Nuova attività")
            }
            if(open.isEmpty()) Text("Nessuna attività aperta.",color=Mint)
            open.take(15).forEach { action ->
                HorizontalDivider()
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("${when(action.kind){"PURCHASE"->"Acquisto";"COUNT"->"Conteggio";else->"Incarico"}} · ${action.title}",
                        fontWeight=FontWeight.SemiBold)
                    if(action.quantity!=null) Text("Quantità richiesta: ${amount(action.quantity)}",fontSize=12.sp,color=TextSecondary)
                    if(action.assignee.isNotBlank()) Text("Responsabile: ${action.assignee}",fontSize=12.sp,color=TextSecondary)
                    if(action.details.isNotBlank()) Text(action.details,fontSize=12.sp,color=TextSecondary)
                    TextButton(onClick={model.closeManagerAction(action.id)},enabled=!model.ui.busy) {
                        Icon(Icons.Outlined.CheckCircleOutline,null,modifier=Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp));Text("Segna completata")
                    }
                }
            }
            if(actions.size>open.size) Text("${actions.size-open.size} attività concluse o annullate conservate nello storico",
                color=TextSecondary,fontSize=11.sp)
        }
    }
    if(adding) AlertDialog(
        onDismissRequest={adding=false},
        title={Text("Nuova attività manageriale")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("PURCHASE" to "Acquisto","COUNT" to "Conteggio","TASK" to "Incarico").forEach { (key,label) ->
                        FilterChip(selected=kind==key,onClick={kind=key},label={Text(label,fontSize=11.sp)})
                    }
                }
                Input(title,{title=it.take(120)},"Oggetto attività")
                if(kind=="PURCHASE") Input(quantity,{quantity=it},"Quantità da acquistare",number=true)
                Input(assignee,{assignee=it.take(100)},"Responsabile (facoltativo)")
                OutlinedTextField(value=details,onValueChange={details=it.take(500)},label={Text("Note / riferimento")},
                    minLines=2,maxLines=4,modifier=Modifier.fillMaxWidth())
            }
        },
        confirmButton={
            val qty=quantity.trim().replace(',','.').toDoubleOrNull()
            val valid=title.trim().length>=3 && (kind!="PURCHASE" || (qty!=null&&qty.isFinite()&&qty>0.0))
            TextButton(onClick={
                model.createManagerAction(kind,title.trim(),if(kind=="PURCHASE")qty else null,assignee,details)
                adding=false;title="";quantity="";assignee="";details=""
            },enabled=valid&&!model.ui.busy){Text("Salva attività")}
        },
        dismissButton={TextButton(onClick={adding=false}){Text("Annulla")}}
    )
}
