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
    val createdItemId: Long? = null,
    val busy: Boolean = false,
    val message: String? = null
)
class StockViewModel(app: Application): AndroidViewModel(app) {
    private val db = StockDb(app.applicationContext)
    var ui by mutableStateOf(StockUiState(profiles=db.profiles()))
        private set

    private var pendingNewItem: Long? = null
    fun acknowledgeNewItem() { ui = ui.copy(createdItemId = null) }
    private fun execute(action: () -> Unit) {
        if(ui.busy) return
        ui=ui.copy(busy=true, message=null)
        viewModelScope.launch {
            val failure=withContext(Dispatchers.IO) { runCatching { action() }.exceptionOrNull()?.message }
            reloadAfterSelection(failure)
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
                createdItemId=if (error==null) pendingNewItem else null,
                busy=false,message=error)
        }
        ui=snapshot
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
    fun signOut() { if(!ui.busy) ui=StockUiState(profiles=ui.profiles) }
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
    fun exportCsv(): String=ui.warehouse?.let{db.csv(it.id)}?:""
    fun refresh()=execute{}
    private fun requireWarehouse():Long=requireNotNull(ui.warehouse?.id){"Seleziona un magazzino"}

    // Consente di aggiornare simultaneamente profilo e magazzino, evitando ricariche stale.
    private suspend fun reloadAfterSelection(error:String?) {
        val selectedProfile=pendingProfileId
        val selectedWarehouse=pendingWarehouseId
        if(selectedProfile!=null) {
            val p=withContext(Dispatchers.IO){db.profiles().firstOrNull{it.id==selectedProfile}}
            ui=ui.copy(profile=p,warehouse=null)
        }
        if(selectedWarehouse!=null) {
            val w=withContext(Dispatchers.IO){ui.profile?.let{db.warehouses(it.id)}?.firstOrNull{it.id==selectedWarehouse}}
            ui=ui.copy(warehouse=w)
        }
        pendingProfileId=null;pendingWarehouseId=null
        reload(error)
    }
}
