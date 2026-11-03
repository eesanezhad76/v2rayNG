package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Learns enchantment labels from completed, manually labelled gathering cycles. */
class AlbionGatheringPatternLearner(context: Context, private val captureSessionId: String) {
    private val file = File(context.applicationContext.filesDir, "albion-gathering-patterns.json")
    private val patterns = LinkedHashMap<String, JSONObject>()
    private var active: JSONObject? = null
    private val activeSamples = ArrayList<JSONObject>()
    private var dirty = false

    init { loadAndRegister() }

    @Synchronized
    fun observe(timestampMs: Long, observations: List<AlbionEntityEventMapper.Observation>) {
        val label = active ?: return
        if (label.optString("activity") !in GATHERING_ACTIVITIES) return
        val expectedTier = label.optString("tier").removePrefix("T").toIntOrNull() ?: return
        observations.filter { it.kind == "harvestable" }.forEach { observation ->
            val family = observation.typeNumber ?: return@forEach
            val tier = observation.tier ?: return@forEach
            val rawEnchant = observation.enchant ?: return@forEach
            val x = observation.x ?: return@forEach
            val y = observation.y ?: return@forEach
            if (tier != expectedTier || !x.isFinite() || !y.isFinite()) return@forEach
            if (activeSamples.size >= MAX_ACTIVE_SAMPLES) return@forEach
            activeSamples.add(JSONObject().put("family", family).put("tier", tier)
                .put("raw_enchant", rawEnchant)
                .put("label_enchant", normalizeEnchant(label.optString("enchantment")))
                .put("time_ms", timestampMs))
        }
    }

    @Synchronized
    fun observeMarker(marker: JSONObject, timestampMs: Long) {
        when (marker.optString("event")) {
            "target_found", "activity_start" -> {
                active = JSONObject().put("activity", marker.optString("activity"))
                    .put("tier", marker.optString("tier"))
                    .put("enchantment", marker.optString("enchantment"))
                    .put("started_at_ms", timestampMs)
                activeSamples.clear()
            }
            "gathering_completed", "activity_end" -> {
                if (active != null && timestampMs > active!!.optLong("started_at_ms")) {
                    activeSamples.forEach { sample ->
                        val key = listOf(sample.optInt("family"), sample.optInt("tier"),
                            sample.optInt("raw_enchant"), sample.optString("label_enchant")).joinToString("|")
                        val record = patterns.getOrPut(key) {
                            JSONObject().put("family", sample.optInt("family"))
                                .put("tier", sample.optInt("tier"))
                                .put("raw_enchant", sample.optInt("raw_enchant"))
                                .put("label_enchant", sample.optString("label_enchant"))
                                .put("samples", 0).put("capture_sessions", JSONArray())
                                .put("first_seen_ms", timestampMs)
                        }
                        record.put("samples", record.optInt("samples") + 1).put("last_seen_ms", timestampMs)
                        val sessions = record.optJSONArray("capture_sessions") ?: JSONArray().also {
                            record.put("capture_sessions", it)
                        }
                        if ((0 until sessions.length()).none { sessions.optString(it) == captureSessionId } &&
                            sessions.length() < MAX_SESSIONS) sessions.put(captureSessionId)
                    }
                    dirty = dirty || activeSamples.isNotEmpty()
                    persistAndRegister()
                }
                active = null
                activeSamples.clear()
            }
        }
    }

    @Synchronized
    fun summaryText(): String {
        val eligible = eligibleMappings().size
        val samples = patterns.values.sumOf { it.optInt("samples") }
        return "\nGathering pattern learner: $samples labelled node samples | $eligible validated enchant mappings | active samples ${activeSamples.size}"
    }

    private fun eligibleMappings(): Map<String, String> {
        val groups = patterns.values.groupBy {
            "${it.optInt("family")}|${it.optInt("tier")}|${it.optInt("raw_enchant")}"
        }
        val result = LinkedHashMap<String, String>()
        groups.forEach { (base, rows) ->
            val labels = rows.filter {
                it.optInt("samples") >= MIN_SAMPLES &&
                    (it.optJSONArray("capture_sessions")?.length() ?: 0) >= MIN_SESSIONS
            }
            if (labels.size == 1) result[base] = labels.single().optString("label_enchant")
        }
        return result
    }

    private fun persistAndRegister() {
        eligibleMappings().forEach { (key, label) ->
            val parts = key.split("|")
            if (parts.size == 3) ResourcePatternLearner.registerLearnedEnchantment(
                parts[0].toIntOrNull() ?: return@forEach,
                parts[1].toIntOrNull() ?: return@forEach,
                parts[2].toIntOrNull() ?: return@forEach,
                label
            )
        }
        if (!dirty) return
        try {
            val array = JSONArray()
            patterns.values.forEach { array.put(it) }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(array.toString(), Charsets.UTF_8)
            if (!temp.renameTo(file)) error("Cannot replace gathering pattern cache")
            dirty = false
        } catch (_: Exception) { dirty = true }
    }

    private fun loadAndRegister() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = listOf(item.optInt("family"), item.optInt("tier"),
                    item.optInt("raw_enchant"), item.optString("label_enchant")).joinToString("|")
                patterns[key] = JSONObject(item.toString())
            }
            eligibleMappings().forEach { (key, label) ->
                val parts = key.split("|")
                if (parts.size == 3) ResourcePatternLearner.registerLearnedEnchantment(
                    parts[0].toIntOrNull() ?: return@forEach,
                    parts[1].toIntOrNull() ?: return@forEach,
                    parts[2].toIntOrNull() ?: return@forEach,
                    label
                )
            }
        } catch (_: Exception) { patterns.clear() }
    }

    private fun normalizeEnchant(value: String): String = when (value.uppercase()) {
        "NORMAL", "0" -> "Normal"
        "E1", "1" -> "E1"
        "E2", "2" -> "E2"
        "E3", "3" -> "E3"
        else -> value
    }

    private companion object {
        const val MAX_ACTIVE_SAMPLES = 500
        const val MAX_SESSIONS = 20
        const val MIN_SAMPLES = 3
        const val MIN_SESSIONS = 2
        val GATHERING_ACTIVITIES = setOf("Ore", "Wood", "Stone", "Fiber", "Hide")
    }
}
