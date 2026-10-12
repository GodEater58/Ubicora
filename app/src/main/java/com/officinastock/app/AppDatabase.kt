package com.officinastock.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Build
import java.io.File
import java.security.SecureRandom

/**
 * 4.12: the archive is encrypted on the phone with SQLCipher (AES-256), with a random key sealed by
 * the Android Keystore. Every part of the app talks to [AppDatabase]; underneath there is either the
 * encrypted engine (phones) or Android's normal SQLite (unit tests, or a phone where the Keystore
 * does not work: then the archive stays as before rather than risking the data).
 */
abstract class AppDatabase {
    abstract fun rawQuery(sql: String, selectionArgs: Array<String>?): Cursor
    abstract fun execSQL(sql: String)
    abstract fun execSQL(sql: String, bindArgs: Array<out Any?>)
    abstract fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long
    abstract fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues): Long
    abstract fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int): Long
    abstract fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int
    abstract fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int
    abstract fun beginTransaction()
    abstract fun setTransactionSuccessful()
    abstract fun endTransaction()
    abstract fun setForeignKeyConstraintsEnabled(enable: Boolean)
    abstract val isReadOnly: Boolean
    abstract var version: Int
    abstract fun compileStatement(sql: String): AppStatement
    companion object {
        // Same values as android.database.sqlite.SQLiteDatabase (and SQLCipher).
        const val CONFLICT_NONE = 0
        const val CONFLICT_ROLLBACK = 1
        const val CONFLICT_ABORT = 2
        const val CONFLICT_FAIL = 3
        const val CONFLICT_IGNORE = 4
        const val CONFLICT_REPLACE = 5
    }
}

/** Prepared statement (bulk inserts), same calls as Android's SQLiteStatement. */
abstract class AppStatement : java.io.Closeable {
    abstract fun clearBindings()
    abstract fun bindLong(index: Int, value: Long)
    abstract fun bindDouble(index: Int, value: Double)
    abstract fun bindString(index: Int, value: String)
    abstract fun bindNull(index: Int)
    abstract fun executeInsert(): Long
    abstract fun executeUpdateDelete(): Int
    abstract fun execute()
}

/** True for a unique/foreign-key/check violation, from either engine. */
fun isConstraintViolation(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.any { it.javaClass.simpleName == "SQLiteConstraintException" }

private class FrameworkDatabase(val db: android.database.sqlite.SQLiteDatabase) : AppDatabase() {
    override fun rawQuery(sql: String, selectionArgs: Array<String>?): Cursor = db.rawQuery(sql, selectionArgs)
    override fun execSQL(sql: String) = db.execSQL(sql)
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = db.execSQL(sql, bindArgs)
    override fun insert(table: String, nullColumnHack: String?, values: ContentValues) = db.insert(table, nullColumnHack, values)
    override fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues) = db.insertOrThrow(table, nullColumnHack, values)
    override fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int) =
        db.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm)
    override fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?) = db.update(table, values, whereClause, whereArgs)
    override fun delete(table: String, whereClause: String?, whereArgs: Array<String>?) = db.delete(table, whereClause, whereArgs)
    override fun beginTransaction() = db.beginTransaction()
    override fun setTransactionSuccessful() = db.setTransactionSuccessful()
    override fun endTransaction() = db.endTransaction()
    override fun setForeignKeyConstraintsEnabled(enable: Boolean) = db.setForeignKeyConstraintsEnabled(enable)
    override val isReadOnly get() = db.isReadOnly
    override var version: Int get() = db.version; set(value) { db.version = value }
    override fun compileStatement(sql: String): AppStatement = db.compileStatement(sql).let { st -> object : AppStatement() {
        override fun clearBindings() = st.clearBindings()
        override fun bindLong(index: Int, value: Long) = st.bindLong(index, value)
        override fun bindDouble(index: Int, value: Double) = st.bindDouble(index, value)
        override fun bindString(index: Int, value: String) = st.bindString(index, value)
        override fun bindNull(index: Int) = st.bindNull(index)
        override fun executeInsert() = st.executeInsert()
        override fun executeUpdateDelete() = st.executeUpdateDelete()
        override fun execute() = st.execute()
        override fun close() = st.close()
    } }
}

