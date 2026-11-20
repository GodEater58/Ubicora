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

fun amount(d:Double):String= if(d%1.0==0.0) d.toLong().toString() else String.format(Locale.ITALY,"%.2f",d)
fun date(ts:Long):String=SimpleDateFormat("dd MMM · HH:mm",Locale.ITALY).format(Date(ts))

@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier,shape=RoundedCornerShape(24.dp),color=PanelBg,border=BorderStroke(1.dp,Hairline.copy(alpha=.06f))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
/** Identita visiva Ubicora. Il marchio non modifica i codici QR o il database. */
@Composable fun UbicoraWordmark(compact:Boolean=false) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(color=Color(0xFF143D48),shape=RoundedCornerShape(if(compact)14.dp else 22.dp)) {
            androidx.compose.foundation.Image(
                painter=painterResource(R.drawable.ic_ubicora_u_selected),
                contentDescription="Simbolo Ubicora",
                modifier=Modifier.size(if(compact)50.dp else 76.dp))
        }
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("Ubicora",color=TextPrimary,fontSize=if(compact)23.sp else 32.sp,
                fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.8).sp)
            Text("INVENTARIO  ·  SPAZI  ·  ANALISI",color=Mint,fontSize=if(compact)8.sp else 9.sp,
                fontWeight=FontWeight.Bold,letterSpacing=0.7.sp)
        }
    }
}

@Composable fun SectionTitle(title:String,subtitle:String="",modifier:Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,color=TextPrimary,fontSize=26.sp,fontWeight=FontWeight.Bold,fontFamily=Display,lineHeight=28.sp)
        if(subtitle.isNotEmpty()) Text(subtitle,color=TextSecondary,fontSize=13.sp)
    }
}
@Composable fun Input(value:String,onChange:(String)->Unit,label:String,modifier:Modifier=Modifier,number:Boolean=false,password:Boolean=false) {
    OutlinedTextField(value,onValueChange=onChange,label={Text(label)},modifier=modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Number else KeyboardType.Text),
        visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable fun PrimaryAction(text:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=48.dp),shape=RoundedCornerShape(14.dp)) {Text(text,fontWeight=FontWeight.SemiBold)}
}

@Composable fun SmallMetric(label:String,value:String,subtitle:String,color:Color,modifier:Modifier=Modifier,onClick:(()->Unit)?=null) {
    Panel(if(onClick!=null) modifier.clickable(onClick=onClick) else modifier){
        Text(label,color=TextSecondary,fontSize=10.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Text(value,color=color,fontSize=27.sp,fontWeight=FontWeight.Bold,maxLines=1)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(subtitle,color=TextSecondary,fontSize=11.sp,modifier=Modifier.weight(1f),maxLines=2)
            if(onClick!=null) Icon(Icons.Outlined.ChevronRight,"Apri dettaglio",tint=color,modifier=Modifier.size(17.dp))
        }
    }
}
@Composable fun QuickAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit,modifier:Modifier=Modifier) {
    Surface(onClick=click,modifier=modifier,shape=RoundedCornerShape(18.dp),color=Surface2) {
        Column(Modifier.padding(vertical=20.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(icon,null,tint=Mint,modifier=Modifier.size(28.dp));Spacer(Modifier.height(8.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable fun MovementRow(move:Movement,onClick:(()->Unit)?=null) {
    Panel(Modifier.fillMaxWidth().then(if(onClick!=null)Modifier.clickable(onClick=onClick) else Modifier)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(if(move.delta>=0) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,null,tint=if(move.delta>=0)Mint else Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(move.itemName,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                // 4.19.3: reason on one line, code and time on the other, so the time never wraps alone.
                if(move.note.isNotBlank()) Text(move.note,fontSize=12.sp,color=TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${move.sku} · ${TodayTasks.who(move.actor,date(move.createdAt))}",fontSize=11.sp,color=TextSecondary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            Text("${if(move.delta>0)"+" else ""}${amount(move.delta)}",color=if(move.delta>=0)Mint else Amber,fontWeight=FontWeight.Bold)
            if(onClick!=null) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Outlined.ChevronRight,"Apri attività",tint=TextSecondary,modifier=Modifier.size(17.dp))
            }
        }
    }
}

/** Unit of measure chosen from a menu: the category's usual units first, then all the others. */
@Composable fun UnitPicker(value:String,category:String,onChange:(String)->Unit) {
    var open by remember { mutableStateOf(false) }
    val options=MeasureUnits.options(category,value)
    val suggested=MeasureUnits.suggested(category)
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(MeasureUnits.label(value),onValueChange={},readOnly=true,singleLine=true,
            label={Text("Unità di misura")},
            trailingIcon={Icon(if(open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,contentDescription=null)},
            modifier=Modifier.fillMaxWidth())
        // The read-only field does not open menus by itself: a transparent layer takes the tap.
        Box(Modifier.matchParentSize().clickable(onClickLabel="Scegli l'unità di misura") { open=true })
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            options.forEachIndexed { index,code ->
                if(index>0 && code !in suggested && options[index-1] in suggested) HorizontalDivider()
                DropdownMenuItem(
                    text={Text(MeasureUnits.label(code),fontWeight=if(code==value) FontWeight.Bold else FontWeight.Normal,
                        color=if(code in suggested || code==value) TextPrimary else TextSecondary)},
                    onClick={onChange(code);open=false})
            }
        }
    }
}

/** Small ±1 button on an inventory row (40 dp touch target). */
@Composable fun QuickStep(icon:androidx.compose.ui.graphics.vector.ImageVector,description:String,enabled:Boolean,onClick:()->Unit) {
    FilledTonalIconButton(onClick=onClick,enabled=enabled,modifier=Modifier.size(40.dp)) {
        Icon(icon,contentDescription=description,modifier=Modifier.size(18.dp))
    }
}

/** 4.7.2: list orderings. */
enum class StockSort(val label:String) {
    NAME("Nome A–Z"), CRITICAL("Critici prima"), QTY_LOW("Quantità crescente"),
    QTY_HIGH("Quantità decrescente"), RECENT("Ultimi movimentati"), CODE("Codice")
}

/** Orders a filtered list; [lastMoved] maps item id to its latest movement time. */
fun sortStock(items:List<StockItem>,sort:StockSort,lastMoved:Map<Long,Long>):List<StockItem> = when(sort) {
    StockSort.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StockSort.CRITICAL -> items.sortedWith(compareBy<StockItem>({ !it.lowStock },{ if(it.minimum>0) it.quantity/it.minimum else Double.MAX_VALUE }).thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StockSort.QTY_LOW -> items.sortedWith(compareBy<StockItem> { it.quantity }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StockSort.QTY_HIGH -> items.sortedWith(compareByDescending<StockItem> { it.quantity }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StockSort.RECENT -> items.sortedWith(compareByDescending<StockItem> { lastMoved[it.id] ?: 0L }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    StockSort.CODE -> items.sortedBy { it.sku }
}
