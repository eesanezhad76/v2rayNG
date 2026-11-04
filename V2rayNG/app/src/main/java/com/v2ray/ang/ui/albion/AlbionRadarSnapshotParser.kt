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
            val activity = item.optString("marker_activity")
            val candidateActivity = item.optString("marker_activity_candidate")
            val tier = item.optInt("tier", -1).takeIf { it > 0 }?.let { "T$it " } ?: ""
            if (activity == "Troll") return if (name != null && !name.lowercase().contains("troll")) "T! $tier$name" else name ?: "Troll"
            if (candidateActivity == "Troll") return if (name != null && !name.lowercase().contains("troll")) "T? $tier$name" else "Troll?"
            if (name != null) return tier + name
            return if (item.has("mobile_type_id")) "Mob ID${item.optInt("mobile_type_id")}" else "Mob"
        }
        if (kind in setOf("static_resource", "harvestable", "resource_state")) {
            if (item.optBoolean("user_confirmed_target", false)) return "T5.3 ✓"
            val inference = item.optJSONObject("resource_inference")?.optJSONObject("resource_name")
            val inferenceStatus = inference?.optString("status")
            val inferenceConfidence = inference?.optDouble("confidence", 0.0) ?: 0.0
            val inferredName = inference?.optString("value")?.takeIf {
                it.isNotBlank() && it != "null" &&
                    inferenceStatus != "CONFLICT" && inferenceStatus != "UNKNOWN" &&
                    inferenceConfidence > 0.0
            }
            val tierLabel = item.optInt("tier", -1).takeIf { it in 2..8 }?.let { " T$it" } ?: ""
            val enchantInference = item.optJSONObject("resource_inference")?.optJSONObject("enchantment_name")
            val enchantValue = enchantInference?.optString("value")?.takeIf { it.startsWith("E") }
            val enchantStatus = enchantInference?.optString("status")
            val enchantLabel = enchantValue?.let { " $it" + if (enchantStatus == "PROVISIONAL_MAPPING") "?" else "" }
                ?: item.optInt("enchant", -1).takeIf { it in 1..3 }?.let { " E$it?" } ?: ""
            val suffix = tierLabel + enchantLabel
            if (inferredName != null) {
                val name = when (inferenceStatus) {
                    "PACKET_CONFIRMED" -> inferredName
                    "CAPTURE_CORROBORATED" -> "$inferredName*"
                    "PROVISIONAL_MAPPING" -> "$inferredName?"
                    else -> inferredName
                }
                return name + suffix
            }
            // Research catalog rows carry source-derived names directly instead of a
            // resource_inference object. Preserve their evidence grade in the label.
            val catalogName = item.optString("name").takeIf {
                it.isNotBlank() && it != "null" && it != "Resource"
            }
            if (catalogName != null) {
                val mappingStatus = item.optString("mapping_status")
                val displayName = if (mappingStatus.contains("PROVISIONAL", ignoreCase = true)) {
                    "$catalogName?"
                } else catalogName
                return displayName + suffix
            }
            if (item.optInt("family", -1) == 29) return "Ore candidate F29$suffix"
            if (item.has("family")) return "Resource F${item.optInt("family")}$suffix"
            if (item.has("mobile_type_id")) return "Resource ID${item.optInt("mobile_type_id")}$suffix"
            return (if (kind == "static_resource") "Resource candidate" else "Resource") + suffix
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
