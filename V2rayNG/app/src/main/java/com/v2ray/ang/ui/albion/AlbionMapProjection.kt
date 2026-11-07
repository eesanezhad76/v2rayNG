package com.v2ray.ang.ui.albion

import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Shared calibrated world-to-screen transform for the mini radar and full map.
 *
 * AlbionRadar11 user-marked UP/DOWN trials in region 4207 produced four
 * time-regression path bearings of 139.072, 140.011 (DOWN reversed),
 * 139.954 and 140.238 degrees in world XY. Their mean is 139.819 degrees,
 * or 49.819 degrees left of world north. The UP control was not traversed
 * perfectly vertically, so inverse DOWN trials are included rather than
 * assuming a 45-degree correction.
 *
 * Rotating screen-space offsets clockwise by 49.819 degrees aligns the
 * measured marked UI-UP axis with screen-up. This is an input-axis
 * calibration, not an independently verified cardinal-north calibration:
 * PCAP alone cannot separate systematic joystick/touch bias from map bias.
 * Nodes, trails and movement arrows all use this same transform.
 */
internal object AlbionMapProjection {
    data class Point(val x: Double, val y: Double)

    internal const val CLOCKWISE_CALIBRATION_DEGREES = 49.819
    private val calibrationRadians = Math.toRadians(CLOCKWISE_CALIBRATION_DEGREES)
    private val calibrationCos = cos(calibrationRadians)
    private val calibrationSin = sin(calibrationRadians)

    fun worldToScreen(dx: Double, dy: Double): Point {
        val screenX = dx
        val screenY = -dy
        return Point(
            screenX * calibrationCos - screenY * calibrationSin,
            screenX * calibrationSin + screenY * calibrationCos
        )
    }

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
