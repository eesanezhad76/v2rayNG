package com.v2ray.ang.ui.albion

import org.json.JSONArray
import kotlin.math.hypot

/**
 * Shared world-to-screen transform for the mini radar and full map.
 * Albion's in-game world-map grid is rotated 45 degrees clockwise relative
 * to the phone screen. Apply that same fixed transform to every world delta:
 * +Y maps to screen upper-right and +X maps to screen lower-right.
 * Movement direction is derived from observed coordinate deltas and passed
 * through this exact transform, so nodes, trail and player arrow stay aligned.
 */
internal object AlbionMapProjection {
    data class Point(val x: Double, val y: Double)

    private const val DIAGONAL = 0.7071067811865476

    fun worldToScreen(dx: Double, dy: Double): Point = Point(
        (dx + dy) * DIAGONAL,
        (dx - dy) * DIAGONAL
    )

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
