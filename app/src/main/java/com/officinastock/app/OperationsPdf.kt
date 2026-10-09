package com.officinastock.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Report manageriale PDF offline (A4), senza servizi cloud o dati inventati. */
object OperationsPdf {
    fun write(state:StockUiState,out:OutputStream) {
        val warehouse=requireNotNull(state.warehouse){"Magazzino non selezionato"}
        require(state.profile!=null && !state.sessionLocked){"Accedi per esportare il report"}
        val report=ManagerMetrics.compute(state.items,state.manager,30)
        val eur=NumberFormat.getCurrencyInstance(Locale.ITALY)
        val doc=PdfDocument()
        val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(25,43,54);textSize=10f
            typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        }
        val titlePen=Paint(pen).apply {textSize=17f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        val sectionPen=Paint(pen).apply {textSize=12f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        var page:PdfDocument.Page?=null
        var canvas:Canvas?=null
        var y=40f
        var pageNo=0
        fun beginPage(){
            page?.let{p->
                p.canvas.drawText("Ubicora · report locale | Pag. $pageNo",36f,813f,pen)
                doc.finishPage(p)
            }
            pageNo++
            page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
            canvas=page!!.canvas
            y=44f
        }
        fun requireSpace(height:Float) {
            if(canvas==null || y+height>790f)beginPage()
        }
        fun writeLine(value:String,style:Paint=pen,spacing:Float=16f){
            var remainder=value.replace("\r"," ").replace("\n"," ").trim()
            val width=523f
            if(remainder.isEmpty()){requireSpace(spacing);y+=spacing;return}
            while(remainder.isNotEmpty()){
                requireSpace(spacing)
                val nch=style.breakText(remainder,true,width,null).coerceAtLeast(1)
                val cut=if(nch<remainder.length) {
                    remainder.lastIndexOf(' ',startIndex=nch-1).takeIf{it>0} ?: nch
                } else nch
                val line=remainder.take(cut).trim()
                canvas!!.drawText(line,36f,y,style)
                y+=spacing
                remainder=remainder.drop(cut).trimStart()
            }
        }
        fun section(text:String){
            requireSpace(38f)
            y+=12f
            writeLine(text.uppercase(Locale.ITALY),sectionPen,21f)
            canvas!!.drawLine(36f,y-10f,559f,y-10f,pen)
        }
        try {
            beginPage()
            writeLine("UBICORA · RAPPORTO MANAGERIALE",titlePen,25f)
            writeLine(warehouse.name+" · "+warehouse.location.ifBlank{"Ubicazione non specificata"})
            writeLine("Profilo: "+state.profile.name+" | Data: "+LocalDate.now())
            writeLine("Dati locali, relativi al magazzino selezionato. Scorte note: valori parziali se mancano costi.")
            section("Sintesi scorte")
            writeLine("Articoli: "+report.itemCount+" · Esauriti: "+report.zeroStock+" · Sotto soglia: "+report.lowStock)
            writeLine("Senza ubicazione: "+report.missingLocation+" · Costo noto per "+report.pricedCount+" articoli")
            writeLine("Valore noto scorte: "+eur.format(report.knownStockValue))
            writeLine("Budget mensile: "+(state.monthlyBudget?.let(eur::format) ?: "non impostato"))
            section("Acquisti")
            if(state.orders.isEmpty())writeLine("Nessuna richiesta registrata.")
            state.orders.forEach {o->
                writeLine("#"+o.id+" ["+o.status+"] "+o.itemName+" · "+o.quantity+
                    " · "+o.supplier.ifBlank{"Senza fornitore"}+
                    (o.unitPrice?.let{" · "+eur.format(it*o.quantity)}?:"")+
                    " · Responsabile "+o.owner)
            }
            section("Inventari ciclici")
            if(state.stockCounts.isEmpty())writeLine("Nessun conteggio registrato.")
            state.stockCounts.forEach{c->
                writeLine("#"+c.id+" ["+c.status+"] "+c.itemName+
                    " · Sistema "+c.expected+" · Contati "+c.counted+
                    " · Differenza "+(c.counted-c.expected)+" · "+c.reason)
            }
            section("Trasferimenti")
            if(state.stockTransfers.isEmpty())writeLine("Nessun trasferimento registrato.")
            state.stockTransfers.forEach{t->
                writeLine("#"+t.id+" ["+t.status+"] "+t.itemName+
                    " · Quantita "+t.quantity+" · Da "+t.fromId+" a "+t.toId)
            }
            section("Manutenzioni")
            if(state.maintenanceWork.isEmpty())writeLine("Nessun intervento registrato.")
            state.maintenanceWork.forEach{w->
                writeLine("#"+w.id+" ["+w.status+"] "+w.machine+
                    " · "+w.description+" · Ricambi "+w.quantity)
            }
            section("Scorte critiche")
            state.items.filter{it.lowStock}.take(120).forEach{i->
                writeLine(i.sku+" · "+i.name+" · Giacenza "+i.quantity+" · Minimo "+i.minimum)
            }
            section("Limiti del rapporto")
            writeLine("Il rapporto considera gli ultimi 250 eventi per tipo di workflow e fino a 120 scorte critiche.")
            writeLine("Budget e valori non rappresentano contabilita certificata. L'accesso e locale per profilo.")
            page?.let{p->
                p.canvas.drawText("Ubicora · report locale | Pag. $pageNo",36f,813f,pen)
                doc.finishPage(p)
                page=null
            }
            doc.writeTo(out)
        } finally {
            page?.let{runCatching{doc.finishPage(it)}}
            doc.close()
        }
    }
}
