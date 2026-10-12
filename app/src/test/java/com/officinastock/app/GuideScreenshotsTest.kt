package com.officinastock.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import android.net.Uri
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real screens with demo data for the user guide. Runs only when
 * UBICORA_GUIDE_SHOTS points to an output folder (the "Guide screenshots" workflow);
 * in normal CI it is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w392dp-h850dp-xhdpi")
class GuideScreenshotsTest {
    @get:Rule val compose=createComposeRule()
    private val out=System.getenv("UBICORA_GUIDE_SHOTS")?.let(::File)
    private val log=StringBuilder()

    private fun settle(model:StockViewModel) {
        repeat(400) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if(model.ui.initialized && !model.ui.busy) {
                compose.mainClock.advanceTimeBy(600)
                shadowOf(Looper.getMainLooper()).idle()
                compose.waitForIdle()
                return
            }
            Thread.sleep(25)
        }
        log.appendLine("settle timeout busy=${model.ui.busy}")
    }

    /** Every window on screen (activity, dialogs, popups), oldest first. */
    @Suppress("UNCHECKED_CAST")
    private fun windowViews():List<android.view.View> {
        val global=Class.forName("android.view.WindowManagerGlobal")
        val instance=global.getMethod("getInstance").invoke(null)
        val field=global.getDeclaredField("mViews").apply { isAccessible=true }
        return (field.get(instance) as List<android.view.View>).filter { it.isAttachedToWindow && it.width>0 && it.height>0 }
    }

    /** Draws the windows in stacking order on one bitmap; compose's captureToImage does not work here. */
    private fun save(name:String) {
        val views=windowViews()
        val base=views.first()
        val bitmap=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap)
        views.forEachIndexed { index,v ->
            val lp=v.layoutParams as? android.view.WindowManager.LayoutParams
            val full=index==0 || lp==null || lp.width==android.view.ViewGroup.LayoutParams.MATCH_PARENT
            if(index>0 && lp!=null && (lp.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)!=0)
                canvas.drawColor(android.graphics.Color.argb((lp.dimAmount*255).toInt(),0,0,0))
            canvas.save()
            if(!full) canvas.translate(lp!!.x.toFloat(),lp.y.toFloat())
            else if(index>0) canvas.translate(((base.width-v.width)/2).toFloat(),((base.height-v.height)/2).toFloat())
            v.draw(canvas)
            canvas.restore()
        }
        File(out,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        log.appendLine("ok $name ${bitmap.width}x${bitmap.height} windows=${views.size}")
    }

    private fun shot(name:String,model:StockViewModel,dialog:Boolean=false,before:()->Unit={}) {
        try {
            before()
            settle(model)
            save(name)
        } catch(e:Throwable) {
            log.appendLine("FAIL $name: ${e::class.simpleName}: ${e.message?.take(400)}")
        }
    }

    @Test fun renderGuideScreens() {
        Assume.assumeTrue(out!=null)
        out!!.mkdirs()
        val app=RuntimeEnvironment.getApplication() as Application
        app.deleteDatabase("officina_stock_native4.db")
        val model=StockViewModel(app)
        compose.setContent {
            StockTheme { StockApp(model,{},{},{},{},{},{},{},{}) }
        }
        try {
            shot("01_nuovo_profilo",model)
            model.createProfile("Officina","1234"); settle(model)
            shot("02_magazzino",model)
            model.createWarehouse("Magazzino officina","Rovigo"); settle(model)
            model.seedDemo(); settle(model)
            shot("03_panoramica",model)

            shot("04_articoli",model) { compose.onNodeWithContentDescription("Articoli").performClick() }
            shot("05_ordina",model) { compose.onNodeWithText("Nome A–Z").performClick() }
            runCatching { compose.onNodeWithText("Critici prima").performClick() }; settle(model)
            shot("06_critici_prima",model)
            // The undo bar lives 6 s: freeze the test clock so it is still there when captured.
            try {
                compose.mainClock.autoAdvance=false
                model.quickMovement(model.ui.items.first().id,1.0)
                for(i in 0 until 400) {
                    shadowOf(Looper.getMainLooper()).idle()
                    if(model.ui.movementUndo!=null && !model.ui.busy) break
                    Thread.sleep(20)
                }
                log.appendLine("undo=${model.ui.movementUndo?.label}")
                repeat(6) { compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idle() }
                save("07_annulla")
            } catch(e:Throwable) { log.appendLine("FAIL 07_annulla: ${e.message?.take(300)}") }
            finally { compose.mainClock.autoAdvance=true }
            val pictured=model.ui.items.firstOrNull { it.photoFile!=null && it.placementId!=null } ?: model.ui.items.first()
            shot("08_scheda_articolo",model,dialog=true) { model.openItemDetails(pictured.id) }
            runCatching { compose.onNodeWithContentDescription("Chiudi").performClick() }; settle(model)
            shot("09_nuovo_articolo",model,dialog=true) { compose.onNodeWithContentDescription("Nuovo articolo").performClick() }
            runCatching { compose.onNodeWithText("Annulla").performClick() }; settle(model)

            shot("10_mappa",model) { compose.onNodeWithContentDescription("Mappa").performClick() }
            pictured.placementId?.let { id -> shot("11_mappa_struttura",model) { model.showOnMap(id) } }
            shot("12_mappa_strutture",model) { compose.onNodeWithText("Strutture").performClick() }
            shot("13_mappa_pareti",model) { compose.onNodeWithText("Pareti").performClick() }
            runCatching { compose.onNodeWithText("Esplora").performClick() }; settle(model)

            shot("14_movimenti",model) { compose.onNodeWithContentDescription("Movimenti").performClick() }
            shot("15_impostazioni",model) { compose.onNodeWithContentDescription("Impostazioni").performClick(); compose.mainClock.advanceTimeBy(1500) }
            model.changeManagerMode(true); settle(model)
            shot("16_panoramica_aziendale",model) { compose.onNodeWithContentDescription("Panoramica").performClick() }
            shot("17_centro_operativo",model) {
                compose.onNodeWithText("Apri gestione aziendale").performScrollTo().performClick()
            }

            // ---------------- 4.7.5 import Excel/CSV, 4.8-4.9 sync and departments
            model.changeManagerMode(false); settle(model)
            compose.onNodeWithContentDescription("Impostazioni").performClick(); settle(model)
            fun scrollTo(text:String) = runCatching {
                compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
            }.onFailure { log.appendLine("scroll $text: ${it.message?.take(200)}") }
            val csv=File(app.cacheDir,"anagrafica.csv")
            csv.writeText(CsvImport.header.joinToString(";")+"\n"+
                ";Fusibile 10A;Elettrico;20;5;pz;;;;;;;\n;Raccordo rapido 1/4;Pneumatica;12;4;pz;;;;;;;\n;Cuscinetto 6204;Meccanica;8;2;pz;;;;;;;\n")
            shot("18_import_anteprima",model) { model.prepareCsvImport(Uri.fromFile(csv)) }
            model.cancelCsvImport(); settle(model)
            scrollTo("SINCRONIZZAZIONE TRA TELEFONI"); settle(model)
            shot("19_sync_inizio",model)
            model.becomeTeamCentral("squadra-2026"); settle(model)
            model.setTeamScope("B",listOf("Meccanica","Pneumatica")); settle(model)
            model.setTeamScope("C",listOf("Elettrico")); settle(model)
            scrollTo("SINCRONIZZAZIONE TRA TELEFONI"); settle(model)
            shot("20_sync_centrale",model)
            scrollTo("REPARTI DEGLI OPERATORI"); settle(model)
            shot("21_reparti",model)
            shot("22_reparto_modifica",model,dialog=true) { compose.onNodeWithTag("scope_B").performClick() }
            runCatching { compose.onNodeWithText("Annulla").performClick() }; settle(model)
            // Network screens: infinite animations, so the clock is driven by hand.
            fun linkShot(name:String,state:LinkUi) = try {
                compose.mainClock.autoAdvance=false
                model.link.preview(state)
                repeat(4) { compose.mainClock.advanceTimeBy(350); shadowOf(Looper.getMainLooper()).idle() }
                save(name)
            } catch(e:Throwable) { log.appendLine("FAIL $name: ${e.message?.take(300)}") }
            linkShot("23_rete_centrale",LinkUi(LinkPhase.CENTRAL,peers=listOf(
                LinkPeer("1","Satellite B",PeerPhase.DONE,"4 movimenti, 1 articoli nuovi, 0 spostamenti"),
                LinkPeer("2","Satellite C",PeerPhase.APPLYING,"Unisco i dati…")),
                message="Rete aperta: i satelliti con Ubicora aperta si collegano da soli"))
            linkShot("24_satellite_cerca",LinkUi(LinkPhase.SEARCHING,message="Cerco il telefono centrale… tienilo vicino con la rete aperta"))
            linkShot("25_satellite_scambio",LinkUi(LinkPhase.TRANSFER,central="Magazzino officina",message="Invio le mie modifiche…"))
            linkShot("26_satellite_fatto",LinkUi(LinkPhase.DONE,central="Magazzino officina",
                message="Sincronizzato: 42 articoli aggiornati · tutto confermato dal centrale"))
            linkShot("27_errore",LinkUi(LinkPhase.ERROR,message="Fuori dal tuo reparto: qui puoi solo prelevare. Per il resto chiedi al telefono centrale"))
            model.link.preview(LinkUi()); repeat(3) { compose.mainClock.advanceTimeBy(300); shadowOf(Looper.getMainLooper()).idle() }
            compose.mainClock.autoAdvance=true; settle(model)

            // ---------------- 4.10 quality of life
            scrollTo("QR per collegare un nuovo telefono"); settle(model)
            shot("36_qr_invito",model,dialog=true) { compose.onNodeWithText("QR per collegare un nuovo telefono").performClick() }
            runCatching { compose.onAllNodesWithText("Chiudi").onLast().performClick() }.onFailure { log.appendLine("close qr: ${it.message?.take(150)}") }; settle(model)
            scrollTo("VISUALIZZAZIONE"); settle(model)
            shot("33_visualizzazione",model)
            shot("29_panoramica_sync",model) { compose.onNodeWithContentDescription("Panoramica").performClick() }
            runCatching { compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText("Condividi lista da ordinare",substring=true)) }
            settle(model)
            shot("30_lista_ordine",model)
            model.ui.items.sortedBy { it.name }.take(2).forEach { UiPrefs.toggleFavorite(it.warehouseId,it.id) }
            shot("31_preferiti",model) { compose.onNodeWithContentDescription("Articoli").performClick() }
            // Burst picking: the scanner is not available here, the scanned label is passed directly.
            val burstItem=model.ui.items.first { it.quantity>=3.0 }
            shot("32_raffica",model,dialog=true) {
                model.startBurst(); model.consumeScanRequest()
                model.handleScannedCode(ArticleCodes.payload(burstItem.warehouseId,burstItem.sku))
            }
            model.endBurst(); settle(model)
            UiPrefs.updateLight(true)
            shot("34_tema_chiaro",model) { compose.onNodeWithContentDescription("Panoramica").performClick() }
            UiPrefs.updateGlove(true)
            shot("35_modalita_guanti",model) { compose.onNodeWithContentDescription("Articoli").performClick() }
            UiPrefs.updateGlove(false); UiPrefs.updateLight(false); settle(model)

            // ---------------- 4.7.4 unit of measure menu
            compose.onAllNodesWithContentDescription("Articoli",useUnmergedTree=true).onFirst().performClick(); settle(model)
            shot("28_unita_misura",model,dialog=true) {
                compose.onNodeWithContentDescription("Nuovo articolo").performClick(); settle(model)
                compose.onNodeWithText("Elettrico").performClick(); settle(model)
                compose.onNode(SemanticsMatcher("unit picker") { it.config.getOrNull(SemanticsActions.OnClick)?.label=="Scegli l'unità di misura" })
                    .performScrollTo().performClick()
            }
            // 4.10 join dialogs, last: each step logged to tell an app problem from a test one.
            repeat(2) { runCatching { compose.onAllNodesWithText("Annulla").onLast().performClick() }; runCatching { settle(model) } }
            try {
                model.leaveTeam(); settle(model)
                compose.onAllNodesWithContentDescription("Impostazioni",useUnmergedTree=true).onFirst().performClick(); settle(model)
                scrollTo("SINCRONIZZAZIONE TRA TELEFONI"); settle(model)
                compose.onNodeWithText("Collega a un telefono centrale").performClick(); settle(model)
                save("38_collega_manuale"); log.appendLine("diag A ok: manual join dialog settles")
                compose.onAllNodesWithText("Annulla").onLast().performClick(); settle(model)
            } catch(e:Throwable) { log.appendLine("diag A FAIL: ${e::class.simpleName}: ${e.message?.take(200)}") }
            try {
                model.handleScannedCode(TeamQr.encode("squadra-2026",listOf("B"),"Magazzino officina"))
                settle(model)
                log.appendLine("teamQr after=${model.ui.teamQr} role=${model.ui.team.role}")
                save("37_collega_qr"); log.appendLine("diag B ok: QR join dialog settles")
            } catch(e:Throwable) { log.appendLine("diag B FAIL: ${e::class.simpleName}: ${e.message?.take(200)}") }
        } finally {
            File(out,"log.txt").writeText(log.toString())
        }
    }
}
