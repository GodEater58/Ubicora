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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

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
    // 4.15: while the app is in front, an NFC tag touched with the phone is read (or written) here.
    override fun onResume() {
        super.onResume()
        runCatching {
            android.nfc.NfcAdapter.getDefaultAdapter(this)?.enableReaderMode(this,{ tag -> onNfcTag(tag) },
                android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B or
                    android.nfc.NfcAdapter.FLAG_READER_NFC_F or android.nfc.NfcAdapter.FLAG_READER_NFC_V,null)
        }
    }
    override fun onPause() {
        runCatching { android.nfc.NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this) }
        super.onPause()
    }
    /** Called on an NFC thread: tag access happens here, the model is touched on the main thread. */
    private fun onNfcTag(tag:android.nfc.Tag) {
        val toWrite=model.nfcWritePayload
        if(toWrite!=null) {
            val failure=runCatching { NfcLabels.write(tag,toWrite) }.exceptionOrNull()
            runOnUiThread { model.nfcWritten(toWrite,failure?.let { it.message ?: "Scrittura non riuscita: riavvicina il tag e tienilo fermo" }) }
        } else {
            val text=runCatching { NfcLabels.read(tag) }.getOrNull()
            runOnUiThread { model.handleNfcText(text) }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        externalActivityPending=savedInstanceState?.getBoolean("externalActivityPending") ?: false
        UiPrefs.init(this)
        // Screens are protected unless the user allows screenshots (Impostazioni › Visualizzazione).
        if(!UiPrefs.screenshots) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            StockTheme {
                val context=LocalContext.current
                // 4.10.1: status and navigation bars follow the light/dark theme.
                val lightTheme=UiPrefs.light
                LaunchedEffect(lightTheme) {
                    val bar=if(lightTheme) 0xFFF2F6F5.toInt() else 0xFF081320.toInt()
                    @Suppress("DEPRECATION") run { window.statusBarColor=bar; window.navigationBarColor=bar }
                    window.decorView.setBackgroundColor(bar)   // behind the bars on edge-to-edge Android 15+
                    androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                        isAppearanceLightStatusBars=lightTheme; isAppearanceLightNavigationBars=lightTheme
                    }
                }
                val allowShots=UiPrefs.screenshots
                LaunchedEffect(allowShots) {
                    if(allowShots) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    else window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
                val arLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if(result.resultCode==RESULT_OK) {
                        result.data?.getStringExtra("map_json")?.let{model.importAr(it)}
                    }
                }
                val qrLauncher=rememberLauncherForActivityResult(ScanContract()) { result ->
                    result.contents?.let { model.handleScannedCode(it) } ?: model.scanCancelled()
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
                        .setPrompt(if(model.ui.burstMode) "Prelievo a raffica: inquadra il prossimo articolo · Indietro per finire"
                            else "Inquadra il QR di un articolo, di una struttura o del telefono centrale")
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
      Box(Modifier.fillMaxSize()) {
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
        if(data.profile!=null && !data.sessionLocked) TeamLinkBanner(model,Modifier.align(Alignment.TopCenter))
        if(data.profile!=null && !data.sessionLocked && data.warehouse!=null && data.burstMode) BurstScanScreen(model)
        if(data.profile!=null && !data.sessionLocked && data.warehouse!=null && data.shelfCheck!=null) ShelfCheckScreen(model)
      }
    }
    // 4.13: automatic exchange with the nearby team phones while the app is open (on unlock and every 10 minutes).
    LaunchedEffect(data.sessionLocked,data.team.role,data.team.warehouseId,data.warehouse?.id,UiPrefs.autoSync) {
        if(data.sessionLocked || data.warehouse==null || data.team.role==TeamRole.NONE || !UiPrefs.autoSync) return@LaunchedEffect
        while(true) { model.autoLink(); kotlinx.coroutines.delay(10*60_000L) }
    }
    // 4.10: screens ask for the scanner through the model (burst picking, team QR).
    LaunchedEffect(data.scanRequest) {
        // 4.15: burst picking has its own screen with the camera inside, so it never opens the separate scanner.
        if(data.scanRequest!=0L) { val burst=data.burstMode; model.consumeScanRequest(); if(!burst) onScan() }
    }
    if(data.profile!=null && !data.sessionLocked) TeamLinkDialog(model)
    // 4.15: waiting for a tag to write the label of an article on.
    data.nfcWrite?.let { label ->
        AlertDialog(onDismissRequest=model::cancelNfcWrite,title={Text("Avvicina il tag NFC")},
            text={Text("Appoggia il tag sul retro del telefono e tienilo fermo un secondo. Verrà scritta l'etichetta di: $label. Quello che il tag conteneva viene sostituito.")},
            confirmButton={TextButton(onClick=model::cancelNfcWrite){Text("Annulla")}})
    }
    if(data.busy && data.initialized) androidx.compose.ui.window.Dialog(
        onDismissRequest={},
        properties=androidx.compose.ui.window.DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)
    ) { Surface { Row(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator();Text("Operazione in corso…")
    } } }
}

// 4.15: the bottom bar shows Oggi, Articoli, the scanner, Mappa and Squadra; the other sections open from inside the screens.
enum class Tab(val label:String) { HOME("Oggi"), STOCK("Articoli"), MAP("Mappa"), MOVES("Movimenti"), SETTINGS("Impostazioni"), MANAGER("Gestione"), TEAM("Squadra") }
enum class StockFocus { ALL, CRITICAL, EMPTY, PRICED, UNLOCATED, IDLE_90, FAVORITES }
enum class ActivityFocus { ALL, INBOUND, OUTBOUND, LOANS }

