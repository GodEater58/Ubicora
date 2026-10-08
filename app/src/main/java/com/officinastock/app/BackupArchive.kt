package com.officinastock.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.*
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup OFFLINE cifrato: SQLite logico + immagini. Mai usare CSV come backup. */
class BackupArchive(private val context: Context, private val stockDb: StockDb) {
    companion object {
        private val tables = listOf("profiles", "warehouses", "placements", "items", "movements", "loans", "walls", "sku_sequences", "demo_assets")
        private val imageName = Regex("item_[a-f0-9-]{36}\\.jpg")
        private const val MAX_TOTAL = 4L * 1024 * 1024 * 1024
    private const val MAX_ENTRY = 32L * 1024 * 1024
    private const val MAX_ROWS = 300_000
    }
    private fun db()=stockDb.readableDatabase
    private fun photosDir()=File(context.filesDir,"article_photos").also{it.mkdirs()}
    private fun addRow(zip: ZipOutputStream, row: JSONObject) {
        zip.write(row.toString().toByteArray(Charsets.UTF_8));zip.write('\n'.code)
    }
    fun export(uri: Uri, password: String) {
        val files=mutableSetOf<String>()
        val dest=context.contentResolver.openOutputStream(uri,"wt") ?: throw IOException("Impossibile aprire il file scelto")
        BackupCipher.encrypt(dest,password).use { sealed ->
                ZipOutputStream(BufferedOutputStream(sealed)).use {zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    // Format ID legacy: non cambiare per mantenere compatibilita con backup 4.1.2+.
                    zip.write(JSONObject().put("app","OfficinaStock").put("schema",1)
                        .put("createdAt",System.currentTimeMillis())
                        .put("tables",tables.size).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    val db=db()
                    for(table in tables) {
                        zip.putNextEntry(ZipEntry("db/$table.ndjson"))
                        db.rawQuery("SELECT * FROM $table",null).use {cursor ->
                            while(cursor.moveToNext()) {
                                val json=JSONObject()
                                for(col in 0 until cursor.columnCount) {
                                    val field=cursor.getColumnName(col)
                                    when(cursor.getType(col)) {
                                        Cursor.FIELD_TYPE_NULL -> json.put(field,JSONObject.NULL)
                                        Cursor.FIELD_TYPE_INTEGER -> json.put(field,cursor.getLong(col))
                                        Cursor.FIELD_TYPE_FLOAT -> json.put(field,cursor.getDouble(col))
                                        Cursor.FIELD_TYPE_STRING -> json.put(field,cursor.getString(col))
                                        else -> throw IOException("Campo binario non previsto in $table")
                                    }
                                    if(table=="items" && field=="photo_file" && !cursor.isNull(col)) {
                                        val name=cursor.getString(col)
                                        require(imageName.matches(name)) { "Nome immagine non valido" }
                                        files.add(name)
                                    }
                                }
                                addRow(zip,json)
                            }
                        }
                        zip.closeEntry()
                    }
                    for(photo in files.sorted()) {
                        val f=File(photosDir(),photo)
                        if(!f.isFile) throw IOException("Foto mancante: $photo. Backup annullato.")
                        zip.putNextEntry(ZipEntry("photos/$photo"))
                        f.inputStream().buffered().use {it.copyTo(zip)}
                        zip.closeEntry()
                    }
                }
        }
    }
    private fun decrypt(input: InputStream, output: File, password: String) {
        BackupCipher.decrypt(BufferedInputStream(input),password).use {clear ->
            output.outputStream().buffered().use {out ->
                val buffer=ByteArray(65536)
                var total=0L
                while(true) {
                    val count=clear.read(buffer)
                    if(count<0)break
                    total+=count
                    require(total<=MAX_TOTAL){"Backup troppo grande"}
                    out.write(buffer,0,count)
                }
            }
        }
    }
    private fun copyBounded(input: InputStream, target: File, limit: Long): Long {
        var total=0L
        target.outputStream().buffered().use{out ->
            val buffer=ByteArray(65536)
            while(true) {
                val count=input.read(buffer)
                if(count<0)break
                total+=count
                require(total<=limit) {"Dimensioni del file di backup non valide"}
                out.write(buffer,0,count)
            }
        }
        return total
    }
    private fun readDbFile(file:File, allowed:Set<String>): List<ContentValues> {
        val values=ArrayList<ContentValues>()
        file.bufferedReader(Charsets.UTF_8).useLines {lines ->
            for(line in lines) {
                if(line.isBlank())continue
                require(values.size < MAX_ROWS) { "Troppe righe nel backup" }
                val obj=JSONObject(line)
                val row=ContentValues()
                val names=obj.keys()
                while(names.hasNext()) {
                    val col=names.next()
                    require(col in allowed) { "Colonna sconosciuta: $col" }
                    val value=obj.get(col)
                    when(value) {
                        JSONObject.NULL -> row.putNull(col)
                        is Int -> row.put(col,value)
                        is Long -> row.put(col,value)
                        is Double -> row.put(col,value)
                        is Float -> row.put(col,value)
                        is String -> row.put(col,value)
                        else -> throw IOException("Valore non valido nel backup: $col")
                    }
                }
                values.add(row)
            }
        }
        return values
    }
    /** L'archivio viene validato fuori transazione. La sostituzione SQLite è atomica. */
    fun restore(uri:Uri,password:String) {
        val root=File(context.cacheDir,"restore_${UUID.randomUUID()}")
        if(!root.mkdirs()) throw IOException("Spazio temporaneo non disponibile")
        val newPhotos=mutableListOf<File>()
        var restored=false
        try {
            val decrypted=File(root,"payload.zip")
            context.contentResolver.openInputStream(uri)?.use{decrypt(it,decrypted,password)}
                ?: throw IOException("Backup non leggibile")
            val parts=File(root,"parts").also{it.mkdirs()}
            var consumed=0L
            val names=mutableSetOf<String>()
            ZipInputStream(BufferedInputStream(FileInputStream(decrypted))).use{zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    val name=entry.name
                    require(!entry.isDirectory && names.add(name)) { "Voce duplicata o non valida nel backup" }
                    val file=when {
                        name=="manifest.json" -> File(parts,"manifest.json")
                        name.startsWith("db/") && name.removePrefix("db/").removeSuffix(".ndjson") in tables && name.endsWith(".ndjson") ->
                            File(parts,"${name.removePrefix("db/")}")
                        name.startsWith("photos/") && imageName.matches(name.removePrefix("photos/")) ->
                            File(parts,name.removePrefix("photos/"))
                        else -> throw IOException("Contenuto backup inatteso")
                    }
                    consumed+=copyBounded(zip,file,MAX_ENTRY)
                    require(consumed<=MAX_TOTAL) { "Backup troppo grande" }
                    zip.closeEntry()
                }
            }
            require(names.contains("manifest.json")) {"Manifest assente"}
            val manifest=JSONObject(File(parts,"manifest.json").readText())
            require(manifest.optString("app")=="OfficinaStock" && manifest.optInt("schema")==1) {
                "Backup incompatibile con questa versione"
            }
            require(tables.all {names.contains("db/$it.ndjson")}) {"Backup incompleto: tabelle mancanti"}
            val db=stockDb.writableDatabase
            val snapshots=linkedMapOf<String,List<ContentValues>>()
            for(table in tables) {
                val columns=mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info($table)",null).use {c-> while(c.moveToNext())columns.add(c.getString(1))}
                snapshots[table]=readDbFile(File(parts,"$table.ndjson"),columns)
            }
            val oldFiles=mutableSetOf<String>()
            db.rawQuery("SELECT photo_file FROM items WHERE photo_file IS NOT NULL",null).use {c ->
                while(c.moveToNext())oldFiles.add(c.getString(0))
            }
            val newNames=mutableMapOf<String,String>()
            for(row in snapshots["items"].orEmpty()) {
                val old=row.getAsString("photo_file") ?: continue
                require(imageName.matches(old) && names.contains("photos/$old")) {"Foto articolo mancante nel backup"}
                if(!newNames.containsKey(old)) {
                    val next="item_${UUID.randomUUID()}.jpg"
                    val file=File(photosDir(),next)
                    File(parts,old).copyTo(file,overwrite=false)
                    newPhotos.add(file);newNames[old]=next
                }
                row.put("photo_file",newNames.getValue(old))
            }
            db.beginTransaction()
            try {
                db.delete("profiles",null,null) // On-delete cascade pulisce warehouse e dati dipendenti.
                db.delete("sku_sequences",null,null)
                for(table in tables) {
                    for(values in snapshots.getValue(table)) {
                        db.insertOrThrow(table,null,values)
                    }
                }
                db.rawQuery("PRAGMA foreign_key_check",null).use{ c ->
                    require(!c.moveToFirst()) { "Relazioni non valide nel backup" }
                }
                db.setTransactionSuccessful()
                restored=true
            } finally {db.endTransaction()}
            // Foto originali eliminate soltanto DOPO la riuscita del commit SQLite.
            oldFiles.forEach{PhotoFiles.delete(context,it)}
        } finally {
            if(!restored) newPhotos.forEach {it.delete()}
            root.deleteRecursively()
        }
    }
}
