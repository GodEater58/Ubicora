package com.officinastock.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

private fun euro(value:Double):String=NumberFormat.getCurrencyInstance(Locale.ITALY).format(value)

@Composable fun PurchasePlanningDialog(item:StockItem,onClose:()->Unit,onSave:(Double?,String,Int?)->Unit) {
    var cost by remember(item.id){mutableStateOf(item.unitCost?.toString()?:"")}
    var supplier by remember(item.id){mutableStateOf(item.supplier)}
    var lead by remember(item.id){mutableStateOf(item.leadDays?.toString()?:"")}
    val parsedCost=cost.trim().replace(',','.').takeIf{it.isNotBlank()}?.toDoubleOrNull()
    val parsedLead=lead.trim().takeIf{it.isNotBlank()}?.toIntOrNull()
    val valid=(cost.isBlank()||(parsedCost!=null&&parsedCost.isFinite()&&parsedCost>=0.0&&parsedCost<=100000000.0)) &&
        (lead.isBlank()||(parsedLead!=null&&parsedLead in 0..3650))
    AlertDialog(onDismissRequest=onClose,title={Text("Pianificazione acquisti")},text={
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text("${item.sku} · ${item.name}",color=TextSecondary,fontSize=12.sp)
            Input(cost,{cost=it},"Costo unitario (€)",number=true)
            Input(supplier,{supplier=it},"Fornitore")
            Input(lead,{lead=it},"Tempo di consegna (giorni)",number=true)
            Text("Lascia il costo vuoto se non lo conosci. Non sarà stimato artificialmente.",color=TextSecondary,fontSize=11.sp)
        }
    },confirmButton={TextButton(onClick={onSave(parsedCost,supplier,parsedLead)},enabled=valid){Text("Salva dati")}},
        dismissButton={TextButton(onClick=onClose){Text("Annulla")}})
}

