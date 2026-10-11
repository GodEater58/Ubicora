package com.officinastock.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.QrCodeScanner
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity: ComponentActivity() {
    private val model: StockViewModel by viewModels()
    private var externalActivityPending=false
    override fun startActivityForResult(intent:Intent,requestCode:Int,options:Bundle?) {
        // Plain startActivity uses requestCode -1 and never delivers a result (for example legal links).
        if(requestCode>=0) externalActivityPending=true
        try { super.startActivityForResult(intent,requestCode,options) }
        catch(e:Exception) { if(requestCode>=0) externalActivityPending=false;throw e }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        externalActivityPending=false
        super.onActivityResult(requestCode,resultCode,data)
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putBoolean("externalActivityPending",externalActivityPending)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        if(!isChangingConfigurations && !externalActivityPending) model.lockForBackground()
        super.onStop()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        externalActivityPending=savedInstanceState?.getBoolean("externalActivityPending") ?: false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            StockTheme {
                val context=LocalContext.current
                val arLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if(result.resultCode==RESULT_OK) {
                        result.data?.getStringExtra("map_json")?.let{model.importAr(it)}
                    }
                }
                val qrLauncher=rememberLauncherForActivityResult(ScanContract()) { result ->
                    result.contents?.let { model.handleScannedCode(it) }
                }
                val csvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val failure=withContext(Dispatchers.IO) {runCatching {
                            val out=context.contentResolver.openOutputStream(uri)
                                ?: error("Impossibile aprire il file di destinazione")
                            out.use{it.write(model.exportCsv().toByteArray(Charsets.UTF_8))}
                        }.exceptionOrNull()?.message}
                        Toast.makeText(context,if(failure==null)"CSV esportato" else "Esportazione non riuscita: $failure",Toast.LENGTH_LONG).show()
                    }
                }
                val operationsCsvLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val failure=withContext(Dispatchers.IO) {runCatching {
                            val csv=OperationsCsv.export(model.ui)
                            val out=context.contentResolver.openOutputStream(uri)
                                ?: error("Impossibile aprire il report")
                            out.use {it.write(csv.toByteArray(Charsets.UTF_8))}
                        }.exceptionOrNull()?.message}
                        Toast.makeText(context,if(failure==null)"Report gestionale esportato" else "Report non riuscito: $failure",Toast.LENGTH_LONG).show()
                    }
                }
                val operationsPdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val failure=withContext(Dispatchers.IO) {runCatching {
                            val out=context.contentResolver.openOutputStream(uri)
                                ?: error("Impossibile aprire il report PDF")
                            out.use {OperationsPdf.write(model.ui,it)}
                        }.exceptionOrNull()?.message}
                        Toast.makeText(context,if(failure==null)"Report PDF esportato" else "Report PDF non riuscito: $failure",Toast.LENGTH_LONG).show()
                    }
                }
                val csvImportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) model.prepareCsvImport(uri)
                }
                val backupLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val error=runCatching {model.exportBackup(uri,model.pendingBackupPassword)}.exceptionOrNull()
                        model.pendingBackupPassword=""
                        Toast.makeText(context,if(error==null)"Backup cifrato esportato" else "Backup non riuscito: ${error.message}",Toast.LENGTH_LONG).show()
                    } else model.pendingBackupPassword=""
                }
                val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val error=runCatching {model.restoreBackup(uri,model.pendingRestorePassword)}.exceptionOrNull()
                        model.pendingRestorePassword=""
                        Toast.makeText(context,if(error==null)"Ripristino completato" else "Ripristino non riuscito: ${error.message}",Toast.LENGTH_LONG).show()
                    } else model.pendingRestorePassword=""
                }
                var showArDisclosure by remember {mutableStateOf(false)}
                val disclosurePrefs=remember(context) {context.getSharedPreferences("officina_legal_disclosures",android.content.Context.MODE_PRIVATE)}
                val launchAr: () -> Unit = {
                    arLauncher.launch(Intent(context,ARScanActivity::class.java).apply {
                        putExtra("site",model.ui.warehouse?.name)
                        putExtra("profileId",model.ui.profile?.id?.toString())
                    })
                }
                StockApp(model,onAr = {
                    if(disclosurePrefs.getBoolean("arcore_notice_ack_v1",false)) launchAr()
                    else showArDisclosure=true
                },onCsvImport={csvImportLauncher.launch(arrayOf("text/*","application/octet-stream"))},onCsv = {csvLauncher.launch("officina_stock_${model.ui.warehouse?.id ?: 0}.csv")},onManagerCsv={operationsCsvLauncher.launch("Ubicora_manager_${model.ui.warehouse?.id ?: 0}.csv")},onManagerPdf={operationsPdfLauncher.launch("Ubicora_manager_${model.ui.warehouse?.id ?: 0}.pdf")},
                onBackup={password ->
                    model.pendingBackupPassword=password
                    backupLauncher.launch("officina_stock_backup_${System.currentTimeMillis()}.osbackup")
                },onRestore={password ->
                    model.pendingRestorePassword=password
                    restoreLauncher.launch(arrayOf("*/*"))
                },onScan = {
                    qrLauncher.launch(ScanOptions()
                        .setCaptureActivity(StockQrCaptureActivity::class.java)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Inquadra il QR di un articolo o di una struttura")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false))
                })
                if(showArDisclosure) ArPrivacyNoticeDialog(
                    onProceed={
                        disclosurePrefs.edit().putBoolean("arcore_notice_ack_v1",true).apply()
                        showArDisclosure=false
                        launchAr()
                    },
                    onDismiss={showArDisclosure=false}
                )
            }
        }
    }
}

