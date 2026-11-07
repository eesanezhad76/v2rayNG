package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.cos
import kotlin.math.sin
import org.junit.jupiter.api.Test

class AlbionMapProjectionTest {
    @Test
    fun albionWorldAxesUseMeasuredClockwiseCalibration() {
        val north = AlbionMapProjection.worldToScreen(0.0, 1.0)
        val east = AlbionMapProjection.worldToScreen(1.0, 0.0)
        val south = AlbionMapProjection.worldToScreen(0.0, -1.0)
        val west = AlbionMapProjection.worldToScreen(-1.0, 0.0)
        val radians = Math.toRadians(AlbionMapProjection.CLOCKWISE_CALIBRATION_DEGREES)
        val c = cos(radians)
        val s = sin(radians)
        assertEquals(s, north.x, 1e-9)
        assertEquals(-c, north.y, 1e-9)
        assertEquals(c, east.x, 1e-9)
        assertEquals(s, east.y, 1e-9)
        assertEquals(-s, south.x, 1e-9)
        assertEquals(c, south.y, 1e-9)
        assertEquals(-c, west.x, 1e-9)
        assertEquals(-s, west.y, 1e-9)
    }

    @Test
    fun movementArrowUsesTheCalibratedUserUpAxis() {
        // Mean measured UP bearing: 139.819 degrees in world XY.
        val radians = Math.toRadians(139.819)
        val history = JSONArray()
            .put(JSONObject().put("x", 0.0).put("y", 0.0))
            .put(JSONObject().put("x", cos(radians)).put("y", sin(radians)))
        val direction = AlbionMapProjection.movementDirection(history)!!
        assertEquals(0.0, direction.x, 0.001)
        assertEquals(-1.0, direction.y, 0.001)
    }

    @Test
    fun movementDirectionDisappearsWhenTheLastPositionSampleIsStale() {
        val history = JSONArray()
            .put(JSONObject().put("x", 1.0).put("y", 1.0).put("time_ms", 1_000L))
            .put(JSONObject().put("x", 2.0).put("y", 2.0).put("time_ms", 1_500L))
        assertEquals(null, AlbionMapProjection.movementDirection(history, nowMs = 5_000L))
    }

    @Test
    fun movementRightAndLeftRemainConsistentWithWorldX() {
        val right = JSONArray()
            .put(JSONObject().put("x", 1.0).put("y", 1.0))
            .put(JSONObject().put("x", 3.0).put("y", 1.0))
        val left = JSONArray()
            .put(JSONObject().put("x", 3.0).put("y", 1.0))
            .put(JSONObject().put("x", 1.0).put("y", 1.0))
        assertTrue(AlbionMapProjection.movementDirection(right)!!.x > 0.0)
        assertTrue(AlbionMapProjection.movementDirection(left)!!.x < 0.0)
    }
}
