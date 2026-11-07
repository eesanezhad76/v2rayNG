package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.math.cos
import kotlin.math.hypot
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
    fun albionRadar11AllFourMarkedWalksMapUpAndDownToScreenVertical() {
        // Exact first/last Move(22) coordinates from AlbionRadar11, region 4207.
        val up1 = JSONArray()
            .put(JSONObject().put("x", 123.33484).put("y", 63.11853))
            .put(JSONObject().put("x", 81.32623).put("y", 99.39885))
        val down1 = JSONArray()
            .put(JSONObject().put("x", 81.32623).put("y", 99.39885))
            .put(JSONObject().put("x", 144.50388).put("y", 45.80742))
        val up2 = JSONArray()
            .put(JSONObject().put("x", 147.12090).put("y", 47.88989))
            .put(JSONObject().put("x", 99.12552).put("y", 88.09909))
        val down2 = JSONArray()
            .put(JSONObject().put("x", 85.52746).put("y", 100.62939))
            .put(JSONObject().put("x", 145.87286).put("y", 50.51274))

        val upDirections = listOf(up1, up2).map { AlbionMapProjection.movementDirection(it)!! }
        val downDirections = listOf(down1, down2).map { AlbionMapProjection.movementDirection(it)!! }

        // Endpoint vectors include the user's slight diagonal hand path.
        // The independently fitted 49.819-degree calibration maps all UP
        // walks to screen-up and both reversed walks to screen-down.
        upDirections.forEach {
            assertTrue(it.y < -0.999)
            assertTrue(kotlin.math.abs(it.x) < 0.02)
        }
        downDirections.forEach {
            assertTrue(it.y > 0.999)
            assertTrue(kotlin.math.abs(it.x) < 0.02)
        }
        upDirections.zip(downDirections).forEach { (up, down) ->
            assertTrue(hypot(up.x + down.x, up.y + down.y) < 0.03)
        }
    }

    @Test
    fun movementDirectionDisappearsWhenTheLastPositionSampleIsStale() {
        val history = JSONArray()
            .put(JSONObject().put("x", 1.0).put("y", 1.0).put("time_ms", 1_000L))
            .put(JSONObject().put("x", 2.0).put("y", 2.0).put("time_ms", 1_500L))
        assertNull(AlbionMapProjection.movementDirection(history, nowMs = 5_000L))
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
