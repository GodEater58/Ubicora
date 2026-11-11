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

// 4.15: moved out of Screens.kt without changes — settings: archive, backup, display, warehouses.

@Composable fun SettingsScreen(model:StockViewModel,onCsv:()->Unit,onCsvImport:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,goTeam:()->Unit={}) {
    val archiveScope=rememberCoroutineScope()
    var archivePins by remember { mutableStateOf<Map<Long,String>>(emptyMap()) }
    var archiveError by remember { mutableStateOf<String?>(null) }
    var newWarehouse by remember{mutableStateOf(false)}
    var deleteWarehouse by remember{mutableStateOf(false)}
    var renameWarehouse by remember{mutableStateOf(false)}
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
        item {SectionTitle("Impostazioni","Azienda, sedi, squadra e sicurezza dei dati")}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("AZIENDA",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text(model.ui.profile?.name ?: "",fontWeight=FontWeight.Bold,fontSize=19.sp)
            OutlinedButton(onClick=model::signOut,modifier=Modifier.fillMaxWidth()){Text("Cambia azienda o esci")}
        }}
        item {Panel(Modifier.fillMaxWidth()) {
            Text("MODALITÀ D'USO",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if(model.ui.businessMode)"Aziendale" else "Personale",fontWeight=FontWeight.Bold,fontSize=17.sp)
                    Text(if(model.ui.businessMode) "Acquisti, approvazioni, inventari e manutenzioni" else
                        "Inventario e planimetria, senza moduli amministrativi",color=TextSecondary,fontSize=11.sp)
                }
                Spacer(Modifier.width(12.dp))
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
            Text("SEDE",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text(model.ui.warehouse?.name ?: "",fontWeight=FontWeight.Bold,fontSize=19.sp)
            OutlinedButton(onClick={model.leaveWarehouse()},modifier=Modifier.fillMaxWidth()){Text("Cambia sede")}
            OutlinedButton(onClick={renameWarehouse=true},enabled=!model.ui.busy,modifier=Modifier.fillMaxWidth()){Text("Rinomina questa sede")}
            OutlinedButton(onClick={newWarehouse=true},modifier=Modifier.fillMaxWidth()){Text("Aggiungi una sede")}
            TextButton(onClick={deleteWarehouse=true},enabled=!model.ui.busy,modifier=Modifier.fillMaxWidth()){Text("Elimina questa sede",color=Danger)}
        }}
        // 4.19.3: the team is managed only in the Squadra tab, not twice.
        item {Panel(Modifier.fillMaxWidth()) {
            Text("SQUADRA",color=Mint,fontSize=11.sp,letterSpacing=1.sp)
            Text(when(model.ui.team.role){TeamRole.CENTRAL->"Questo è il telefono centrale";TeamRole.SATELLITE->"Satellite ${model.ui.team.letter}";else->"Telefono non collegato"},
                fontWeight=FontWeight.Bold,fontSize=17.sp)
            OutlinedButton(onClick=goTeam,modifier=Modifier.fillMaxWidth()){Text("Apri Squadra")}
        }}
        item { DisplayPanel() }
        item { SecurityPanel(model) }
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
                Text("${model.ui.demoCount} elementi di esempio in questa sede. La rimozione preserva gli elementi creati da te.",fontSize=12.sp,color=TextSecondary)
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
                // 4.19.3: a secondary button, so it does not look like the main action in an archive with real data.
                Text("42 articoli di esempio per provare l'app. Restano separati dai tuoi dati e si tolgono con un tocco.",fontSize=12.sp,color=TextSecondary)
                OutlinedButton(onClick=model::seedDemo,modifier=Modifier.fillMaxWidth()){Text("Carica dati dimostrativi")}
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
            text={Text("Aggiungere " + count + " articoli a questa sede? Non saranno modificati quelli esistenti. La quantità iniziale genera un movimento. Le fotografie non sono comprese.")},
            confirmButton={TextButton(onClick=model::confirmCsvImport,enabled=!model.ui.busy){Text("Importa")}},
            dismissButton={TextButton(onClick=model::cancelCsvImport){Text("Annulla")}})
    }
    if(newWarehouse) AlertDialog(onDismissRequest={newWarehouse=false},title={Text("Nuova sede")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Input(name,{name=it},"Nome");Input(location,{location=it},"Località")}
    },confirmButton={TextButton(onClick={model.createWarehouse(name,location);newWarehouse=false},enabled=name.isNotBlank()){Text("Crea")}},
        dismissButton={TextButton(onClick={newWarehouse=false}){Text("Annulla")}})
    if(deleteWarehouse) model.ui.warehouse?.let { w -> DeleteWarehouseDialog(model,w) { deleteWarehouse=false } }
    if(renameWarehouse) model.ui.warehouse?.let { w -> RenameWarehouseDialog(model,w) { renameWarehouse=false } }
    if(confirmDemoRefresh) AlertDialog(onDismissRequest={confirmDemoRefresh=false},title={Text("Rigenerare la demo completa?")},text={
        Text("Sostituisce tutti gli elementi marcati DEMO, comprese eventuali prove fatte su quegli articoli. Non elimina gli articoli creati personalmente. È consigliato esportare un backup prima.")
    },confirmButton={TextButton(onClick={model.refreshDemo();confirmDemoRefresh=false}){Text("Rigenera demo")}},
        dismissButton={TextButton(onClick={confirmDemoRefresh=false}){Text("Annulla")}})
    if(confirmDemoRemoval) AlertDialog(onDismissRequest={confirmDemoRemoval=false},title={Text("Eliminare la demo?")},text={
        Text("Rimuove soltanto articoli, strutture e pareti marcati come dimostrativi in questa sede. Gli articoli personali non verranno cancellati.")
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


/** 4.12.1/4.13: delete a warehouse after typing its name; refused while it is the synced one. */
@Composable fun DeleteWarehouseDialog(model:StockViewModel,w:Warehouse,onClose:()->Unit) {
    var typed by remember(w.id){mutableStateOf("")}
    val synced=model.ui.team.role!=TeamRole.NONE && model.ui.team.covers(w.id)
    AlertDialog(onDismissRequest=onClose,title={Text("Eliminare «${w.name}»?")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(synced) Text("Questa sede è condivisa con la squadra. Per eliminarla, prima scollega questo telefono dalla scheda Squadra.",color=Danger)
            else {
                Text("Cancella definitivamente articoli, foto, movimenti, prestiti, planimetria e dati gestionali di questa sede. Non si può annullare: se servono, esporta prima un backup.")
                Input(typed,{typed=it},"Scrivi il nome della sede per confermare")
            }
        }
    },confirmButton={ if(!synced) TextButton(onClick={model.deleteWarehouse(w,typed);onClose()},
        enabled=typed.trim().equals(w.name.trim(),ignoreCase=true)&&!model.ui.busy){Text("Elimina",color=Danger)} },
        dismissButton={TextButton(onClick=onClose){Text(if(synced) "Chiudi" else "Annulla")}})
}

/** 4.14: new name and place of a warehouse. On a synced satellite the central decides the name. */
@Composable fun RenameWarehouseDialog(model:StockViewModel,w:Warehouse,onClose:()->Unit) {
    var name by remember(w.id){mutableStateOf(w.name)}
    var location by remember(w.id){mutableStateOf(w.location)}
    val satellite=model.ui.team.role==TeamRole.SATELLITE && model.ui.team.covers(w.id)
    AlertDialog(onDismissRequest=onClose,title={Text("Rinomina sede")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(satellite) Text("Questa sede è condivisa: il nome si cambia dal telefono centrale e arriva qui con la sincronizzazione.",color=Amber)
            else {
                Input(name,{name=it.take(160)},"Nome sede")
                Input(location,{location=it.take(300)},"Località / descrizione")
                if(model.ui.team.role==TeamRole.CENTRAL && model.ui.team.covers(w.id))
                    Text("Il nuovo nome arriva anche ai satelliti con la prossima sincronizzazione.",fontSize=11.sp,color=TextSecondary)
            }
        }
    },confirmButton={ if(!satellite) TextButton(onClick={model.renameWarehouse(w,name,location);onClose()},
        enabled=name.isNotBlank()&&(name.trim()!=w.name||location.trim()!=w.location)&&!model.ui.busy){Text("Salva")} },
        dismissButton={TextButton(onClick=onClose){Text(if(satellite) "Chiudi" else "Annulla")}})
}
