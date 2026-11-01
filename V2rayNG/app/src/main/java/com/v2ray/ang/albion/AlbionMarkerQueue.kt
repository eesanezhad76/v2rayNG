package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Durable FIFO for UI annotations. Entries are removed only after the capture writer confirms them. */
object AlbionMarkerQueue {
    private const val PREFS = "albion_marker_queue_v1"
    private const val KEY = "pending"
    private val lock = Any()

    fun enqueue(context: Context, markerJson: String): String? = synchronized(lock) {
        try {
            val marker = JSONObject(markerJson)
            val id = marker.optString("marker_id").ifBlank { UUID.randomUUID().toString() }
            marker.put("marker_id", id)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val queue = JSONArray(prefs.getString(KEY, "[]"))
            if (queue.length() >= 4096) return null
            queue.put(marker)
            if (!prefs.edit().putString(KEY, queue.toString()).commit()) return null
            id
        } catch (_: Exception) { null }
    }

    fun pending(context: Context): List<JSONObject> = synchronized(lock) {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { item -> JSONObject(item.toString()) } }
    }

    fun acknowledge(context: Context, markerId: String): Boolean = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try { JSONArray(prefs.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("marker_id") != markerId) next.put(item)
        }
        prefs.edit().putString(KEY, next.toString()).commit()
    }

    fun count(context: Context): Int = pending(context).size
}
