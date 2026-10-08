package com.officinastock.app

import org.junit.Assert.*
import org.junit.Test

class DemoCatalogTest {
    @Test fun coversAllCategoriesAndOperationalConditions() {
        val list = DemoCatalog.items
        assertEquals(42,list.size)
        assertEquals(ArticleCodes.categoryPrefixes.keys,list.map { it.category }.toSet())
        assertTrue(list.count { it.illustration } >= 15)
        assertTrue(list.count { it.stock <= it.minimum } >= 10)
        assertTrue(list.count { it.stock == 0.0 } >= 2)
        assertTrue(list.count { it.used90 > 0.0 } > 20)
        assertTrue(list.count { it.used90 == 0.0 } > 4)
        assertTrue(list.count { it.onLoan > 0.0 } >= 2)
        assertTrue(list.count { it.cost == null } >= 1)
        assertTrue(list.count { it.placement == null } >= 1)
        assertTrue(list.count { it.unit != "pz" } >= 4)
    }
    @Test fun historyBalancesAndPricesAreSane() {
        for (item in DemoCatalog.items) {
            assertTrue("Giacenza negativa: ${item.name}",item.stock >= 0.0)
            assertTrue("Saldo iniziale demo negativo: ${item.name}",
                item.stock + item.used90 + item.onLoan - item.received30 >= 0.0)
            assertTrue(item.cost == null || item.cost >= 0.0)
            assertTrue(item.leadDays == null || item.leadDays in 0..3650)
            assertTrue(item.placement == null || item.placement in 0..8)
        }
    }
}
