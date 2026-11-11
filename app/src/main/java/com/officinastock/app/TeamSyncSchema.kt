package com.officinastock.app

import android.content.ContentValues
import android.util.Base64
import com.officinastock.app.AppDatabase as SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// 4.19.1: moved out of TeamSync.kt without changes — tables, triggers and settings of the team sync.

/**
 * 4.15: letter of the phone behind every movement (A = central, B…Z = satellites), kept beside the
 * movements so their table and the backup format stay as they are. Written by a trigger, so every
 * code path that moves stock is covered. Without a team nothing is recorded.
 */
object MovementActors {
    fun create(db: SQLiteDatabase) {
        fun s(key: String) = "(SELECT value FROM team_sync_settings WHERE key='$key')"
        // While a central update is applied on a satellite the movement comes from the central;
        // while the central imports a satellite's file ("actor") it comes from that satellite.
        val letter = """CASE WHEN COALESCE(${s("applying")},'0')='1' THEN 'A'
            ELSE COALESCE(NULLIF(${s("actor")},''), CASE ${s("role")} WHEN 'central' THEN 'A' WHEN 'satellite' THEN ${s("letter")} END) END"""
        db.execSQL("CREATE TABLE IF NOT EXISTS team_sync_settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS movement_actors(
            movement_id INTEGER PRIMARY KEY NOT NULL REFERENCES movements(id) ON DELETE CASCADE, letter TEXT NOT NULL)""")
        // 4.15: open loans of the other phones, as told by the last exchange (read-only copy, rebuilt at every sync).
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_loans(
            letter TEXT NOT NULL, item_uid TEXT NOT NULL, borrower TEXT NOT NULL, quantity REAL NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS movement_actor AFTER INSERT ON movements WHEN $letter IS NOT NULL
            BEGIN INSERT OR REPLACE INTO movement_actors(movement_id,letter) VALUES(NEW.id,$letter); END""")
    }
}

