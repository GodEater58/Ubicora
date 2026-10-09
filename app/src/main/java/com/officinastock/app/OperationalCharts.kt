package com.officinastock.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable fun OperationalCharts(reports:Map<Int,OperationalReport>,budget:Double?,onSection:(Int)->Unit) {
    var days by remember{mutableIntStateOf(30)}
    val r=reports[days] ?: return
    val money=NumberFormat.getCurrencyInstance(Locale.ITALY)
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("INDICATORI E COSTI",color=Mint,fontWeight=FontWeight.Bold,fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(7,30,90).forEach{n->FilterChip(selected=days==n,onClick={days=n},label={Text("$n giorni")})}
        }
        Text("Magazzino selezionato · stato attuale e consuntivi nel periodo",color=TextSecondary,fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SmallMetric("SCORTE CRITICHE",r.criticalAvailable.toString(),"Dopo le prenotazioni",Danger,Modifier.weight(1f),onClick={onSection(7)})
            SmallMetric("PRENOTATE",r.reservedReferences.toString(),"Referenze impegnate",Sky,Modifier.weight(1f),onClick={onSection(7)})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SmallMetric("LOTTI SCADUTI",r.expiredLots.toString(),"Con stock residuo",Danger,Modifier.weight(1f),onClick={onSection(8)})
            SmallMetric("IN SCADENZA",r.expiringLots.toString(),"Entro 30 giorni",Amber,Modifier.weight(1f),onClick={onSection(8)})
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("Acquisti · stato delle richieste",fontWeight=FontWeight.Bold)
            Text("Tutte le richieste del magazzino, senza limite di 250 record",color=TextSecondary,fontSize=11.sp)
            val labels=listOf("DRAFT" to "Da approvare","APPROVED" to "Da ordinare","ORDERED" to "In consegna","RECEIVED" to "Ricevuti","CANCELLED" to "Annullati")
            val max=labels.maxOf{r.orderStages[it.first]?:0}.coerceAtLeast(1)
            labels.forEach{(status,label)->
                val n=r.orderStages[status]?:0
                Column(Modifier.fillMaxWidth().clickable{onSection(1)}.padding(vertical=4.dp)) {
                    Row{Text(label,Modifier.weight(1f),fontSize=12.sp);Text(n.toString(),color=Sky,fontSize=12.sp)}
                    LinearProgressIndicator(progress={n.toFloat()/max},modifier=Modifier.fillMaxWidth().height(6.dp),color=Sky)
                }
            }
            TextButton(onClick={onSection(1)}){Text("Gestisci acquisti")}
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("Budget acquisti · mese corrente",fontWeight=FontWeight.Bold)
            Text("Tutti i magazzini del profilo · richieste create questo mese",color=TextSecondary,fontSize=11.sp)
            Text("Impegni con prezzo noto: ${money.format(r.monthlyCommitments)}",color=Mint)
            budget?.let{limit->
                Text("Budget ${money.format(limit)} · residuo ${money.format(limit-r.monthlyCommitments)}",fontSize=12.sp,
                    color=if(r.monthlyCommitments>limit)Danger else TextSecondary)
                LinearProgressIndicator(progress={if(limit>0)(r.monthlyCommitments/limit).toFloat().coerceIn(0f,1f) else if(r.monthlyCommitments>0)1f else 0f},
                    modifier=Modifier.fillMaxWidth().height(8.dp),color=if(r.monthlyCommitments>limit)Danger else Mint)
            } ?: Text("Imposta il budget nelle impostazioni del profilo.",fontSize=12.sp,color=TextSecondary)
            if(r.unpricedOrders>0)Text("${r.unpricedOrders} richieste senza prezzo: totale e residuo parziali",color=Amber,fontSize=11.sp)
            Text("Include approvati, ordinati e ricevuti; esclude bozze e annullati. È un impegno d'acquisto, non un pagamento.",color=TextSecondary,fontSize=11.sp)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("Manutenzione · costi per macchina",fontWeight=FontWeight.Bold)
            Text("${r.completedWork} interventi completati · ${money.format(r.maintenanceCost)} con costo completo noto",color=Mint,fontSize=12.sp)
            if(r.unpricedWork>0)Text("${r.unpricedWork} interventi con costi incompleti esclusi dal totale",color=Amber,fontSize=11.sp)
            if(r.assetCosts.isEmpty())Text("Nessun completamento nel periodo scelto",color=TextSecondary,fontSize=12.sp)
            val max=r.assetCosts.maxOfOrNull{it.knownCost}?.coerceAtLeast(1.0) ?: 1.0
            r.assetCosts.take(6).forEach{cost->
                Column(Modifier.fillMaxWidth().clickable{onSection(4)}.padding(vertical=4.dp)) {
                    Text(cost.machine,fontSize=12.sp)
                    Text("${money.format(cost.knownCost)} · ${cost.completed} completati · ${cost.unpriced} incompleti",fontSize=11.sp,color=TextSecondary)
                    LinearProgressIndicator(progress={(cost.knownCost/max).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(6.dp),color=Mint)
                }
            }
            TextButton(onClick={onSection(4)}){Text("Apri manutenzioni")}
        }
        Panel(Modifier.fillMaxWidth().clickable{onSection(2)}) {
            Text("Inventari · conteggi senza scostamento",fontWeight=FontWeight.Bold)
            Text(r.countAccuracy?.let{String.format(Locale.ITALY,"%.1f%%",it)} ?: "Nessun conteggio confermato",color=Sky,fontSize=22.sp)
            Text("${r.exactCounts} esatti su ${r.confirmedCounts} conteggi confermati e creati nei $days giorni. È il campione contato, non l'accuratezza dell'intero inventario.",color=TextSecondary,fontSize=11.sp)
            TextButton(onClick={onSection(2)}){Text("Verifica conteggi")}
        }
    }
}
