package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists raw, bounded Photon evidence around gathering and inventory changes.
 * Inventory parameter layouts are deliberately not interpreted until verified.
 */
class AlbionProtocolEvidenceStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "albion-protocol-evidence.json")
    private val events = ArrayList<JSONObject>()
    private val sessionStartedMs = System.currentTimeMillis()
    private var dirty = false
    private var lastFlushMs = 0L

    init { load() }

    @Synchronized
    fun observe(timestampMs: Long, eventCode: Int, parameters: Map<Int, Any?>) {
        val kind = when (eventCode) {
            in INVENTORY_CODES -> "inventory"
            in HARVEST_CODES -> "harvest"
            39, 40, 46 -> "resource"
            else -> return
        }
        val payload = JSONObject()
        parameters.entries.sortedBy { it.key }.forEach { (key, value) ->
            payload.put(key.toString(), safeValue(value))
        }
        events.add(JSONObject()
            .put("time_ms", timestampMs)
            .put("event_code", eventCode)
            .put("kind", kind)
            .put("parameters", payload))
        while (events.size > MAX_EVENTS) events.removeAt(0)
        dirty = true
        if (timestampMs - lastFlushMs >= FLUSH_INTERVAL_MS) flush(timestampMs)
    }

    @Synchronized
    fun flush() = flush(System.currentTimeMillis())

    @Synchronized
    fun summaryText(): String {
        val current = events.filter { it.optLong("time_ms") >= sessionStartedMs }
        val inventory = current.filter { it.optString("kind") == "inventory" }
        val harvestFinished = current.filter {
            it.optString("kind") == "harvest" && it.optInt("event_code") == 61
        }
        val resources = current.filter {
            it.optString("kind") == "resource" && it.optInt("event_code") == 40
        }
        var paired = 0
        var resourceCandidates = 0
        val details = ArrayList<String>()
        inventory.forEach { inv ->
            val invTime = inv.optLong("time_ms")
            val finish = harvestFinished.minByOrNull { kotlin.math.abs(it.optLong("time_ms") - invTime) }
                ?.takeIf { kotlin.math.abs(it.optLong("time_ms") - invTime) <= PAIR_WINDOW_MS }
                ?: return@forEach
            paired++
            val finishTime = finish.optLong("time_ms")
            val resource = resources.minByOrNull { kotlin.math.abs(it.optLong("time_ms") - finishTime) }
                ?.takeIf { kotlin.math.abs(it.optLong("time_ms") - finishTime) <= RESOURCE_WINDOW_MS }
            if (resource != null) {
                resourceCandidates++
                val p = resource.optJSONObject("parameters") ?: JSONObject()
                val id = p.opt("0")
                val type = p.opt("5")
                val tier = p.opt("7")
                val enchant = p.opt("11")
                details.add("inventory event ${inv.optInt("event_code")} near HarvestFinished; resource candidate id=$id type=$type tier=$tier enchant=$enchant")
            }
        }
        return buildString {
            appendLine()
            appendLine("PROTOCOL / INVENTORY EVIDENCE (raw; item schema not yet verified)")
            appendLine("Inventory events this capture: ${inventory.size}")
            appendLine("HarvestFinished events this capture: ${harvestFinished.size}")
            appendLine("Inventory events within ${PAIR_WINDOW_MS / 1000}s of HarvestFinished: $paired")
            appendLine("With a nearby typed resource event (candidate only): $resourceCandidates")
            details.takeLast(5).forEach { appendLine(it) }
            append("No item ID, stack delta, or Ore identity is asserted from these raw events.")
        }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) array.optJSONObject(i)?.let { events.add(JSONObject(it.toString())) }
        } catch (_: Exception) { events.clear() }
    }

    private fun flush(nowMs: Long) {
        if (!dirty) return
        try {
            val array = JSONArray()
            events.forEach { array.put(it) }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(array.toString(), Charsets.UTF_8)
            if (!temp.renameTo(file)) error("Cannot replace protocol evidence cache")
            dirty = false
            lastFlushMs = nowMs
        } catch (_: Exception) {
            // Retain dirty state; capture must continue even if persistence fails.
        }
    }

    private fun safeValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is String, is Number, is Boolean -> value
        is ByteArray -> JSONArray(value.take(MAX_ARRAY_VALUES).map { it.toInt() and 0xff })
        is IntArray -> JSONArray(value.take(MAX_ARRAY_VALUES))
        is LongArray -> JSONArray(value.take(MAX_ARRAY_VALUES))
        is ShortArray -> JSONArray(value.take(MAX_ARRAY_VALUES).map { it.toInt() })
        is FloatArray -> JSONArray(value.take(MAX_ARRAY_VALUES).map { if (it.isFinite()) it else JSONObject.NULL })
        is DoubleArray -> JSONArray(value.take(MAX_ARRAY_VALUES).map { if (it.isFinite()) it else JSONObject.NULL })
        is Array<*> -> JSONArray(value.take(MAX_ARRAY_VALUES).map { safeValue(it) })
        is Iterable<*> -> JSONArray(value.take(MAX_ARRAY_VALUES).map { safeValue(it) })
        else -> value.toString().take(256)
    }

    private companion object {
        const val MAX_EVENTS = 2000
        const val MAX_ARRAY_VALUES = 128
        const val FLUSH_INTERVAL_MS = 2000L
        const val PAIR_WINDOW_MS = 15_000L
        const val RESOURCE_WINDOW_MS = 30_000L
        val INVENTORY_CODES = 26..37
        val HARVEST_CODES = setOf(59, 60, 61)
    }
}
