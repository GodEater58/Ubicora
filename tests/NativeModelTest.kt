package com.officinastock.app

fun main() {
    check(StockRules.sku("  a-01  ")=="A-01")
    check(StockRules.applyDelta(5.0,-2.0)==3.0)
    check(runCatching{StockRules.applyDelta(2.0,-3.0)}.isFailure)
    check(runCatching{StockRules.applyDelta(2.0,Double.NaN)}.isFailure)
    check(StockRules.mapPosition(-20f)==0f)
    check(StockRules.mapPosition(120f)==100f)
    check(runCatching{StockRules.normalizeWall(floatArrayOf(0f,0f,0f,0f))}.isFailure)
    check(StockRules.normalizeWall(floatArrayOf(0f,0f,1f,2f)).size==4)
    println("8/8 test dominio superati")
}
