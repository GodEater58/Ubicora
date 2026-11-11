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

// 4.19.1: moved out of TeamSync.kt without changes — what the rest of the app sees of the team.

enum class TeamRole { NONE, CENTRAL, SATELLITE }

data class TeamPeer(val letter: String, val processedSeq: Long, val lastImport: Long)
data class TeamConflict(val id: Long, val letter: String, val item: String, val detail: String, val createdAt: Long)
data class TeamSyncStatus(
    val role: TeamRole = TeamRole.NONE, val warehouseId: Long? = null, val letter: String? = null,
    val pending: Int = 0, val lastSync: Long = 0, val peers: List<TeamPeer> = emptyList(),
    val conflicts: List<TeamConflict> = emptyList(),
    /** Satellite: its department (categories); empty = all. */
    val scope: List<String> = emptyList(),
    /** Central: departments per satellite letter. */
    val scopes: Map<String, List<String>> = emptyMap(),
    /** 4.11: the team password is not readable on this phone (e.g. backup restored on a new phone). */
    val needsPassword: Boolean = false,
    /** 4.18: the other sites of the company shared with the team (local ids), beside [warehouseId]. */
    val sites: List<Long> = emptyList(),
    /** 4.18, satellite: site numbers printed on the central's labels -> the same site on this phone. */
    val labelIds: Map<Long, Long> = emptyMap(),
    /** 4.15: tools on loan from the other phones of the team (read-only here: they are returned where they were lent). */
    val loans: List<TeamLoan> = emptyList()
)
/** 4.18: true when [id] is a site shared with the team. */
fun TeamSyncStatus.covers(id: Long?): Boolean = id != null && role != TeamRole.NONE && (id == warehouseId || id in sites)
data class TeamLoan(val letter: String, val itemId: Long?, val itemName: String, val borrower: String, val quantity: Double, val createdAt: Long)
data class TeamImportResult(val articles: Int, val movements: Int, val locations: Int, val skipped: Int, val conflicts: Int,
                            val letter: String? = null, val removed: Int = 0)
