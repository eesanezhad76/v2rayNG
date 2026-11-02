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
