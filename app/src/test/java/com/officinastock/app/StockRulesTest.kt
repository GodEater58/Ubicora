package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class StockRulesTest {
    @Test fun decimalConsumptionEndsAtZeroWithoutHidingRealShortages() {
        assertEquals(0.2,StockRules.applyDelta(0.3,-0.1),0.0)
        assertEquals(0.0,StockRules.applyDelta(StockRules.applyDelta(0.3,-0.1),-0.2),0.0)
        // Legacy rows may already contain a binary floating-point residual.
        assertEquals(0.0,StockRules.applyDelta(0.3-0.1,-0.2),0.0)
        assertThrows(IllegalArgumentException::class.java){StockRules.applyDelta(0.1,-0.10001)}
    }
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
    @Test fun scannerParsesGeneratedQrAndLegacyCode() {
        assertEquals(ArticleCodes.ScanTarget(27L,"RIC-000001"),
            ArticleCodes.parseScan(ArticleCodes.payload(27,"RIC-000001")))
        assertEquals(ArticleCodes.ScanTarget(null,"UTE-000002"),ArticleCodes.parseScan(" ute-000002 "))
        assertNull(ArticleCodes.parseScan("OFFICINA-STOCK:1:W27:"))
        assertNull(ArticleCodes.parseScan("OFFICINA-STOCK:2:W27:RIC-000001"))
        assertNull(ArticleCodes.parseScan("https://example.org"))
        assertNull(ArticleCodes.parseScan("OFFICINA-STOCK:1:W0:RIC-000001"))
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