@Composable fun StockApp(model:StockViewModel,onAr:()->Unit,onCsv:()->Unit,onCsvImport:()->Unit,onManagerCsv:()->Unit,onManagerPdf:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,onScan:()->Unit) {
    val data=model.ui
    Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when {
            !data.initialized -> Column(Modifier.padding(24.dp)) {
                if(data.busy) CircularProgressIndicator()
                data.message?.let { Text(it);Button(onClick=model::refresh,enabled=!data.busy){Text("Riprova")} }
            }
            data.profiles.isEmpty() -> NewProfileScreen(model,first=true)
            data.profile==null || data.sessionLocked -> ProfileChooserScreen(model)
            data.warehouse==null -> WarehouseChooserScreen(model)
            else -> StockShell(model,onAr,onCsv,onCsvImport,onManagerCsv,onManagerPdf,onBackup,onRestore,onScan)
        }
    }
    if(data.busy && data.initialized) androidx.compose.ui.window.Dialog(
        onDismissRequest={},
        properties=androidx.compose.ui.window.DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)
    ) { Surface { Row(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator();Text("Operazione in corso…")
    } } }
}

enum class Tab(val label:String) { HOME("Panoramica"), STOCK("Articoli"), MAP("Mappa"), MOVES("Movimenti"), SETTINGS("Impostazioni"), MANAGER("Gestione") }
enum class StockFocus { ALL, CRITICAL, EMPTY, PRICED, UNLOCATED, IDLE_90 }
enum class ActivityFocus { ALL, INBOUND, OUTBOUND, LOANS }

@Composable fun StockShell(model:StockViewModel,onAr:()->Unit,onCsv:()->Unit,onCsvImport:()->Unit,onManagerCsv:()->Unit,onManagerPdf:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,onScan:()->Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var stockFocus by rememberSaveable { mutableStateOf(StockFocus.ALL) }
    var activityFocus by rememberSaveable { mutableStateOf(ActivityFocus.ALL) }
    var activityDays by rememberSaveable { mutableIntStateOf(0) }
    val data=model.ui
    fun openStock(focus:StockFocus) { stockFocus=focus; tab=Tab.STOCK }
    fun openActivity(focus:ActivityFocus,days:Int=0) { activityFocus=focus; activityDays=days; tab=Tab.MOVES }
    LaunchedEffect(data.scannedItemId) {
        if(data.scannedItemId!=null) tab=Tab.STOCK
    }
    // "Mostra in pianta", shelf QR and "Scegli sulla mappa" all open the map.
    LaunchedEffect(data.mapFocusPlacementId,data.locationPickItemIds) {
        if(data.mapFocusPlacementId!=null || data.locationPickItemIds!=null) tab=Tab.MAP
    }
    Scaffold(containerColor=Ink, floatingActionButton={
        if(tab==Tab.HOME || tab==Tab.STOCK) {
            ExtendedFloatingActionButton(
                onClick=onScan,
                icon={ Icon(Icons.Outlined.QrCodeScanner,contentDescription=null) },
                text={ Text("Scansiona QR") },
                containerColor=Mint,
                contentColor=Ink
            )
        }
    }, bottomBar={
        NavigationBar(containerColor=PanelBg) {
            Tab.entries.filter{it!=Tab.MANAGER}.forEach { item ->
                val icon=when(item){
                    Tab.HOME -> Icons.Outlined.Dashboard
                    Tab.STOCK -> Icons.Outlined.Inventory2
                    Tab.MAP -> Icons.Outlined.Map
                    Tab.MOVES -> Icons.Outlined.SyncAlt
                    Tab.SETTINGS -> Icons.Outlined.Settings
                    Tab.MANAGER -> Icons.Outlined.Dashboard
                }
                NavigationBarItem(selected=tab==item,onClick={tab=item},
                    icon={Icon(icon,contentDescription=item.label)},
                    label={Text(item.label,maxLines=1)},alwaysShowLabel=tab==item)
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(data.message!=null) {
                Surface(color=Danger.copy(alpha=.18f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(data.message,color=TextPrimary,modifier=Modifier.weight(1f))
                        TextButton(onClick=model::dismissMessage){Text("Chiudi")}
                    }
                }
            }
            when(tab) {
                Tab.HOME -> DashboardScreen(model,
                    goStock={openStock(StockFocus.ALL)},
                    goMap={tab=Tab.MAP},
                    goMoves={openActivity(ActivityFocus.ALL)},
                    goManager={tab=Tab.MANAGER},
                    onStockFocus={openStock(it)},
                    onActivityFocus={focus,days->openActivity(focus,days)},
                    onItemFocus={itemId->model.openItemDetails(itemId);tab=Tab.STOCK})
                Tab.STOCK -> InventoryScreen(model,onScan,stockFocus)
                Tab.MAP -> NativeMapScreen(model,onAr)
                Tab.MOVES -> MovementsScreen(model,activityFocus,activityDays)
                Tab.SETTINGS -> SettingsScreen(model,onCsv,onCsvImport,onBackup,onRestore)
                Tab.MANAGER -> OperationsScreen(model,onBack={tab=Tab.HOME},onCsvExport=onManagerCsv,onPdfExport=onManagerPdf)
            }
        }
    }
}
