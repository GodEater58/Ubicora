package com.officinastock.app

import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * 4.10: QR shown by the central phone to add a satellite without typing the team password.
 * "UBICORA-TEAM:1:<password>:<letters already used>:<warehouse>" (password and name in Base64).
 */
object TeamQr {
    private const val PREFIX = "UBICORA-TEAM:1:"
    data class Invite(val password: String, val taken: Set<String>, val warehouse: String) {
        /** First free letter among those offered on screen (B…F), then the rest of the alphabet. */
        val suggestedLetter: String get() = ('B'..'Z').map { it.toString() }.firstOrNull { it !in taken } ?: "B"
    }
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()

    fun encode(password: String, taken: Collection<String>, warehouse: String): String =
        PREFIX + enc.encodeToString(password.toByteArray(Charsets.UTF_8)) + ":" +
            taken.filter { ArticleCodes.isSatelliteLetter(it) }.sorted().joinToString("") + ":" +
            enc.encodeToString(warehouse.take(60).toByteArray(Charsets.UTF_8))

    fun parse(raw: String): Invite? {
        if (!raw.startsWith(PREFIX)) return null
        val parts = raw.removePrefix(PREFIX).split(':')
        if (parts.size != 3) return null
        return runCatching {
            val password = String(dec.decode(parts[0]), Charsets.UTF_8)
            val taken = parts[1].map { it.toString() }.filter { ArticleCodes.isSatelliteLetter(it) }.toSet()
            Invite(password, taken, String(dec.decode(parts[2]), Charsets.UTF_8))
        }.getOrNull()?.takeIf { it.password.length >= 8 }
    }
}

/** 4.10: list of articles under their minimum, ready to send by WhatsApp or email. */
object ReorderList {
    /** Quantity that brings the article back to twice its minimum (at least 1). */
    fun suggested(item: StockItem): Double = ceil(maxOf(item.minimum * 2 - item.quantity, 1.0))

    fun text(warehouse: String, items: List<StockItem>, at: Long = System.currentTimeMillis()): String {
        val low = items.filter { it.lowStock }
        val day = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY).format(Date(at))
        if (low.isEmpty()) return "Ubicora · $warehouse · $day\nNessun articolo da ordinare."
        return buildString {
            appendLine("Ubicora · Da ordinare · $warehouse · $day")
            low.groupBy { it.supplier.trim().ifEmpty { "Senza fornitore" } }
                .toSortedMap(compareBy<String> { it == "Senza fornitore" }.thenBy { it.lowercase(Locale.ITALY) })
                .forEach { (supplier, group) ->
                    appendLine()
                    appendLine("— $supplier —")
                    group.sortedBy { it.name.lowercase(Locale.ITALY) }.forEach {
                        appendLine("• ${it.name} (${it.sku}): ${amount(it.quantity)}/${amount(it.minimum)} ${it.unit} → ordina ${amount(suggested(it))} ${it.unit}")
                    }
                }
            appendLine()
            append("${low.size} ${if (low.size == 1) "articolo" else "articoli"} sotto scorta")
        }
    }
}

/** "adesso", "12 min fa", "oggi alle 14:20", "ieri alle 9:05", "3 giorni fa", "mai". */
fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return "mai"
    val minutes = (now - ts) / 60_000
    if (minutes < 1) return "adesso"
    if (minutes < 60) return "$minutes min fa"
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val hour = SimpleDateFormat("H:mm", Locale.ITALY).format(Date(ts))
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "oggi alle $hour"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (then.get(Calendar.YEAR) == today.get(Calendar.YEAR) && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) return "ieri alle $hour"
    val days = ((now - ts) / 86_400_000).coerceAtLeast(2)
    return "$days giorni fa"
}
