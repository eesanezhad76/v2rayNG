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
class AlbionLiveKnowledgeStore(
    context: Context,
    private val sessionStartedMs: Long = System.currentTimeMillis()
) {
    private val file = File(context.applicationContext.filesDir, "albion-live-knowledge.json")
    private val regionScanDir = File(context.applicationContext.filesDir, "albion-region-scans")
    // Exact server TypeID -> uniqueName/resource role map, sourced from the current
    // OpenRadar mobs.min.json dump (same source commit recorded in this asset).
    private val mobileTypeEntries: JSONObject = runCatching {
        context.applicationContext.assets.open("albion-mobile-type-dictionary.json")
            .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()).optJSONObject("entries") ?: JSONObject() }
    }.getOrDefault(JSONObject())
    private val pendingTrollMarkers = LinkedHashMap<String, JSONObject>()
    private var activeRegionId: String? = null
    private var regionScanId: String? = null
    private var regionScanRegionId: String? = null
    private var regionScanStartedMs = 0L
    private var regionScanEndsMs = 0L
    private var lastRegionScanFlushMs = 0L
    private val records = LinkedHashMap<String, JSONObject>()
    private var dirty = false
    private var lastFlushMs = 0L
    private var snapshotDirty = true
    private var cachedSnapshotJson: String? = null

    init {
        load()
    }

    @Synchronized
    fun observe(timestampMs: Long, observations: List<AlbionEntityEventMapper.Observation>) {
        observations.forEach { observation ->
            if (observation.kind == "region_change") {
                val newRegionId = observation.regionId?.takeIf { it.isNotBlank() }
                if (newRegionId != null && newRegionId != activeRegionId) {
                    activeRegionId = newRegionId
                    snapshotDirty = true
                    runCatching { beginRegionScan(timestampMs, newRegionId) }
                }
                return@forEach
            }
            val id = observation.entityId ?: return@forEach
            if (observation.kind == "health") {
                val regionKey = activeRegionId ?: ""
                val target = records.values
                    .filter {
                        it.optString("entity_id") == id.toString() &&
                            it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                            it.optString("region_id", "") == regionKey &&
                            it.optString("entity_class") == "mob"
                    }
                    .maxByOrNull { it.optLong("last_seen_ms") }
                    ?: records.getOrPut("$sessionStartedMs:$regionKey:health:$id") {
                        JSONObject().put("record_key", "$sessionStartedMs:$regionKey:health:$id")
                            .put("entity_id", id.toString()).put("session_started_ms", sessionStartedMs)
                            .put("first_seen_ms", timestampMs).put("sightings", 0)
                    }
                target.put("last_health_ms", timestampMs)
                target.put("health_event_count", target.optInt("health_event_count") + 1)
                observation.secondaryValue?.takeIf { it.isFinite() }?.let { target.put("health_value", it.toDouble()) }
                observation.value?.takeIf { it.isFinite() }?.let { target.put("health_delta", it.toDouble()) }
                observation.sourceEntityId?.let { target.put("last_health_source_entity_id", it.toString()) }
                target.put("health_observed", true)
                // Event 6 explicitly omits current HP when a mob dies. Event 7 is
                // batched; a missing value there is not enough evidence to call death.
                if (target.optString("entity_class") == "mob" &&
                    observation.eventCode == 6 &&
                    (observation.secondaryValue == null || observation.secondaryValue <= 0f)) {
                    target.put("mob_dead", true).put("mob_state", "dead").put("mob_dead_ms", timestampMs)
                } else if (target.optString("entity_class") == "mob" &&
                    observation.secondaryValue != null && observation.secondaryValue <= 0f) {
                    target.put("mob_dead", true).put("mob_state", "dead").put("mob_dead_ms", timestampMs)
                }
                dirty = true
                snapshotDirty = true
                return@forEach
            }
            if (observation.kind !in TRACKED_KINDS) return@forEach
            val entityId = id.toString()
            // Keep a stable record for each observed spawn generation. Runtime entity IDs can
            // be reused by the server after depletion; never overwrite the depleted E2/E3 node.
            val regionKey = activeRegionId ?: ""
            val baseKey = "$sessionStartedMs:$regionKey:$entityId"
            val latestEntry = records.entries
                .filter { it.value.optString("entity_id") == entityId &&
                    it.value.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                    it.value.optString("region_id", "") == regionKey }
                .maxByOrNull { it.value.optInt("spawn_generation", 0) }
            var key = latestEntry?.key ?: baseKey
            var record = records[key] ?: JSONObject()
                .put("record_key", key)
                .put("entity_id", entityId)
                .put("session_started_ms", sessionStartedMs)
                .put("first_seen_ms", timestampMs)
                .put("spawn_generation", 0)
                .put("sightings", 0).also {
                    if (activeRegionId != null) it.put("region_id", activeRegionId)
                    records[key] = it
                }

            val incomingAmount = observation.size ?: 0
            val confirmedRefill = observation.kind == "harvestable" && incomingAmount > 0
            if (record.optBoolean("depleted", false) && confirmedRefill) {
                val nextGeneration = record.optInt("spawn_generation", 0) + 1
                val nextKey = "$sessionStartedMs:$entityId:spawn:$nextGeneration"
                record.put("respawn_observed_at_ms", timestampMs)
                    .put("superseded_by_record_key", nextKey)
                key = nextKey
                record = JSONObject()
                    .put("record_key", key)
                    .put("entity_id", entityId)
                    .put("session_started_ms", sessionStartedMs)
                    .put("first_seen_ms", timestampMs)
                    .put("spawn_generation", nextGeneration)
                    .put("previous_spawn_record_key", latestEntry?.key ?: baseKey)
                    .put("sightings", 0)
                records[key] = record
            }
            record.put("last_seen_ms", timestampMs)
            activeRegionId?.let { record.put("region_id", it) }
            val sightingSignature = listOf(
                observation.kind, observation.eventCode, observation.typeId, observation.typeNumber,
                observation.tier, observation.enchant, observation.x, observation.y
            ).joinToString("|")
            val lastCountedAt = record.optLong("last_sighting_counted_ms", Long.MIN_VALUE)
            val signatureChanged = record.optString("last_sighting_signature") != sightingSignature
            val sufficientlySeparated = lastCountedAt == Long.MIN_VALUE ||
                (timestampMs >= lastCountedAt && timestampMs - lastCountedAt >= MIN_REPEAT_EVIDENCE_INTERVAL_MS)
            if (signatureChanged || sufficientlySeparated) {
                record.put("sightings", record.optInt("sightings") + 1)
                record.put("last_sighting_signature", sightingSignature)
                record.put("last_sighting_counted_ms", timestampMs)
            }
            record.put("last_event_code", observation.eventCode)
            if (observation.kind == "leave") {
                record.put("left", true).put("left_ms", timestampMs)
                if (record.optString("entity_class") == "mob" && !record.optBoolean("mob_dead", false)) {
                    record.put("mob_state", "left")
                }
            } else {
                record.put("last_kind", observation.kind)
                record.put("left", false)
            }
            if (observation.kind == "mob") {
                record.put("entity_class", "mob")
                if (!record.optBoolean("mob_dead", false)) record.put("mob_state", "alive")
            }
            observation.typeId?.let {
                recordEvidenceValue(record, "mobile_type_id", it)
                record.put("mobile_type_id", it)
            }
            observation.typeNumber?.let {
                recordEvidenceValue(record, "family", it)
                record.put("family", it)
            }
            observation.tier?.let { if (it in 1..8) {
                recordEvidenceValue(record, "tier", it)
                record.put("tier", it)
            } }
            observation.enchant?.let { if (it in 0..4) {
                recordEvidenceValue(record, "enchant", it)
                record.put("enchant", it)
            } }
            observation.size?.let { record.put("amount", it) }
            observation.name?.let { record.put("name", it) }
            observation.typeId?.let { applyMobileTypeCatalog(record, it) }
            if (observation.kind == "harvestable" && record.optString("entity_class") == "mob" &&
                record.optBoolean("is_harvestable_mob", false)) {
                record.put("mob_dead", true).put("mob_state", "dead_harvestable")
                    .put("mob_dead_ms", record.optLong("mob_dead_ms", timestampMs))
            }
            pendingTrollMarkers.remove(id.toString())?.let { applyTrollMarker(record, it, timestampMs) }
            val x = observation.x?.toDouble()
            val y = observation.y?.toDouble()
            if (x != null && y != null && x.isFinite() && y.isFinite() &&
                kotlin.math.abs(x) <= MAX_COORDINATE && kotlin.math.abs(y) <= MAX_COORDINATE) {
                record.put("x", x).put("y", y)
                if (observation.kind == "own_player_move") {
                    val movementSession = record.optLong("movement_session_started_ms", Long.MIN_VALUE)
                    if (movementSession < sessionStartedMs) {
                        // Never connect paths across captures when zone identity is unknown.
                        record.put("movement_session_started_ms", sessionStartedMs)
                        record.put("movement_history", JSONArray())
                    }
                    val prior = record.optJSONArray("movement_history") ?: JSONArray()
                    val history = JSONArray()
                    val start = (prior.length() - MAX_MOVEMENT_POINTS + 1).coerceAtLeast(0)
                    for (index in start until prior.length()) {
                        prior.optJSONObject(index)?.let { history.put(JSONObject(it.toString())) }
                    }
                    val last = history.optJSONObject(history.length() - 1)
                    val moved = last == null || kotlin.math.hypot(
                        x - last.optDouble("x"), y - last.optDouble("y")
                    ) >= MIN_MOVEMENT_DISTANCE
                    val elapsed = last == null || timestampMs - last.optLong("time_ms") >= MIN_MOVEMENT_INTERVAL_MS
                    if (moved || elapsed) {
                        history.put(JSONObject().put("x", x).put("y", y).put("time_ms", timestampMs))
                    }
                    record.put("movement_history", history)
                }
            }
            if (observation.kind == "resource_state") {
                if (observation.removed) {
                    record.put("depleted", true)
                    if (record.optString("entity_class") == "mob" &&
                        record.optBoolean("is_harvestable_mob", false)) {
                        record.put("mob_state", "mined").put("mob_mined_ms", timestampMs)
                    }
                } else if ((observation.size ?: 0) > 0) {
                    record.put("depleted", false)
                }
                record.put("resource_state_seen", true)
                observation.size?.let { record.put("resource_remaining", it) }
            }
            if (observation.kind == "harvestable") {
                record.put("typed_resource_seen", true)
                observation.size?.let {
                    record.put("resource_remaining", it)
                    // A positive fresh amount means the node has respawned/refilled.
                    if (it > 0) record.put("depleted", false)
                }
            }
            observation.heading?.let { if (it.isFinite() && it in 0f..360f) record.put("heading", it) }
            observation.speed?.let { if (it.isFinite() && it in 0f..100f) record.put("speed", it) }
            val targetX = observation.targetX?.toDouble()
            val targetY = observation.targetY?.toDouble()
            if (targetX != null && targetY != null && targetX.isFinite() && targetY.isFinite() &&
                kotlin.math.abs(targetX) <= MAX_COORDINATE && kotlin.math.abs(targetY) <= MAX_COORDINATE) {
                record.put("target_x", targetX).put("target_y", targetY)
            }
            val conflicts = conflictingEvidenceFields(record).toMutableList()
            updateResourceInference(record, conflicts)
            val inferredMobileStatus = record.optJSONObject("resource_inference")
                ?.optJSONObject("mobile_type_id")?.optString("status")
            if (inferredMobileStatus == ResourcePatternLearner.Status.CONFLICT.name) {
                conflicts.add("resource_catalog_mobile_type_mapping")
            }
            record.put("evidence_conflicts", JSONArray(conflicts))
            record.put("classification", classification(record, conflicts))
            record.put("evidence_grade", evidenceGrade(record, conflicts))
            record.put("evidence_score", evidenceScore(record, conflicts))
            record.put("evidence_reason", evidenceReason(record, conflicts))
            dirty = true
        }
        // Keep a large persistent history while bounding memory and avoiding per-packet disk I/O.
        while (records.size > MAX_RECORDS) {
            val oldest = records.entries.minByOrNull { it.value.optLong("last_seen_ms") } ?: break
            records.remove(oldest.key)
            dirty = true
        }
        if (dirty) snapshotDirty = true
        if (regionScanId != null && timestampMs >= regionScanStartedMs) {
            val finished = timestampMs >= regionScanEndsMs
            if ((finished && lastRegionScanFlushMs < regionScanEndsMs) ||
                timestampMs - lastRegionScanFlushMs >= FLUSH_INTERVAL_MS) {
                flushRegionScan(timestampMs, finished)
            }
        }
        if (dirty && timestampMs - lastFlushMs >= FLUSH_INTERVAL_MS) flush(timestampMs)
    }

    @Synchronized
    fun flush() = flush(System.currentTimeMillis())

    /** Starts an isolated 60-second observation window for the active server region. */
    @Synchronized
    fun beginRegionScan(
        nowMs: Long = System.currentTimeMillis(),
        regionId: String? = activeRegionId
    ): String {
        if (regionScanId != null) flushRegionScan(nowMs, true)
        if (!regionScanDir.exists() && !regionScanDir.mkdirs()) error("Cannot create region scan directory")
        val id = "region-$nowMs"
        regionScanId = id
        regionScanRegionId = regionId
        regionScanStartedMs = nowMs
        regionScanEndsMs = nowMs + REGION_SCAN_DURATION_MS
        lastRegionScanFlushMs = 0L
        flushRegionScan(nowMs, false)
        return id
    }

    /** Coordinate rows observed/updated since the active region scan began. */
    @Synchronized
    fun regionScanSnapshotJson(nowMs: Long = System.currentTimeMillis()): String {
        val id = regionScanId ?: return "[]"
        val finished = nowMs >= regionScanEndsMs
        if ((finished && lastRegionScanFlushMs < regionScanEndsMs) ||
            nowMs - lastRegionScanFlushMs >= FLUSH_INTERVAL_MS) {
            flushRegionScan(nowMs, finished)
        }
        val array = JSONArray()
        records.values
            .filter {
                it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                    it.optLong("last_seen_ms", Long.MIN_VALUE) >= regionScanStartedMs &&
                    it.optString("region_id", "") == (regionScanRegionId ?: "") &&
                    it.has("x") && it.has("y")
            }
            .sortedByDescending { it.optLong("last_seen_ms") }
            .forEach { row ->
                array.put(JSONObject(row.toString())
                    .put("region_scan_id", id)
                    .put("region_scan_started_ms", regionScanStartedMs))
            }
        return array.toString()
    }

    @Synchronized
    private fun flushRegionScan(nowMs: Long, finished: Boolean) {
        val id = regionScanId ?: return
        try {
            if (!regionScanDir.exists() && !regionScanDir.mkdirs()) return
            val nodes = JSONArray(regionScanSnapshotRowsJson())
            val document = JSONObject()
                .put("schema_version", 1)
                .put("scan_id", id)
                .put("started_at_ms", regionScanStartedMs)
                .put("scheduled_end_at_ms", regionScanEndsMs)
                .put("updated_at_ms", nowMs)
                .put("finished", finished)
                .put("region_identity", regionScanRegionId?.let { it } ?: JSONObject.NULL)
                .put("region_identity_note", if (regionScanRegionId != null)
                    "Server cluster/map ID from ChangeCluster or Join response."
                else "No verified server region ID was available when this scan started.")
                .put("node_count", nodes.length())
                .put("nodes", nodes)
            val target = File(regionScanDir, "$id.json")
            val temp = File(regionScanDir, "$id.json.tmp")
            temp.writeText(document.toString(2), Charsets.UTF_8)
            if (!temp.renameTo(target)) error("Cannot atomically save region scan")
            lastRegionScanFlushMs = nowMs
        } catch (_: Exception) {
            // Keep the scan active; the next observation/poll retries the atomic save.
        }
    }

    private fun regionScanSnapshotRowsJson(): String {
        val id = regionScanId ?: return "[]"
        val array = JSONArray()
        records.values
            .filter {
                it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                    it.optLong("last_seen_ms", Long.MIN_VALUE) >= regionScanStartedMs &&
                    it.optString("region_id", "") == (regionScanRegionId ?: "") &&
                    it.has("x") && it.has("y")
            }
            .sortedByDescending { it.optLong("last_seen_ms") }
            .forEach { row ->
                array.put(JSONObject(row.toString())
                    .put("region_scan_id", id)
                    .put("region_scan_started_ms", regionScanStartedMs))
            }
        return array.toString()
    }

    @Synchronized
    fun snapshotJson(): String {
        if (!snapshotDirty) cachedSnapshotJson?.let { return it }
        val array = JSONArray()
        records.values
            .filter {
                it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                    (activeRegionId == null || it.optString("region_id", "") == activeRegionId) &&
                    it.has("x") && it.has("y")
            }
            .sortedByDescending { it.optLong("last_seen_ms") }
            // The previous 2,000-row UI cap silently hid older known nodes. Keep the
            // complete current-session coordinate catalog (bounded by MAX_RECORDS).
            .forEach { array.put(JSONObject(it.toString())) }
        return array.toString().also {
            cachedSnapshotJson = it
            snapshotDirty = false
        }
    }

    @Synchronized
    fun snapshotText(): String {
        val recent = records.values
            .filter { it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
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
                val name = record.optString("name").takeIf { it.isNotBlank() && it != "null" }?.let { " $it" } ?: ""
                val health = if (record.has("health_value")) " HP=${"%.0f".format(java.util.Locale.US, record.optDouble("health_value"))}" else ""
                val position = if (record.has("x") && record.has("y")) " (${"%.1f".format(java.util.Locale.US, record.optDouble("x"))}, ${"%.1f".format(java.util.Locale.US, record.optDouble("y"))})" else ""
                val state = when {
                    record.optBoolean("depleted", false) -> " [depleted]"
                    record.optBoolean("left", false) -> " [left]"
                    else -> ""
                }
                val evidence = record.optString("evidence_grade", "unclassified")
                val evidenceSuffix = if (kind in RESOURCE_KINDS)
                    " [$evidence EV${record.optInt("evidence_score")}/100]"
                else " [$evidence]"
                appendLine("$label$name$tier$enchant$amount$health$position$state$evidenceSuffix")
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
        val ambiguous = records.values.count { it.optString("classification") == "ambiguous_resource_candidate" }
        val evidenceBearing = records.values.filter {
            it.has("family") || it.optString("last_kind") in RESOURCE_KINDS
        }
        val averageEvidenceScore = if (evidenceBearing.isEmpty()) 0 else
            evidenceBearing.sumOf { it.optInt("evidence_score") } / evidenceBearing.size
        val currentSessionMapEntities = records.values.count { record ->
            record.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                record.has("x") && record.has("y")
        }
        return buildString {
            appendLine()
            appendLine("Live knowledge cache")
            appendLine("Unique tracked entities: ${records.size}")
            appendLine("Current-session map entities: $currentSessionMapEntities")
            appendLine("Typed resource observations: $typed")
            appendLine("Static resource candidates (not confirmed Ore): $candidates")
            appendLine("Entities marked depleted: $depleted")
            appendLine("Corroborated by same-ID resource state: $corroborated")
            appendLine("Repeated typed resource observations: $repeated")
            appendLine("Ambiguous entities due to conflicting typed evidence: $ambiguous")
            appendLine("Average evidence score (0-100 heuristic): $averageEvidenceScore")
            appendLine("Evidence score is an ordinal heuristic, not a calibrated probability.")
            appendLine("Persistent file: ${file.name}")
            appendLine("Classification is conservative; no family-to-Ore guesses are made.")
        }
    }


    /**
     * Adds a machine-readable interpretation layer without overwriting packet facts.
     * Inferred names and catalog mappings are always accompanied by provenance/status.
     * Conflicting evidence suppresses affected inferred values rather than picking a winner.
     */
    private fun updateResourceInference(record: JSONObject, conflicts: List<String>) {
        if (record.optString("last_kind") !in RESOURCE_KINDS && !record.has("family")) return
        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(
                family = if (record.has("family")) record.optInt("family") else null,
                tier = if (record.has("tier")) record.optInt("tier") else null,
                enchantRaw = if (record.has("enchant")) record.optInt("enchant") else null,
                mobileTypeId = if (record.has("mobile_type_id")) record.optInt("mobile_type_id") else null
            )
        )
        fun field(value: ResourcePatternLearner.FieldInference, blocked: Boolean, blockedReason: String): JSONObject {
            val conflict = blocked && value.status != ResourcePatternLearner.Status.UNKNOWN
            return JSONObject()
                .put("value", if (conflict) JSONObject.NULL else value.value ?: JSONObject.NULL)
                .put("status", if (conflict) ResourcePatternLearner.Status.CONFLICT.name else value.status.name)
                .put("confidence", if (conflict) 0.0 else value.confidence)
                .put("reason", if (conflict) blockedReason else value.reason)
        }
        record.put("resource_inference", JSONObject()
            .put("catalog_version", ResourcePatternLearner.CATALOG_VERSION)
            .put("resource_name", field(inferred.resourceName, "family" in conflicts,
                "Conflicting family evidence prevents a stable resource-name inference."))
            .put("mobile_type_id", field(inferred.mobileTypeId,
                conflicts.any { it in setOf("family", "tier", "mobile_type_id") },
                "Conflicting family/tier/mobile-type evidence prevents a stable mobile-type mapping."))
            .put("enchantment_name", field(inferred.enchantmentName, "enchant" in conflicts,
                "Conflicting enchantment evidence prevents a stable enchantment-name inference."))
            .put("source_fields", JSONArray(listOf("family", "tier", "enchant", "mobile_type_id"))))
    }

    private fun recordEvidenceValue(record: JSONObject, field: String, value: Any) {
        val key = "${field}_evidence"
        val values = record.optJSONArray(key) ?: JSONArray().also { array ->
            if (record.has(field)) array.put(record.opt(field).toString())
        }
        val normalized = value.toString()
        if ((0 until values.length()).none { values.optString(it) == normalized } && values.length() < 16) {
            values.put(normalized)
        }
        record.put(key, values)
    }

    private fun conflictingEvidenceFields(record: JSONObject): List<String> =
        listOf("mobile_type_id", "family", "tier", "enchant").filter { field ->
            (record.optJSONArray("${field}_evidence")?.length() ?: 0) > 1
        }

    private fun evidenceGrade(record: JSONObject, conflicts: List<String>): String {
        val typed = record.has("mobile_type_id") && record.has("tier") && record.has("x") && record.has("y")
        return when {
            conflicts.isNotEmpty() -> "conflicting_typed_observation"
            typed && record.optBoolean("resource_state_seen", false) -> "corroborated_same_id"
            typed && record.optInt("sightings") >= 3 -> "repeated_typed"
            typed -> "typed_single"
            record.optString("last_kind") == "static_resource" && record.optInt("sightings") >= 3 -> "repeated_candidate"
            record.optString("last_kind") == "static_resource" -> "candidate"
            else -> "unclassified"
        }
    }

    private fun evidenceScore(record: JSONObject, conflicts: List<String>): Int {
        val typed = record.has("mobile_type_id") && record.has("tier") && record.has("x") && record.has("y")
        return when {
            conflicts.isNotEmpty() -> 20
            typed && record.optBoolean("resource_state_seen", false) -> 90
            typed && record.optInt("sightings") >= 3 -> 75
            typed -> 55
            record.optString("last_kind") == "static_resource" && record.optInt("sightings") >= 3 -> 40
            record.optString("last_kind") == "static_resource" -> 25
            else -> 0
        }
    }

    private fun evidenceReason(record: JSONObject, conflicts: List<String>): String = when {
        conflicts.isNotEmpty() -> "conflicting_fields=" + conflicts.joinToString(",")
        record.optString("evidence_grade") == "corroborated_same_id" -> "typed_resource_plus_same_id_state"
        record.optString("evidence_grade") == "repeated_typed" -> "repeated_typed_observations"
        record.optString("evidence_grade") == "typed_single" -> "single_typed_resource_observation"
        record.optString("evidence_grade") == "repeated_candidate" -> "repeated_static_candidate_only"
        record.optString("evidence_grade") == "candidate" -> "single_static_candidate_only"
        else -> "insufficient_typed_evidence"
    }

    private fun classification(record: JSONObject, conflicts: List<String>): String = when {
        conflicts.isNotEmpty() && (record.has("family") || record.has("tier") || record.optString("last_kind") in RESOURCE_KINDS) ->
            "ambiguous_resource_candidate"
        conflicts.isNotEmpty() -> "ambiguous_entity"
        record.has("mobile_type_id") && record.has("tier") && record.has("x") && record.has("y") ->
            "typed_resource_observation"
        record.optString("last_kind") == "static_resource" && record.has("x") && record.has("y") ->
            "static_resource_candidate"
        else -> "unclassified_observation"
    }

    /** Exact lookup; never classify a mob from a nearby label or a guessed name. */
    private fun applyMobileTypeCatalog(record: JSONObject, mobileTypeId: Int) {
        val entry = mobileTypeEntries.optJSONObject(mobileTypeId.toString()) ?: when (mobileTypeId) {
            571 -> JSONObject().put("unique_name", "T3_MOB_CRITTER_ORE_MOUNTAIN_RED")
                .put("display_name", "Ore Elemental").put("tier", 3).put("loot_tier", 3)
                .put("category", "critter").put("loot_type", "ORE_CRITTER").put("is_harvestable", true)
            573 -> JSONObject().put("unique_name", "T4_MOB_CRITTER_ORE_MOUNTAIN_RED")
                .put("display_name", "Ore Elemental").put("tier", 4).put("loot_tier", 4)
                .put("category", "critter").put("loot_type", "ORE_CRITTER").put("is_harvestable", true)
            574 -> JSONObject().put("unique_name", "T5_MOB_CRITTER_ORE_MOUNTAIN_RED")
                .put("display_name", "Ore Elemental").put("tier", 5).put("loot_tier", 5)
                .put("category", "critter").put("loot_type", "ORE_CRITTER").put("is_harvestable", true)
            else -> return
        }
        val uniqueName = entry.optString("unique_name").takeIf { it.isNotBlank() } ?: return
        val isHarvestable = entry.optBoolean("is_harvestable", false)
        record.put("type_unique_name", uniqueName)
            .put("display_name", entry.optString("display_name"))
            .put("localization_tag", entry.optString("localization_tag"))
            .put("mob_category", entry.optString("category"))
            .put("loot_type", entry.optString("loot_type"))
            .put("is_harvestable_mob", isHarvestable)
        if (isHarvestable) {
            val role = when {
                uniqueName.contains("CRITTER_ORE", ignoreCase = true) ||
                    entry.optString("loot_type").contains("ORE", ignoreCase = true) -> "ore_elemental"
                else -> "living_resource"
            }
            record.put("resource_role", role)
            if (!record.has("name") || record.optString("name").isBlank() ||
                record.optString("name").startsWith("@MISTS@", ignoreCase = true)) {
                record.put("name", entry.optString("display_name"))
            }
            val resourceTier = entry.optInt("loot_tier", entry.optInt("tier", 0))
            if (resourceTier in 1..8 && !record.has("tier")) record.put("tier", resourceTier)
        } else if (!record.has("name") || record.optString("name").isBlank()) {
            record.put("name", entry.optString("display_name"))
        }
    }

    /** Troll is a user activity label. Attach it only by exact entity ID, never proximity. */
    @Synchronized
    fun observeMarker(marker: JSONObject, timestampMs: Long) {
        if (!marker.optString("activity").equals("Troll", ignoreCase = true)) return
        val targetId = marker.optString("target_id").takeIf { it.isNotBlank() && it != "null" } ?: return
        val record = records.values.firstOrNull {
            it.optString("entity_id") == targetId &&
                it.optLong("session_started_ms", Long.MIN_VALUE) == sessionStartedMs &&
                it.optString("entity_class") == "mob"
        }
        if (record == null) pendingTrollMarkers[targetId] = JSONObject(marker.toString())
        else applyTrollMarker(record, marker, timestampMs)
        dirty = true
        snapshotDirty = true
    }

    private fun applyTrollMarker(record: JSONObject, marker: JSONObject, timestampMs: Long) {
        record.put("manual_activity", "Troll")
            .put("manual_marker_identity_proven", true)
            .put("manual_marker_seen_ms", timestampMs)
        marker.optString("tier").toIntOrNull()?.takeIf { it in 1..8 }?.let {
            record.put("marker_tier", it)
        }
        marker.optString("enchantment").removePrefix("E").toIntOrNull()
            ?.takeIf { it in 0..3 }?.let { record.put("marker_enchant", it) }
        marker.optString("enchantment").takeIf { it.equals("Normal", true) }?.let {
            record.put("marker_enchant", 0)
        }
    }

    private fun load() {
        if (!file.isFile) return
        try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("entity_id")
                val key = item.optString("record_key").takeIf { it.isNotBlank() && it != "null" } ?: id
                if (key.isNotBlank() && key != "null" && id.isNotBlank() && id != "null") {
                    val copy = JSONObject(item.toString())
                    if (!copy.has("record_key")) copy.put("record_key", key)
                    records[key] = copy
                }
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
        const val MAX_RECORDS = 25000
        const val REGION_SCAN_DURATION_MS = 60_000L
        const val MAX_COORDINATE = 10000.0
        const val MAX_MOVEMENT_POINTS = 240
        const val MIN_MOVEMENT_DISTANCE = 0.5
        const val MIN_MOVEMENT_INTERVAL_MS = 300L
        const val FLUSH_INTERVAL_MS = 2000L
        const val MIN_REPEAT_EVIDENCE_INTERVAL_MS = 500L
        val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        val TRACKED_KINDS = setOf("static_resource", "harvestable", "resource_state", "mob", "leave", "player_move", "own_player_move")
    }
}