@Composable fun StockShell(model:StockViewModel,onAr:()->Unit,onCsv:()->Unit,onCsvImport:()->Unit,onManagerCsv:()->Unit,onManagerPdf:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,onScan:()->Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // 4.7.2: Back returns to the previous section instead of leaving the app.
    val history=remember { mutableStateListOf<Tab>() }
    fun go(next:Tab) {
        if(next==tab) return
        history.remove(next)
        history.add(tab)
        if(history.size>12) history.removeAt(0)
        tab=next
    }
    androidx.activity.compose.BackHandler(enabled=history.isNotEmpty() || tab!=Tab.HOME) {
        tab=if(history.isNotEmpty()) history.removeAt(history.lastIndex) else Tab.HOME
    }
    var stockFocus by rememberSaveable { mutableStateOf(StockFocus.ALL) }
    var activityFocus by rememberSaveable { mutableStateOf(ActivityFocus.ALL) }
    var activityDays by rememberSaveable { mutableIntStateOf(0) }
    val data=model.ui
    fun openStock(focus:StockFocus) { stockFocus=focus; go(Tab.STOCK) }
    fun openActivity(focus:ActivityFocus,days:Int=0) { activityFocus=focus; activityDays=days; go(Tab.MOVES) }
    LaunchedEffect(data.scannedItemId) {
        if(data.scannedItemId!=null) go(Tab.STOCK)
    }
    LaunchedEffect(data.teamQr) { if(data.teamQr!=null) go(Tab.TEAM) }
    // "Mostra in pianta", shelf QR and "Scegli sulla mappa" all open the map.
    LaunchedEffect(data.mapFocusPlacementId,data.locationPickItemIds,data.guidedMapRequest) {
        if(data.mapFocusPlacementId!=null || data.locationPickItemIds!=null || data.guidedMapRequest!=0L) { go(Tab.MAP); model.consumeGuidedMap() }
    }
    // 4.15: no floating button over the lists; the scanner sits in the middle of the bottom bar.
    Scaffold(containerColor=Ink, bottomBar={
        NavigationBar(containerColor=PanelBg) {
            listOf(Tab.HOME,Tab.STOCK,Tab.MAP,Tab.TEAM).forEach { item ->
                val icon=when(item){
                    Tab.HOME -> Icons.Outlined.Dashboard
                    Tab.STOCK -> Icons.Outlined.Inventory2
                    Tab.MAP -> Icons.Outlined.Map
                    Tab.MOVES -> Icons.Outlined.SyncAlt
                    Tab.SETTINGS -> Icons.Outlined.Settings
                    Tab.MANAGER -> Icons.Outlined.Dashboard
                    Tab.TEAM -> Icons.Outlined.SyncAlt
                }
                // 4.15: Movimenti is a page of Articoli.
                NavigationBarItem(selected=tab==item || (item==Tab.STOCK && tab==Tab.MOVES),onClick={go(item)},
                    icon={Icon(icon,contentDescription=item.label)},
                    label={Text(item.label,maxLines=1)},alwaysShowLabel=true)
                if(item==Tab.STOCK) NavigationBarItem(selected=false,onClick=onScan,
                    icon={ Surface(color=Mint,shape=androidx.compose.foundation.shape.CircleShape) {
                        Icon(Icons.Outlined.QrCodeScanner,contentDescription="Scansiona QR",tint=OnMint,modifier=Modifier.padding(14.dp).size(28.dp)) } })
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
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when(tab) {
                    Tab.HOME -> DashboardScreen(model,
                        goStock={openStock(StockFocus.ALL)},
                        goMap={go(Tab.MAP)},
                        goMoves={openActivity(ActivityFocus.ALL)},
                        goManager={go(Tab.MANAGER)},
                        goSettings={go(Tab.SETTINGS)},
                        goTeam={go(Tab.TEAM)},
                        onStockFocus={openStock(it)},
                        onActivityFocus={focus,days->openActivity(focus,days)},
                        onItemFocus={itemId->model.openItemDetails(itemId);go(Tab.STOCK)})
                    Tab.STOCK -> Column(Modifier.fillMaxSize()) {
                        StockSections(movements=false,onSelect={ if(it) go(Tab.MOVES) },goSettings={go(Tab.SETTINGS)})
                        InventoryScreen(model,onScan,stockFocus)
                    }
                    Tab.MAP -> NativeMapScreen(model,onAr,onSettings={go(Tab.SETTINGS)})
                    Tab.MOVES -> Column(Modifier.fillMaxSize()) {
                        StockSections(movements=true,onSelect={ if(!it) go(Tab.STOCK) },goSettings={go(Tab.SETTINGS)})
                        MovementsScreen(model,activityFocus,activityDays)
                    }
                    Tab.SETTINGS -> SettingsScreen(model,onCsv,onCsvImport,onBackup,onRestore)
                    Tab.TEAM -> TeamScreen(model,goSettings={go(Tab.SETTINGS)})
                    Tab.MANAGER -> OperationsScreen(model,onBack={go(Tab.HOME)},onCsvExport=onManagerCsv,onPdfExport=onManagerPdf)
                }
            }
            GuidedPickBar(model,onMapTab=tab==Tab.MAP)
            data.movementUndo?.let { undo ->
                // 4.15: at the bottom, next to the thumb. Visible for 6 seconds; disappears earlier if anything else changes the archive.
                LaunchedEffect(undo.token) { kotlinx.coroutines.delay(6000); model.dismissMovementUndo(undo.token) }
                Surface(color=Surface2) {
                    Row(Modifier.fillMaxWidth().padding(start=14.dp,end=6.dp,top=2.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(undo.label,color=TextPrimary,fontSize=13.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                        TextButton(onClick=model::undoMovement,enabled=!data.busy){Text("Annulla",color=Mint,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)}
                    }
                }
            }
        }
    }
}
