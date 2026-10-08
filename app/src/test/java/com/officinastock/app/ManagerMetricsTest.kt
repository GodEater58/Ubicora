package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class ManagerMetricsTest {
    private fun entry(id: Long, qty: Double, min:Double,cost:Double?=null)= StockItem(id,1,"RIC-$id","Articolo $id","Ricambi",qty,min,"pz",null,"",null,cost)
    @Test fun missingPricesAreNotInvented() {
        val report=ManagerMetrics.compute(listOf(entry(1,5.0,2.0,10.0),entry(2,8.0,5.0)),ManagerSnapshot(),30)
        assertEquals(50.0,report.knownStockValue,.001)
        assertEquals(1,report.pricedCount)
        assertEquals(2,report.missingLocation)
    }
    @Test fun loansAreNotPartOfUsageAndZeroStockHasPriority() {
        val report=ManagerMetrics.compute(listOf(entry(1,0.0,2.0,4.0),entry(2,3.0,4.0,6.0)),
            ManagerSnapshot(usage=listOf(ItemUsage(2,1.0,3.0,8.0))),30)
        assertEquals(1L,report.reorder.first().item.id)
        assertEquals(18.0,report.usageValue,.001)
        assertEquals(1,report.usageValued)
        assertEquals(1,report.noWithdrawal90)
    }
    @Test fun abcIsWeightedByWithdrawalValue() {
        val report=ManagerMetrics.compute(listOf(entry(1,10.0,0.0,20.0),entry(2,10.0,0.0,1.0)),
            ManagerSnapshot(usage=listOf(ItemUsage(1,10.0,10.0,10.0),ItemUsage(2,1.0,1.0,1.0))),30)
        assertEquals("A",report.abc[0].group)
        assertEquals("C",report.abc[1].group)
        assertEquals(201.0,report.usageValue,.001)
    }
    @Test fun quantitiesExactlyAtMinimumMatchInventoryWarning() {
        val atMinimum=entry(1,5.0,5.0)
        val aboveMinimum=entry(2,6.0,5.0)
        assertTrue(atMinimum.lowStock)
        val report=ManagerMetrics.compute(listOf(atMinimum,aboveMinimum),ManagerSnapshot(),30)
        assertEquals(1,report.lowStock)
        assertEquals(1L,report.reorder.single().item.id)
    }
    @Test fun noDemandMeansNoFakeCoverage() {
        assertNull(ManagerMetrics.compute(listOf(entry(1,2.0,4.0)),ManagerSnapshot(),30).reorder[0].coverageDays)
    }
}
