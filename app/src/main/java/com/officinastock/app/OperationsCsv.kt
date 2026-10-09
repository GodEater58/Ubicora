package com.officinastock.app

/** Report importabile in Excel. Solo le informazioni del magazzino selezionato. */
object OperationsCsv {
    private fun field(value:Any?):String {
        var s=value?.toString().orEmpty()
        // Non permettere formule eseguibili quando si apre il report in un foglio di calcolo.
        if(s.trimStart().startsWith("=") || s.trimStart().startsWith("+") ||
            s.trimStart().startsWith("-") || s.trimStart().startsWith("@") ||
            s.startsWith("\t") || s.startsWith("\r")) s="'"+s
        return "\""+s.replace("\"","＂").replace("\r"," ").replace("\n"," ")+"\""
    }
    private fun row(vararg columns:Any?):String=columns.joinToString(";"){field(it)}
    fun export(state:StockUiState):String {
        val warehouse=requireNotNull(state.warehouse){"Magazzino non selezionato"}
        require(state.profile!=null && !state.sessionLocked){"Accedi per esportare i dati"}
        val out=StringBuilder()
        out.appendLine(row("UBICORA","REPORT GESTIONALE",warehouse.name,warehouse.location))
        out.appendLine(row("Sezione","Riferimento","Stato","Articolo / macchina","Quantita","Valore noto EUR","Dettaglio"))
        state.orders.forEach {o ->
            out.appendLine(row("ACQUISTI","#"+o.id,o.status,o.itemName,o.quantity,
                o.unitPrice?.let{it*o.quantity},o.supplier+" · Responsabile: "+o.owner))
        }
        state.stockCounts.forEach{c ->
            out.appendLine(row("INVENTARI","#"+c.id,c.status,c.itemName,c.counted-c.expected,
                null,"Previsto: "+c.expected+"; rilevato: "+c.counted+"; "+c.reason))
        }
        state.stockTransfers.forEach{t ->
            out.appendLine(row("TRASFERIMENTI","#"+t.id,t.status,t.itemName,t.quantity,null,
                "Da "+t.fromId+" a "+t.toId))
        }
        state.maintenanceWork.forEach{w ->
            out.appendLine(row("MANUTENZIONE","#"+w.id,w.status,w.machine,w.quantity,null,w.description))
        }
        state.items.filter{it.lowStock}.forEach{i ->
            out.appendLine(row("DA RIORDINARE",i.sku,"CRITICO",i.name,i.minimum-i.quantity,
                i.unitCost?.let{c->c*(i.minimum-i.quantity)},i.supplier))
        }
        return out.toString()
    }
}
