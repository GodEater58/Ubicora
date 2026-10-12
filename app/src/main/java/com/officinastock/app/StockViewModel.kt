package com.officinastock.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/** Dati persistiti in SQLite, stato effimero in Compose, operazioni fuori dal thread grafico. */
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
    val mapRedoLabel: String? = null
)
class StockViewModel(app: Application): AndroidViewModel(app) {
    private val db = StockDb(app.applicationContext)
    private val operations = OperationsDb(db)
    private val enterprise = EnterpriseDb(db)
    private val planning = StockPlanningDb(db)
    private val trace = StockTraceDb(db)
    private val team = TeamSync(db)
    /** 4.9: automatic exchange with nearby phones (the central's own network). */
    val link = TeamLink(app.applicationContext, viewModelScope, team) { joined -> refreshAfterLink(joined) }
    var ui by mutableStateOf(StockUiState())
        private set
    private var operationalRowLimit=250
    private var backgroundGeneration=0L
    private var archiveAuthorization:Set<Long>?=null
    private var pendingCsvImport: Pair<Long,List<CsvImportRow>>? = null
    var pendingBackupPassword=""
    var pendingRestorePassword=""
    fun lockForBackground() { link.stop();clearMapHistory();pendingWalls.clear();ui=ui.copy(locationPickItemIds=null,mapFocusPlacementId=null);backgroundGeneration++;archiveAuthorization=null;pendingCsvImport=null;pendingBackupPassword="";pendingRestorePassword="";ui=ui.copy(sessionLocked=true,createdItemId=null,scannedItemId=null,csvPreviewCount=null) }