object TeamSyncSchema {
    fun create(db: SQLiteDatabase) {
        fun addColumn(table: String, column: String) {
            val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                var found = false; while (c.moveToNext()) if (c.getString(1) == column) found = true; found
            }
            if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column TEXT DEFAULT NULL")
        }
        addColumn("items", "uid")
        addColumn("placements", "uid")
        backfillIds(db)
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_items_uid ON items(uid)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_placements_uid ON placements(uid)")
        // Every new article or structure gets a global id, whatever code inserted it.
        for (table in listOf("items", "placements")) db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS ${table}_uid AFTER INSERT ON $table WHEN NEW.uid IS NULL
            BEGIN UPDATE $table SET uid=lower(hex(randomblob(16))) WHERE id=NEW.id; END""")
        db.execSQL("CREATE TABLE IF NOT EXISTS team_sync_settings(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_outbox(
            seq INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('ITEM','MOVE','LOCATE')),
            ref_id INTEGER NOT NULL, created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_peers(
            device_uid TEXT PRIMARY KEY NOT NULL, letter TEXT NOT NULL UNIQUE,
            processed_seq INTEGER NOT NULL DEFAULT 0, last_import INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS team_sync_conflicts(
            id INTEGER PRIMARY KEY AUTOINCREMENT, letter TEXT NOT NULL, item TEXT NOT NULL,
            detail TEXT NOT NULL, created_at INTEGER NOT NULL)""")
        // Outbox triggers: active only on a satellite, only for the synced warehouse, and never
        // while a central update is being applied (that is not a local change to send back).
        val active = """(SELECT value FROM team_sync_settings WHERE key='role')='satellite'
            AND COALESCE((SELECT value FROM team_sync_settings WHERE key='applying'),'0')='0'"""
        val now = "CAST(strftime('%s','now') AS INTEGER)*1000"
        // 4.18: rebuilt at every open, so a new definition replaces the one an older version created.
        for (t in listOf("team_out_item", "team_out_move", "team_out_locate")) db.execSQL("DROP TRIGGER IF EXISTS $t")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_item AFTER INSERT ON items
            WHEN $active AND ${inTeam("NEW.warehouse_id")}
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('ITEM',NEW.id,$now); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_move AFTER INSERT ON movements
            WHEN $active AND ${inTeam("(SELECT warehouse_id FROM items WHERE id=NEW.item_id)")}
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('MOVE',NEW.id,$now); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_out_locate AFTER UPDATE OF placement_id,shelf_level,shelf_slot ON items
            WHEN $active AND ${inTeam("NEW.warehouse_id")} AND (OLD.placement_id IS NOT NEW.placement_id
                OR OLD.shelf_level IS NOT NEW.shelf_level OR OLD.shelf_slot IS NOT NEW.shelf_slot)
            BEGIN INSERT INTO team_sync_outbox(kind,ref_id,created_at) VALUES('LOCATE',NEW.id,$now); END""")
        createScopeTriggers(db)
    }
    /**
     * 4.9 departments: on a satellite the central may limit new articles, loads and moves to some
     * categories (setting "scope", one category per line; empty = all). Withdrawals stay free.
     * Enforced here for every code path; the central checks again when importing.
     */
    fun createScopeTriggers(db: SQLiteDatabase) {
        fun s(key: String) = "(SELECT value FROM team_sync_settings WHERE key='$key')"
        val active = "${s("role")}='satellite' AND COALESCE(${s("applying")},'0')='0'"
        for (t in listOf("team_scope_item", "team_scope_category", "team_scope_load", "team_scope_locate")) db.execSQL("DROP TRIGGER IF EXISTS $t")
        fun outside(category: String) = "COALESCE(${s("scope")},'')<>'' AND instr(char(10)||${s("scope")}||char(10), char(10)||$category||char(10))=0"
        val message = "Fuori dal tuo reparto: qui puoi solo prelevare. Per il resto chiedi al telefono centrale"
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_item BEFORE INSERT ON items
            WHEN $active AND ${inTeam("NEW.warehouse_id")} AND ${outside("NEW.category")}
            BEGIN SELECT RAISE(ABORT,'Fuori dal tuo reparto: non puoi creare articoli di questa categoria'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_category BEFORE UPDATE OF category ON items
            WHEN $active AND ${inTeam("NEW.warehouse_id")} AND ${outside("NEW.category")}
            BEGIN SELECT RAISE(ABORT,'Fuori dal tuo reparto: categoria non consentita'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_load BEFORE INSERT ON movements
            WHEN $active AND NEW.delta>0 AND COALESCE(${s("returning")},'0')='0'
                AND EXISTS(SELECT 1 FROM items i WHERE i.id=NEW.item_id AND ${inTeam("i.warehouse_id")} AND ${outside("i.category")})
            BEGIN SELECT RAISE(ABORT,'$message'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS team_scope_locate BEFORE UPDATE OF placement_id,shelf_level,shelf_slot ON items
            WHEN $active AND ${inTeam("NEW.warehouse_id")} AND ${outside("OLD.category")} AND (OLD.placement_id IS NOT NEW.placement_id
                OR OLD.shelf_level IS NOT NEW.shelf_level OR OLD.shelf_slot IS NOT NEW.shelf_slot)
            BEGIN SELECT RAISE(ABORT,'$message'); END""")
    }
    /**
     * 4.18: true when [expr] is one of the sites shared with the team: the first one ("warehouse_id")
     * or any other of the company ("warehouse_ids", written as ",1,5,").
     */
    fun inTeam(expr: String): String {
        fun s(key: String) = "(SELECT value FROM team_sync_settings WHERE key='$key')"
        return "instr(COALESCE(${s("warehouse_ids")}, ','||${s("warehouse_id")}||','), ','||$expr||',')>0"
    }
    fun backfillIds(db: SQLiteDatabase) {
        for (table in listOf("items", "placements"))
            db.execSQL("UPDATE $table SET uid=lower(hex(randomblob(16))) WHERE uid IS NULL")
    }
    fun setting(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM team_sync_settings WHERE key=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    fun put(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("team_sync_settings", null, ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun satelliteLetter(db: SQLiteDatabase): String? =
        if (setting(db, "role") == "satellite") setting(db, "letter") else null
}
