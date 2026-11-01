package com.v2ray.ang.albion

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps bounded snapshots of decoded Photon parameters. This exposes state changes for later
 * schema discovery without falsely labeling an unknown event as an inventory update.
 */
class AlbionEventStateTracker {
    private val previousByEvent = HashMap<Int, JSONObject>()
    private val countsByEvent = HashMap<Int, Int>()

    fun reset() {
        previousByEvent.clear()
        countsByEvent.clear()
    }

    fun observe(eventCode: Int, parameters: Map<Int, Any?>): JSONObject {
        val current = JSONObject()
        parameters.toSortedMap().forEach { (key, value) -> current.put(key.toString(), encode(value, 0)) }
        val previous = previousByEvent[eventCode]
        val changed = JSONArray()
        val keys = LinkedHashSet<String>()
        val currentKeys = current.keys()
        while (currentKeys.hasNext()) keys.add(currentKeys.next())
        if (previous != null) {
            val oldKeys = previous.keys()
            while (oldKeys.hasNext()) keys.add(oldKeys.next())
        }
        keys.sorted().forEach { key ->
            if (previous == null || previous.opt(key).toString() != current.opt(key).toString()) changed.put(key)
        }
        previousByEvent[eventCode] = current
        val count = (countsByEvent[eventCode] ?: 0) + 1
        countsByEvent[eventCode] = count
        return JSONObject()
            .put("event_code", eventCode)
            .put("occurrence", count)
            .put("changed_parameter_keys", changed)
            .put("parameter_snapshot", current)
            .put("inventory_classification", "unknown_schema")
    }

    private fun encode(value: Any?, depth: Int): Any? {
        if (value == null) return JSONObject.NULL
        if (depth >= 4) return value.javaClass.simpleName
        return when (value) {
            is String, is Number, is Boolean -> value
            is ByteArray -> JSONObject().put("type", "bytes").put("length", value.size)
                .put("base64", Base64.encodeToString(value.copyOf(minOf(value.size, 256)), Base64.NO_WRAP))
            is ShortArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it.toInt()) } }
            is IntArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it) } }
            is LongArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it) } }
            is FloatArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it.toDouble()) } }
            is DoubleArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it) } }
            is BooleanArray -> JSONArray().also { a -> value.take(256).forEach { a.put(it) } }
            is Map<*, *> -> JSONObject().also { o ->
                value.entries.take(128).forEach { (k, v) -> o.put(k?.toString() ?: "null", encode(v, depth + 1)) }
            }
            is Iterable<*> -> JSONArray().also { a -> value.take(256).forEach { a.put(encode(it, depth + 1)) } }
            is Array<*> -> JSONArray().also { a -> value.take(256).forEach { a.put(encode(it, depth + 1)) } }
            else -> JSONObject().put("type", value.javaClass.simpleName)
        }
    }
}
