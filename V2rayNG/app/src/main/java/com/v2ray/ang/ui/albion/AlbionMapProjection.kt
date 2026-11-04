package com.v2ray.ang.ui.albion

import org.json.JSONArray
import kotlin.math.hypot

/**
 * Shared world-to-screen transform for the mini radar and full map.
 * The existing evidence-backed convention is world +Y up and world +X right.
 * Movement direction is derived from observed coordinate deltas, not an
 * assumed heading-angle convention. This keeps marker displacement and player
 * movement arrows in the same projection, preventing a separate UI rotation
 * from making the arrow disagree with the resource positions.
 */
internal object AlbionMapProjection {
    data class Point(val x: Double, val y: Double)

    fun worldToScreen(dx: Double, dy: Double): Point = Point(dx, -dy)

    fun movementDirection(history: JSONArray?): Point? {
        if (history == null || history.length() < 2) return null
        val last = history.optJSONObject(history.length() - 1) ?: return null
        val lastX = last.optDouble("x", Double.NaN)
        val lastY = last.optDouble("y", Double.NaN)
        if (!lastX.isFinite() || !lastY.isFinite()) return null
        for (index in history.length() - 2 downTo 0) {
            val previous = history.optJSONObject(index) ?: continue
            val x = previous.optDouble("x", Double.NaN)
            val y = previous.optDouble("y", Double.NaN)
            if (!x.isFinite() || !y.isFinite()) continue
            val delta = worldToScreen(lastX - x, lastY - y)
            val length = hypot(delta.x, delta.y)
            if (length >= MIN_DIRECTION_DISTANCE) {
                return Point(delta.x / length, delta.y / length)
            }
        }
        return null
    }

    private const val MIN_DIRECTION_DISTANCE = 0.05
}