    private var pendingNewItem: Long? = null
    fun acknowledgeNewItem() { ui = ui.copy(createdItemId = null) }
    fun acknowledgeScannedItem() { ui = ui.copy(scannedItemId = null) }
    /** Navigation from dashboards: same detail handoff used by the QR scanner. */
    fun openItemDetails(itemId: Long) {
        if(ui.items.any { it.id==itemId }) ui=ui.copy(scannedItemId=itemId,message=null)
    }
    /** Lookup locale. Nessuna movimentazione viene mai eseguita automaticamente da una scansione. */
    fun handleScannedCode(raw: String) {
        val warehouse=ui.warehouse ?: return
        ArticleCodes.parseLocationScan(raw)?.let { location ->
            ui=when {
                location.warehouseId!=warehouse.id -> ui.copy(scannedItemId=null,message="Questa etichetta appartiene a un altro magazzino. Cambia magazzino e riprova.")
                ui.placements.none { it.id==location.placementId } -> ui.copy(scannedItemId=null,message="Struttura non trovata: potrebbe essere stata eliminata.")
                else -> ui.copy(scannedItemId=null,mapFocusPlacementId=location.placementId,message=null)
            }
            return
        }
        val target=ArticleCodes.parseScan(raw)
        if(target == null) {
            ui=ui.copy(scannedItemId=null,message="Codice QR non riconosciuto. Usa un'etichetta Ubicora o Officina Stock.")
            return
        }
        if(target.warehouseId != null && target.warehouseId != warehouse.id) {
            ui=ui.copy(scannedItemId=null,message="Questa etichetta appartiene a un altro magazzino. Cambia magazzino e riprova.")
            return
        }
        val item=ui.items.firstOrNull { it.sku.equals(target.sku,ignoreCase=true) }
        ui=if(item!=null) ui.copy(scannedItemId=item.id,message=null)
        else ui.copy(scannedItemId=null,message="Articolo ${target.sku} non trovato in ${warehouse.name}.")
    }
    private fun execute(onSuccess: (() -> Unit)? = null, action: () -> Unit) {
        if(ui.busy) return
        val generation=backgroundGeneration
        ui=ui.copy(busy=true, message=null)
        viewModelScope.launch {
            try {
                val failure=withContext(Dispatchers.IO) {
                    try { action(); null } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { (e.message ?: "Operazione non riuscita").substringBefore(" (code ") }
                }
                reloadAfterSelection(failure,generation)
                if(failure==null && generation==backgroundGeneration) onSuccess?.invoke()
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { ui=ui.copy(message="Archivio non disponibile: ${e.message}") }
            finally { ui=ui.copy(busy=false); drainWalls() }
        }
    }
    private suspend fun reload(error: String? = null) {
        val id=ui.profile?.id;val warehouseId=ui.warehouse?.id
        val snapshot=withContext(Dispatchers.IO) {
            val profiles=db.profiles()
            val profile=profiles.firstOrNull{it.id==id}
            val warehouses=profile?.let{db.warehouses(it.id)} ?: emptyList()
            val warehouse=warehouses.firstOrNull{it.id==warehouseId}
            StockUiState(profiles,profile,warehouses,warehouse,
                warehouse?.let { db.items(it.id) } ?: emptyList(),
                warehouse?.let { db.placements(it.id) } ?: emptyList(),
                warehouse?.let { db.walls(it.id) } ?: emptyList(),
                warehouse?.let { db.movements(it.id) } ?: emptyList(),
                warehouse?.let { db.activeLoans(it.id) } ?: emptyList(),
                warehouse?.let {
                    val now=System.currentTimeMillis()
                    val day=86_400_000L
                    ManagerSnapshot(db.dailyActivity(it.id,now-90*day),db.usageSummary(it.id,now))
                } ?: ManagerSnapshot(),
                demoCount=warehouse?.let {db.demoAssetCount(it.id)} ?: 0,
                createdItemId=if (error==null) pendingNewItem else null,
                busy=true,message=error).copy(
                    orders=warehouse?.let {operations.orders(it.id,operationalRowLimit)} ?: emptyList(),
                    stockCounts=warehouse?.let {operations.counts(it.id,operationalRowLimit)} ?: emptyList(),
                    stockTransfers=warehouse?.let {operations.transfers(it.id,operationalRowLimit)} ?: emptyList(),
                    maintenanceWork=warehouse?.let {operations.maintenance(it.id,operationalRowLimit)} ?: emptyList(),
                    operationsEvents=warehouse?.let {operations.events(it.id,operationalRowLimit)} ?: emptyList(),
                    businessMode=profile?.let {operations.businessMode(it.id)} ?: false,
                    monthlyBudget=profile?.let {operations.monthlyBudget(it.id)},
                    costCenters=profile?.let {enterprise.centers(it.id)} ?: emptyList(),
                    businessSuppliers=profile?.let {enterprise.suppliers(it.id)} ?: emptyList(),
                    businessAssets=warehouse?.let {enterprise.assets(it.id)} ?: emptyList(),
                    staffDirectory=profile?.let {enterprise.staffDirectory(it.id)} ?: emptyList(),
                    unsentCatalogEvents=profile?.let {enterprise.pendingEventCount(it.id)} ?: 0,
                    reservations=warehouse?.let {planning.reservations(it.id,operationalRowLimit)} ?: emptyList(),
                    stockLots=warehouse?.let {trace.lots(it.id,operationalRowLimit)} ?: emptyList(),
                    stockSerials=warehouse?.let {trace.serials(it.id,operationalRowLimit)} ?: emptyList(),
                    operationsLimit=operationalRowLimit,
                    reservedByItem=warehouse?.let {planning.reservedByItem(it.id)} ?: emptyMap(),
                    placementActivity=warehouse?.let {db.placementActivity(it.id,System.currentTimeMillis()-30*86_400_000L)} ?: emptyMap(),
                    team=team.status(),
                    operationalReports=warehouse?.takeIf{profile!=null && operations.businessMode(profile.id)}?.let {w->
                        val now=System.currentTimeMillis()
                        listOf(7,30,90).associateWith{OperationalAnalytics(db).report(w.id,it,now)}
                    } ?: emptyMap()
                )
        }
        ui=snapshot.copy(initialized=true,sessionLocked=ui.sessionLocked,mapFocusPlacementId=ui.mapFocusPlacementId,
            locationPickItemIds=ui.locationPickItemIds?.takeIf{ids->snapshot.warehouse?.id==ui.warehouse?.id && ids.all{id->snapshot.items.any{it.id==id}}},
            mapUndoLabel=mapUndo.lastOrNull()?.label,mapRedoLabel=mapRedo.lastOrNull()?.label)
        pendingNewItem = null
    }

    // ---- Map: targeted reload, undo/redo, focus, camera ----
    private val mapUndo=ArrayDeque<MapEdit>()
    private val mapRedo=ArrayDeque<MapEdit>()
    private val pendingWalls=ArrayDeque<Pair<Long,FloatArray>>()
    private fun clearMapHistory() { mapUndo.clear();mapRedo.clear();ui=ui.copy(mapUndoLabel=null,mapRedoLabel=null) }
    private val mapCameras=mutableMapOf<Long,Triple<Float,Float,Float>>()
    /** Zoom/pan survive tab switches; reset when the room is resized. */
    fun mapCamera(warehouseId:Long):Triple<Float,Float,Float> = mapCameras[warehouseId] ?: Triple(1f,0f,0f)
    fun saveMapCamera(warehouseId:Long,zoom:Float,panX:Float,panY:Float) { mapCameras[warehouseId]=Triple(zoom,panX,panY) }
    fun showOnMap(placementId:Long) { if(ui.placements.any{it.id==placementId}) ui=ui.copy(mapFocusPlacementId=placementId,scannedItemId=null) }
    fun acknowledgeMapFocus() { ui=ui.copy(mapFocusPlacementId=null) }
    fun startLocationPick(itemIds:List<Long>) {
        val ids=itemIds.filter{id->ui.items.any{it.id==id}}.distinct()
        if(ids.isNotEmpty()) ui=ui.copy(locationPickItemIds=ids,scannedItemId=null,message=null)
    }
    fun cancelLocationPick() { ui=ui.copy(locationPickItemIds=null) }
    fun completeLocationPick(placementId:Long,level:Int?,slot:Int?) {
        val ids=ui.locationPickItemIds ?: return
        if(ui.busy) return
        ui=ui.copy(locationPickItemIds=null)
        assignItems(ids,placementId,level,slot)
    }

    /**
     * Map edits reload only rooms, structures, walls and items instead of the whole archive
     * (orders, analytics, lots…), so dragging and assigning stay fast with large inventories.
     */
    /** On a satellite the map belongs to the central phone: it arrives with each update. */
    private fun requireMapEditable() {
        val t=ui.team
        require(!(t.role==TeamRole.SATELLITE && t.warehouseId==ui.warehouse?.id)) {
            "Su questo telefono (satellite ${t.letter}) la mappa si modifica solo dal telefono centrale"
        }
    }
    /** Satellite with a department: true when [category] is outside it (the central decides). */
    private fun outsideDepartment(category:String?):Boolean {
        val t=ui.team
        return t.role==TeamRole.SATELLITE && t.warehouseId==ui.warehouse?.id && t.scope.isNotEmpty() && category!=null && category !in t.scope
    }
    private fun department()=ui.team.scope.joinToString(", ")
    /** Categories offered for a new article: the department on a satellite, otherwise all (empty). */
    fun departmentCategories():List<String> { val t=ui.team
        return if(t.role==TeamRole.SATELLITE && t.warehouseId==ui.warehouse?.id) t.scope else emptyList() }
    private fun requireDepartment(itemIds:List<Long>,what:String) {
        val outside=ui.items.filter { it.id in itemIds && outsideDepartment(it.category) }
        require(outside.isEmpty()) { "${outside.first().name} non è del tuo reparto (${department()}): $what" }
    }
    private fun executeMap(record:Boolean=true,action:()->MapEdit?) = runMap(action) { edit ->
        if(record) { mapUndo.addLast(edit);while(mapUndo.size>50)mapUndo.removeFirst();mapRedo.clear() }
    }
    private fun runMap(action:()->MapEdit?,onSuccess:(MapEdit)->Unit) {
        if(ui.busy) return
        val generation=backgroundGeneration
        val warehouseId=ui.warehouse?.id ?: return
        ui=ui.copy(busy=true,message=null)
        viewModelScope.launch {
            try {
                var failure:String?=null
                val edit=withContext(Dispatchers.IO) {
                    try { action() } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { failure=(e.message ?: "Operazione non riuscita").substringBefore(" (code "); null }
                }
                if(edit!=null && generation==backgroundGeneration && ui.warehouse?.id==warehouseId) onSuccess(edit)
                reloadMap(failure)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { ui=ui.copy(message="Archivio non disponibile: ${e.message}") }
            finally {
                ui=ui.copy(busy=false,mapUndoLabel=mapUndo.lastOrNull()?.label,mapRedoLabel=mapRedo.lastOrNull()?.label)
                drainWalls()
            }
        }
    }
    private suspend fun reloadMap(error:String?) {
        val profileId=ui.profile?.id
        val warehouseId=ui.warehouse?.id
        if(profileId==null || warehouseId==null) { reload(error);return }
        val fresh=withContext(Dispatchers.IO) {
            val warehouses=db.warehouses(profileId)
            val warehouse=warehouses.firstOrNull{it.id==warehouseId}
            if(warehouse==null) null else listOf(warehouses,warehouse,db.items(warehouseId),db.placements(warehouseId),
                db.walls(warehouseId),db.placementActivity(warehouseId,System.currentTimeMillis()-30*86_400_000L))
        }
        if(fresh==null) { reload(error);return }
        @Suppress("UNCHECKED_CAST")
        ui=ui.copy(warehouses=fresh[0] as List<Warehouse>,warehouse=fresh[1] as Warehouse,items=fresh[2] as List<StockItem>,
            placements=fresh[3] as List<Placement>,walls=fresh[4] as List<Wall>,placementActivity=fresh[5] as Map<Long,Int>,message=error)
    }
    // An edit leaves its stack only when the revert really runs; a double tap while saving does not
    // lose it. If the revert fails (e.g. the structure was deleted meanwhile) it is dropped on purpose.
    fun undoMap() {
        if(ui.busy) return
        val edit=mapUndo.removeLastOrNull() ?: return
        runMap({edit.revert(db)}) { inverse -> mapRedo.addLast(inverse) }
    }
    fun redoMap() {
        if(ui.busy) return
        val edit=mapRedo.removeLastOrNull() ?: return
        runMap({edit.revert(db)}) { inverse -> mapUndo.addLast(inverse) }
    }
    fun dismissMessage(){ui=ui.copy(message=null)}
    fun createProfile(name: String,pin: String) = execute {
        val id=db.createProfile(name,pin)
        // Il nuovo profilo è selezionato solo dopo la creazione completata.
        val selected=db.profiles().firstOrNull{it.id==id}
        if(selected!=null) pendingProfileId=id
    }
    private var pendingProfileId: Long? = null
    private var pendingWarehouseId: Long? = null
    // All operation/session fields must exist before the first coroutine starts.
    init { refresh() }
    fun unlock(profile: Profile,pin: String) = execute {
        require(db.authenticate(profile.id,pin)) { "PIN errato" }
        pendingProfileId=profile.id
    }
    fun signOut() { if(!ui.busy) {clearMapHistory();archiveAuthorization=null;pendingCsvImport=null;ui=StockUiState(profiles=ui.profiles,initialized=true)} }
    fun leaveWarehouse() { if(!ui.busy) {clearMapHistory();pendingCsvImport=null;ui=ui.copy(warehouse=null,items=emptyList(),placements=emptyList(),walls=emptyList(),movements=emptyList(),loans=emptyList(),orders=emptyList(),stockCounts=emptyList(),stockTransfers=emptyList(),maintenanceWork=emptyList(),operationsEvents=emptyList(),csvPreviewCount=null)} }
    fun selectWarehouse(w: Warehouse) {
        if(ui.busy||ui.profile?.id!=w.profileId)return
        operationalRowLimit=250
        clearMapHistory()
        ui=ui.copy(warehouse=w)
        execute {} // ricarica tutte le liste per il magazzino scelto
    }
    fun createWarehouse(name: String,location: String) {
        if(!ui.busy) clearMapHistory()
        createWarehouseNow(name,location)
    }
    private fun createWarehouseNow(name: String,location: String) = execute {
        val pid=requireNotNull(ui.profile?.id){"Seleziona un profilo"}
        pendingWarehouseId=db.createWarehouse(pid,name,location)
    }
    fun addItem(name:String,cat:String,qty:Double,min:Double,unit:String,note:String,photoUri:String?=null,
                cost:Double?=null,supplier:String="",leadDays:Int?=null) = execute {
        require(!outsideDepartment(cat)) { "Puoi creare solo articoli del tuo reparto (${department()})" }
        val picture=photoUri?.let { PhotoFiles.save(getApplication<Application>(),Uri.parse(it)) }
        try {
            pendingNewItem = db.addItem(requireWarehouse(),name,cat,qty,min,unit,note,picture,cost,supplier,leadDays)
        } catch(t:Throwable) {
            PhotoFiles.delete(getApplication<Application>(),picture)
            throw t
        } finally {
            photoUri?.let{PhotoFiles.removeCapturedTemp(getApplication<Application>(),Uri.parse(it))}
        }
    }
    fun replaceItemPhoto(itemId:Long,photoUri:String) = execute {
        val current=requireNotNull(db.item(itemId)){"Articolo inesistente"}
        val saved=PhotoFiles.save(getApplication<Application>(),Uri.parse(photoUri))
        try {
            db.updatePhoto(itemId,saved)
            PhotoFiles.delete(getApplication<Application>(),current.photoFile)
        } catch(t:Throwable) {
            PhotoFiles.delete(getApplication<Application>(),saved)
            throw t
        } finally {
            PhotoFiles.removeCapturedTemp(getApplication<Application>(),Uri.parse(photoUri))
        }
    }
    fun removeItemPhoto(itemId:Long) = execute {
        val current=requireNotNull(db.item(itemId)){"Articolo inesistente"}
        db.updatePhoto(itemId,null)
        PhotoFiles.delete(getApplication<Application>(),current.photoFile)
    }
    fun updateItemDetails(itemId: Long, name: String, category: String, minimum: Double,
                          unit: String, note: String, cost: Double?, supplier: String, leadDays: Int?) = execute {
        team.requireDetailsEditable(itemId)
        require(!outsideDepartment(category)) { "Categoria fuori dal tuo reparto (${department()})" }
        db.updateItemDetails(requireWarehouse(),itemId,name,category,minimum,unit,note,cost,supplier,leadDays)
    }
    fun updatePurchase(itemId:Long,cost:Double?,supplier:String,leadDays:Int?) = execute {
        team.requireDetailsEditable(itemId)
        db.updatePurchase(itemId,cost,supplier,leadDays)
    }
    fun movement(itemId:Long,delta:Double,note:String) {
        val item=ui.items.firstOrNull { it.id==itemId }
        val label=MovementUndo.label(delta,item)
        execute(onSuccess={ ui=ui.copy(movementUndo=MovementUndo(itemId,delta,label,System.nanoTime())) }) {
            if(delta>0) requireDepartment(listOf(itemId),"qui puoi solo prelevare")
            db.movement(itemId,delta,note)
        }
    }
    /** Quick +1 / -1 from the list: same traced movement as the item page. */
    fun quickMovement(itemId:Long,delta:Double) = movement(itemId,delta,if(delta>0) "Carico rapido" else "Prelievo rapido")
    /** Undo never deletes history: it records the opposite movement, so the audit trail stays complete. */
    fun undoMovement() {
        val undo=ui.movementUndo ?: return
        if(ui.busy) return
        ui=ui.copy(movementUndo=null)
        execute { team.returning { db.movement(undo.itemId,-undo.delta,"Annullato: ${undo.label}".take(250)) } }
    }
    fun dismissMovementUndo(token:Long) { if(ui.movementUndo?.token==token) ui=ui.copy(movementUndo=null) }
    fun reconcileQuantity(itemId:Long,counted:Double,reason:String) = execute {
        requireDepartment(listOf(itemId),"la rettifica la fa il telefono centrale")
        db.reconcileQuantity(itemId,counted,reason)
    }
    fun lend(itemId:Long,borrower:String,amount:Double) = execute { db.lend(itemId,borrower,amount) }
    fun returnLoan(id:Long) = execute { team.returning { db.returnLoan(id) } }
    /** Local managerial actions operate only on the currently authenticated warehouse. */
    private fun managerActor():String = requireNotNull(ui.profile){"Profilo non selezionato"}.name
    fun changeManagerMode(business:Boolean) = execute {
        val profile=requireNotNull(ui.profile){"Profilo non selezionato"}
        operations.setBusinessMode(profile.id,business)
    }
    fun changeMonthlyBudget(value:Double?) = execute {
        val profile=requireNotNull(ui.profile){"Profilo non selezionato"}
        operations.setMonthlyBudget(profile.id,value)
    }
    fun addCostCenter(code:String,name:String,budget:Double?) = execute {
        val pid=requireNotNull(ui.profile?.id){"Seleziona un profilo"}
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        enterprise.createCenter(pid,code,name,budget)
    }
    fun addBusinessSupplier(name:String,taxId:String,email:String,leadDays:Int?) = execute {
        val pid=requireNotNull(ui.profile?.id){"Seleziona un profilo"}
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        enterprise.createSupplier(pid,name,taxId,email,leadDays)
    }
    fun addBusinessAsset(code:String,name:String,centerId:Long?) = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        enterprise.createAsset(requireWarehouse(),code,name,centerId)
    }
    fun addStaffDirectoryEntry(name:String,role:String) = execute {
        val pid=requireNotNull(ui.profile?.id){"Seleziona un profilo"}
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        enterprise.addStaffDirectoryEntry(pid,name,role)
    }
    fun createStockLot(itemId:Long,code:String,quantity:Double,expiresAt:Long?) = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        trace.registerLot(requireWarehouse(),itemId,code,quantity,expiresAt)
    }
    fun consumeStockLot(lotId:Long,quantity:Double,discard:Boolean=false) = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        trace.consumeLot(requireWarehouse(),lotId,quantity,managerActor(),discard)
    }
    fun createStockSerial(itemId:Long,serial:String) = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        trace.registerSerial(requireWarehouse(),itemId,serial)
    }
    fun issueStockSerial(serialId:Long) = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        trace.issueSerial(requireWarehouse(),serialId,managerActor())
    }
    fun reserveStock(itemId:Long,quantity:Double,reference:String) = execute {
        val actor=managerActor()
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        planning.reserve(requireWarehouse(),itemId,quantity,reference,actor)
    }
    fun closeReservation(id:Long,consume:Boolean) = execute {
        val actor=managerActor()
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        planning.close(requireWarehouse(),id,consume,actor)
    }
    fun availableStock(itemId:Long):StockAvailability {
        val item=ui.items.firstOrNull{it.id==itemId} ?: return StockAvailability(0.0,0.0)
        val held=ui.reservedByItem[item.id] ?: 0.0
        return StockAvailability(item.quantity,held)
    }
    fun requestPurchase(itemId:Long,quantity:Double,supplier:String,owner:String,
                        costCenterId:Long?=null,supplierId:Long?=null) = execute {
        operations.createOrder(requireWarehouse(),itemId,quantity,supplier,owner.ifBlank{managerActor()},
            costCenterId,supplierId)
    }
    fun advancePurchase(id:Long,action:String) = execute {
        operations.advanceOrder(requireWarehouse(),id,action,managerActor())
    }
    fun receivePurchase(id:Long,quantity:Double) = execute {
        operations.receiveOrderQuantity(requireWarehouse(),id,quantity,managerActor())
    }
    fun requestCycleCount(itemId:Long,counted:Double,reason:String) = execute {
        operations.requestCount(requireWarehouse(),itemId,counted,reason,managerActor())
    }
    fun decideCycleCount(id:Long,approve:Boolean) = execute {
        operations.finishCount(requireWarehouse(),id,approve,managerActor())
    }
    fun requestTransfer(itemId:Long,targetWarehouseId:Long,quantity:Double) = execute {
        operations.requestTransfer(requireWarehouse(),targetWarehouseId,itemId,quantity,managerActor())
    }
    fun processTransfer(id:Long,action:String) = execute {
        val transfer=ui.stockTransfers.firstOrNull{it.id==id}
        requireNotNull(transfer){"Trasferimento non disponibile"}
        require(ui.warehouse?.id==transfer.fromId || ui.warehouse?.id==transfer.toId){"Magazzino non autorizzato"}
        if(action=="SHIP")require(ui.warehouse?.id==transfer.fromId){"Seleziona il magazzino di origine"}
        if(action=="RECEIVE")require(ui.warehouse?.id==transfer.toId){"Seleziona il magazzino di destinazione"}
        operations.progressTransfer(id,managerActor(),action)
    }
    fun createWorkOrder(machine:String,description:String,itemId:Long?,quantity:Double,
                        assetId:Long?=null,laborHours:Double=0.0,hourlyRate:Double?=null) = execute {
        operations.createMaintenance(requireWarehouse(),machine,description,itemId,quantity,managerActor(),
            assetId,laborHours,hourlyRate)
    }
    fun finishWorkOrder(id:Long,complete:Boolean) = execute {
        operations.finishMaintenance(requireWarehouse(),id,complete,managerActor())
    }
    fun addPlacement(kind:String,label:String,x:Float?=null,y:Float?=null) = executeMap {
        requireMapEditable()
        val wh=requireNotNull(ui.warehouse){"Seleziona un magazzino"}
        MapEdit.Created(listOf(db.addPlacement(wh.id,kind,label,x ?: wh.mapWidth/2f,y ?: wh.mapHeight/2f)))
    }
    fun duplicatePlacement(id:Long) = executeMap {
        requireMapEditable()
        val src=requireNotNull(ui.placements.firstOrNull{it.id==id}){"Struttura non trovata"}
        val e=PlanGeometry.extents(src)
        MapEdit.Created(listOf(db.duplicatePlacement(id,src.x+e.x*2+.3f,src.y)))
    }
    fun createPlacementRow(id:Long,count:Int,gap:Float,vertical:Boolean) = executeMap {
        requireMapEditable()
        MapEdit.Created(db.createPlacementRow(id,count,gap,vertical))
    }
    fun resizeMap(width:Float,height:Float) {
        val warehouseId=ui.warehouse?.id ?: return
        if(ui.busy) return
        mapCameras.remove(warehouseId)
        // Older edits refer to the previous room size: undoing them could put things outside it.
        clearMapHistory()
        executeMap(record=false) { requireMapEditable();db.updateMapSize(warehouseId,width,height);null }
    }
    fun resizePlacement(id:Long,width:Float,depth:Float,rotation:Float)=executeMap {
        requireMapEditable()
        val before=requireNotNull(db.placement(id)){"Struttura non trovata"}
        db.updatePlacementGeometry(id,width,depth,rotation)
        MapEdit.Reshaped(before)
    }
    // Durante il drag aggiorniamo l'UI immediatamente; il commit avviene solo al rilascio.
    fun previewMove(id:Long,x:Float,y:Float) {
        val wh=ui.warehouse ?: return
        ui=ui.copy(placements=ui.placements.map{p ->
            if(p.id!=id || p.locked) p else {
                val pos=PlanGeometry.boundedPlacementCenter(x,y,p.width,p.depth,p.rotation,
                    wh.mapWidth,wh.mapHeight)
                p.copy(x=pos.x,y=pos.y)
            }
        })
    }
    fun commitMove(id:Long,fromX:Float,fromY:Float,x:Float,y:Float) = executeMap {
        requireMapEditable()
        require(db.movePlacement(id,x,y)){"Struttura bloccata o non trovata"}
        val after=requireNotNull(db.placement(id))
        MapEdit.Moved(id,fromX,fromY,after.x,after.y)
    }
    fun lockPlacement(id:Long,locked:Boolean)=executeMap(record=false){requireMapEditable();db.lockPlacement(id,locked);null}
    fun renamePlacement(id:Long,label:String)=executeMap(record=false){requireMapEditable();db.renamePlacement(id,label);null}
    fun updatePlacementLayout(id:Long,levels:Int,slots:Int)=executeMap(record=false){requireMapEditable();db.updatePlacementLayout(id,levels,slots);null}
    fun deletePlacement(id:Long)=executeMap {
        requireMapEditable()
        val snapshot=requireNotNull(db.placement(id)){"Struttura non trovata"}
        val spots=db.itemSpots(id)
        db.deletePlacement(id)
        MapEdit.Deleted(listOf(snapshot to spots))
    }
    fun assignItem(itemId:Long,placementId:Long?,level:Int?=null,slot:Int?=null)=
        executeMap(record=false){requireDepartment(listOf(itemId),"lo sposta il telefono centrale");db.assignItems(listOf(itemId),placementId,level,slot);null}
    fun assignItems(itemIds:List<Long>,placementId:Long?,level:Int?=null,slot:Int?=null)=
        executeMap(record=false){requireDepartment(itemIds,"lo sposta il telefono centrale");db.assignItems(itemIds,placementId,level,slot);null}
    /** Walls drawn faster than they are saved are queued, never dropped: the chain stays closed. */
    fun addWall(coords:FloatArray) {
        val warehouseId=ui.warehouse?.id ?: return
        pendingWalls.addLast(warehouseId to coords.copyOf())
        drainWalls()
    }
    private fun drainWalls() {
        if(ui.busy) return
        val (warehouseId,coords)=pendingWalls.removeFirstOrNull() ?: return
        if(ui.warehouse?.id!=warehouseId) { pendingWalls.clear(); return }
        executeMap { requireMapEditable();MapEdit.WallsAdded(listOf(db.addWall(warehouseId,coords))) }
    }
    fun deleteWall(id:Long)=executeMap{
        requireMapEditable()
        val wall=requireNotNull(db.wall(id)){"Parete non trovata"}
        db.deleteWall(id)
        MapEdit.WallsDeleted(listOf(wall))
    }
    fun setWallLength(id:Long,length:Float)=executeMap{
        requireMapEditable()
        val wall=requireNotNull(db.wall(id)){"Parete non trovata"}
        db.updateWall(id,PlanGeometry.withLength(wall,length))
        MapEdit.WallChanged(id,floatArrayOf(wall.x1,wall.y1,wall.x2,wall.y2))
    }
    fun importAr(json:String) { if(!ui.busy) clearMapHistory(); execute{requireMapEditable();db.importArScan(requireWarehouse(),json)} }
    // Map history is touched on the main thread only, before the work starts.
    private fun requireNoSync() = require(ui.team.role==TeamRole.NONE) { "Con la sincronizzazione attiva i dati dimostrativi non sono disponibili" }
    fun seedDemo() { if(!ui.busy) clearMapHistory(); execute { requireNoSync();db.seedDemo(requireWarehouse()) } }
    fun refreshDemo() { if(!ui.busy) clearMapHistory(); refreshDemoNow() }
    private fun refreshDemoNow() = execute {
        requireNoSync()
        val names=db.removeDemo(requireWarehouse())
        names.forEach {PhotoFiles.delete(getApplication<Application>(),it)}
        db.seedDemo(requireWarehouse())
    }
    fun removeDemo() { if(!ui.busy) clearMapHistory(); removeDemoNow() }
    private fun removeDemoNow() = execute {
        val names=db.removeDemo(requireWarehouse())
        names.forEach {PhotoFiles.delete(getApplication<Application>(),it)}
    }
    /** Il backup completo include anche gli altri profili e magazzini del telefono. */
    suspend fun authorizeArchive(pins:Map<Long,String>) {
        require(!ui.busy && ui.profile!=null && !ui.sessionLocked) { "Accedi prima a un profilo" }
        archiveAuthorization=null
        val generation=backgroundGeneration
        val ids=ui.profiles.map{it.id}.toSet()
        ui=ui.copy(busy=true)
        try {
            withContext(Dispatchers.IO) {
                for(id in ids) require(db.authenticate(id,pins[id].orEmpty())) { "PIN errato per uno dei profili" }
            }
            require(generation==backgroundGeneration && !ui.sessionLocked) { "Sessione bloccata" }
            archiveAuthorization=ids
        } finally {ui=ui.copy(busy=false)}
    }
    private fun requireArchiveAuthorization() {
        require(ui.profile!=null && !ui.sessionLocked && archiveAuthorization==ui.profiles.map{it.id}.toSet()) {
            "Per l'archivio completo verifica il PIN di tutti i profili"
        }
        archiveAuthorization=null
    }
    suspend fun exportBackup(uri:Uri,password:String) {
        require(!ui.busy) {"Attendi il completamento dell'operazione in corso"}
        requireArchiveAuthorization()
        ui=ui.copy(busy=true)
        try {withContext(Dispatchers.IO){BackupArchive(getApplication(),db).export(uri,password)}}
        finally {ui=ui.copy(busy=false)}
    }
    /** Ripristina l'intero archivio e ritorna alla scelta del profilo. */
    suspend fun restoreBackup(uri:Uri,password:String) {
        require(!ui.busy) {"Attendi il completamento dell'operazione in corso"}
        requireArchiveAuthorization()
        ui=ui.copy(busy=true)
        try {
            withContext(Dispatchers.IO){BackupArchive(getApplication(),db).restore(uri,password)}
            clearMapHistory();pendingWalls.clear()
            val profiles=withContext(Dispatchers.IO){db.profiles()}
            pendingNewItem=null;pendingProfileId=null;pendingWarehouseId=null
            ui=StockUiState(profiles=profiles,initialized=true,message="Backup ripristinato. Seleziona il profilo e inserisci il relativo PIN.")
        } finally {if(ui.busy)ui=ui.copy(busy=false)}
    }
    /** Legge 100 movimenti alla volta (più un record per sapere se esiste la pagina successiva). */
    suspend fun historyPage(warehouseId: Long, focus: ActivityFocus, days: Int, page: Int): List<Movement> {
        require(ui.profile!=null && !ui.sessionLocked && ui.warehouse?.id==warehouseId) { "Seleziona un magazzino e accedi" }
        require(days in listOf(0,7,30,90) && page in 0..(Int.MAX_VALUE/100) && focus!=ActivityFocus.LOANS) {
            "Filtri cronologia non validi"
        }
        val since=if(days==0) 0L else System.currentTimeMillis()-days*86_400_000L
        val direction=when(focus) {
            ActivityFocus.INBOUND -> 1
            ActivityFocus.OUTBOUND -> -1
            else -> 0
        }
        return withContext(Dispatchers.IO) { db.movementsPage(warehouseId,since,direction,page*100,101) }
    }
    fun prepareCsvImport(uri: Uri) {
        if(ui.busy || ui.sessionLocked || ui.profile==null || ui.warehouse==null) return
        pendingCsvImport=null
        val warehouseId=ui.warehouse!!.id
        val generation=backgroundGeneration
        ui=ui.copy(busy=true,csvPreviewCount=null,message=null)
        viewModelScope.launch {
            try {
                val rows=withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    val input=resolver.openInputStream(uri) ?: error("Impossibile leggere il CSV")
                    val bytes=java.io.ByteArrayOutputStream()
                    input.use { stream ->
                        val chunk=ByteArray(16384)
                        while(true) {
                            val count=stream.read(chunk)
                            if(count<0) break
                            require(bytes.size()+count<=CsvImport.MAX_BYTES) { "CSV troppo grande" }
                            bytes.write(chunk,0,count)
                        }
                    }
                    val parsed=CsvImport.parse(CsvImport.decode(bytes.toByteArray()))
                    db.validateCsvImport(warehouseId,parsed)
                    parsed
                }
                if(generation==backgroundGeneration && !ui.sessionLocked && ui.warehouse?.id==warehouseId) {
                    pendingCsvImport=warehouseId to rows
                    ui=ui.copy(csvPreviewCount=rows.size)
                }
            } catch(e:Exception) {
                pendingCsvImport=null
                ui=ui.copy(message=e.message ?: "CSV non valido",csvPreviewCount=null)
            } finally {ui=ui.copy(busy=false)}
        }
    }
    fun cancelCsvImport() {
        if(!ui.busy) { pendingCsvImport=null;ui=ui.copy(csvPreviewCount=null) }
    }
    fun confirmCsvImport() = execute {
        val (warehouseId,rows)=requireNotNull(pendingCsvImport) { "Seleziona un CSV" }
        rows.firstOrNull { outsideDepartment(it.category) }?.let { throw IllegalArgumentException("${it.name}: categoria ${it.category} fuori dal tuo reparto (${department()})") }
        require(warehouseId==requireWarehouse() && !ui.sessionLocked) { "Magazzino cambiato" }
        db.importCsv(warehouseId,rows)
        pendingCsvImport=null
    }
    // ---- 4.8 phone-to-phone sync ----
    fun becomeTeamCentral(password:String) = execute { team.becomeCentral(requireWarehouse(),password) }
    fun leaveTeam(force:Boolean=false) { link.stop();execute { team.leave(force) } }
    /** Central: opens the short-range network for the synced warehouse. */
    fun startCentralLink() {
        val t=ui.team; val w=ui.warehouse
        if(t.role!=TeamRole.CENTRAL || w==null || t.warehouseId!=w.id || ui.sessionLocked) return
        link.startCentral(team.warehouseUid() ?: return,w.name)
    }
    /** Satellite: looks for its central and exchanges on its own. */
    fun startSatelliteLink() {
        val t=ui.team
        if(t.role!=TeamRole.SATELLITE || ui.sessionLocked) return
        link.startSatellite(team.warehouseUid() ?: return,t.letter ?: return)
    }
    /** First connection without a file: the nearby central sends its archive. */
    fun joinNearby(password:String,letter:String) {
        val profileId=ui.profile?.id ?: return
        if(ui.team.role!=TeamRole.NONE || ui.sessionLocked) return
        link.startJoin(profileId,password,letter.uppercase())
    }
    fun stopLink()=link.stop()
    private suspend fun refreshAfterLink(joinedWarehouse:Long?) {
        if(ui.sessionLocked) return
        if(joinedWarehouse!=null) pendingWarehouseId=joinedWarehouse
        clearMapHistory()
        reloadAfterSelection(null,backgroundGeneration)
    }
    override fun onCleared() { link.stop();super.onCleared() }
    fun dismissTeamConflicts() = execute { team.dismissConflicts() }
    /** Central: departments of a satellite letter (empty = all categories). */
    fun setTeamScope(letter:String,categories:List<String>) = execute { team.setScope(letter,categories) }
    /** Runs a sync file operation on the IO thread, then reloads; returns the text to show. */
    private suspend fun teamFile(block:()->String):String {
        require(!ui.busy && ui.profile!=null && !ui.sessionLocked) { "Attendi il completamento dell'operazione in corso" }
        val generation=backgroundGeneration
        ui=ui.copy(busy=true,message=null)
        try {
            val text=withContext(Dispatchers.IO) { block() }
            clearMapHistory()
            reloadAfterSelection(null,generation)
            return text
        } finally { ui=ui.copy(busy=false) }
    }
    suspend fun teamExport(uri:Uri):String = teamFile {
        val resolver=getApplication<Application>().contentResolver
        (resolver.openOutputStream(uri) ?: error("Impossibile creare il file")).use { out ->
            if(team.status().role==TeamRole.CENTRAL) { team.exportState(out); "Aggiornamento pronto: invialo agli altri telefoni" }
            else { val n=team.exportChanges(out); if(n==0) "Nessuna modifica nuova: il file serve solo a confermare" else "$n modifiche pronte: invia il file al telefono centrale" }
        }
    }
    suspend fun teamImport(uri:Uri):String = teamFile {
        val resolver=getApplication<Application>().contentResolver
        (resolver.openInputStream(uri) ?: error("Impossibile leggere il file")).use { input ->
            if(team.status().role==TeamRole.CENTRAL) {
                val r=team.importChanges(input)
                "Importati: ${r.articles} articoli nuovi, ${r.movements} movimenti, ${r.locations} spostamenti" +
                    (if(r.skipped>0) " · ${r.skipped} già importati" else "") + (if(r.conflicts>0) " · ${r.conflicts} conflitti da controllare" else "")
            } else {
                val r=team.importState(input)
                "Aggiornato dal centrale: ${r.articles} articoli" + (if(r.skipped>0) " · ${r.skipped} tue modifiche ancora da inviare" else "")
            }
        }
    }
    suspend fun teamJoin(uri:Uri,password:String,letter:String):String {
        val profileId=requireNotNull(ui.profile?.id) { "Accedi prima a un profilo" }
        return teamFile {
            val resolver=getApplication<Application>().contentResolver
            (resolver.openInputStream(uri) ?: error("Impossibile leggere il file")).use { input ->
                val r=team.joinAsSatellite(profileId,input,password,letter.uppercase())
                pendingWarehouseId=team.status().warehouseId
                "Collegato come satellite $letter: ${r.articles} articoli ricevuti"
            }
        }
    }
    fun exportCsv(): String=ui.warehouse?.let{db.csv(it.id)}?:""
    fun loadMoreOperations() = execute {
        require(ui.businessMode && !ui.sessionLocked){"Attiva la modalità aziendale"}
        operationalRowLimit=(operationalRowLimit+250).coerceAtMost(1000000)
    }
    fun refresh()=execute{}
    private fun requireWarehouse():Long=requireNotNull(ui.warehouse?.id){"Seleziona un magazzino"}

    // Consente di aggiornare simultaneamente profilo e magazzino, evitando ricariche stale.
    private suspend fun reloadAfterSelection(error:String?,generation:Long) {
        val selectedProfile=pendingProfileId
        val selectedWarehouse=pendingWarehouseId
        if(selectedProfile!=null) {
            val p=withContext(Dispatchers.IO){db.profiles().firstOrNull{it.id==selectedProfile}}
            ui=ui.copy(profile=p,warehouse=null,sessionLocked=generation!=backgroundGeneration)
        }
        if(selectedWarehouse!=null) {
            val w=withContext(Dispatchers.IO){ui.profile?.let{db.warehouses(it.id)}?.firstOrNull{it.id==selectedWarehouse}}
            operationalRowLimit=250
            ui=ui.copy(warehouse=w)
        }
        pendingProfileId=null;pendingWarehouseId=null
        reload(error)
    }
}



data class MovementUndo(val itemId: Long, val delta: Double, val label: String, val token: Long) {
    companion object {
        fun label(delta: Double, item: StockItem?): String =
            "${if(delta>0) "Carico" else "Prelievo"} ${amount(kotlin.math.abs(delta))} ${item?.unit.orEmpty()}".trim() +
                (item?.let { " · ${it.name}" } ?: "")
    }
}
