package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionMapProjectionTest {
    @Test
    fun albionWorldAxesAreCardinalNorthUp() {
        val north = AlbionMapProjection.worldToScreen(0.0, 1.0)
        val east = AlbionMapProjection.worldToScreen(1.0, 0.0)
        val south = AlbionMapProjection.worldToScreen(0.0, -1.0)
        val west = AlbionMapProjection.worldToScreen(-1.0, 0.0)
        assertEquals(0.0, north.x, 1e-9)
        assertEquals(-1.0, north.y, 1e-9)
        assertEquals(1.0, east.x, 1e-9)
        assertEquals(0.0, east.y, 1e-9)
        assertEquals(0.0, south.x, 1e-9)
        assertEquals(1.0, south.y, 1e-9)
        assertEquals(-1.0, west.x, 1e-9)
        assertEquals(0.0, west.y, 1e-9)
    }

    @Test
    fun movementArrowUsesSameNorthUpProjectionAsMapCoordinates() {
        val north = JSONArray()
            .put(JSONObject().put("x", 10.0).put("y", 10.0))
            .put(JSONObject().put("x", 10.0).put("y", 12.0))
        val south = JSONArray()
            .put(JSONObject().put("x", 10.0).put("y", 12.0))
            .put(JSONObject().put("x", 10.0).put("y", 10.0))
        val northDirection = AlbionMapProjection.movementDirection(north)!!
        val southDirection = AlbionMapProjection.movementDirection(south)!!
        assertEquals(0.0, northDirection.x, 1e-9)
        assertEquals(-1.0, northDirection.y, 1e-9)
        assertEquals(0.0, southDirection.x, 1e-9)
        assertEquals(1.0, southDirection.y, 1e-9)
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
