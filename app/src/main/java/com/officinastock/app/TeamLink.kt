package com.officinastock.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

enum class LinkPhase { IDLE, CENTRAL, SEARCHING, CONNECTING, TRANSFER, DONE, ERROR }
enum class PeerPhase { CONNECTING, RECEIVING, APPLYING, SENDING, DONE, ERROR }
data class LinkPeer(val endpointId: String, val name: String, val phase: PeerPhase, val detail: String = "")
data class LinkUi(
    val phase: LinkPhase = LinkPhase.IDLE,
    /** Central: the satellites seen in this session. */
    val peers: List<LinkPeer> = emptyList(),
    /** Satellite: the central found nearby. */
    val central: String? = null,
    val message: String? = null
)

/**
 * The central phone opens its own short-range network (Bluetooth + Wi-Fi Direct through Google
 * Nearby, no router or internet); satellites with the app open find it and exchange on their own.
 * Only bytes travel here: content and checks are in [TeamLinkProtocol] and [TeamSync].
 */
@SuppressLint("MissingPermission")
class TeamLink(
    private val context: Context,
    private val scope: CoroutineScope,
    private val team: TeamSync,
    /** Called on the main thread after the local archive changed (joinedWarehouseId on a first join). */
    private val onDataChanged: suspend (joinedWarehouseId: Long?) -> Unit
) {
    companion object {
        /** Runtime permissions Nearby needs on this Android version. */
        fun permissions(): List<String> = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        fun missingPermissions(context: Context): List<String> =
            permissions().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }

    var ui by mutableStateOf(LinkUi())
        private set

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val strategy = Strategy.P2P_STAR
    private val centralLock = Mutex()
    /** Satellite session: what to send and how to apply the answer. */
    private var join: TeamLinkProtocol.Join? = null
    private var satelliteName = ""
    private var expectedTag: String? = null
    private var connectedTo: String? = null
    private var answerArrived = false
    /** Central: outgoing payload id -> endpoint, to know when an answer has fully left. */
    private val outgoing = mutableMapOf<Long, String>()
    private val peerSummary = mutableMapOf<String, String>()
    private var session = 0L

    private fun available(): Boolean {
        val ok = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (!ok) ui = LinkUi(LinkPhase.ERROR, message = "Su questo telefono mancano i servizi Google: usa lo scambio con i file")
        return ok
    }
    private fun failure(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return "Rete non avviabile: attiva Bluetooth, Wi-Fi e Posizione e riprova" + (code?.let { " (codice $it)" } ?: "")
    }

    // ------------------------------------------------------------------ central

    fun startCentral(warehouseUid: String, warehouseName: String) {
        stop()
        if (!available()) return
        val current = ++session
        ui = LinkUi(LinkPhase.CENTRAL, message = "Rete aperta: i satelliti con Ubicora aperta si collegano da soli")
        client.startAdvertising(TeamLinkProtocol.endpointName(TeamLinkProtocol.tag(warehouseUid), warehouseName),
            TeamLinkProtocol.SERVICE_ID, centralLifecycle, AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { if (current == session) ui = LinkUi(LinkPhase.ERROR, message = failure(it)) }
    }

    private fun peer(endpointId: String, phase: PeerPhase, detail: String = "", name: String? = null) {
        val old = ui.peers.firstOrNull { it.endpointId == endpointId }
        val updated = LinkPeer(endpointId, name ?: old?.name ?: "Satellite", phase, detail)
        ui = ui.copy(peers = if (old == null) ui.peers + updated else ui.peers.map { if (it.endpointId == endpointId) updated else it })
    }

    private val centralPayloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val stream = payload.asStream()?.asInputStream() ?: return
            val current = session
            peer(endpointId, PeerPhase.RECEIVING, "Ricevo le modifiche…")
            scope.launch {
                val answer = withContext(Dispatchers.IO) {
                    val request = runCatching { readAll(stream) }.getOrNull()
                    centralLock.withLock {
                        if (request == null) null
                        else { withContext(Dispatchers.Main) { if (current == session) peer(endpointId, PeerPhase.APPLYING, "Unisco i dati…") }
                            TeamLinkProtocol.answer(team, request) }
                    }
                }
                // The archive changed even if the network was closed meanwhile: refresh the screens anyway.
                if (answer != null && !answer.failed) onDataChanged(null)
                if (current != session) return@launch
                if (answer == null) { peer(endpointId, PeerPhase.ERROR, "Trasferimento interrotto"); return@launch }
                val name = answer.letter?.let { "Satellite $it" }
                peerSummary[endpointId] = answer.summary
                if (answer.failed) peer(endpointId, PeerPhase.ERROR, answer.summary, name)
                else peer(endpointId, PeerPhase.SENDING, "Invio l'archivio aggiornato…", name)
                val out = Payload.fromStream(ByteArrayInputStream(answer.bytes))
                outgoing[out.id] = endpointId
                client.sendPayload(endpointId, out)
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val target = outgoing[update.payloadId] ?: return
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    outgoing.remove(update.payloadId)
                    if (ui.peers.firstOrNull { it.endpointId == target }?.phase == PeerPhase.SENDING)
                        peer(target, PeerPhase.DONE, peerSummary[target] ?: "Sincronizzato")
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    outgoing.remove(update.payloadId); peer(target, PeerPhase.ERROR, "Invio non riuscito: riprova dal satellite")
                }
            }
        }
    }

    private val centralLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            peer(endpointId, PeerPhase.CONNECTING, "Collegamento…", info.endpointName.take(40))
            client.acceptConnection(endpointId, centralPayloads)
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) peer(endpointId, PeerPhase.ERROR, "Collegamento non riuscito")
        }
        override fun onDisconnected(endpointId: String) {
            val p = ui.peers.firstOrNull { it.endpointId == endpointId } ?: return
            when (p.phase) {
                PeerPhase.DONE, PeerPhase.ERROR -> {}
                // Its changes are already merged here; the satellite's own screen says if it got the answer.
                PeerPhase.SENDING -> peer(endpointId, PeerPhase.DONE, peerSummary[endpointId] ?: "Sincronizzato")
                else -> peer(endpointId, PeerPhase.ERROR, "Collegamento interrotto: il satellite può riprovare")
            }
        }
    }

    // ------------------------------------------------------------------ satellite

    /** A satellite already joined looks for its own central. */
    fun startSatellite(warehouseUid: String, letter: String) {
        search(TeamLinkProtocol.tag(warehouseUid), null, "Satellite $letter")
    }
    /** First connection: any nearby central (the team password decides if the data can be read). */
    fun startJoin(profileId: Long, password: String, letter: String) {
        search(null, TeamLinkProtocol.Join(profileId, password, letter), "Satellite $letter (nuovo)")
    }

    private fun search(tag: String?, joining: TeamLinkProtocol.Join?, name: String) {
        stop()
        if (!available()) return
        val current = ++session
        expectedTag = tag; join = joining; satelliteName = name
        ui = LinkUi(LinkPhase.SEARCHING, message = "Cerco il telefono centrale… tienilo vicino con la rete aperta")
        client.startDiscovery(TeamLinkProtocol.SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { if (current == session) ui = LinkUi(LinkPhase.ERROR, message = failure(it)) }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (ui.phase != LinkPhase.SEARCHING) return
            val (tag, name) = TeamLinkProtocol.parseEndpointName(info.endpointName) ?: return
            if (expectedTag != null && tag != expectedTag) return
            val current = session
            client.stopDiscovery()
            ui = ui.copy(phase = LinkPhase.CONNECTING, central = name, message = "Trovato il centrale: mi collego…")
            client.requestConnection(satelliteName, endpointId, satelliteLifecycle)
                .addOnFailureListener { if (current == session) ui = LinkUi(LinkPhase.ERROR, central = name, message = "Collegamento non riuscito: riprova") }
        }
        override fun onEndpointLost(endpointId: String) {}
    }

    private val satelliteLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, satellitePayloads)
        }
        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) { ui = ui.copy(phase = LinkPhase.ERROR, message = "Il centrale non ha accettato il collegamento: riprova"); return }
            connectedTo = endpointId; answerArrived = false
            val current = session
            ui = ui.copy(phase = LinkPhase.TRANSFER, message = "Invio le mie modifiche…")
            scope.launch {
                val request = withContext(Dispatchers.IO) {
                    runCatching { join?.let { TeamLinkProtocol.joinRequest(team, it.password, it.letter) } ?: TeamLinkProtocol.changesRequest(team) }
                }
                if (current != session) return@launch
                request.onSuccess { client.sendPayload(endpointId, Payload.fromStream(ByteArrayInputStream(it))) }
                    .onFailure { fail(it.message ?: "Modifiche non leggibili") }
                // The central always answers; silence means the link dropped without notice.
                delay(120_000)
                if (current == session && ui.phase == LinkPhase.TRANSFER && !answerArrived) fail("Il centrale non risponde: avvicina i telefoni e riprova")
            }
        }
        override fun onDisconnected(endpointId: String) {
            if (ui.phase == LinkPhase.TRANSFER || ui.phase == LinkPhase.CONNECTING) fail("Collegamento interrotto: avvicina i telefoni e riprova")
        }
    }

    private val satellitePayloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val stream = payload.asStream()?.asInputStream() ?: return
            val current = session
            answerArrived = true
            ui = ui.copy(message = "Ricevo l'archivio del centrale…")
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { TeamLinkProtocol.finish(team, readAll(stream), join) }
                        .getOrElse { TeamLinkProtocol.Outcome.Failed("Trasferimento interrotto: riprova") }
                }
                if (current != session) return@launch
                when (outcome) {
                    is TeamLinkProtocol.Outcome.Done -> {
                        onDataChanged(outcome.joinedWarehouseId)
                        ui = ui.copy(phase = LinkPhase.DONE, message = outcome.summary)
                    }
                    is TeamLinkProtocol.Outcome.Failed -> ui = ui.copy(phase = LinkPhase.ERROR, message = outcome.message)
                }
                join = null
                client.disconnectFromEndpoint(endpointId); connectedTo = null
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun fail(message: String) {
        ui = ui.copy(phase = LinkPhase.ERROR, message = message)
        connectedTo?.let { client.disconnectFromEndpoint(it) }; connectedTo = null
    }

    /** Guide screenshots only: shows a given state without radio. */
    internal fun preview(state: LinkUi) { ui = state }

    // ------------------------------------------------------------------ common

    /** Closes the network or the search; safe to call at any time. */
    fun stop() {
        session++
        if (ui.phase != LinkPhase.IDLE) runCatching {
            client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints()
        }
        join = null; expectedTag = null; connectedTo = null
        outgoing.clear(); peerSummary.clear()
        ui = LinkUi()
    }

    private fun readAll(input: java.io.InputStream): ByteArray = input.use { stream ->
        val out = ByteArrayOutputStream(); val buffer = ByteArray(65536)
        while (true) {
            val n = stream.read(buffer); if (n < 0) break
            require(out.size() + n <= TeamLinkProtocol.MAX_MESSAGE) { "File troppo grande" }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }
}
