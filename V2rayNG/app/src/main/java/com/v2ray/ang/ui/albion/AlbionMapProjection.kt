package com.v2ray.ang.ui.albion

import org.json.JSONArray
import kotlin.math.hypot

/**
 * Shared world-to-screen transform for the mini radar and full map.
 * Albion world coordinates use east-positive X and north-positive Y.
 * Screen Y is inverted, so +Y is straight up and +X is straight right.
 * The same cardinal transform is shared by nodes, trail and player arrow.
 */
internal object AlbionMapProjection {
    data class Point(val x: Double, val y: Double)

    fun worldToScreen(dx: Double, dy: Double): Point = Point(dx, -dy)

    fun movementDirection(
        history: JSONArray?,
        nowMs: Long = System.currentTimeMillis()
    ): Point? {
        if (history == null || history.length() < 2) return null
        val last = history.optJSONObject(history.length() - 1) ?: return null
        val lastTime = last.optLong("time_ms", 0L)
        if (lastTime > 0L && nowMs >= lastTime && nowMs - lastTime > MAX_DIRECTION_AGE_MS) return null
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
    private const val MAX_DIRECTION_AGE_MS = 3_000L
}
