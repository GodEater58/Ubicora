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
    /** "warehouseId:itemId" of the favourite articles. */
    var favorites by mutableStateOf(emptySet<String>()); private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("ubicora_display", Context.MODE_PRIVATE)
        prefs = p
        light = p.getBoolean("light", false)
        glove = p.getBoolean("glove", false)
        screenshots = p.getBoolean("screenshots", false)
        favorites = p.getStringSet("favorites", emptySet())!!.toSet()
    }

    fun updateLight(value: Boolean) { light = value; prefs?.edit()?.putBoolean("light", value)?.apply() }
    fun updateGlove(value: Boolean) { glove = value; prefs?.edit()?.putBoolean("glove", value)?.apply() }
    fun updateScreenshots(value: Boolean) { screenshots = value; prefs?.edit()?.putBoolean("screenshots", value)?.apply() }

    private fun key(warehouseId: Long, itemId: Long) = "$warehouseId:$itemId"
    fun isFavorite(warehouseId: Long, itemId: Long) = key(warehouseId, itemId) in favorites
    fun toggleFavorite(warehouseId: Long, itemId: Long) {
        val k = key(warehouseId, itemId)
        favorites = if (k in favorites) favorites - k else favorites + k
        prefs?.edit()?.putStringSet("favorites", favorites)?.apply()
    }
}
