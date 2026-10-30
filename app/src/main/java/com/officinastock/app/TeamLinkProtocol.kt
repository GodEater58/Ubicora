package com.officinastock.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 4.9 automatic exchange between nearby phones. The radio part (Google Nearby) only moves bytes;
 * everything they contain is decided here, on top of the same encrypted files used by the manual
 * exchange, so the two ways are interchangeable and the team password still protects the data.
 *
 * One round per satellite:
 *  satellite → central  'C' + its changes file   (or 'J' to join for the first time)
 *  central   → satellite 'S' + the complete state (or 'E' + a message if something is wrong)
 */
object TeamLinkProtocol {
    const val SERVICE_ID = "com.ubicora.app.teamsync"
    private const val CHANGES: Byte = 'C'.code.toByte()
    private const val JOIN: Byte = 'J'.code.toByte()
    private const val STATE: Byte = 'S'.code.toByte()
    private const val ERROR: Byte = 'E'.code.toByte()
    /** 4.11: answer to a phone that joined with the QR one-time code: team password (sealed with the code) + state. */
    private const val KEYED_STATE: Byte = 'Q'.code.toByte()
    /** 4.11: a stranger nearby must not be able to fill the central's memory. */
    const val MAX_MESSAGE = 24L * 1024 * 1024
    /** Satellites the central serves at the same time; more wait and retry. */
    const val MAX_PEERS = 6
    /** A connection that sends nothing within this time is closed. */
    const val IDLE_MS = 60_000L
    /** 4.13: how long a satellite looks for its central (by hand / automatically) before giving up. */
    const val SEARCH_MS = 90_000L
    const val AUTO_SEARCH_MS = 45_000L
    /** 4.13: no bytes in either direction for this long means the link is stuck. */
    const val STALL_MS = 90_000L

    /** Short public tag of the shared warehouse: satellites recognise their central by it. */
    fun tag(warehouseUid: String): String =
        MessageDigest.getInstance("SHA-256").digest(warehouseUid.toByteArray(Charsets.UTF_8))
            .take(5).joinToString("") { "%02x".format(it) }

    /** Name the central advertises: "tag|warehouse name". */
    fun endpointName(tag: String, warehouseName: String): String = "$tag|${warehouseName.replace('|', ' ').take(40)}"
    fun parseEndpointName(name: String): Pair<String, String>? {
        val tag = name.substringBefore('|', "")
        if (!Regex("^[0-9a-f]{10}$").matches(tag)) return null
        return tag to name.substringAfter('|').ifBlank { "Telefono centrale" }
    }

    // ------------------------------------------------------------------ satellite side

    fun changesRequest(team: TeamSync): ByteArray =
        ByteArrayOutputStream().apply { write(CHANGES.toInt()); team.exportChanges(this) }.toByteArray()
    /** First connection: the ticket proves the password (or the QR code) and books the letter on the central. */
    fun joinRequest(team: TeamSync, secret: String, letter: String): ByteArray =
        ByteArrayOutputStream().apply { write(JOIN.toInt()); team.joinTicket(this, secret, letter) }.toByteArray()

    /** [password] is the team password, or the one-time code from the central's QR when [viaCode]. */
    data class Join(val profileId: Long, val password: String, val letter: String, val viaCode: Boolean = false)
    sealed interface Outcome {
        data class Done(val summary: String, val joinedWarehouseId: Long? = null) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Applies the central's answer on the satellite ([join] for the first connection). */
    fun finish(team: TeamSync, answer: ByteArray, join: Join?): Outcome = try {
        require(answer.isNotEmpty()) { "Risposta vuota dal centrale" }
        when (answer[0]) {
            ERROR -> Outcome.Failed(String(answer, 1, answer.size - 1, Charsets.UTF_8))
            STATE -> {
                val body = ByteArrayInputStream(answer, 1, answer.size - 1)
                if (join != null) {
                    val r = team.joinAsSatellite(join.profileId, body, join.password, join.letter)
                    Outcome.Done("Collegato come satellite ${join.letter}: ${r.articles} articoli ricevuti", team.status().warehouseId)
                } else {
                    val r = team.importState(body)
                    Outcome.Done("Sincronizzato: ${r.articles} articoli aggiornati" +
                        (if (r.removed > 0) " · ${r.removed} eliminati dal centrale" else "") +
                        (if (r.skipped > 0) " · ${r.skipped} modifiche ancora da confermare" else " · tutto confermato dal centrale"))
                }
            }
            KEYED_STATE -> {
                require(join != null && join.viaCode) { "Risposta del centrale non attesa" }
                require(answer.size > 5) { "Risposta del centrale incompleta" }
                val length = java.nio.ByteBuffer.wrap(answer, 1, 4).int
                require(length in 1..(answer.size - 5)) { "Risposta del centrale non valida" }
                val password = team.openInviteKey(ByteArrayInputStream(answer, 5, length), join.password)
                val r = team.joinAsSatellite(join.profileId, ByteArrayInputStream(answer, 5 + length, answer.size - 5 - length), password, join.letter)
                Outcome.Done("Collegato come satellite ${join.letter}: ${r.articles} articoli ricevuti", team.status().warehouseId)
            }
            else -> Outcome.Failed("Risposta del centrale non riconosciuta: aggiorna l'app su entrambi i telefoni")
        }
    } catch (e: Exception) { Outcome.Failed(e.message ?: "Sincronizzazione non riuscita") }

    // ------------------------------------------------------------------ central side

    data class Answer(val bytes: ByteArray, val summary: String, val failed: Boolean, val letter: String? = null)

    /** What the central replies to a satellite's message. Never throws: errors go back as text. */
    fun answer(team: TeamSync, request: ByteArray): Answer = try {
        require(request.isNotEmpty()) { "Messaggio vuoto" }
        when (request[0]) {
            CHANGES -> {
                val r = team.importChanges(ByteArrayInputStream(request, 1, request.size - 1))
                Answer(state(team), "${r.movements} movimenti, ${r.articles} articoli nuovi, ${r.locations} spostamenti" +
                    (if (r.conflicts > 0) " · ${r.conflicts} conflitti" else ""), false, r.letter)
            }
            JOIN -> {
                require(request.size > 1) { "Aggiorna Ubicora sul telefono che si collega" }
                val accepted = team.acceptJoin(ByteArrayInputStream(request, 1, request.size - 1))
                val bytes = accepted.inviteCode?.let { code ->
                    // Joined with the QR code: the team password travels only encrypted with that code.
                    val key = ByteArrayOutputStream().also { team.inviteKey(it, code) }.toByteArray()
                    val archive = ByteArrayOutputStream().also { team.exportState(it) }.toByteArray()
                    ByteArrayOutputStream().apply {
                        write(KEYED_STATE.toInt()); write(java.nio.ByteBuffer.allocate(4).putInt(key.size).array()); write(key); write(archive)
                    }.toByteArray()
                } ?: state(team)
                Answer(bytes, "Nuovo telefono collegato: archivio inviato", false, accepted.letter)
            }
            else -> throw IllegalArgumentException("Messaggio non riconosciuto: aggiorna l'app su entrambi i telefoni")
        }
    } catch (e: Exception) {
        val message = e.message ?: "Sincronizzazione non riuscita"
        Answer(byteArrayOf(ERROR) + message.toByteArray(Charsets.UTF_8), message, true)
    }

    private fun state(team: TeamSync): ByteArray =
        ByteArrayOutputStream().apply { write(STATE.toInt()); team.exportState(this) }.toByteArray()
}
