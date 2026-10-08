package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class StockRulesTest {
    @Test fun categorySkuCodes() {
        assertEquals("RIC-000001",ArticleCodes.format(ArticleCodes.prefix("Ricambi"),1))
        assertEquals("ELE-000012",ArticleCodes.format(ArticleCodes.prefix("Elettrico"),12))
        assertEquals("DPI-999999",ArticleCodes.format(ArticleCodes.prefix("DPI"),999999))
        assertThrows(IllegalArgumentException::class.java){ ArticleCodes.prefix("Non esistente") }
        assertThrows(IllegalArgumentException::class.java){ ArticleCodes.format("RIC",0) }
    }
    @Test fun payloadIdentifiesWarehouse() {
        assertEquals("OFFICINA-STOCK:1:W27:RIC-000001",ArticleCodes.payload(27,"RIC-000001"))
    }
    @Test fun skuNormalized() = assertEquals("M8-1",StockRules.sku(" m8-1 "))
    @Test fun negativeStockBlocked() {
        assertThrows(IllegalArgumentException::class.java) { StockRules.applyDelta(2.0,-3.0) }
    }
    @Test fun nonFiniteBlocked() {
        assertFalse(StockRules.validQuantity(Double.NaN))
        assertFalse(StockRules.validQuantity(Double.POSITIVE_INFINITY))
    }
    @Test fun mapCoordinatesClamped() {
        assertEquals(0f,StockRules.mapPosition(-20f),.001f)
        assertEquals(100f,StockRules.mapPosition(150f),.001f)
    }
    @Test fun degenerateWallBlocked() {
        assertThrows(IllegalArgumentException::class.java) {
            StockRules.normalizeWall(floatArrayOf(0f,0f,0f,0f))
        }
    }
}
