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

// 4.15: moved out of Screens.kt without changes — history of movements and loans.

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
