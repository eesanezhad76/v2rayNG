package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap

/**
 * Persistent, conservative live knowledge cache.
 *
 * It records observations emitted by the existing typed event mapper. It never
 * promotes a family/type to an Ore name by proximity or by a single sighting.
 */
class AlbionLiveKnowledgeStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "albion-live-knowledge.json")
    private val records = LinkedHashMap<String, JSONObject>()
    private var dirty = false
    private var lastFlushMs = 0L
    private val sessionStartedMs = System.currentTimeMillis()

    init {
        load()
    }

    @Synchronized
    fun observe(timestampMs: Long, observations: List<AlbionEntityEventMapper.Observation>) {
        observations.forEach { observation ->
            if (observation.kind !in TRACKED_KINDS) return@forEach
            val id = observation.entityId ?: return@forEach
            val key = id.toString()
            val record = records.getOrPut(key) {
                JSONObject()
                    .put("entity_id", key)
                    .put("first_seen_ms", timestampMs)
                    .put("sightings", 0)
            }
            record.put("last_seen_ms", timestampMs)
            record.put("sightings", record.optInt("sightings") + 1)
            record.put("last_event_code", observation.eventCode)
            record.put("last_kind", observation.kind)
            observation.typeId?.let { record.put("mobile_type_id", it) }
            observation.typeNumber?.let { record.put("family", it) }
            observation.tier?.let { if (it in 1..8) record.put("tier", it) }
            observation.enchant?.let { if (it in 0..4) record.put("enchant", it) }
            observation.size?.let { record.put("amount", it) }
            observation.name?.let { record.put("name", it) }
            val x = observation.x?.toDouble()
            val y = observation.y?.toDouble()
            if (x != null && y != null && x.isFinite() && y.isFinite() &&
                kotlin.math.abs(x) <= MAX_COORDINATE && kotlin.math.abs(y) <= MAX_COORDINATE) {
                record.put("x", x).put("y", y)
            }
            if (observation.kind == "resource_state") {
                record.put("depleted", observation.removed)
                record.put("resource_state_seen", true)
            }
            if (observation.kind == "harvestable") record.put("typed_resource_seen", true)
            observation.heading?.let { if (it.isFinite() && it in 0f..360f) record.put("heading", it) }
            observation.speed?.let { if (it.isFinite() && it in 0f..100f) record.put("speed", it) }
            val targetX = observation.targetX?.toDouble()
            val targetY = observation.targetY?.toDouble()
            if (targetX != null && targetY != null && targetX.isFinite() && targetY.isFinite() &&
                kotlin.math.abs(targetX) <= MAX_COORDINATE && kotlin.math.abs(targetY) <= MAX_COORDINATE) {
                record.put("target_x", targetX).put("target_y", targetY)
            }
            record.put("classification", classification(record))
            record.put("evidence_grade", evidenceGrade(record))
            dirty = true
        }
        // Bound storage and avoid disk I/O for every packet in a busy scene.
        while (records.size > MAX_RECORDS) {
            val oldest = records.entries.minByOrNull { it.value.optLong("last_seen_ms") } ?: break
            records.remove(oldest.key)
            dirty = true
        }
        if (dirty && timestampMs - lastFlushMs >= FLUSH_INTERVAL_MS) flush(timestampMs)
    }

    @Synchronized
    fun flush() = flush(System.currentTimeMillis())

    @Synchronized
    fun snapshotJson(): String {
        val array = JSONArray()
        records.values
            .filter { it.optLong("last_seen_ms") >= sessionStartedMs && it.has("x") && it.has("y") }
            .sortedByDescending { it.optLong("last_seen_ms") }
            .take(200)
            .forEach { array.put(JSONObject(it.toString())) }
        return array.toString()
    }

    @Synchronized
    fun snapshotText(): String {
        val recent = records.values
            .filter { it.optLong("last_seen_ms") >= sessionStartedMs &&
                it.optString("last_kind") in setOf("static_resource", "harvestable", "resource_state", "player_move", "own_player_move", "mob") }
            .sortedByDescending { it.optLong("last_seen_ms") }
            .take(8)
        if (recent.isEmpty()) return "Live entities: waiting for decoded game observations"
        return buildString {
            appendLine("LIVE ENTITY SNAPSHOT")
            recent.forEach { record ->
                val kind = record.optString("last_kind")
                val label = when (kind) {
                    "static_resource", "harvestable", "resource_state" -> "Resource"
                    "player_move" -> "Player movement"
                    "own_player_move" -> "YOU"
                    "mob" -> "Mob"
                    else -> kind
                }
                val tier = if (record.has("tier")) " T${record.optInt("tier")}" else ""
                val enchant = if (record.has("enchant") && record.optInt("enchant") > 0) " E${record.optInt("enchant")}" else ""
                val amount = if (record.has("amount")) " amount=${record.optInt("amount")}" else ""
                val position = if (record.has("x") && record.has("y")) " (${"%.1f".format(java.util.Locale.US, record.optDouble("x"))}, ${"%.1f".format(java.util.Locale.US, record.optDouble("y"))})" else ""
                val state = if (record.optBoolean("depleted", false)) " [depleted]" else ""
                val evidence = record.optString("evidence_grade", "unclassified")
                appendLine("$label$tier$enchant$amount$position$state [$evidence]")
            }
            append("Showing ${recent.size} recent observations; proximity alone is not confirmation.")
        }
    }

    @Synchronized
    fun summaryText(): String {
        val typed = records.values.count { it.optString("classification") == "typed_resource_observation" }
        val candidates = records.values.count { it.optString("classification") == "static_resource_candidate" }
        val depleted = records.values.count { it.optBoolean("depleted", false) }
        val corroborated = records.values.count { it.optString("evidence_grade") == "corroborated_same_id" }
        val repeated = records.values.count { it.optString("evidence_grade") == "repeated_typed" }
        return buildString {
            appendLine()
            appendLine("Live knowledge cache")
            appendLine("Unique tracked entities: ${records.size}")
            appendLine("Typed resource observations: $typed")
            appendLine("Static resource candidates (not confirmed Ore): $candidates")
            appendLine("Entities marked depleted: $depleted")
            appendLine("Corroborated by same-ID resource state: $corroborated")
            appendLine("Repeated typed resource observations: $repeated")
            appendLine("Evidence grades are ordinal rules, not calibrated probabilities.")
            appendLine("Persistent file: ${file.name}")
            appendLine("Classification is conservative; no family-to-Ore guesses are made.")
        }
    }

    private fun evidenceGrade(record: JSONObject): String {
        val typed = record.has("mobile_type_id") && record.has("tier") && record.has("x") && record.has("y")
        return when {
            typed && record.optBoolean("resource_state_seen", false) -> "corroborated_same_id"
            typed && record.optInt("sightings") >= 3 -> "repeated_typed"
            typed -> "typed_single"
            record.optString("last_kind") == "static_resource" && record.optInt("sightings") >= 3 -> "repeated_candidate"
            record.optString("last_kind") == "static_resource" -> "candidate"
            else -> "unclassified"
        }
    }

    private fun classification(record: JSONObject): String = when {
        record.has("mobile_type_id") && record.has("tier") && record.has("x") && record.has("y") ->
            "typed_resource_observation"
        record.optString("last_kind") == "static_resource" && record.has("x") && record.has("y") ->
            "static_resource_candidate"
        else -> "unclassified_observation"
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("entity_id")
                if (id.isNotBlank() && id != "null") records[id] = JSONObject(item.toString())
            }
        } catch (_: Exception) {
            // A corrupt cache is ignored; live capture continues and a fresh cache is written.
            records.clear()
        }
    }

    private fun flush(nowMs: Long) {
        if (!dirty) return
        try {
            val array = JSONArray()
            records.values.forEach { array.put(it) }
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(array.toString(), Charsets.UTF_8)
            // renameTo is atomic on the app data filesystem and replaces the prior snapshot.
            if (!temp.renameTo(file)) error("Cannot atomically replace knowledge cache")
            dirty = false
            lastFlushMs = nowMs
        } catch (_: Exception) {
            // Keep dirty=true so a later flush retries without interrupting packet capture.
        }
    }

    private companion object {
        const val MAX_RECORDS = 5000
        const val MAX_COORDINATE = 10000.0
        const val FLUSH_INTERVAL_MS = 2000L
        val TRACKED_KINDS = setOf("static_resource", "harvestable", "resource_state", "mob", "leave", "player_move", "own_player_move")
    }
}
