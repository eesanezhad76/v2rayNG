package com.v2ray.ang.albion

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Durable FIFO for local overlay annotations.
 *
 * A malformed persisted queue is treated as corruption, never as an empty queue. This is
 * deliberately fail-closed: callers must not overwrite unreadable evidence with a fresh list.
 */
object AlbionMarkerQueue {
    private const val PREFS = "albion_marker_queue_v1"
    private const val KEY = "pending"
    private const val CAPACITY = 4096
    private val lock = Any()

    fun enqueue(context: Context, markerJson: String): String? = synchronized(lock) {
        val marker = runCatching { JSONObject(markerJson) }.getOrNull() ?: return@synchronized null
        val requestedId = marker.optString("marker_id").takeIf { it.isNotBlank() }
        val id = requestedId ?: UUID.randomUUID().toString()
        marker.put("marker_id", id)

        val prefs = preferences(context) ?: return@synchronized null
        val queue = readQueue(prefs) ?: return@synchronized null
        for (i in 0 until queue.length()) {
            val existing = queue.optJSONObject(i) ?: return@synchronized null
            if (existing.optString("marker_id") == id) {
                // A retry with the same durable payload is idempotent. Reusing an ID for
                // different content is rejected rather than silently replacing evidence.
                if (existing.toString() != marker.toString()) return@synchronized null
                // SharedPreferences.commit() may update its in-memory map even when disk
                // persistence fails. Re-commit the complete queue before reporting success.
                return@synchronized if (writeQueue(prefs, queue)) id else null
            }
        }
        if (queue.length() >= CAPACITY) return@synchronized null
        queue.put(marker)
        if (!writeQueue(prefs, queue)) return@synchronized null
        id
    }

    /**
     * Returns defensive copies. Throws on corrupt storage so callers can report the problem
     * without mistaking it for a genuinely empty queue.
     */
    fun pending(context: Context): List<JSONObject> = synchronized(lock) {
        val prefs = preferences(context) ?: throw IllegalStateException("Marker queue preferences unavailable")
        val queue = readQueue(prefs)
            ?: throw IllegalStateException("Marker queue data is corrupt; original data was preserved")
        (0 until queue.length()).map { index ->
            JSONObject(queue.getJSONObject(index).toString())
        }
    }

    /**
     * Removes an item only when the complete queue is valid and the commit succeeds.
     * A missing ID is an idempotent success; corrupt data is never rewritten.
     */
    fun acknowledge(context: Context, markerId: String): Boolean = synchronized(lock) {
        if (markerId.isBlank()) return@synchronized false
        val prefs = preferences(context) ?: return@synchronized false
        val old = readQueue(prefs) ?: return@synchronized false
        val next = JSONArray()
        var found = false
        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            if (item.optString("marker_id") == markerId) {
                found = true
            } else {
                next.put(item)
            }
        }
        // Commit even when the ID is already absent: a previous failed commit may have
        // changed the in-memory map without durably updating the backing file.
        writeQueue(prefs, next)
    }

    /** Returns -1 when storage is corrupt or unavailable; never reports corruption as empty. */
    fun count(context: Context): Int = synchronized(lock) {
        val prefs = preferences(context) ?: return@synchronized -1
        readQueue(prefs)?.length() ?: -1
    }

    private fun preferences(context: Context): SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    private fun readQueue(prefs: SharedPreferences): JSONArray? {
        return try {
            if (!prefs.contains(KEY)) {
                JSONArray()
            } else {
                val raw = prefs.getString(KEY, null)
                if (raw == null) {
                    null
                } else {
                    val array = JSONArray(raw)
                    if (array.length() > CAPACITY) {
                        null
                    } else {
                        val ids = HashSet<String>()
                        var valid = true
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i)
                            val id = item?.optString("marker_id")?.takeIf { it.isNotBlank() }
                            if (id == null || !ids.add(id)) {
                                valid = false
                                break
                            }
                        }
                        if (valid) array else null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeQueue(prefs: SharedPreferences, queue: JSONArray): Boolean = try {
        prefs.edit().putString(KEY, queue.toString()).commit()
    } catch (_: Exception) {
        false
    }
}