private class CipherDatabase(val db: net.zetetic.database.sqlcipher.SQLiteDatabase) : AppDatabase() {
    override fun rawQuery(sql: String, selectionArgs: Array<String>?): Cursor = db.rawQuery(sql, selectionArgs)
    override fun execSQL(sql: String) = db.execSQL(sql)
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = db.execSQL(sql, bindArgs)
    override fun insert(table: String, nullColumnHack: String?, values: ContentValues) = db.insert(table, nullColumnHack, values)
    override fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues) = db.insertOrThrow(table, nullColumnHack, values)
    override fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int) =
        db.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm)
    override fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?) = db.update(table, values, whereClause, whereArgs)
    override fun delete(table: String, whereClause: String?, whereArgs: Array<String>?) = db.delete(table, whereClause, whereArgs)
    override fun beginTransaction() = db.beginTransaction()
    override fun setTransactionSuccessful() = db.setTransactionSuccessful()
    override fun endTransaction() = db.endTransaction()
    override fun setForeignKeyConstraintsEnabled(enable: Boolean) = db.setForeignKeyConstraintsEnabled(enable)
    override val isReadOnly get() = db.isReadOnly
    override var version: Int get() = db.version; set(value) { db.version = value }
    override fun compileStatement(sql: String): AppStatement = db.compileStatement(sql).let { st -> object : AppStatement() {
        override fun clearBindings() = st.clearBindings()
        override fun bindLong(index: Int, value: Long) = st.bindLong(index, value)
        override fun bindDouble(index: Int, value: Double) = st.bindDouble(index, value)
        override fun bindString(index: Int, value: String) = st.bindString(index, value)
        override fun bindNull(index: Int) = st.bindNull(index)
        override fun executeInsert() = st.executeInsert()
        override fun executeUpdateDelete() = st.executeUpdateDelete()
        override fun execute() = st.execute()
        override fun close() = st.close()
    } }
}

/**
 * Same life cycle as Android's SQLiteOpenHelper (onConfigure, onCreate, onUpgrade, onOpen), on
 * top of the engine chosen by [DbCipher].
 */
abstract class AppOpenHelper(context: Context, private val name: String, @Suppress("UNUSED_PARAMETER") factory: Nothing?, private val version: Int) {
    private val appContext = context.applicationContext
    abstract fun onCreate(db: AppDatabase)
    abstract fun onUpgrade(db: AppDatabase, oldVersion: Int, newVersion: Int)
    open fun onConfigure(db: AppDatabase) {}
    open fun onOpen(db: AppDatabase) {}

    private interface Engine { fun database(): AppDatabase; fun close() }
    private var engine: Engine? = null
    @Synchronized private fun engine(): Engine = engine ?: open().also { engine = it }

    val writableDatabase: AppDatabase get() = engine().database()
    val readableDatabase: AppDatabase get() = engine().database()
    /** True when this archive is encrypted on the phone. */
    val encrypted: Boolean get() = engine() is CipherEngine
    @Synchronized fun close() { engine?.close(); engine = null }

    private fun open(): Engine {
        val key = DbCipher.prepare(appContext, name)
        return if (key == null) FrameworkEngine() else CipherEngine(key)
    }

