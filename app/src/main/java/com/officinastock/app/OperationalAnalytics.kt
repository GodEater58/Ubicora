package com.officinastock.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AssetCostSummary(val machine:String,val knownCost:Double,val completed:Int,val unpriced:Int)
data class OperationalReport(
    val orderStages:Map<String,Int> = emptyMap(),
    val pendingCounts:Int=0,val activeTransfers:Int=0,val openWork:Int=0,
    val criticalAvailable:Int=0,val reservedReferences:Int=0,
    val expiringLots:Int=0,val expiredLots:Int=0,
    val confirmedCounts:Int=0,val exactCounts:Int=0,
    val completedWork:Int=0,val unpricedWork:Int=0,val maintenanceCost:Double=0.0,
    val assetCosts:List<AssetCostSummary> = emptyList(),
    val monthlyCommitments:Double=0.0,val unpricedOrders:Int=0
) {
    val openOrders:Int get()=listOf("DRAFT","APPROVED","ORDERED").sumOf{orderStages[it]?:0}
    val countAccuracy:Double? get()=if(confirmedCounts==0)null else exactCounts*100.0/confirmedCounts
}

/** Full-database aggregates. List-screen pagination never limits managerial totals. */
class OperationalAnalytics(private val stock:StockDb) {
    fun report(wid:Long,days:Int,now:Long=System.currentTimeMillis()):OperationalReport {
        require(days in listOf(7,30,90))
        val db=stock.readableDatabase
        val zone=ZoneId.systemDefault()
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start=today.minusDays(days.toLong()-1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthStart=today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Expiry dates are stored as UTC calendar dates by the traceability editor.
        val expiryToday=today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val expirySoon=today.plusDays(30).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        fun count(sql:String,args:Array<String>):Int=db.rawQuery(sql,args).use{c->c.moveToFirst();c.getInt(0)}
        val w=arrayOf(wid.toString())
        val stages=db.rawQuery("SELECT status,COUNT(*) FROM ops_orders WHERE warehouse_id=? GROUP BY status",w).use{c->
            buildMap{while(c.moveToNext())put(c.getString(0),c.getInt(1))}
        }
        val counts=db.rawQuery("""SELECT COUNT(*),COALESCE(SUM(CASE WHEN ABS(expected-counted)<0.0000001 THEN 1 ELSE 0 END),0)
            FROM ops_counts WHERE warehouse_id=? AND status='APPLIED' AND created_at BETWEEN ? AND ?""",
            arrayOf(wid.toString(),start.toString(),now.toString())).use{c->c.moveToFirst();c.getInt(0) to c.getInt(1)}
        val costs=db.rawQuery("""SELECT CASE WHEN asset_id IS NULL THEN 'Testo libero: '||machine ELSE 'Impianto #'||asset_id||': '||machine END,
            COUNT(*),COALESCE(SUM(CASE WHEN parts_cost IS NOT NULL AND (labor_hours=0 OR hourly_rate IS NOT NULL)
                THEN parts_cost+labor_hours*COALESCE(hourly_rate,0) ELSE 0 END),0),
            COALESCE(SUM(CASE WHEN parts_cost IS NULL OR (labor_hours>0 AND hourly_rate IS NULL) THEN 1 ELSE 0 END),0)
            FROM ops_maintenance WHERE warehouse_id=? AND status='DONE' AND completed_at BETWEEN ? AND ?
            GROUP BY asset_id,machine ORDER BY 3 DESC""",arrayOf(wid.toString(),start.toString(),now.toString())).use{c->
            buildList{while(c.moveToNext())add(AssetCostSummary(c.getString(0),c.getDouble(2),c.getInt(1),c.getInt(3)))}
        }
        val budget=db.rawQuery("""SELECT COALESCE(SUM(o.quantity*o.unit_price),0),
            COALESCE(SUM(CASE WHEN o.unit_price IS NULL THEN 1 ELSE 0 END),0)
            FROM ops_orders o JOIN warehouses wh ON wh.id=o.warehouse_id
            WHERE wh.profile_id=(SELECT profile_id FROM warehouses WHERE id=?)
            AND o.status IN ('APPROVED','ORDERED','RECEIVED') AND o.created_at BETWEEN ? AND ?""",
            arrayOf(wid.toString(),monthStart.toString(),now.toString())).use{c->c.moveToFirst();c.getDouble(0) to c.getInt(1)}
        val critical=count("""SELECT COUNT(*) FROM items i LEFT JOIN
            (SELECT item_id,SUM(quantity) held FROM stock_reservations WHERE status='ACTIVE' GROUP BY item_id) r ON r.item_id=i.id
            WHERE i.warehouse_id=? AND i.quantity-COALESCE(r.held,0)<=i.minimum""",w)
        return OperationalReport(stages,
            count("SELECT COUNT(*) FROM ops_counts WHERE warehouse_id=? AND status='PENDING'",w),
            count("SELECT COUNT(*) FROM ops_transfers WHERE (from_id=? OR to_id=?) AND status IN ('REQUESTED','IN_TRANSIT')",arrayOf(wid.toString(),wid.toString())),
            count("SELECT COUNT(*) FROM ops_maintenance WHERE warehouse_id=? AND status='OPEN'",w),critical,
            count("SELECT COUNT(DISTINCT item_id) FROM stock_reservations WHERE warehouse_id=? AND status='ACTIVE'",w),
            count("SELECT COUNT(*) FROM stock_lots WHERE warehouse_id=? AND quantity>0 AND expires_at BETWEEN ? AND ?",arrayOf(wid.toString(),expiryToday.toString(),expirySoon.toString())),
            count("SELECT COUNT(*) FROM stock_lots WHERE warehouse_id=? AND quantity>0 AND expires_at<?",arrayOf(wid.toString(),expiryToday.toString())),
            counts.first,counts.second,costs.sumOf{it.completed},costs.sumOf{it.unpriced},costs.sumOf{it.knownCost},costs,budget.first,budget.second)
    }
}
