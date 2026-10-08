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

/** Il QR contiene soltanto un identificatore locale, nessun dato personale. */
object QrLabels {
    fun bitmap(item: StockItem, size: Int = 480): Bitmap {
        val value=ArticleCodes.payload(item.warehouseId,item.sku)
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
        manager.print("Ubicora ${item.sku}", LabelDocumentAdapter(item), attributes)
    }
}

/** A4 non richiesto: etichetta singola 60 × 40 mm tramite il servizio di stampa Android. */
private class LabelDocumentAdapter(private val item: StockItem): PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?,newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,callback: LayoutResultCallback,extras: Bundle?) {
        if(cancellationSignal.isCanceled) {callback.onLayoutCancelled();return}
        callback.onLayoutFinished(PrintDocumentInfo.Builder("${item.sku}.pdf")
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
            val qr=QrLabels.bitmap(item,360)
            val dest=android.graphics.Rect(8,11,99,102)
            canvas.drawBitmap(qr,null,dest,Paint(Paint.FILTER_BITMAP_FLAG))
            qr.recycle()
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=AndroidColor.BLACK}
            paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
            paint.textSize=10f
            if (paint.measureText(item.sku) > 65f) paint.textSize=8f
            canvas.drawText(item.sku,101f,29f,paint)
            paint.typeface=Typeface.DEFAULT
            paint.textSize=8f
            // Ellissi per evitare testo fuori dall'etichetta.
            val label=item.name
            val clipped=if(label.length>15) label.take(14)+"…" else label
            canvas.drawText(clipped,101f,48f,paint)
            paint.textSize=6.5f
            canvas.drawText(item.category,101f,61f,paint)
            canvas.drawText("Ubicora",101f,90f,paint)
            document.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use {document.writeTo(it)}
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch(e:Exception) {
            callback.onWriteFailed(e.localizedMessage ?: "Errore nella stampa")
        } finally {document.close()}
    }
}

@Composable fun QrLabelDialog(item: StockItem,onDismiss:()->Unit) {
    val context=LocalContext.current
    val image=remember(item.id,item.sku) { QrLabels.bitmap(item) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Etichetta articolo")},text={
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
       dismissButton={TextButton(onClick=onDismiss){Text("Chiudi")}})
}
