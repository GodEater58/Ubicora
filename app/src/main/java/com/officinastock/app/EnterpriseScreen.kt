package com.officinastock.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Offline catalog CRUD. Staff roles are informational, never authentication. */
@Composable fun EnterpriseDirectoryPanel(model:StockViewModel) {
    val ui=model.ui
    var dialog by remember {mutableStateOf<String?>(null)}
    var title by remember {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    var second by remember {mutableStateOf("")}
    var third by remember {mutableStateOf("")}
    var amount by remember {mutableStateOf("")}
    var role by remember {mutableStateOf("WAREHOUSE")}
    var center by remember {mutableStateOf<Long?>(null)}
    fun resetAndOpen(kind:String){
        title="";code="";second="";third="";amount="";role="WAREHOUSE";center=null
        dialog=kind
    }
    Panel(Modifier.fillMaxWidth()) {
        Text("ANAGRAFICHE AZIENDALI",color=Mint,fontWeight=FontWeight.Bold,fontSize=12.sp)
        Text("Centri di costo, fornitori, macchine e rubrica del personale sono separati per profilo.",
            color=TextSecondary,fontSize=12.sp)
        Text("Le attività restano locali. ${ui.unsentCatalogEvents} eventi anagrafici pronti per un futuro server.",
            color=TextSecondary,fontSize=11.sp)
        HorizontalDivider()
        Text("Centri di costo · ${ui.costCenters.size}",fontWeight=FontWeight.SemiBold)
        ui.costCenters.take(25).forEach{ c ->
            Text("${c.code} · ${c.name} · budget ${c.monthlyBudget?.let{amount(it)+" €"} ?: "non assegnato"}",
                fontSize=12.sp,color=TextSecondary)
            val requests=ui.orders.filter{it.centerId==c.id &&
                it.status in listOf("APPROVED","ORDERED","RECEIVED")}
            val known=requests.filter{it.unitPrice!=null}.sumOf{it.quantity*(it.unitPrice?:0.0)}
            if(requests.isNotEmpty())Text("Impegni noti: ${amount(known)} € su ${requests.size} richieste" +
                if(requests.any{it.unitPrice==null})" · prezzi mancanti" else "",fontSize=11.sp,color=TextSecondary)
        }
        OutlinedButton(onClick={resetAndOpen("center")},modifier=Modifier.fillMaxWidth(),
            enabled=!ui.busy){Text("Aggiungi centro di costo")}
        HorizontalDivider()
        Text("Fornitori · ${ui.businessSuppliers.size}",fontWeight=FontWeight.SemiBold)
        ui.businessSuppliers.take(30).forEach{
            Text(it.name+" · "+(it.leadDays?.let{d->"$d giorni"} ?: "consegna non definita"),
                fontSize=12.sp,color=TextSecondary)
        }
        OutlinedButton(onClick={resetAndOpen("supplier")},modifier=Modifier.fillMaxWidth(),
            enabled=!ui.busy){Text("Aggiungi fornitore")}
        HorizontalDivider()
        Text("Macchine e impianti · ${ui.businessAssets.size}",fontWeight=FontWeight.SemiBold)
        ui.businessAssets.take(30).forEach{
            Text(it.code+" · "+it.name,fontSize=12.sp,color=TextSecondary)
        }
        OutlinedButton(onClick={resetAndOpen("asset")},modifier=Modifier.fillMaxWidth(),
            enabled=!ui.busy){Text("Registra macchina / impianto")}
        HorizontalDivider()
        Text("Rubrica persone · ${ui.staffDirectory.size}",fontWeight=FontWeight.SemiBold)
        ui.staffDirectory.take(25).forEach{
            Text(it.displayName+" · "+it.proposedRole,fontSize=12.sp,color=TextSecondary)
        }
        OutlinedButton(onClick={resetAndOpen("staff")},modifier=Modifier.fillMaxWidth(),
            enabled=!ui.busy){Text("Aggiungi riferimento interno")}
        Text("ATTENZIONE: la rubrica NON concede permessi. Prima di autorizzare acquisti tramite ruolo occorrono login personali verificati e controlli lato server.",
            color=Amber,fontSize=11.sp)
        Text("Server non configurato: nessun invio automatico di dati. La coda locale contiene solo le nuove anagrafiche.",
            color=TextSecondary,fontSize=11.sp)
    }
    val current=dialog
    if(current!=null) {
        val enteredBudget=amount.trim().replace(',','.').toDoubleOrNull()
        val lead=amount.trim().toIntOrNull()
        val valid=when(current) {
            "center" -> code.trim().matches(Regex("[A-Za-z0-9_-]{2,24}")) &&
                title.trim().length>=2 && (amount.isBlank() || (enteredBudget!=null && enteredBudget.isFinite() && enteredBudget>=0))
            "supplier" -> title.trim().length>=2 &&
                (amount.isBlank()||(lead!=null && lead in 0..3650)) &&
                (third.isBlank() || third.contains('@'))
            "asset" -> code.trim().matches(Regex("[A-Za-z0-9_-]{2,32}")) && title.trim().length>=2
            else -> title.trim().length>=2
        }
        AlertDialog(onDismissRequest={dialog=null},
            title={Text(when(current) {
                "center"->"Nuovo centro di costo"
                "supplier"->"Nuovo fornitore"
                "asset"->"Nuova macchina"
                else->"Nuovo contatto aziendale"
            })},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title,{title=it.take(160)},label={Text("Nome")},singleLine=true,
                    modifier=Modifier.fillMaxWidth())
                if(current=="center" || current=="asset") OutlinedTextField(code,{code=it.take(32)},
                    label={Text("Codice")},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(current=="center") OutlinedTextField(amount,{amount=it},label={Text("Budget mensile € (facoltativo)")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                if(current=="supplier") {
                    OutlinedTextField(second,{second=it.take(40)},label={Text("P. IVA / identificativo fiscale")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(third,{third=it.take(180)},label={Text("Email (facoltativa)")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(amount,{amount=it.take(4)},label={Text("Consegna prevista, giorni")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                }
                if(current=="asset") {
                    Text("Collega a un centro di costo (facoltativo)",fontSize=12.sp)
                    ui.costCenters.take(30).forEach{c->
                        FilterChip(selected=center==c.id,onClick={center=if(center==c.id)null else c.id},
                            label={Text(c.code+" · "+c.name,fontSize=11.sp)})
                    }
                }
                if(current=="staff") {
                    Text("Ruolo previsto, non abilitazione all'accesso",fontSize=12.sp)
                    listOf("MANAGER","PURCHASING","WAREHOUSE","MAINTENANCE","VIEWER").forEach{entry->
                        FilterChip(selected=role==entry,onClick={role=entry},
                            label={Text(entry,fontSize=11.sp)})
                    }
                }
            }},
            confirmButton={TextButton(onClick={
                when(current) {
                    "center"->model.addCostCenter(code,title,enteredBudget)
                    "supplier"->model.addBusinessSupplier(title,second,third,lead)
                    "asset"->model.addBusinessAsset(code,title,center)
                    "staff"->model.addStaffDirectoryEntry(title,role)
                }
                dialog=null
            },enabled=valid && !ui.busy){Text("Salva")}},
            dismissButton={TextButton(onClick={dialog=null}){Text("Annulla")}}
        )
    }
}
