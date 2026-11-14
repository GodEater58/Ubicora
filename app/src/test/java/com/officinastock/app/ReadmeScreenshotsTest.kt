package com.officinastock.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
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
 * Renders the real screens with the demo warehouse for the README and the guide.
 * Runs only when UBICORA_SCREENSHOTS points to an output folder (workflow "Schermate");
 * in normal CI it is skipped. Generic demo names only: no real company or person.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34], qualifiers="w392dp-h850dp-xhdpi")
class ReadmeScreenshotsTest {
    @get:Rule val compose=createComposeRule()
    private val out=System.getenv("UBICORA_SCREENSHOTS")?.let(::File)
    private val log=StringBuilder()

    private fun idleOnce() { shadowOf(Looper.getMainLooper()).idle() }

    /** Waits for the model; with the clock frozen it only advances by hand (screens with endless animations). */
    private fun settle(model:StockViewModel,frozen:Boolean=false) {
        repeat(400) {
            idleOnce()
            if(frozen) compose.mainClock.advanceTimeByFrame() else compose.waitForIdle()
            if(model.ui.initialized && !model.ui.busy) {
                if(frozen) repeat(12) { compose.mainClock.advanceTimeBy(100); idleOnce() }
                else { compose.mainClock.advanceTimeBy(800); idleOnce(); compose.waitForIdle() }
                return
            }
            Thread.sleep(25)
        }
        log.appendLine("settle timeout busy=${model.ui.busy}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowViews():List<android.view.View> {
        val global=Class.forName("android.view.WindowManagerGlobal")
        val instance=global.getMethod("getInstance").invoke(null)
        val field=global.getDeclaredField("mViews").apply { isAccessible=true }
        return (field.get(instance) as List<android.view.View>).filter { it.isAttachedToWindow && it.width>0 && it.height>0 }
    }

    /** Draws every window (activity, dialogs, popups) in stacking order on one bitmap. */
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

    private fun shot(name:String,model:StockViewModel,frozen:Boolean=false,before:()->Unit={}) {
        try {
            if(frozen) compose.mainClock.autoAdvance=false
            before()
            settle(model,frozen)
            save(name)
        } catch(e:Throwable) {
            log.appendLine("FAIL $name: ${e::class.simpleName}: ${e.message?.take(400)}")
        } finally {
            compose.mainClock.autoAdvance=true
        }
    }

    private fun tab(label:String) = compose.onAllNodesWithContentDescription(label,useUnmergedTree=true).onFirst().performClick()

    @Test fun renderScreens() {
        Assume.assumeTrue(out!=null)
        out!!.mkdirs()
        val app=RuntimeEnvironment.getApplication() as Application
        app.deleteDatabase("officina_stock_native4.db")
        val model=StockViewModel(app)
        compose.setContent {
            StockTheme { StockApp(model,{},{},{},{},{},{_->},{_->}) }
        }
        try {
            settle(model)
            model.createProfile("Azienda demo","1234"); settle(model)
            model.createWarehouse("Magazzino principale","Sede 1"); settle(model)
            model.seedDemo(); settle(model)
            log.appendLine("items=${model.ui.items.size} placements=${model.ui.placements.size} message=${model.ui.message}")
            model.dismissMessage()

            shot("oggi",model)
            shot("articoli",model) { tab("Articoli") }
            val located=model.ui.items.filter { it.placementId!=null }
            val pictured=located.firstOrNull { it.photoFile!=null } ?: located.firstOrNull() ?: model.ui.items.first()
            shot("scheda_articolo",model) { model.openItemDetails(pictured.id) }
            runCatching { compose.onAllNodesWithContentDescription("Chiudi").onFirst().performClick() }
            model.acknowledgeScannedItem(); settle(model)

            shot("mappa",model,frozen=true) { tab("Mappa") }
            shot("mappa_dove",model,frozen=true) { model.showItemOnMap(pictured.id) }
            model.consumeMapFind(); settle(model,frozen=true)
            // The shelf holding the most articles, seen from the front.
            val shelfId=located.groupBy { it.placementId!! }.maxByOrNull { it.value.size }?.key
            if(shelfId!=null) shot("scaffale",model,frozen=true) { model.openShelf(shelfId) }
            model.closeShelf(); settle(model)

            // Burst picking: there is no camera here, the scanned label is passed directly.
            val burstItem=model.ui.items.firstOrNull { it.quantity>=3.0 }
            if(burstItem!=null) shot("prelievo_raffica",model,frozen=true) {
                model.startBurst(); model.consumeScanRequest()
                model.handleScannedCode(ArticleCodes.payload(burstItem.warehouseId,burstItem.sku))
            }
            model.endBurst(); settle(model)

            model.becomeTeamCentral("squadra-demo-2026"); settle(model)
            log.appendLine("team role=${model.ui.team.role} message=${model.ui.message}")
            model.dismissMessage()
            shot("squadra",model,frozen=true) { tab("Squadra") }
            shot("rete_centrale",model,frozen=true) {
                model.link.previewForScreenshots(LinkUi(LinkPhase.CENTRAL,peers=listOf(
                    LinkPeer("1","Satellite B",PeerPhase.DONE,"4 movimenti, 1 articoli nuovi, 0 spostamenti"),
                    LinkPeer("2","Satellite C",PeerPhase.APPLYING,"Unisco i dati…")),
                    message="Rete aperta: i satelliti con Ubicora aperta si collegano da soli"))
            }
            model.link.previewForScreenshots(LinkUi()); settle(model,frozen=true)

            UiPrefs.updateLight(true)
            shot("tema_chiaro",model) { tab("Oggi") }
            shot("articoli_chiaro",model) { tab("Articoli") }
            shot("mappa_chiaro",model,frozen=true) { tab("Mappa") }
            UiPrefs.updateLight(false); settle(model,frozen=true)
        } finally {
            File(out,"log.txt").writeText(log.toString())
        }
    }
}
