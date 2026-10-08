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
    val sessionLocked: Boolean = false
)
class StockViewModel(app: Application): AndroidViewModel(app) {
    private val db = StockDb(app.applicationContext)
    var ui by mutableStateOf(StockUiState())
        private set
    init { refresh() }
    private var backgroundGeneration=0L
    private var archiveAuthorization:Set<Long>?=null
    var pendingBackupPassword=""
    var pendingRestorePassword=""
    fun lockForBackground() { backgroundGeneration++;archiveAuthorization=null;pendingBackupPassword="";pendingRestorePassword="";ui=ui.copy(sessionLocked=true,createdItemId=null,scannedItemId=null) }

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
        val target=ArticleCodes.parseScan(raw)
        if(target == null) {
            ui=ui.copy(scannedItemId=null,message="Codice QR non riconosciuto. Usa un'etichetta Ubicora o Officina Stock.")
            return
        }
        if(target.warehouseId != null && target.warehouseId != warehouse.id) {
            ui=ui.copy(scannedItemId=null,message="Questo QR appartiene al magazzino ${target.warehouseId}. Cambia magazzino e riprova.")
            return
        }
        val item=ui.items.firstOrNull { it.sku.equals(target.sku,ignoreCase=true) }
        ui=if(item!=null) ui.copy(scannedItemId=item.id,message=null)
        else ui.copy(scannedItemId=null,message="Articolo ${target.sku} non trovato in ${warehouse.name}.")
    }
    private fun execute(action: () -> Unit) {
        if(ui.busy) return
        val generation=backgroundGeneration
        ui=ui.copy(busy=true, message=null)
        viewModelScope.launch {
            try {
                val failure=withContext(Dispatchers.IO) {
                    try { action(); null } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { e.message ?: "Operazione non riuscita" }
                }
                reloadAfterSelection(failure,generation)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { ui=ui.copy(message="Archivio non disponibile: ${e.message}") }
            finally { ui=ui.copy(busy=false) }
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
                busy=false,message=error)
        }
        ui=snapshot.copy(initialized=true,sessionLocked=ui.sessionLocked)
        pendingNewItem = null
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
    fun unlock(profile: Profile,pin: String) = execute {
        require(db.authenticate(profile.id,pin)) { "PIN errato" }
        pendingProfileId=profile.id
    }
    fun signOut() { if(!ui.busy) {archiveAuthorization=null;ui=StockUiState(profiles=ui.profiles,initialized=true)} }
    fun leaveWarehouse() { if(!ui.busy) ui=ui.copy(warehouse=null,items=emptyList(),placements=emptyList(),walls=emptyList(),movements=emptyList(),loans=emptyList()) }
    fun selectWarehouse(w: Warehouse) {
        if(ui.busy||ui.profile?.id!=w.profileId)return
        ui=ui.copy(warehouse=w)
        execute {} // ricarica tutte le liste per il magazzino scelto
    }
    fun createWarehouse(name: String,location: String) = execute {
        val pid=requireNotNull(ui.profile?.id){"Seleziona un profilo"}
        pendingWarehouseId=db.createWarehouse(pid,name,location)
    }
    fun addItem(name:String,cat:String,qty:Double,min:Double,unit:String,note:String,photoUri:String?=null,
                cost:Double?=null,supplier:String="",leadDays:Int?=null) = execute {
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
        db.updateItemDetails(requireWarehouse(),itemId,name,category,minimum,unit,note,cost,supplier,leadDays)
    }
    fun updatePurchase(itemId:Long,cost:Double?,supplier:String,leadDays:Int?) = execute {
        db.updatePurchase(itemId,cost,supplier,leadDays)
    }
    fun movement(itemId:Long,delta:Double,note:String) = execute { db.movement(itemId,delta,note) }
    fun lend(itemId:Long,borrower:String,amount:Double) = execute { db.lend(itemId,borrower,amount) }
    fun returnLoan(id:Long) = execute {db.returnLoan(id)}
    fun addPlacement(kind:String,label:String) = execute {
        val wh=requireNotNull(ui.warehouse){"Seleziona un magazzino"}
        db.addPlacement(wh.id,kind,label,wh.mapWidth/2f,wh.mapHeight/2f)
    }
    fun resizeMap(width:Float,height:Float)=execute { db.updateMapSize(requireWarehouse(),width,height) }
    fun resizePlacement(id:Long,width:Float,depth:Float,rotation:Float)=execute {
        db.updatePlacementGeometry(id,width,depth,rotation)
    }
    // Durante il drag aggiorniamo l'UI immediatamente; il commit avviene solo al rilascio.
    fun previewMove(id:Long,x:Float,y:Float) {
        ui=ui.copy(placements=ui.placements.map{if(it.id==id && !it.locked)it.copy(x=x.coerceIn(0f,ui.warehouse?.mapWidth ?: 12f),y=y.coerceIn(0f,ui.warehouse?.mapHeight ?: 8f)) else it})
    }
    fun commitMove(id:Long,x:Float,y:Float) = execute { db.movePlacement(id,x,y) }
    fun lockPlacement(id:Long,locked:Boolean)=execute{db.lockPlacement(id,locked)}
    fun deletePlacement(id:Long)=execute{db.deletePlacement(id)}
    fun assignItem(itemId:Long,placementId:Long?)=execute{db.assignItem(itemId,placementId)}
    fun addWall(coords:FloatArray)=execute{db.addWall(requireWarehouse(),coords)}
    fun deleteWall(id:Long)=execute{db.deleteWall(id)}
    fun importAr(json:String)=execute{db.importArScan(requireWarehouse(),json)}
    fun seedDemo() = execute { db.seedDemo(requireWarehouse()) }
    fun refreshDemo() = execute {
        val names=db.removeDemo(requireWarehouse())
        names.forEach {PhotoFiles.delete(getApplication<Application>(),it)}
        db.seedDemo(requireWarehouse())
    }
    fun removeDemo() = execute {
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
    fun exportCsv(): String=ui.warehouse?.let{db.csv(it.id)}?:""
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
            ui=ui.copy(warehouse=w)
        }
        pendingProfileId=null;pendingWarehouseId=null
        reload(error)
    }
}
