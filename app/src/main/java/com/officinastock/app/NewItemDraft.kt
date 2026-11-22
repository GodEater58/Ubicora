package com.officinastock.app

import android.content.Context
import org.json.JSONObject

/**
 * 4.21: the article being created, step by step. It lives in the ViewModel, so the screen lock
 * (PIN) does not lose it, and it is also kept on the phone, sealed by the Android Keystore,
 * so it comes back even if Android closed the app meanwhile. Only "Annulla" or "Salva" end it.
 */
data class NewItemDraft(
    val warehouseId: Long, val step: Int = 1, val name: String = "", val category: String = "",
    val qty: String = "0", val min: String = "0", val unit: String = "pz", val note: String = "",
    val cost: String = "", val supplier: String = "", val lead: String = "", val photoUri: String? = null,
    val copiedFrom: Long? = null, val place: NewItemPlace? = null, val moreDetails: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().put("w", warehouseId).put("step", step).put("name", name).put("category", category)
        .put("qty", qty).put("min", min).put("unit", unit).put("note", note).put("cost", cost).put("supplier", supplier)
        .put("lead", lead).put("photo", photoUri ?: JSONObject.NULL).put("copied", copiedFrom ?: JSONObject.NULL)
        .put("more", moreDetails).put("place", place?.let { p ->
            JSONObject().put("id", p.placementId ?: JSONObject.NULL).put("level", p.level ?: JSONObject.NULL)
                .put("slot", p.slot ?: JSONObject.NULL).put("map", p.onMap) } ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): NewItemDraft {
            fun long(key: String, from: JSONObject = o) = if (from.isNull(key)) null else from.getLong(key)
            fun int(key: String, from: JSONObject) = if (from.isNull(key)) null else from.getInt(key)
            val place = o.optJSONObject("place")?.let { p -> NewItemPlace(long("id", p), int("level", p), int("slot", p), p.optBoolean("map")) }
            return NewItemDraft(o.getLong("w"), o.optInt("step", 1).coerceIn(1, 3), o.optString("name"), o.optString("category"),
                o.optString("qty", "0"), o.optString("min", "0"), o.optString("unit", "pz"), o.optString("note"), o.optString("cost"),
                o.optString("supplier"), o.optString("lead"), if (o.isNull("photo")) null else o.optString("photo"),
                long("copied"), place, o.optBoolean("more"))
        }
    }
}

/** 4.21: where the unfinished article waits (one per site), sealed like the team password. */
object NewItemDrafts {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("ubicora_drafts", Context.MODE_PRIVATE)
    private fun key(warehouseId: Long) = "new_item_$warehouseId"

    fun save(context: Context, draft: NewItemDraft) {
        prefs(context).edit().putString(key(draft.warehouseId), DeviceSecrets.seal(draft.toJson().toString())).apply()
    }
    fun load(context: Context, warehouseId: Long): NewItemDraft? {
        val stored = prefs(context).getString(key(warehouseId), null) ?: return null
        return runCatching { NewItemDraft.fromJson(JSONObject(requireNotNull(DeviceSecrets.open(stored)))) }
            .getOrNull()?.takeIf { it.warehouseId == warehouseId }
    }
    fun clear(context: Context, warehouseId: Long) { prefs(context).edit().remove(key(warehouseId)).apply() }
    /** 4.21.2: drafts and their photos belong to the archive they were made in; a restored backup drops them. */
    fun clearAll(context: Context) { prefs(context).edit().clear().commit() }
    /** 4.21.2: photos still used by a draft, so the cleanup of old camera files keeps them. */
    fun photoUris(context: Context): Set<String> = prefs(context).all.values.mapNotNull { stored ->
        runCatching { JSONObject(requireNotNull(DeviceSecrets.open(stored as String))).let { if (it.isNull("photo")) null else it.getString("photo") } }.getOrNull()
    }.toSet()
}
