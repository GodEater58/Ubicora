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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity: ComponentActivity() {
    private val model: StockViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
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
                var backupPassword by remember {mutableStateOf("")}
                var restorePassword by remember {mutableStateOf("")}
                val backupLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val error=runCatching {model.exportBackup(uri,backupPassword)}.exceptionOrNull()
                        backupPassword=""
                        Toast.makeText(context,if(error==null)"Backup cifrato esportato" else "Backup non riuscito: ${error.message}",Toast.LENGTH_LONG).show()
                    } else backupPassword=""
                }
                val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if(uri!=null) lifecycleScope.launch {
                        val error=runCatching {model.restoreBackup(uri,restorePassword)}.exceptionOrNull()
                        restorePassword=""
                        Toast.makeText(context,if(error==null)"Ripristino completato" else "Ripristino non riuscito: ${error.message}",Toast.LENGTH_LONG).show()
                    } else restorePassword=""
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
                },onCsv = {csvLauncher.launch("officina_stock_${model.ui.warehouse?.id ?: 0}.csv")},
                onBackup={password ->
                    backupPassword=password
                    backupLauncher.launch("officina_stock_backup_${System.currentTimeMillis()}.osbackup")
                },onRestore={password ->
                    restorePassword=password
                    restoreLauncher.launch(arrayOf("*/*"))
                },onScan = {
                    qrLauncher.launch(ScanOptions()
                        .setCaptureActivity(StockQrCaptureActivity::class.java)
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Inquadra il codice QR dell'articolo")
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

@Composable fun StockApp(model:StockViewModel,onAr:()->Unit,onCsv:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,onScan:()->Unit) {
    val data=model.ui
    Surface(color=Ink,modifier=Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        when {
            data.profiles.isEmpty() -> NewProfileScreen(model,first=true)
            data.profile==null -> ProfileChooserScreen(model)
            data.warehouse==null -> WarehouseChooserScreen(model)
            else -> StockShell(model,onAr,onCsv,onBackup,onRestore,onScan)
        }
    }
}

enum class Tab(val label:String) { HOME("Panoramica"), STOCK("Articoli"), MAP("Mappa"), MOVES("Movimenti"), SETTINGS("Impostazioni") }

@Composable fun StockShell(model:StockViewModel,onAr:()->Unit,onCsv:()->Unit,onBackup:(String)->Unit,onRestore:(String)->Unit,onScan:()->Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val data=model.ui
    LaunchedEffect(data.scannedItemId) {
        if(data.scannedItemId!=null) tab=Tab.STOCK
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
            Tab.entries.forEach { item ->
                val icon=when(item){
                    Tab.HOME -> Icons.Outlined.Dashboard
                    Tab.STOCK -> Icons.Outlined.Inventory2
                    Tab.MAP -> Icons.Outlined.Map
                    Tab.MOVES -> Icons.Outlined.SyncAlt
                    Tab.SETTINGS -> Icons.Outlined.Settings
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
                Tab.HOME -> DashboardScreen(model, goStock={tab=Tab.STOCK},goMap={tab=Tab.MAP},goMoves={tab=Tab.MOVES})
                Tab.STOCK -> InventoryScreen(model,onScan)
                Tab.MAP -> NativeMapScreen(model,onAr)
                Tab.MOVES -> MovementsScreen(model)
                Tab.SETTINGS -> SettingsScreen(model,onCsv,onBackup,onRestore)
            }
        }
    }
}
