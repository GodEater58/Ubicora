package com.officinastock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.FileOutputStream

/** One printable label: QR payload plus up to three text lines. */
data class LabelSpec(val payload: String, val title: String, val subtitle: String, val caption: String) {
    companion object {
        fun item(item: StockItem, location: String = item.category) =
            LabelSpec(ArticleCodes.payload(item.warehouseId,item.sku),item.sku,item.name,location)
        fun placement(p: Placement) = LabelSpec(ArticleCodes.locationPayload(p.warehouseId,p.id,p.uid),p.label,
            if(p.hasPositions) "${p.kind} · ${p.levels} ripiani × ${p.slots} posti" else p.kind,"UBICAZIONE")
    }
}

/** Il QR contiene soltanto un identificatore locale, nessun dato personale. */
object QrLabels {
    fun bitmap(item: StockItem, size: Int = 480): Bitmap = bitmap(ArticleCodes.payload(item.warehouseId,item.sku),size)
    fun bitmap(value: String, size: Int = 480): Bitmap {
        val matrix=MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE,size,size,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8",
                  EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                  EncodeHintType.MARGIN to 2))
        val pixels=IntArray(size*size)
        for(y in 0 until size) for(x in 0 until size)
            pixels[y*size+x]=if(matrix[x,y]) AndroidColor.BLACK else AndroidColor.WHITE
        return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
    }

    fun print(context: Context, item: StockItem) {
        val manager=context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val paper=PrintAttributes.MediaSize("officina_60_40","Etichetta 60 × 40 mm",2362,1575)
        val attributes=PrintAttributes.Builder().setMediaSize(paper)
            .setResolution(PrintAttributes.Resolution("print_300","300 dpi",300,300))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        manager.print("Ubicora ${item.sku}", LabelDocumentAdapter(LabelSpec.item(item)), attributes)
    }
    fun print(context: Context, label: LabelSpec) {
        val manager=context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val paper=PrintAttributes.MediaSize("officina_60_40","Etichetta 60 × 40 mm",2362,1575)
        val attributes=PrintAttributes.Builder().setMediaSize(paper)
            .setResolution(PrintAttributes.Resolution("print_300","300 dpi",300,300))
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        manager.print("Ubicora ${label.title}", LabelDocumentAdapter(label), attributes)
    }
    /** A4 sheet, 3 × 8 labels per page (about 70 × 37 mm), several pages if needed. */
    fun printSheet(context: Context, title: String, labels: List<LabelSpec>) {
        require(labels.isNotEmpty()) { "Nessuna etichetta da stampare" }
        require(labels.size<=2400) { "Massimo 2400 etichette per stampa" }
        val manager=context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes=PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build()
        manager.print("Ubicora · $title", LabelSheetAdapter(title,labels), attributes)
    }
    /** Fits [text] into [maxWidth] points with an ellipsis. */
    internal fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if(paint.measureText(text)<=maxWidth) return text
        var cut=text.length
        while(cut>1 && paint.measureText(text.take(cut)+"…")>maxWidth) cut--
        return text.take(cut)+"…"
    }
}

private class LabelSheetAdapter(private val title: String, private val labels: List<LabelSpec>): PrintDocumentAdapter() {
    private val perPage=24
    private val pages get() = (labels.size+perPage-1)/perPage
    override fun onLayout(oldAttributes: PrintAttributes?,newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,callback: LayoutResultCallback,extras: Bundle?) {
        if(cancellationSignal.isCanceled) {callback.onLayoutCancelled();return}
        callback.onLayoutFinished(PrintDocumentInfo.Builder("Ubicora_etichette.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),true)
    }
    override fun onWrite(pageRanges: Array<out android.print.PageRange>, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        val document=PdfDocument()
        try {
            val cellW=595f/3f;val cellH=(842f-36f)/8f
            val bold=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=AndroidColor.BLACK;typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);textSize=10f}
            val text=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=AndroidColor.BLACK;textSize=8f}
            val small=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=AndroidColor.DKGRAY;textSize=6.5f}
            val guide=Paint().apply{color=AndroidColor.LTGRAY;style=Paint.Style.STROKE;strokeWidth=.3f}
            for(pageIndex in 0 until pages) {
                if(cancellationSignal.isCanceled){callback.onWriteCancelled();return}
                val page=document.startPage(PdfDocument.PageInfo.Builder(595,842,pageIndex+1).create())
                val canvas=page.canvas
                canvas.drawColor(AndroidColor.WHITE)
                labels.drop(pageIndex*perPage).take(perPage).forEachIndexed { i, label ->
                    val left=(i%3)*cellW;val top=18f+(i/3)*cellH
                    canvas.drawRect(left+2f,top+2f,left+cellW-2f,top+cellH-2f,guide)
                    val qrSize=cellH-14f
                    val qr=QrLabels.bitmap(label.payload,300)
                    canvas.drawBitmap(qr,null,android.graphics.RectF(left+6f,top+7f,left+6f+qrSize,top+7f+qrSize),Paint(Paint.FILTER_BITMAP_FLAG))
                    qr.recycle()
                    val tx=left+12f+qrSize;val maxW=cellW-qrSize-20f
                    canvas.drawText(QrLabels.fit(bold,label.title,maxW),tx,top+26f,bold)
                    canvas.drawText(QrLabels.fit(text,label.subtitle,maxW),tx,top+44f,text)
                    canvas.drawText(QrLabels.fit(small,label.caption,maxW),tx,top+60f,small)
                    canvas.drawText("Ubicora",tx,top+cellH-12f,small)
                }
                document.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use {document.writeTo(it)}
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch(e:Exception) {
            callback.onWriteFailed(e.localizedMessage ?: "Errore nella stampa")
        } finally {document.close()}
    }
}

