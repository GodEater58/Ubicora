package com.officinastock.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

// 4.19.1: moved out of StockViewModel.kt without changes — everything the screens read.

data class StockUiState(
    val profiles: List<Profile> = emptyList(),
    val profile: Profile? = null,
    val warehouses: List<Warehouse> = emptyList(),
    val warehouse: Warehouse? = null,
    val items: List<StockItem> = emptyList(),
    val placements: List<Placement> = emptyList(),
    val walls: List<Wall> = emptyList(),
    val movements: List<Movement> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val manager: ManagerSnapshot = ManagerSnapshot(),
    val demoCount: Int = 0,
    val createdItemId: Long? = null,
    /** 4.21: the article being created (kept through the screen lock until Salva or Annulla). */
    val newItem: NewItemDraft? = null,
    val scannedItemId: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val initialized: Boolean = false,
    val sessionLocked: Boolean = false,
    val csvPreviewCount: Int? = null,
    /** 4.7.2: last carico/prelievo, offered for a few seconds as "Annulla". */
    val movementUndo: MovementUndo? = null,
    /** 4.8: role of this phone in the phone-to-phone sync. */
    val team: TeamSyncStatus = TeamSyncStatus(),
    val orders: List<PurchaseRequest> = emptyList(),
    val stockCounts: List<StockCount> = emptyList(),
    val stockTransfers: List<StockTransfer> = emptyList(),
    val maintenanceWork: List<MaintenanceWork> = emptyList(),
    val operationsEvents: List<OperationsEvent> = emptyList(),
    val businessMode: Boolean = false,
    val monthlyBudget: Double? = null,
    val costCenters: List<CostCenter> = emptyList(),
    val businessSuppliers: List<BusinessSupplier> = emptyList(),
    val businessAssets: List<BusinessAsset> = emptyList(),
    val staffDirectory: List<StaffDirectoryEntry> = emptyList(),
    val unsentCatalogEvents: Int = 0,
    val reservations: List<StockReservation> = emptyList(),
    val stockLots: List<StockLot> = emptyList(),
    val stockSerials: List<StockSerial> = emptyList(),
    val operationalReports: Map<Int,OperationalReport> = emptyMap(),
    val reservedByItem: Map<Long,Double> = emptyMap(),
    val operationsLimit:Int=250,
    /** Structure to center and highlight when the map opens ("Mostra in pianta", shelf QR). */
    val mapFocusPlacementId: Long? = null,
    /** Movements per structure in the last 30 days, for the "most used" map view. */
    val placementActivity: Map<Long,Int> = emptyMap(),
    /** Items waiting for the user to tap a structure on the map ("Scegli sulla mappa"). */
    val locationPickItemIds: List<Long>? = null,
    val mapUndoLabel: String? = null,
    val mapRedoLabel: String? = null,
    /** 4.10 burst picking: scanner reopens after every withdrawal until the user stops. */
    val burstMode: Boolean = false,
    val burstItemId: Long? = null,
    val burstCount: Int = 0,
    /** 4.15: quantity waiting for confirmation; scanning the same label again adds one. */
    val burstQty: Double = 1.0,
    /** 4.15 guided picking: what to take, walked in the order of the plan. */
    val pickList: List<PickLine> = emptyList(),
    /** 4.15: article whose label the scanner must read to confirm the guided withdrawal. */
    val pickScanItemId: Long? = null,
    /** 4.15: article whose label could not be read: the panel then offers the confirmation by hand. */
    val pickScanMissedId: Long? = null,
    /** 4.15: name of the article whose label is waiting to be written on an NFC tag (null = not writing). */
    val nfcWrite: String? = null,
    /** 4.15 shelf check: the camera sweeps a structure and ticks what it finds (nothing is moved or counted). */
    val shelfCheck: ShelfCheck? = null,
    /** 4.17: articles matching the search in the other sites of the company. */
    val siteHits: List<SiteHit> = emptyList(),
    /** 4.15: the guided bar asks to open the map (token, 0 = none). */
    val guidedMapRequest: Long = 0,
    /** 4.15: outcome of the last reading, for the sound and the vibration of the scanner screen. */
    val scanSignal: ScanSignal? = null,
    /** 4.10: a screen asks MainActivity to open the scanner (token, 0 = none). */
    val scanRequest: Long = 0,
    /** 4.10: team invite read from the central's QR, waiting for the join dialog. */
    val teamQr: TeamQr.Invite? = null,
    /** 4.20: structure shown from the front, over any tab (null = closed). */
    val shelfOpenId: Long? = null,
    /** 4.20: article the map has to show ("dov'è"), consumed by the map. */
    val mapFindItemId: Long? = null,
    /** 4.20: text of an NFC tag that opened the app; it is read once the PIN is entered. */
    val pendingCode: String? = null
)

data class MovementUndo(val itemId: Long, val delta: Double, val label: String, val token: Long) {
    companion object {
        fun label(delta: Double, item: StockItem?): String =
            "${if(delta>0) "Carico" else "Prelievo"} ${amount(kotlin.math.abs(delta))} ${item?.unit.orEmpty()}".trim() +
                (item?.let { " · ${it.name}" } ?: "")
    }
}
