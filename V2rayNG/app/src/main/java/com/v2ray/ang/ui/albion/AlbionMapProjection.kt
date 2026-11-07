package com.v2ray.ang.ui.albion

import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Calibrated map projection with an independently implemented movement transform.
 *
 * AlbionRadar11 user-marked UP/DOWN trials in region 4207 produced four
 * time-regression path bearings of 139.072, 140.011 (DOWN reversed),
 * 139.954 and 140.238 degrees in world XY. Their mean is 139.819 degrees,
 * or a 49.819-degree clockwise correction from the existing screen basis.
 *
 * This aligns the marked UI-UP input axis with screen-up. It is not proof
 * of absolute game-world north: PCAP alone cannot separate systematic
 * joystick/touch bias from map-orientation bias.
 *
 * Map and movement transforms intentionally have separate constants and
 * implementations. Keep them separate even while their measured values match.
 */
internal object AlbionMapProjection {
    data class Point(val x: Double, val y: Double)

    internal const val CLOCKWISE_CALIBRATION_DEGREES = 49.819
    private val mapCalibrationRadians = Math.toRadians(CLOCKWISE_CALIBRATION_DEGREES)
    private val mapCalibrationCos = cos(mapCalibrationRadians)
    private val mapCalibrationSin = sin(mapCalibrationRadians)

    // Independently calibrated movement transform. Do not replace these
    // values with the map constant or route movement through worldToScreen.
    internal const val MOVEMENT_CLOCKWISE_CALIBRATION_DEGREES = 45.0
    private val movementCalibrationRadians = Math.toRadians(MOVEMENT_CLOCKWISE_CALIBRATION_DEGREES)
    private val movementCalibrationCos = cos(movementCalibrationRadians)
    private val movementCalibrationSin = sin(movementCalibrationRadians)

    fun worldToScreen(dx: Double, dy: Double): Point {
        val screenX = dx
        val screenY = -dy
        return Point(
            screenX * mapCalibrationCos - screenY * mapCalibrationSin,
            screenX * mapCalibrationSin + screenY * mapCalibrationCos
        )
    }

    private fun movementToScreen(dx: Double, dy: Double): Point {
        val screenX = dx
        val screenY = -dy
        return Point(
            screenX * movementCalibrationCos - screenY * movementCalibrationSin,
            screenX * movementCalibrationSin + screenY * movementCalibrationCos
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
            // Only outbound own-player Operation 22 position history defines
            // movement direction. Mount/dismount observations and UI markers
            // are state transitions, never synthetic position/stop vectors.
            val delta = movementToScreen(lastX - x, lastY - y)
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
