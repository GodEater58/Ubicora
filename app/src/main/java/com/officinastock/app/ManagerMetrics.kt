package com.officinastock.app

/** La reportistica usa i movimenti effettivi, mai dati casuali o valori ipotetici. */
data class DailyActivity(val day: String, val inboundEvents: Int, val outboundEvents: Int)
data class ItemUsage(val itemId: Long, val consumed7: Double, val consumed30: Double, val consumed90: Double)
data class ManagerSnapshot(val daily: List<DailyActivity> = emptyList(), val usage: List<ItemUsage> = emptyList())
data class ReorderPriority(val item: StockItem, val shortfall: Double, val coverageDays: Double?, val leadRisk: Boolean)
data class AbcLine(val item: StockItem, val usageValue: Double, val group: String)
data class ManagerReport(
    val periodDays: Int,
    val itemCount: Int,
    val zeroStock: Int,
    val lowStock: Int,
    val missingLocation: Int,
    val pricedCount: Int,
    val knownStockValue: Double,
    val inboundEvents: Int,
    val outboundEvents: Int,
    val noWithdrawal90: Int,
    val usageValue: Double,
    val usageValued: Int,
    val usageUnpriced: Int,
    val reorder: List<ReorderPriority>,
    val categoryValues: List<Pair<String,Double>>,
    val abc: List<AbcLine>,
    val topConsumed: List<Pair<StockItem,Double>>
)

object ManagerMetrics {
    fun compute(items: List<StockItem>, snapshot: ManagerSnapshot, days: Int): ManagerReport {
        require(days in listOf(7,30,90))
        val usageById=snapshot.usage.associateBy { it.itemId }
        fun used(x: ItemUsage?): Double=when(days){7->x?.consumed7 ?: 0.0;30->x?.consumed30 ?: 0.0;else->x?.consumed90 ?: 0.0}
        val nowSince=java.time.LocalDate.now().minusDays(days.toLong()-1).toString()
        val filtered=snapshot.daily.filter{it.day>=nowSince}
        val under=items.filter { it.quantity < it.minimum || it.quantity == 0.0 }
        val priorities=under.map{ item ->
            val monthly=usageById[item.id]?.consumed30 ?: 0.0
            val coverage=if(monthly>0.0) item.quantity * 30.0/monthly else null
            ReorderPriority(item,(item.minimum-item.quantity).coerceAtLeast(0.0),coverage,
                coverage!=null && item.leadDays!=null && coverage <= item.leadDays.toDouble())
        }.sortedWith(compareByDescending<ReorderPriority>{it.item.quantity==0.0}.thenByDescending{it.leadRisk}.thenByDescending{it.shortfall})
        val cats=items.filter{it.unitCost!=null && it.quantity>0.0}
            .groupBy { it.category }.map { (category,rows)-> category to rows.sumOf{it.quantity*(it.unitCost?:0.0)} }
            .sortedByDescending{it.second}
        val consumed=items.mapNotNull { item ->
            val qty=used(usageById[item.id])
            if(qty > 0.0) item to qty else null
        }
        val valuated=consumed.filter{it.first.unitCost!=null}.map { (item,q)-> item to q*(item.unitCost?:0.0) }
            .sortedByDescending{it.second}
        val total=valuated.sumOf{it.second}
        var cumulative=0.0
        val abc=valuated.filter{it.second>0}.map{ (item,value) ->
            val before=if(total>0)cumulative/total else 1.0
            cumulative+=value
            AbcLine(item,value,if(before < .8)"A" else if(before < .95)"B" else "C")
        }
        return ManagerReport(
            days,items.size,items.count{it.quantity==0.0},under.size,items.count{it.placementId==null},
            items.count{it.unitCost!=null},items.sumOf{it.quantity*(it.unitCost?:0.0)},
            filtered.sumOf{it.inboundEvents},filtered.sumOf{it.outboundEvents},
            items.count{(usageById[it.id]?.consumed90?:0.0)==0.0},total,
            consumed.count{it.first.unitCost!=null},consumed.count{it.first.unitCost==null},
            priorities,cats,abc,consumed.sortedByDescending{it.second}.take(7)
        )
    }
}
