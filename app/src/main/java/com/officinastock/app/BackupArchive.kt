package com.officinastock.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.officinastock.app.AppDatabase as SQLiteDatabase
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
        private val legacyTables = listOf("profiles", "warehouses", "placements", "items", "movements", "loans", "walls", "sku_sequences", "demo_assets")
        private val operationsTables = listOf("ops_orders", "ops_counts", "ops_transfers", "ops_maintenance", "ops_events", "ops_settings")
        private val managerTables = legacyTables + operationsTables
        // Restore catalogs before orders, because orders can reference center/supplier IDs.
        private val companyTables = listOf("org_centers","org_suppliers","org_assets","org_staff_directory","sync_outbox")
        private val enterpriseTables = legacyTables + companyTables + operationsTables
        private val planningTables = listOf("stock_reservations")
        private val reservedTables = enterpriseTables + planningTables
        private val traceTables = listOf("stock_lots","stock_serials")
        private val beforeTeamSync = reservedTables + traceTables
        // 4.8: the phone's role in the sync travels with the backup (restoring it moves the phone).
        private val teamTables = listOf("team_sync_settings","team_sync_peers","team_sync_outbox","team_sync_conflicts")
        private val withTeamSync = beforeTeamSync + teamTables
        // 4.20.3: which phone made each movement (restored after the team settings, so no trigger rewrites it).
        private val withActors = withTeamSync + listOf("movement_actors")
        // 4.24.1: a maintainer's card changes not yet validated travel with the backup too.
        private val tables = withActors + listOf("team_edits")
        private val imageName = Regex("item_[a-f0-9-]{36}\\.jpg")
        private const val MAX_TOTAL = 4L * 1024 * 1024 * 1024
    private const val MAX_ENTRY = 32L * 1024 * 1024
    }
    /** 4.24.1: a restore or export killed half way leaves readable copies in the cache: removed at start. */
    fun removeLeftovers() {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("restore_") || it.name.startsWith("ubicora_export_") }
            ?.forEach { it.deleteRecursively() }
    }
    private fun db()=stockDb.readableDatabase
    private fun photosDir()=File(context.filesDir,"article_photos").also{it.mkdirs()}
    private fun addRow(zip: ZipOutputStream, row: JSONObject, remaining:Long):Long {
        val bytes=row.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size.toLong()+1<=remaining) { "Archivio troppo grande per il formato backup. Esportazione annullata." }
        zip.write(bytes);zip.write('\n'.code)
        return bytes.size.toLong()+1
    }
    fun export(uri: Uri, password: String) {
        require(password.length>=8) {"Password backup: almeno 8 caratteri"}
        // Complete and validate the encrypted archive before touching an existing destination.
        val staged=File.createTempFile("ubicora_export_", ".osbackup", context.cacheDir)
        try {
            staged.outputStream().use {writeEncrypted(it,password)}
            val dest=context.contentResolver.openOutputStream(uri,"wt")
                ?: throw IOException("Impossibile aprire il file scelto")
            dest.use {output->staged.inputStream().buffered().use {it.copyTo(output)}}
        } finally {staged.delete()}
    }
    private fun writeEncrypted(dest: OutputStream, password: String) {
        val files=mutableSetOf<String>()
        BackupCipher.encrypt(dest,password).use { sealed ->
                ZipOutputStream(BufferedOutputStream(sealed)).use {zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    // Format ID legacy: non cambiare per mantenere compatibilita con backup 4.1.2+.
                    // 4.24.1: the exact list of tables and the database version travel too (older apps ignore them).
                    zip.write(JSONObject().put("app","OfficinaStock").put("schema",1)
                        .put("createdAt",System.currentTimeMillis())
                        .put("tables",tables.size).put("tableList",org.json.JSONArray(tables))
                        .put("dbVersion",db().rawQuery("PRAGMA user_version",null).use { it.moveToFirst(); it.getInt(0) }).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    val db=stockDb.writableDatabase
                    var total=1024L
                    // 4.24.1: all tables read in one transaction: a team file arriving meanwhile cannot leave the
                    // backup half before and half after it (which could make it impossible to restore).
                    db.beginTransaction()
                    try { for(table in tables) {
                        var entryBytes=0L
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
                                entryBytes+=addRow(zip,json,MAX_TOTAL-total-entryBytes)
                            }
                        }
                        zip.closeEntry()
                        total+=entryBytes
                    }
                    } finally { db.endTransaction() }
                    for(photo in files.sorted()) {
                        val f=File(photosDir(),photo)
                        if(!f.isFile) throw IOException("Foto mancante: $photo. Backup annullato.")
                        require(f.length()<=MAX_ENTRY) { "Foto troppo grande per il formato backup" }
                        total+=f.length()
                        require(total<=MAX_TOTAL) { "Backup troppo grande" }
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
    private fun forEachRow(file:File, allowed:Map<String,String>, action:(ContentValues)->Unit) {
        file.bufferedReader(Charsets.UTF_8).useLines {lines ->
            for(line in lines) {
                if(line.isBlank())continue
                val obj=JSONObject(line)
                val row=ContentValues()
                val names=obj.keys()
                while(names.hasNext()) {
                    val col=names.next()
                    require(col in allowed) { "Colonna sconosciuta: $col" }
                    val value=obj.get(col)
                    if(value!=JSONObject.NULL) when(allowed.getValue(col).uppercase(java.util.Locale.ROOT)) {
                        "INTEGER" -> require(value is Int || value is Long) { "Intero non valido nel backup: $col" }
                        "REAL" -> require(value is Number && value.toDouble().isFinite()) { "Numero non valido nel backup: $col" }
                        "TEXT" -> require(value is String) { "Testo non valido nel backup: $col" }
                        else -> throw IOException("Tipo colonna non supportato: $col")
                    }
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
                action(row)
            }
        }
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
                    consumed+=copyBounded(zip,file,if(name.startsWith("db/")) MAX_TOTAL else MAX_ENTRY)
                    require(consumed<=MAX_TOTAL) { "Backup troppo grande" }
                    zip.closeEntry()
                }
            }
            require(names.contains("manifest.json")) {"Manifest assente"}
            val manifest=JSONObject(File(parts,"manifest.json").readText())
            require(manifest.optString("app")=="OfficinaStock" && manifest.optInt("schema")==1) {
                "Backup incompatibile con questa versione"
            }
            val listed=manifest.optJSONArray("tableList")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            val archiveTableCount=manifest.optInt("tables",-1)
            require(listed!=null || archiveTableCount in setOf(legacyTables.size,managerTables.size,
                enterpriseTables.size,reservedTables.size,beforeTeamSync.size,withTeamSync.size,withActors.size,tables.size)) {
                "Numero tabelle incompatibile nel manifest del backup"
            }
            val mandatory=if(listed!=null) {
                require(listed.toSet().size==listed.size && listed.all { it in tables } && legacyTables.all { it in listed }) { "Elenco tabelle non valido nel backup" }
                listed
            } else when(archiveTableCount){
                legacyTables.size -> legacyTables
                managerTables.size -> managerTables
                enterpriseTables.size -> enterpriseTables
                reservedTables.size -> reservedTables
                beforeTeamSync.size -> beforeTeamSync
                withTeamSync.size -> withTeamSync
                withActors.size -> withActors
                else -> tables
            }
            require(mandatory.all {names.contains("db/$it.ndjson")}) {"Backup incompleto: tabelle mancanti"}
            require(tables.filter{it !in mandatory}.none{names.contains("db/$it.ndjson")}) {
                "Backup misto o non valido"
            }
            val db=stockDb.writableDatabase
            val columns=tables.associateWith { table -> buildMap<String,String> {
                db.rawQuery("PRAGMA table_info($table)",null).use {c-> while(c.moveToNext()) put(c.getString(1),c.getString(2))} } }
            val oldFiles=mutableSetOf<String>()
            db.rawQuery("SELECT photo_file FROM items WHERE photo_file IS NOT NULL",null).use {c ->
                while(c.moveToNext())oldFiles.add(c.getString(0))
            }
            val newNames=mutableMapOf<String,String>()
            fun renamePhoto(row:ContentValues) {
                val old=row.getAsString("photo_file") ?: return
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
                // Cleared first, so no sync trigger fires while the archive is written back.
                for(table in teamTables) db.delete(table,null,null)
                // 4.21: card changes waiting for validation belong to the team of the replaced archive
                // (4.24.1: the backup brings back its own); the loans seen on the other phones come with the next exchange.
                db.delete("team_edits",null,null)
                db.delete("team_loans",null,null)
                // 4.24.1: one row at a time, so the size of a table no longer depends on the phone's memory.
                for(table in tables) if(names.contains("db/$table.ndjson")) forEachRow(File(parts,"$table.ndjson"),columns.getValue(table)) { values ->
                    if(table=="items") renamePhoto(values)
                    db.insertOrThrow(table,null,values)
                }
                db.rawQuery("PRAGMA foreign_key_check",null).use{ c ->
                    require(!c.moveToFirst()) { "Relazioni non valide nel backup" }
                }
                stockDb.repairRestoredPositions(db)
                stockDb.validateRestoredData()
                // 4.24.1: on a new phone the change counter would restart from 1, below what the central has already
                // received from this phone: its new changes would be taken as old and lost. The counter jumps ahead.
                db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT 'team_sync_outbox',0 WHERE NOT EXISTS(SELECT 1 FROM sqlite_sequence WHERE name='team_sync_outbox')")
                db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name='team_sync_outbox'",arrayOf(System.currentTimeMillis()))
                db.setTransactionSuccessful()
            } finally {db.endTransaction()}
            restored=true
            // Foto originali eliminate soltanto DOPO la riuscita del commit SQLite.
            oldFiles.forEach{PhotoFiles.delete(context,it)}
        } finally {
            if(!restored) newPhotos.forEach {it.delete()}
            root.deleteRecursively()
        }
    }
}

