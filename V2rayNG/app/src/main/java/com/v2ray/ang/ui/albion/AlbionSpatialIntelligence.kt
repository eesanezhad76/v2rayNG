package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Spatial safeguards for the live radar.
 *
 * Coordinates are only compared inside one active snapshot. This class never
 * combines historical capture coordinates or assumes that two zones share an origin.
 */
object AlbionSpatialIntelligence {
    private val resourceKinds = setOf("static_resource", "harvestable", "resource_state")

    /** Collapse duplicate observations at the same half-unit coordinate without merging distinct typed resources. */
    fun deduplicate(entities: List<JSONObject>): List<JSONObject> {
        val retained = entities.filter { it.optString("last_kind") !in resourceKinds }.toMutableList()
        val resources = entities.filter {
            it.optString("last_kind") in resourceKinds &&
                it.has("x") && it.has("y") &&
                it.optDouble("x").isFinite() && it.optDouble("y").isFinite()
        }
        val groups = resources.groupBy { coordinateKey(it) }
        groups.values.forEach { group ->
            val typed = group.filter { isTypedResource(it) }
            if (typed.isEmpty()) {
                // Event 39 can repeat the same untyped candidate under different runtime IDs.
                retained.add(group.maxByOrNull { it.optLong("last_seen_ms") }!!)
            } else {
                // A typed observation supersedes an Event 39-only candidate at the same point.
                // Keep every distinct typed runtime ID. Same coordinates/type are not proof
                // that two independently identified live entities are the same game object.
                retained.addAll(typed)
            }
        }
        return retained
    }

    /** Only typed harvestable observations or explicitly user-confirmed targets may guide gathering. */
    fun isNavigableResource(item: JSONObject): Boolean {
        if (item.optString("last_kind") !in resourceKinds) return false
        if (item.optBoolean("depleted", false) || item.optBoolean("left", false)) return false
        if (item.optBoolean("user_confirmed_target", false)) return true
        if (!isTypedResource(item)) return false
        val conflicts = item.optJSONArray("evidence_conflicts")
        return conflicts == null || conflicts.length() == 0
    }

    /** Candidate-only pins are intentionally capped when the user enables the unclassified layer. */
    fun limitUnclassifiedCandidates(
        entities: List<JSONObject>,
        playerX: Double,
        playerY: Double,
        limit: Int = 40
    ): List<JSONObject> {
        if (limit <= 0) return entities.filterNot { isUnclassifiedCandidate(it) }
        val candidates = entities.filter(::isUnclassifiedCandidate)
            .sortedBy { distanceSquared(it, playerX, playerY) }
            .take(limit)
        val candidateIds = candidates.map { it.optString("entity_id") }.toSet()
        return entities.filterNot(::isUnclassifiedCandidate) +
            entities.filter { isUnclassifiedCandidate(it) && it.optString("entity_id") in candidateIds }
    }

    private fun isTypedResource(item: JSONObject): Boolean =
        item.optString("last_kind") in setOf("harvestable", "resource_state") &&
            item.optInt("mobile_type_id", -1) > 0 &&
            item.optInt("tier", -1) in 1..8

    private fun isUnclassifiedCandidate(item: JSONObject): Boolean =
        item.optString("last_kind") == "static_resource" &&
            !item.has("mobile_type_id") && !item.has("family") && !item.has("tier")

    private fun coordinateKey(item: JSONObject): String =
        "${(item.optDouble("x") * 2.0).roundToInt()}:${(item.optDouble("y") * 2.0).roundToInt()}"

    private fun distanceSquared(item: JSONObject, x: Double, y: Double): Double {
        val dx = item.optDouble("x") - x
        val dy = item.optDouble("y") - y
        return dx * dx + dy * dy
    }
}
