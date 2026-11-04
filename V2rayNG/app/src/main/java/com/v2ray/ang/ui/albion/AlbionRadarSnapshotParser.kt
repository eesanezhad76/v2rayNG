package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Strictly parses the coordinate snapshot consumed by the mini radar. */
object AlbionRadarSnapshotParser {
    data class ParsedSnapshot(
        val entities: List<JSONObject>,
        val invalidCoordinateCount: Int
    )

    /**
     * Prefers a provenance-backed catalog label. Raw numeric identifiers remain a fallback,
     * and conflicting/unknown inference is never presented as a confirmed semantic name.
     */
    fun labelForEntity(item: JSONObject): String {
        val kind = item.optString("last_kind")
        if (kind == "own_player_move") return "YOU"
        val tier = item.optInt("marker_tier", -1).takeIf { it in 1..8 }
            ?: item.optInt("tier", -1).takeIf { it in 1..8 } ?: -1
        if (kind == "mob") {
            val markerActivity = item.optString("marker_activity")
            val manualActivity = item.optString("manual_activity")
            val candidateActivity = item.optString("marker_activity_candidate")
            val uniqueName = item.optString("type_unique_name")
            val databaseTroll = uniqueName.contains("TROLL", ignoreCase = true)
            val trollMarked = databaseTroll || markerActivity.equals("Troll", true) ||
                manualActivity.equals("Troll", true) || candidateActivity.equals("Troll", true)
            if (trollMarked) {
                val trollIdentityProven = databaseTroll || item.optBoolean("manual_marker_identity_proven", false)
                return if (trollIdentityProven) {
                    if (tier > 0) "TG" + tier else "Troll"
                } else {
                    if (tier > 0) "TG?" + tier else "TG?"
                }
            }
            val displayName = item.optString("display_name")
            val oreElemental = item.optString("resource_role") == "ore_elemental" ||
                (item.optBoolean("is_harvestable_mob", false) &&
                    uniqueName.contains("CRITTER_ORE", ignoreCase = true) &&
                    displayName.equals("Ore Elemental", ignoreCase = true))
            if (oreElemental) return if (tier > 0) "OE" + tier else "OE"
            val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
                ?: displayName.takeIf { it.isNotBlank() && it != "null" }
            if (name != null) return if (tier > 0) "T" + tier + " " + name else name
            return if (item.has("mobile_type_id")) "Mob ID" + item.optInt("mobile_type_id") else "Mob"
        }
        if (kind in setOf("static_resource", "harvestable", "resource_state")) {
            return if (tier > 0) "T" + tier else "Resource"
        }
        if (kind == "player_move") return item.optString("name").takeIf { it.isNotBlank() } ?: "Player"
        return kind.ifBlank { "Unknown entity" }
    }

    fun parse(json: String): ParsedSnapshot? {
        val array = try { JSONArray(json) } catch (_: Exception) { return null }
        val accepted = ArrayList<JSONObject>(array.length())
        var invalidCoordinates = 0
        for (index in 0 until array.length()) {
            // One malformed entity must not blank the entire radar snapshot.
            val item = array.optJSONObject(index)
            if (item == null || item.optString("last_kind").isBlank() || !item.has("entity_id") ||
                !item.has("x") || !item.has("y")) {
                invalidCoordinates++
                continue
            }
            val x = item.optDouble("x", Double.NaN)
            val y = item.optDouble("y", Double.NaN)
            if (!x.isFinite() || !y.isFinite() || abs(x) > 10000.0 || abs(y) > 10000.0) {
                invalidCoordinates++
                continue
            }
            accepted.add(JSONObject(item.toString()))
        }
        return ParsedSnapshot(accepted, invalidCoordinates)
    }
}
