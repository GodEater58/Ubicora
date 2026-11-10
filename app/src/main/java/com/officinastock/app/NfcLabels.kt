package com.officinastock.app

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/**
 * 4.15: an NFC tag carries the same text as the QR label of an article or of a structure, so
 * touching it with the phone does exactly what a scan does (open the article, burst picking,
 * guided picking, shelf check). The QR stays the universal label: NFC is a faster second way.
 */
object NfcLabels {
    /** Text of the first text record of the tag, or null when the tag is empty or holds something else. */
    fun read(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        ndef.connect()
        try { return ndef.ndefMessage?.records?.firstNotNullOfOrNull { text(it) } }
        finally { runCatching { ndef.close() } }
    }

    /** Decodes an NFC Forum "text" record: status byte (encoding + language length), language, text. */
    fun text(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val language = status and 0x3F
        if (payload.size < 1 + language) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + language, payload.size - 1 - language, charset).takeIf { it.isNotBlank() }
    }

    fun message(payload: String): NdefMessage = NdefMessage(arrayOf(NdefRecord.createTextRecord("it", payload)))

    /** Writes the label on a blank or rewritable tag; the message says why when it cannot. */
    fun write(tag: Tag, payload: String) {
        val message = message(payload)
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                require(ndef.isWritable) { "Questo tag è protetto dalla scrittura" }
                require(ndef.maxSize >= message.toByteArray().size) { "Questo tag è troppo piccolo per l'etichetta" }
                ndef.writeNdefMessage(message)
            } finally { runCatching { ndef.close() } }
        } else {
            val blank = requireNotNull(NdefFormatable.get(tag)) { "Tag non compatibile: servono tag NFC di tipo NDEF (per esempio NTAG213)" }
            blank.connect()
            try { blank.format(message) } finally { runCatching { blank.close() } }
        }
    }
}
