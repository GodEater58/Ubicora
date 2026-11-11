package com.officinastock.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 4.10 display preferences of this phone (not part of the archive or of the sync):
 * light theme, glove mode, screenshots allowed, favourite articles.
 * Values are Compose state, so every screen follows a change at once.
 */
object UiPrefs {
    private var prefs: SharedPreferences? = null

    var light by mutableStateOf(false); private set
    var glove by mutableStateOf(false); private set
    var screenshots by mutableStateOf(false); private set
    /** 4.13: nearby phones of the team exchange on their own while the app is open. */
    var autoSync by mutableStateOf(true); private set
    /** 4.15: beep of the burst scanner (the vibration always stays). */
    var scanSound by mutableStateOf(true); private set
    /** 4.15: article list without photo and code, to see more rows. */
    var compactList by mutableStateOf(false); private set
    /** 4.19.3: the swipe hint above the articles was read and closed. */
    var swipeHintSeen by mutableStateOf(false); private set
    /** 4.15: days after which a tool on loan appears in "Da fare adesso". */
    var loanDays by mutableStateOf(7); private set
    /** "warehouseId:itemId" of the favourite articles. */
    var favorites by mutableStateOf(emptySet<String>()); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("ubicora_display", Context.MODE_PRIVATE)
        prefs = p
        light = p.getBoolean("light", false)
        glove = p.getBoolean("glove", false)
        screenshots = p.getBoolean("screenshots", false)
        autoSync = p.getBoolean("auto_sync", true)
        scanSound = p.getBoolean("scan_sound", true)
        compactList = p.getBoolean("compact_list", false)
        swipeHintSeen = p.getBoolean("swipe_hint_seen", false)
        loanDays = p.getInt("loan_days", 7)
        favorites = p.getStringSet("favorites", emptySet())!!.toSet()
    }

    fun updateLight(value: Boolean) { light = value; prefs?.edit()?.putBoolean("light", value)?.apply() }
    fun updateGlove(value: Boolean) { glove = value; prefs?.edit()?.putBoolean("glove", value)?.apply() }
    fun updateAutoSync(value: Boolean) { autoSync = value; prefs?.edit()?.putBoolean("auto_sync", value)?.apply() }
    fun updateScanSound(value: Boolean) { scanSound = value; prefs?.edit()?.putBoolean("scan_sound", value)?.apply() }
    fun hideSwipeHint() { swipeHintSeen = true; prefs?.edit()?.putBoolean("swipe_hint_seen", true)?.apply() }
    fun updateCompactList(value: Boolean) { compactList = value; prefs?.edit()?.putBoolean("compact_list", value)?.apply() }
    // 4.17: the site (warehouse) each company (profile) used last, opened again after the PIN.
    fun lastSite(companyId: Long): Long? = prefs?.getLong("last_site_$companyId", 0L)?.takeIf { it > 0 }
    fun rememberSite(companyId: Long, siteId: Long) { prefs?.edit()?.putLong("last_site_$companyId", siteId)?.apply() }
    fun updateLoanDays(value: Int) { loanDays = value; prefs?.edit()?.putInt("loan_days", value)?.apply() }
    fun updateScreenshots(value: Boolean) { screenshots = value; prefs?.edit()?.putBoolean("screenshots", value)?.apply() }

    private fun key(warehouseId: Long, itemId: Long) = "$warehouseId:$itemId"
    fun isFavorite(warehouseId: Long, itemId: Long) = key(warehouseId, itemId) in favorites
    fun toggleFavorite(warehouseId: Long, itemId: Long) {
        val k = key(warehouseId, itemId)
        favorites = if (k in favorites) favorites - k else favorites + k
        prefs?.edit()?.putStringSet("favorites", favorites)?.apply()
    }
}
