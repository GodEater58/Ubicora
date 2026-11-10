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

// 4.15: moved out of Screens.kt without changes — profiles and warehouses: first profile, PIN, choice of the warehouse.

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
@OptIn(ExperimentalFoundationApi::class)
@Composable fun WarehouseChooserScreen(model:StockViewModel) {
    var show by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<Warehouse?>(null)}
    var renaming by remember{mutableStateOf<Warehouse?>(null)}
    var actionsFor by remember{mutableStateOf<Warehouse?>(null)}
    var name by remember{mutableStateOf("")};var location by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Spacer(Modifier.height(26.dp))
        Text("IL TUO AMBIENTE",color=Mint,fontWeight=FontWeight.SemiBold,letterSpacing=2.sp)
        Spacer(Modifier.height(12.dp))
        SectionTitle("Dove lavoriamo?","Profilo ${model.ui.profile?.name}")
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(model.ui.warehouses,key={it.id}) { w ->
                Panel(Modifier.fillMaxWidth().combinedClickable(onClick={model.selectWarehouse(w)},onLongClick={actionsFor=w})) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warehouse,null,tint=Mint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {Text(w.name,fontWeight=FontWeight.Bold);Text(w.location.ifBlank{"Nessuna località"},color=TextSecondary,fontSize=12.sp)}
                        Icon(Icons.Outlined.ArrowForwardIos,null,tint=TextSecondary,modifier=Modifier.size(16.dp))
                    }
                }
            }
            if(model.ui.warehouses.isNotEmpty()) item { Text("Tieni premuto un magazzino per rinominarlo o eliminarlo",fontSize=11.sp,color=TextSecondary) }
            // 4.10: a colleague's phone can join the team straight away, without creating a warehouse first.
            if(model.ui.team.role==TeamRole.NONE) item { TeamSyncPanel(model) }
        }
        PrimaryAction("+  Crea nuovo magazzino",{show=true},modifier=Modifier.fillMaxWidth())
        TextButton(onClick=model::signOut){Text("Cambia profilo")}
        model.ui.message?.let{Text(it,color=Danger)}
    }
    deleting?.let { w -> DeleteWarehouseDialog(model,w) { deleting=null } }
    renaming?.let { w -> RenameWarehouseDialog(model,w) { renaming=null } }
    actionsFor?.let { w -> AlertDialog(onDismissRequest={actionsFor=null},title={Text(w.name)},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={actionsFor=null;renaming=w},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Edit,null);Spacer(Modifier.width(8.dp));Text("Rinomina")}
            OutlinedButton(onClick={actionsFor=null;deleting=w},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DeleteOutline,null,tint=Danger);Spacer(Modifier.width(8.dp));Text("Elimina",color=Danger)}
        }
    },confirmButton={TextButton(onClick={actionsFor=null}){Text("Chiudi")}}) }
    if(show) AlertDialog(onDismissRequest={show=false},title={Text("Nuovo magazzino")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {Input(name,{name=it},"Nome magazzino");Input(location,{location=it},"Località / descrizione")}
    },confirmButton={TextButton(onClick={model.createWarehouse(name,location);show=false},enabled=name.isNotBlank()){Text("Crea")}},
        dismissButton={TextButton(onClick={show=false}){Text("Annulla")}})
}
