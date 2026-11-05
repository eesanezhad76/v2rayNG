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
    private val ORE_ELEMENTAL_TYPE_IDS = setOf(571, 573, 574)

    /** Stable TypeIDs from the research dictionary outrank variable display labels. */
    fun isOreElemental(item: JSONObject): Boolean {
        val typeId = item.optInt("mobile_type_id", -1)
        if (typeId in ORE_ELEMENTAL_TYPE_IDS) return true
        return item.optString("type_unique_name").contains("ORE_MOUNTAIN", ignoreCase = true) ||
            item.optString("localization_tag").contains("OREELEMENTAL", ignoreCase = true) ||
            item.optString("name").contains("Ore Elemental", ignoreCase = true)
    }

    /** Do not let a stale/manual Troll label override a packet-identified Ore Elemental. */
    fun isTrollMob(item: JSONObject): Boolean {
        if (isOreElemental(item)) return false
        val label = (item.optString("name") + " " + item.optString("type_unique_name") + " " +
            item.optString("localization_tag")).lowercase()
        if (label.contains("troll")) return true
        val marker = item.optString("marker_activity")
        val confidence = item.optString("marker_match_confidence").uppercase()
        return marker.equals("Troll", ignoreCase = true) &&
            confidence in setOf("HIGH", "CONFIRMED", "USER_CONFIRMED") &&
            (item.optBoolean("marker_identity_proven", false) ||
                item.optBoolean("manual_marker_identity_proven", false))
    }

    /** A manual Troll activity label is authoritative only when linked to an exact target ID. */
    fun isUserMarkedTroll(item: JSONObject): Boolean =
        item.optString("manual_activity").equals("Troll", ignoreCase = true) &&
            item.optBoolean("manual_marker_identity_proven", false)

    /** Exact user-tagged targets and packet-identified Troll mobs share the Troll radar layer. */
    fun isTrollCategoryMember(item: JSONObject): Boolean =
        isUserMarkedTroll(item) || isTrollMob(item)

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
        val kind = item.optString("last_kind")
        if (kind !in resourceKinds && kind != "mob") return false
        if (item.optBoolean("depleted", false) || item.optBoolean("left", false)) return false
        if (item.optBoolean("user_confirmed_target", false)) return true
        val conflicts = item.optJSONArray("evidence_conflicts")
        if (conflicts != null && conflicts.length() > 0) return false
        if (kind == "mob") {
            // Living resource creatures (e.g. Ore Elementals) arrive as mob observations.
            // Require explicit harvestability from the matched research dictionary.
            // The research corpus is authoritative when it has an explicit value;
            // otherwise use the broader external TypeID dictionary so its harvestable
            // creatures are also navigable. A research-confirmed false must override
            // any conflicting external metadata (e.g. Mountain Rabbit TypeID 482).
            val harvestable = if (item.has("research_catalog_harvestable"))
                item.optBoolean("research_catalog_harvestable", false)
            else item.optBoolean("is_harvestable_mob", false)
            return harvestable &&
                item.optInt("mobile_type_id", -1) > 0 &&
                item.optInt("tier", -1) in 1..8
        }
        return isTypedResource(item)
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
