package com.officinastock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

/** Immagini private dell'app. In SQLite conserviamo solo il nome, mai il JPEG binario. */
object PhotoFiles {
    private val storedName = Regex("item_[a-f0-9\\-]{36}\\.jpg")
    private val shotName = Regex("shot_[a-f0-9\\-]{36}\\.jpg")

    private fun photos(context: Context): File = File(context.filesDir, "article_photos").also { it.mkdirs() }
    private fun captures(context: Context): File = File(context.cacheDir, "article_camera").also { it.mkdirs() }

    fun captureUri(context: Context): Uri {
        // Rimuovi soltanto gli scatti temporanei abbandonati da oltre 24 ore.
        val expiration=System.currentTimeMillis()-24*60*60*1000L
        captures(context).listFiles()?.forEach { if(it.lastModified()<expiration) it.delete() }
        val file=File(captures(context),"shot_${UUID.randomUUID()}.jpg")
        if(!file.createNewFile()) throw IOException("Impossibile creare il file della fotocamera")
        return FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
    }

    fun removeCapturedTemp(context: Context, uri: Uri) {
        if(uri.authority!="${context.packageName}.fileprovider") return
        val name=uri.lastPathSegment?.substringAfterLast('/') ?: return
        if(!shotName.matches(name)) return
        File(captures(context),name).delete()
    }

    private fun storedFile(context: Context, name: String?): File? =
        name?.takeIf { storedName.matches(it) }?.let { File(photos(context),it) }

    fun delete(context: Context, name: String?) { storedFile(context,name)?.delete() }

    fun preview(context: Context, selectedUri: String?, savedFile: String?, edge: Int=650): Bitmap? {
        val u=selectedUri?.let(Uri::parse)
        if(u!=null) return runCatching { decode(context,u,edge) }.getOrNull()
        val file=storedFile(context,savedFile)?.takeIf{it.isFile} ?: return null
        return runCatching { decodeFile(file,edge) }.getOrNull()
    }

    fun save(context: Context, source: Uri): String {
        val bitmap=decode(context,source,1600) ?: throw IOException("Immagine non leggibile")
        val filename="item_${UUID.randomUUID()}.jpg"
        val target=File(photos(context),filename)
        val tmp=File(photos(context),"$filename.tmp")
        try {
            tmp.outputStream().buffered().use { out ->
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,85,out))
                    throw IOException("Conversione foto non riuscita")
            }
            if(!tmp.renameTo(target)) throw IOException("Salvataggio della foto non riuscito")
            return filename
        } finally {
            tmp.delete()
            bitmap.recycle()
        }
    }

    private fun decodeFile(file: File, edge: Int): Bitmap? {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.absolutePath,bounds)
        val opts=BitmapFactory.Options().apply { inSampleSize=sample(bounds.outWidth,bounds.outHeight,edge) }
        return BitmapFactory.decodeFile(file.absolutePath,opts)?.let { scale(it,edge) }
    }

    private fun decode(context: Context, source: Uri, edge: Int): Bitmap? {
        val cr=context.contentResolver
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        val boundsStream=cr.openInputStream(source) ?: throw IOException("File foto non disponibile")
        boundsStream.use { BitmapFactory.decodeStream(it,null,bounds) }
        if(bounds.outWidth<=0||bounds.outHeight<=0) throw IOException("Formato immagine non supportato")
        val opts=BitmapFactory.Options().apply { inSampleSize=sample(bounds.outWidth,bounds.outHeight,edge) }
        val decoded=cr.openInputStream(source)?.use{ BitmapFactory.decodeStream(it,null,opts) }
            ?: throw IOException("Immagine non leggibile")
        // Le foto di fotocamera possono avere un orientamento EXIF diverso dai pixel.
        val orientation=runCatching {
            cr.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix=Matrix()
        when(orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f,1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postScale(-1f,1f);matrix.postRotate(90f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postScale(-1f,1f);matrix.postRotate(270f) }
        }
        val rotated=if(matrix.isIdentity) decoded else Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
        if(rotated!==decoded) decoded.recycle()
        return scale(rotated,edge)
    }

    private fun sample(width: Int,height: Int,edge: Int): Int {
        var factor=1
        while(width/factor>edge*2||height/factor>edge*2) factor*=2
        return factor
    }
    private fun scale(source: Bitmap,edge: Int): Bitmap {
        val biggest=max(source.width,source.height)
        if(biggest<=edge) return source
        val factor=edge.toFloat()/biggest
        val resized=Bitmap.createScaledBitmap(source,(source.width*factor).toInt().coerceAtLeast(1),
            (source.height*factor).toInt().coerceAtLeast(1),true)
        if(resized!==source) source.recycle()
        return resized
    }
}
