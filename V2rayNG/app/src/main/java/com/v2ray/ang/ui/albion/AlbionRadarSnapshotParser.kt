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
        if (kind == "mob") {
            val name = item.optString("name").takeIf { it.isNotBlank() && it != "null" }
            if (name != null) return name
            return if (item.has("mobile_type_id")) "Mob ID${item.optInt("mobile_type_id")}" else "Mob"
        }
        if (kind in setOf("static_resource", "harvestable", "resource_state")) {
            val inference = item.optJSONObject("resource_inference")?.optJSONObject("resource_name")
            val inferredName = inference?.optString("value")?.takeIf {
                it.isNotBlank() && it != "null" &&
                    inference.optString("status") !in setOf("CONFLICT", "UNKNOWN") &&
                    inference.optDouble("confidence", 0.0) > 0.0
            }
            if (inferredName != null) return inferredName
            if (item.has("family")) return "Resource F${item.optInt("family")}"
            if (item.has("mobile_type_id")) return "Resource ID${item.optInt("mobile_type_id")}"
            return if (kind == "static_resource") "Resource candidate" else "Resource"
        }
        if (kind == "player_move") return item.optString("name").takeIf { it.isNotBlank() } ?: "Player"
        return kind.ifBlank { "Unknown entity" }
    }

    fun parse(json: String): ParsedSnapshot? {
        val array = try { JSONArray(json) } catch (_: Exception) { return null }
        val accepted = ArrayList<JSONObject>(array.length())
        var invalidCoordinates = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: return null
            if (item.optString("last_kind").isBlank() || !item.has("entity_id") ||
                !item.has("x") || !item.has("y")) return null
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
