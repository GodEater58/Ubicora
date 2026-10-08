package com.officinastock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.IOException
import java.util.UUID

/** Illustrazioni sintetiche chiaramente marcate DEMO: non fotografie di prodotti reali. */
object DemoArtwork {
    fun create(context: Context, name: String, category: String): String {
        val width=960; val height=540
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val primary=when(category) {
            "Ricambi" -> Color.rgb(47,148,153)
            "Utensili" -> Color.rgb(235,164,86)
            "Elettrico" -> Color.rgb(108,135,235)
            "Pneumatica" -> Color.rgb(66,167,200)
            "Idraulica" -> Color.rgb(73,133,217)
            "Consumabili" -> Color.rgb(153,135,211)
            "DPI" -> Color.rgb(91,177,115)
            "Meccanica" -> Color.rgb(176,160,123)
            else -> Color.rgb(141,159,174)
        }
        val background=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),
                Color.rgb(15,32,46),Color.rgb(31,52,68),Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),background)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=primary;style=Paint.Style.STROKE;strokeWidth=12f}
        for(i in 0..15){val y=28f+i*34;canvas.drawLine(0f,y,width.toFloat(),y,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(13,255,255,255)})}
        canvas.drawRoundRect(RectF(42f,42f,918f,500f),30f,30f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.argb(28,255,255,255)})
        // Sagoma tecnica neutra, volutamente NON somigliante a un articolo fotografato.
        val shape=Path().apply {
            moveTo(285f,144f);lineTo(402f,202f);lineTo(402f,335f)
            lineTo(285f,393f);lineTo(168f,335f);lineTo(168f,202f);close()
        }
        canvas.drawPath(shape,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(60,100,215,218);style=Paint.Style.FILL })
        canvas.drawPath(shape,paint)
        canvas.drawLine(285f,144f,285f,276f,paint)
        canvas.drawLine(285f,276f,168f,202f,paint)
        canvas.drawLine(285f,276f,402f,202f,paint)
        canvas.drawLine(285f,276f,285f,393f,paint)
        val heading=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=43f;typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD) }
        val label=name.take(40)
        val split=heading.breakText(label,true,455f,null)
        canvas.drawText(label.take(split),443f,227f,heading)
        if(label.length>split) canvas.drawText(label.drop(split).take(heading.breakText(label.drop(split),true,440f,null)),443f,277f,heading)
        val sub=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=primary;typeface=Typeface.DEFAULT_BOLD;textSize=26f}
        canvas.drawText(category.uppercase(),445f,335f,sub)
        canvas.drawRoundRect(RectF(443f,383f,838f,450f),14f,14f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(73,84,95)})
        canvas.drawText("IMMAGINE DEMO · NON REALE",461f,427f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=23f;typeface=Typeface.DEFAULT_BOLD})
        val folder=File(context.filesDir,"article_photos").also{it.mkdirs()}
        val fileName="item_${UUID.randomUUID()}.jpg"
        val output=File(folder,fileName)
        val temp=File(folder,"$fileName.tmp")
        try {
            temp.outputStream().buffered().use { out ->
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,83,out)) throw IOException("Immagine demo non generata")
            }
            if(!temp.renameTo(output)) throw IOException("Impossibile salvare immagine demo")
            return fileName
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }
}