    private inner class FrameworkEngine : Engine {
        private var raw: android.database.sqlite.SQLiteDatabase? = null
        private var wrapped: AppDatabase? = null
        private val helper = object : android.database.sqlite.SQLiteOpenHelper(appContext, name, null, version) {
            override fun onConfigure(db: android.database.sqlite.SQLiteDatabase) = this@AppOpenHelper.onConfigure(wrap(db))
            override fun onCreate(db: android.database.sqlite.SQLiteDatabase) = this@AppOpenHelper.onCreate(wrap(db))
            override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                this@AppOpenHelper.onUpgrade(wrap(db), oldVersion, newVersion)
            override fun onOpen(db: android.database.sqlite.SQLiteDatabase) = this@AppOpenHelper.onOpen(wrap(db))
        }
        @Synchronized private fun wrap(db: android.database.sqlite.SQLiteDatabase): AppDatabase =
            wrapped?.takeIf { raw === db } ?: FrameworkDatabase(db).also { raw = db; wrapped = it }
        override fun database(): AppDatabase = wrap(helper.writableDatabase)
        override fun close() = helper.close()
    }

    private inner class CipherEngine(key: String) : Engine {
        private var raw: net.zetetic.database.sqlcipher.SQLiteDatabase? = null
        private var wrapped: AppDatabase? = null
        private val helper = object : net.zetetic.database.sqlcipher.SQLiteOpenHelper(appContext, name, key, null, version, 0, null, null, false) {
            override fun onConfigure(db: net.zetetic.database.sqlcipher.SQLiteDatabase) = this@AppOpenHelper.onConfigure(wrap(db))
            override fun onCreate(db: net.zetetic.database.sqlcipher.SQLiteDatabase) = this@AppOpenHelper.onCreate(wrap(db))
            override fun onUpgrade(db: net.zetetic.database.sqlcipher.SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                this@AppOpenHelper.onUpgrade(wrap(db), oldVersion, newVersion)
            override fun onOpen(db: net.zetetic.database.sqlcipher.SQLiteDatabase) = this@AppOpenHelper.onOpen(wrap(db))
        }
        @Synchronized private fun wrap(db: net.zetetic.database.sqlcipher.SQLiteDatabase): AppDatabase =
            wrapped?.takeIf { raw === db } ?: CipherDatabase(db).also { raw = db; wrapped = it }
        override fun database(): AppDatabase = wrap(helper.writableDatabase)
        override fun close() = helper.close()
    }
}

/**
 * Key and one-time migration of the archive to the encrypted format.
 * Order of the migration, so that no step can lose data:
 *  1. copy the plain archive into "<name>.enc-tmp", encrypted (sqlcipher_export);
 *  2. open the copy with the key and compare every table row by row count;
 *  3. rename plain → "<name>.plain-old", then copy → "<name>";
 *  4. delete the plain file. An interrupted migration is completed or undone at the next start.
 * Any failure before step 3 leaves the plain archive exactly as it was (and the app keeps using it).
 */
object DbCipher {
    private const val PREFS = "ubicora_archive"
    private const val KEY = "db_key"
    private val HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    @Volatile private var loaded = false
    /** Last migration problem, shown in Settings (null = none). */
    @Volatile var lastProblem: String? = null; private set

    private fun underTest() = Build.FINGERPRINT == "robolectric"
    /** Instrumented tests only: create an archive the pre-4.12 way, to test the migration. */
    @Volatile internal var plainForTests = false

    private fun loadLibrary(): Boolean {
        if (loaded) return true
        return runCatching { System.loadLibrary("sqlcipher") }.isSuccess.also { loaded = it }
    }

    private fun isPlain(file: File): Boolean = file.exists() && file.length() >= 16 &&
        file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }.contentEquals(HEADER)