/** A4 non richiesto: etichetta singola 60 × 40 mm tramite il servizio di stampa Android. */
private class LabelDocumentAdapter(private val item: LabelSpec): PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?,newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,callback: LayoutResultCallback,extras: Bundle?) {
        if(cancellationSignal.isCanceled) {callback.onLayoutCancelled();return}
        callback.onLayoutFinished(PrintDocumentInfo.Builder("${item.title}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),true)
    }
    override fun onWrite(pages: Array<out android.print.PageRange>, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
        val document=PdfDocument()
        try {
            if(cancellationSignal.isCanceled){callback.onWriteCancelled();return}
            // PDF dimensions expressed in points (1/72 inch), independent from screen density.
            val page=document.startPage(PdfDocument.PageInfo.Builder(170,113,1).create())
            val canvas=page.canvas
            canvas.drawColor(AndroidColor.WHITE)
            val qr=QrLabels.bitmap(item.payload,360)
            val dest=android.graphics.Rect(8,11,99,102)
            canvas.drawBitmap(qr,null,dest,Paint(Paint.FILTER_BITMAP_FLAG))
            qr.recycle()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=AndroidColor.BLACK}
            paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
            paint.textSize=10f
            if (paint.measureText(item.title) > 65f) paint.textSize=8f
            canvas.drawText(QrLabels.fit(paint,item.title,66f),101f,29f,paint)
            paint.typeface=Typeface.DEFAULT
            paint.textSize=8f
            // Ellissi per evitare testo fuori dall'etichetta.
            canvas.drawText(QrLabels.fit(paint,item.subtitle,66f),101f,48f,paint)
            paint.textSize=6.5f
            canvas.drawText(QrLabels.fit(paint,item.caption,66f),101f,61f,paint)
            canvas.drawText("Ubicora",101f,90f,paint)
            document.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use {document.writeTo(it)}
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch(e:Exception) {
            callback.onWriteFailed(e.localizedMessage ?: "Errore nella stampa")
        } finally {document.close()}
    }
}

/** [onAnother]: shown right after a new article is saved, to load the next one at once (4.24). */
@Composable fun QrLabelDialog(item: StockItem,onDismiss:()->Unit,onAnother:(()->Unit)?=null) {
    val context=LocalContext.current
    val image=remember(item.id,item.sku) { QrLabels.bitmap(item) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(onAnother!=null) "Salvato: ecco l'etichetta" else "Etichetta articolo")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=RoundedCornerShape(14.dp),color=Color.White) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Image(bitmap=image.asImageBitmap(),contentDescription="Codice QR ${item.sku}",
                        modifier=Modifier.fillMaxWidth().heightIn(max=220.dp))
                    Text(item.sku,color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text(item.name,color=Color.Black,fontSize=13.sp)
                    Text(item.category,color=Color.DarkGray,fontSize=12.sp)
                }
            }
            Text("Codice assegnato e permanente. Etichetta 60 × 40 mm; seleziona una stampante o Salva come PDF.",
                color=TextSecondary,fontSize=12.sp)
        }
    },confirmButton={Button(onClick={
        try { QrLabels.print(context,item) }
        catch(e:Exception) { android.widget.Toast.makeText(context,e.localizedMessage ?: "Stampa non disponibile",android.widget.Toast.LENGTH_LONG).show() }
    }) { Icon(Icons.Outlined.Print,null);Spacer(Modifier.width(8.dp));Text("Stampa / PDF") }},
       dismissButton={Row {
           if(onAnother!=null) TextButton(onClick=onAnother){Text("Aggiungine un altro")}
           TextButton(onClick=onDismiss){Text("Chiudi")}
       }})
}

@Composable fun PlacementQrDialog(p: Placement, items: List<StockItem>, onDismiss:()->Unit) {
    val context=LocalContext.current
    val label=remember(p.id,p.label,p.levels,p.slots) { LabelSpec.placement(p) }
    val image=remember(label.payload) { QrLabels.bitmap(label.payload) }
    val contents=items.filter{it.placementId==p.id}
    fun safely(block:()->Unit) = try { block() } catch(e:Exception) {
        android.widget.Toast.makeText(context,e.localizedMessage ?: "Stampa non disponibile",android.widget.Toast.LENGTH_LONG).show()
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Etichetta ubicazione")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=RoundedCornerShape(14.dp),color=Color.White) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Image(bitmap=image.asImageBitmap(),contentDescription="Codice QR ${p.label}",
                        modifier=Modifier.fillMaxWidth().heightIn(max=200.dp))
                    Text(p.label,color=Color.Black,fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text(label.subtitle,color=Color.DarkGray,fontSize=12.sp)
                }
            }
            Text("Attacca l'etichetta alla struttura: scansionandola Ubicora apre la mappa sulla struttura e ne mostra il contenuto.",
                color=TextSecondary,fontSize=12.sp)
            OutlinedButton(onClick={safely{QrLabels.printSheet(context,p.label,listOf(label)+contents.map{LabelSpec.item(it,p.label)})}},
                enabled=contents.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                Text("Foglio A4: struttura + ${contents.size} articoli")
            }
        }
    },confirmButton={Button(onClick={safely{QrLabels.print(context,label)}}) {
        Icon(Icons.Outlined.Print,null);Spacer(Modifier.width(8.dp));Text("Stampa 60×40")
    }},dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}