@Composable fun ManagerAnalyticsPanel(items:List<StockItem>,snapshot:ManagerSnapshot,onReorder:()->Unit) {
    var days by remember { mutableIntStateOf(30) }
    var expanded by remember { mutableStateOf(true) }
    val report=remember(items,snapshot,days){ManagerMetrics.compute(items,snapshot,days)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INTELLIGENCE DI MAGAZZINO",color=Mint,fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.4.sp)
                Text("Controllo manageriale",fontSize=22.sp,fontWeight=FontWeight.Bold)
            }
            IconButton(onClick={expanded=!expanded}) {
                Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Espandi report")
            }
        }
        if(expanded) {
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(7,30,90).forEach { n ->
                    FilterChip(selected=days==n,onClick={days=n},label={Text("$n giorni")})
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SmallMetric("VALORE NOTO",euro(report.knownStockValue),"${report.pricedCount}/${report.itemCount} costi noti",Mint,Modifier.weight(1f))
                SmallMetric("ESAURITI",report.zeroStock.toString(),"Referenze a zero",Danger,Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SmallMetric("MOV. ENTRATA",report.inboundEvents.toString(),"Operazioni nei $days giorni",Sky,Modifier.weight(1f))
                SmallMetric("MOV. USCITA",report.outboundEvents.toString(),"Operazioni nei $days giorni",Amber,Modifier.weight(1f))
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BarChart,null,tint=Mint)
                    Spacer(Modifier.width(8.dp))
                    Text("Flusso movimenti",fontWeight=FontWeight.Bold,fontSize=16.sp)
                }
                Text("Numero di operazioni, non somma di pezzi, kg e metri.",color=TextSecondary,fontSize=11.sp)
                if(snapshot.daily.isEmpty()) {
                    Text("Ancora nessun movimento registrato.",color=TextSecondary,fontSize=12.sp)
                } else {
                    ManagementFlowChart(snapshot.daily,days)
                    Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        Text("●  Entrate",color=Sky,fontSize=12.sp)
                        Text("●  Uscite",color=Amber,fontSize=12.sp)
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber,null,tint=Amber)
                    Spacer(Modifier.width(7.dp))
                    Text("Priorità di riordino",fontWeight=FontWeight.Bold,fontSize=16.sp)
                }
                Text("${report.lowStock} referenze sotto soglia o esaurite · ${report.missingLocation} senza ubicazione",fontSize=12.sp,color=TextSecondary)
                if(report.reorder.isEmpty()) Text("Nessun articolo critico con le soglie attuali.",fontSize=12.sp,color=Mint)
                report.reorder.take(5).forEach { risk ->
                    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                        Icon(if(risk.item.quantity==0.0)Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,null,
                            tint=if(risk.item.quantity==0.0)Danger else Amber,modifier=Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(risk.item.name,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            val coverage=risk.coverageDays?.let { " · Copertura ${it.toInt()} gg" }?:""
                            Text("${risk.item.sku} · ${amount(risk.item.quantity)} / minimo ${amount(risk.item.minimum)}$coverage",fontSize=11.sp,color=TextSecondary)
                        }
                        if(risk.leadRisk) Text("Rischio",color=Danger,fontSize=11.sp,fontWeight=FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick=onReorder,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Inventory2,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Apri inventario")
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Text("Capitale immobilizzato per categoria",fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text("Giacenza × costo unitario, solo articoli valorizzati",fontSize=11.sp,color=TextSecondary)
                if(report.categoryValues.isEmpty()) Text("Inserisci i costi unitari per attivare il grafico.",color=TextSecondary,fontSize=12.sp)
                val total=report.categoryValues.sumOf{it.second}
                report.categoryValues.take(7).forEach { (category,value) ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(category,modifier=Modifier.weight(1f),fontSize=12.sp)
                        Text(euro(value),fontSize=12.sp,color=Mint)
                    }
                    LinearProgressIndicator(progress={if(total<=0.0)0f else (value/total).toFloat().coerceIn(0f,1f)},
                        modifier=Modifier.fillMaxWidth().height(6.dp),color=Mint,trackColor=Color.White.copy(alpha=.08f))
                }
                if(report.pricedCount<report.itemCount) Text("${report.itemCount-report.pricedCount} articoli senza costo: totale parziale, non valore completo.",color=Amber,fontSize=11.sp)
            }
            Panel(Modifier.fillMaxWidth()) {
                Text("Analisi ABC · valore dei consumi",fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text("Prelievi valorizzati nel periodo, prestiti esclusi. A ≈ 80%, B ≈ 15%, C ≈ 5%.",fontSize=11.sp,color=TextSecondary)
                if(report.abc.isEmpty()) Text("Servono prelievi registrati e costi unitari per elaborare la classificazione.",fontSize=12.sp,color=TextSecondary)
                report.abc.take(6).forEach {line ->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(color=when(line.group){"A"->Amber.copy(alpha=.18f);"B"->Sky.copy(alpha=.18f);else->Mint.copy(alpha=.14f)},shape=RoundedCornerShape(8.dp)) {
                            Text(line.group,modifier=Modifier.padding(horizontal=11.dp,vertical=6.dp),fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(line.item.name,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
                        Text(euro(line.usageValue),fontSize=11.sp,color=TextSecondary)
                    }
                }
                Text("Totale consumi valorizzati: ${euro(report.usageValue)}",color=Mint,fontSize=12.sp)
                if(report.usageUnpriced>0)Text("${report.usageUnpriced} referenze consumate senza prezzo: ABC parziale.",fontSize=11.sp,color=Amber)
            }
            Panel(Modifier.fillMaxWidth()) {
                Text("Utilizzo e scorte ferme",fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text("${report.noWithdrawal90} referenze senza prelievi registrati negli ultimi 90 giorni. Include quelle nuove: non equivale a obsolescenza.",fontSize=12.sp,color=TextSecondary)
                report.topConsumed.take(5).forEach { (item,qty) ->
                    Row {
                        Text(item.name,modifier=Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("${amount(qty)} ${item.unit}",color=Sky,fontSize=12.sp)
                    }
                }
                if(report.topConsumed.isEmpty()) Text("Nessun prelievo nel periodo scelto.",fontSize=12.sp,color=TextSecondary)
            }
            Text("Nota: non sono calcolabili rotazione contabile, margini, accuratezza inventariale e puntualità fornitori senza costi storici, conteggi fisici e ordini d'acquisto.",
                fontSize=11.sp,color=TextSecondary)
        }
    }
}

@Composable private fun ManagementFlowChart(daily:List<DailyActivity>,periodDays:Int) {
    val today=LocalDate.now()
    val values=daily.associateBy{it.day}
    val days=(periodDays-1 downTo 0).map{today.minusDays(it.toLong()).toString()}
    val buckets=if(periodDays==7)7 else 10
    val counts=(0 until buckets).map { idx ->
        val start=idx*periodDays/buckets
        val end=(idx+1)*periodDays/buckets
        val chunk=days.subList(start,end)
        chunk.sumOf{values[it]?.inboundEvents?:0} to chunk.sumOf{values[it]?.outboundEvents?:0}
    }
    val maxCount=max(1,counts.maxOf{max(it.first,it.second)})
    Canvas(Modifier.fillMaxWidth().height(155.dp)) {
        val spacing=size.width/counts.size
        val baseline=size.height-5.dp.toPx()
        counts.forEachIndexed {idx,pair ->
            val barWidth=spacing*.22f
            val groupX=idx*spacing+spacing*.21f
            listOf(pair.first to Sky,pair.second to Amber).forEachIndexed {j,item ->
                val h=(item.first.toFloat()/maxCount*baseline).coerceAtLeast(if(item.first>0)3.dp.toPx() else 0f)
                drawRoundRect(item.second,topLeft=Offset(groupX+j*(barWidth+spacing*.09f),baseline-h),
                    size=Size(barWidth,h),cornerRadius=CornerRadius(3.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("${periodDays} gg fa",fontSize=10.sp,color=TextSecondary)
        Text("Oggi",fontSize=10.sp,color=TextSecondary)
    }
}
