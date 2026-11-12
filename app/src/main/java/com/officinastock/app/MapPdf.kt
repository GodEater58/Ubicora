package com.officinastock.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Printable floor plan (A4 landscape) plus a structure index. Offline, no external services. */
object MapPdf {
    private const val W = 842f
    private const val H = 595f

    fun write(state: StockUiState, out: OutputStream) {
        val warehouse = requireNotNull(state.warehouse) { "Magazzino non selezionato" }
        require(state.profile != null && !state.sessionLocked) { "Accedi per esportare la pianta" }
        val counts = state.items.groupingBy { it.placementId }.eachCount()
        val doc = PdfDocument()
        try {
            val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 40, 52); textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 45, 55); textSize = 8.5f }
            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 7f }
            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            val c = page.canvas
            c.drawText("${warehouse.name} · Pianta", 32f, 36f, title)
            c.drawText("${fmt(warehouse.mapWidth)} × ${fmt(warehouse.mapHeight)} m · ${state.placements.size} strutture · " +
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date()), 32f, 52f, small)
            drawPlan(c, state, counts, RectF(32f, 66f, W - 32f, H - 40f))
            c.drawText("Ubicora · pianta in scala · pag. $pageNo", 32f, H - 18f, small)
            doc.finishPage(page)

            // Index of structures: what is where.
            val rows = state.placements.sortedWith(compareBy({ !it.holdsStock }, { it.label.lowercase(Locale.ITALY) }))
            var y = 0f
            fun newPage() {
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
                page.canvas.drawText("Indice delle strutture", 32f, 36f, title)
                y = 62f
            }
            newPage()
            for (p in rows) {
                if (y > H - 40f) {
                    page.canvas.drawText("Ubicora · pag. $pageNo", 32f, H - 18f, small)
                    doc.finishPage(page); newPage()
                }
                val layout = if (p.hasPositions) "${p.levels} rip. × ${p.slots} posti" else ""
                val stock = if (p.holdsStock) "${counts[p.id] ?: 0} articoli" else "riferimento"
                page.canvas.drawText(QrLabels.fit(text, p.label, 260f), 32f, y, text)
                page.canvas.drawText(p.kind, 300f, y, text)
                page.canvas.drawText(layout, 400f, y, text)
                page.canvas.drawText(stock, 520f, y, text)
                page.canvas.drawText("${fmt(p.width)} × ${fmt(p.depth)} m · x ${fmt(p.x)} y ${fmt(p.y)}", 620f, y, small)
                y += 15f
            }
            page.canvas.drawText("Ubicora · pag. $pageNo", 32f, H - 18f, small)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally { doc.close() }
    }

    private fun drawPlan(c: Canvas, s: StockUiState, counts: Map<Long?, Int>, area: RectF) {
        val w = s.warehouse!!
        val scale = minOf(area.width() / w.mapWidth, area.height() / w.mapHeight)
        val ox = area.left + (area.width() - w.mapWidth * scale) / 2f
        val oy = area.top + (area.height() - w.mapHeight * scale) / 2f
        fun X(m: Float) = ox + m * scale
        fun Y(m: Float) = oy + m * scale
        val grid = Paint().apply { color = Color.rgb(228, 234, 238); strokeWidth = .4f }
        var g = 0f
        while (g <= w.mapWidth + .001f) { c.drawLine(X(g), Y(0f), X(g), Y(w.mapHeight), grid); g += 1f }
        g = 0f
        while (g <= w.mapHeight + .001f) { c.drawLine(X(0f), Y(g), X(w.mapWidth), Y(g), grid); g += 1f }
        c.drawRect(X(0f), Y(0f), X(w.mapWidth), Y(w.mapHeight),
            Paint().apply { style = Paint.Style.STROKE; color = Color.rgb(90, 110, 120); strokeWidth = 1f })
        val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 40, 50); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND }
        s.walls.forEach { c.drawLine(X(it.x1), Y(it.y1), X(it.x2), Y(it.y2), wall) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 6f; textAlign = Paint.Align.CENTER }
        for (p in s.placements) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (p.kind) {
                    "Porta", "Cancello" -> Color.rgb(214, 190, 160); "Colonna" -> Color.rgb(150, 156, 162)
                    "Corridoio" -> Color.argb(40, 45, 160, 140); "Armadio" -> Color.rgb(190, 205, 225)
                    "Banco" -> Color.rgb(232, 210, 180); else -> Color.rgb(190, 225, 222)
                }
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = .8f; color = Color.rgb(40, 70, 80)
                if (p.kind == "Corridoio") pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            }
            c.save()
            c.rotate(p.rotation, X(p.x), Y(p.y))
            val r = RectF(X(p.x - p.width / 2), Y(p.y - p.depth / 2), X(p.x + p.width / 2), Y(p.y + p.depth / 2))
            c.drawRect(r, fill); c.drawRect(r, stroke)
            if (p.holdsStock && p.slots > 1) for (i in 1 until p.slots) {
                val x = r.left + r.width() * i / p.slots
                c.drawLine(x, r.top, x, r.bottom, stroke)
            }
            c.restore()
            c.drawText(QrLabels.fit(label, p.label, maxOf(40f, p.width * scale + 30f)), X(p.x), Y(p.y) + 2.5f, label)
            if (p.holdsStock) c.drawText("${counts[p.id] ?: 0} art.", X(p.x), Y(p.y) + 10f, badge)
        }
    }

    private fun fmt(v: Float) = String.format(Locale.ITALY, "%.2f", v)
}
