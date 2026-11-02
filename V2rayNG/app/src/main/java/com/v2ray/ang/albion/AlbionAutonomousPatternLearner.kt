package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Bounded online learner for observable Photon event shapes; never guesses semantic names. */
class AlbionAutonomousPatternLearner(context: Context, private val captureSessionId: String = System.currentTimeMillis().toString()) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val patterns = LinkedHashMap<String, JSONObject>()
    private val recent = ArrayDeque<Pair<Long, String>>()
    private var activeLabel: JSONObject? = null
    private var dirty = false
    private var lastFlush = 0L

    init { load() }

    @Synchronized fun observe(timestampMs: Long, eventCode: Int, parameters: Map<Int, Any?>) {
        if (eventCode < 0) return
        val shape = parameters.keys.sorted().joinToString(",") { key -> "$key:${shapeOf(parameters[key])}" }
        val signature = sha256("$eventCode|$shape")
        val record = patterns.getOrPut(signature) {
            JSONObject().put("signature", signature).put("event_code", eventCode)
                .put("parameter_shape", shape).put("first_seen_ms", timestampMs)
                .put("sightings", 0).put("capture_sessions", JSONArray())
                .put("labels", JSONArray()).put("status", "unknown_candidate")
        }
        record.put("last_seen_ms", timestampMs).put("sightings", record.optInt("sightings") + 1)
        addUnique(record.optJSONArray("capture_sessions") ?: JSONArray().also { record.put("capture_sessions", it) }, captureSessionId, 12)
        recent.addLast(timestampMs to signature)
        while (recent.size > MAX_RECENT_EVENTS) recent.removeFirst()
        activeLabel?.let { label ->
            val start = label.optLong("started_at_ms")
            if (timestampMs >= start && timestampMs - start <= MAX_LABEL_WINDOW_MS) {
                label.optJSONArray("signatures")?.let { addUnique(it, signature, MAX_LABEL_SIGNATURES) }
                label.put("event_count", label.optInt("event_count") + 1)
            }
        }
        updateStatus(record)
        while (patterns.size > MAX_PATTERN_RECORDS) {
            val oldest = patterns.entries.minByOrNull { it.value.optLong("last_seen_ms") } ?: break
            patterns.remove(oldest.key)
        }
        dirty = true
        if (timestampMs - lastFlush >= FLUSH_INTERVAL_MS) flush(timestampMs)
    }

    /** Invoke only after marker append succeeds in the PCAP and JSONL sinks. */
    @Synchronized fun observeMarker(marker: JSONObject, timestampMs: Long) {
        when (marker.optString("event")) {
            "target_found", "activity_start", "pvp_started", "movement_started" -> {
                closeActive(timestampMs)
                val label = JSONObject().put("activity", marker.optString("activity", "Unknown"))
                    .put("tier", marker.optString("tier", "")).put("enchantment", marker.optString("enchantment", ""))
                    .put("started_at_ms", timestampMs).put("signatures", JSONArray()).put("event_count", 0)
                recent.filter { timestampMs >= it.first && timestampMs - it.first <= PRE_ROLL_MS }
                    .forEach { pair -> label.optJSONArray("signatures")?.let { addUnique(it, pair.second, MAX_LABEL_SIGNATURES) } }
                activeLabel = label
            }
            "gathering_completed", "mob_killed", "activity_end", "pvp_ended", "movement_completed" -> closeActive(timestampMs)
        }
        dirty = true
        flush(timestampMs)
    }

    @Synchronized fun flush() = flush(System.currentTimeMillis())

    @Synchronized fun snapshotJson(): String {
        val array = JSONArray()
        patterns.values.sortedByDescending { it.optInt("sightings") }.take(MAX_EXPORTED_PATTERNS)
            .forEach { array.put(JSONObject(it.toString())) }
        return array.toString()
    }

    @Synchronized fun summaryText(): String {
        val all = patterns.values
        val repeated = all.count { it.optInt("sightings") >= MIN_REPEATED_SIGHTINGS }
        val labeled = all.count { (it.optJSONArray("labels")?.length() ?: 0) > 0 }
        return "\nAutonomous signatures: ${all.size} | repeated: $repeated | marker-correlated: $labeled\nUnknown signatures remain candidates; no semantic names are guessed."
    }

    private fun closeActive(timestampMs: Long) {
        val label = activeLabel ?: return
        val duration = timestampMs - label.optLong("started_at_ms")
        if (duration in 1..MAX_LABEL_WINDOW_MS) {
            val labelKey = listOf(label.optString("activity"), label.optString("tier"), label.optString("enchantment")).joinToString("|")
            val signatures = label.optJSONArray("signatures") ?: JSONArray()
            for (i in 0 until signatures.length()) {
                val record = patterns[signatures.optString(i)] ?: continue
                val labels = record.optJSONArray("labels") ?: JSONArray().also { record.put("labels", it) }
                var entry: JSONObject? = null
                for (j in 0 until labels.length()) {
                    val candidate = labels.optJSONObject(j)
                    if (candidate?.optString("label_key") == labelKey) { entry = candidate; break }
                }
                val sample = entry ?: JSONObject().put("label_key", labelKey)
                    .put("activity", label.optString("activity")).put("tier", label.optString("tier"))
                    .put("enchantment", label.optString("enchantment")).put("completed_samples", 0).also { labels.put(it) }
                sample.put("completed_samples", sample.optInt("completed_samples") + 1).put("last_sample_ms", timestampMs)
                updateStatus(record)
            }
        }
        activeLabel = null
    }

    private fun updateStatus(record: JSONObject) {
        val sessions = record.optJSONArray("capture_sessions")?.length() ?: 0
        val labels = record.optJSONArray("labels") ?: JSONArray()
        val strongest = (0 until labels.length()).mapNotNull { labels.optJSONObject(it) }.maxOfOrNull { it.optInt("completed_samples") } ?: 0
        val sightings = record.optInt("sightings")
        val status = when {
            strongest >= MIN_LABEL_SAMPLES && sessions >= 2 -> "label_corroborated_candidate"
            sightings >= MIN_REPEATED_SIGHTINGS && sessions >= MIN_CAPTURE_SESSIONS -> "repeated_candidate"
            sightings >= MIN_REPEATED_SIGHTINGS -> "repeated_single_session_candidate"
            else -> "unknown_candidate"
        }
        record.put("status", status).put("heuristic_evidence", minOf(100, sightings * 2 + sessions * 8 + strongest * 12))
            .put("evidence_semantics", "ordinal heuristic; not a probability or semantic classification")
    }

    private fun shapeOf(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> "bool"
        is Byte, is Short, is Int, is Long -> "integer"
        is Float, is Double -> "float"
        is String -> "string"
        is ByteArray -> "bytes"
        is ShortArray, is IntArray, is LongArray, is FloatArray, is DoubleArray -> "numeric_array"
        is Map<*, *> -> "map"
        is List<*> -> "list"
        else -> value.javaClass.simpleName
    }

    private fun addUnique(array: JSONArray, value: String, max: Int) {
        for (i in 0 until array.length()) if (array.optString(i) == value) return
        if (array.length() < max) array.put(value)
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString("signature")
                if (key.isNotBlank()) patterns[key] = JSONObject(item.toString())
            }
        } catch (_: Exception) { patterns.clear() }
    }

    private fun flush(nowMs: Long) {
        if (!dirty) return
        try {
            val array = JSONArray()
            patterns.values.forEach { array.put(it) }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(array.toString(), Charsets.UTF_8)
            if (!temp.renameTo(file)) error("Cannot atomically replace autonomous pattern cache")
            dirty = false
            lastFlush = nowMs
        } catch (_: Exception) { /* Keep dirty for retry; never interrupt capture. */ }
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE_NAME = "albion-autonomous-patterns.json"
        const val MAX_RECENT_EVENTS = 2048
        const val MAX_PATTERN_RECORDS = 5000
        const val MAX_EXPORTED_PATTERNS = 5000
        const val MAX_LABEL_SIGNATURES = 512
        const val MAX_LABEL_WINDOW_MS = 15 * 60_000L
        const val PRE_ROLL_MS = 3_000L
        const val FLUSH_INTERVAL_MS = 3_000L
        const val MIN_REPEATED_SIGHTINGS = 10
        const val MIN_CAPTURE_SESSIONS = 2
        const val MIN_LABEL_SAMPLES = 3
    }
}
