package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class QualityOfLifeTest {
    private fun item(id:Long,sku:String,name:String,qty:Double,min:Double=0.0) =
        StockItem(id,1,sku,name,"Ricambi",qty,min,"pz",null,"")

    private val a=item(1,"RIC-000003","bullone",10.0,2.0)
    private val b=item(2,"RIC-000001","Anello",1.0,5.0)
    private val c=item(3,"RIC-000002","Cinghia",0.0,1.0)
    private val d=item(4,"RIC-000004","Dado",4.0,4.0)
    private val all=listOf(a,b,c,d)

    @Test fun nameSortIgnoresCase() {
        assertEquals(listOf(2L,1L,3L,4L),sortStock(all,StockSort.NAME,emptyMap()).map{it.id})
    }
    @Test fun criticalFirstPutsTheMostDepletedOnTop() {
        // Cinghia 0/1, Anello 1/5, Dado 4/4 are critical (quantity <= minimum); bullone is not.
        assertEquals(listOf(3L,2L,4L,1L),sortStock(all,StockSort.CRITICAL,emptyMap()).map{it.id})
    }
    @Test fun quantityAndCodeOrders() {
        assertEquals(listOf(3L,2L,4L,1L),sortStock(all,StockSort.QTY_LOW,emptyMap()).map{it.id})
        assertEquals(listOf(1L,4L,2L,3L),sortStock(all,StockSort.QTY_HIGH,emptyMap()).map{it.id})
        assertEquals(listOf(2L,3L,1L,4L),sortStock(all,StockSort.CODE,emptyMap()).map{it.id})
    }
    @Test fun recentlyMovedFirstThenNeverMovedByName() {
        val moved=mapOf(4L to 300L,1L to 500L)
        assertEquals(listOf(1L,4L,2L,3L),sortStock(all,StockSort.RECENT,moved).map{it.id})
    }
    @Test fun undoLabelSaysWhatWillBeReversed() {
        assertEquals("Prelievo 1 pz · Anello",MovementUndo.label(-1.0,b))
        assertEquals("Carico 2,50 pz · Anello",MovementUndo.label(2.5,b))
        assertEquals("Carico 3",MovementUndo.label(3.0,null))
    }
}
