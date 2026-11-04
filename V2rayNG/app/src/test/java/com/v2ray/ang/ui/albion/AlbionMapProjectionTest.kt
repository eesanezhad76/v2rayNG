package com.v2ray.ang.ui.albion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionMapProjectionTest {
    @Test
    fun positiveWorldYMovesUpAndNegativeWorldYMovesDownOnScreen() {
        assertEquals(-1.0, AlbionMapProjection.worldToScreen(0.0, 1.0).y)
        assertEquals(1.0, AlbionMapProjection.worldToScreen(0.0, -1.0).y)
    }

    @Test
    fun movementArrowUsesTheSameNorthUpProjectionAsMapCoordinates() {
        val history = JSONArray()
            .put(JSONObject().put("x", 10.0).put("y", 10.0))
            .put(JSONObject().put("x", 10.0).put("y", 12.0))
        val direction = AlbionMapProjection.movementDirection(history)!!
        assertTrue(direction.y < 0.0, "Moving toward positive world Y must point upward on screen")
        assertEquals(0.0, direction.x, 1e-9)
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