    /** Raw key "x'…'" for the archive, created and sealed on first use; null if it cannot be kept safely. */
    private fun key(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { stored ->
            return DeviceSecrets.open(stored)?.takeIf { DeviceSecrets.isSealed(stored) }
                ?: throw IllegalStateException("Chiave dell'archivio non leggibile su questo telefono")
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val raw = "x'" + bytes.joinToString("") { "%02X".format(it) } + "'"
        val sealed = DeviceSecrets.seal(raw)
        if (!DeviceSecrets.isSealed(sealed)) return null            // no Keystore: do not encrypt
        check(prefs.edit().putString(KEY, sealed).commit()) { "Chiave dell'archivio non salvata" }
        return raw
    }

    /**
     * Prepares the archive [name] and returns the key to open it encrypted, or null to keep using
     * the normal engine (tests, no Keystore, failed migration: the data stay as they are).
     */
    @Synchronized fun prepare(context: Context, name: String): String? {
        if (underTest() || plainForTests || !loadLibrary()) return null
        val file = context.getDatabasePath(name)
        val tmp = File(file.path + ".enc-tmp")
        val old = File(file.path + ".plain-old")
        // An interrupted migration: finish it or undo it.
        if (!file.exists() && old.exists()) { if (tmp.exists()) tmp.renameTo(file) else old.renameTo(file) }
        if (file.exists() && !isPlain(file) && old.exists()) deleteWithSidecars(old)
        tmp.takeIf { it.exists() && file.exists() }?.let { deleteWithSidecars(it) }

        val plainArchive = isPlain(file)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // An encrypted archive whose key is gone cannot be opened by the normal engine either: say so.
        if (file.exists() && !plainArchive && prefs.getString(KEY, null) == null)
            throw IllegalStateException("Archivio cifrato senza chiave su questo telefono: ripristina un backup")
        val key = key(context) ?: return null
        if (!plainArchive) return key                                       // new or already encrypted
        return try {
            migrate(file, tmp, old, key)
            lastProblem = null
            key
        } catch (e: Exception) {
            deleteWithSidecars(tmp)
            if (!file.exists() && old.exists()) old.renameTo(file)
            lastProblem = "Cifratura dell'archivio rimandata: ${e.message ?: e.javaClass.simpleName}"
            null                                                            // keep the plain archive, untouched
        }
    }

    private fun migrate(file: File, tmp: File, old: File, key: String) {
        deleteWithSidecars(tmp)
        val counts = mutableMapOf<String, Long>()
        val plain = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(file.path, "", null, null, null)
        try {
            val version = plain.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getInt(0) }
            plain.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", null).use { c ->
                while (c.moveToNext()) counts[c.getString(0)] = 0 }
            counts.keys.toList().forEach { t -> counts[t] = plain.rawQuery("SELECT COUNT(*) FROM \"$t\"", null).use { it.moveToFirst(); it.getLong(0) } }
            plain.execSQL("ATTACH DATABASE '${tmp.path.replace("'", "''")}' AS encrypted KEY \"$key\"")
            plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            plain.execSQL("PRAGMA encrypted.user_version = $version")
            plain.execSQL("DETACH DATABASE encrypted")
        } finally { plain.close() }
        // Verify the copy before touching the original.
        val copy = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(tmp.path, key, null, null, null)
        try {
            counts.forEach { (t, n) ->
                val copied = copy.rawQuery("SELECT COUNT(*) FROM \"$t\"", null).use { it.moveToFirst(); it.getLong(0) }
                check(copied == n) { "tabella $t: $copied righe invece di $n" }
            }
            check(copy.rawQuery("PRAGMA integrity_check", null).use { it.moveToFirst(); it.getString(0) } == "ok") { "verifica integrità non superata" }
        } finally { copy.close() }
        check(!isPlain(tmp)) { "la copia non risulta cifrata" }
        deleteWithSidecars(old)
        check(file.renameTo(old)) { "rinomina dell'archivio non riuscita" }
        File(file.path + "-wal").renameTo(File(old.path + "-wal")); File(file.path + "-shm").renameTo(File(old.path + "-shm"))
        File(file.path + "-journal").renameTo(File(old.path + "-journal"))
        check(tmp.renameTo(file)) { old.renameTo(file); "attivazione della copia cifrata non riuscita" }
        deleteWithSidecars(old)
    }

    private fun deleteWithSidecars(f: File) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(f.path + suffix).delete()
    }
}
