package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionEntityEventMapperTest {
    private fun event(code: Int, parameters: Map<Int, Any?>) =
        AlbionProtocol18Decoder.Message(
            kind = "event",
            dispatchCode = code,
            realCode = code,
            parameters = parameters
        )

    @Test
    fun mapsHealthUpdateSeparatelyFromEnergyUpdate() {
        AlbionEntityEventMapper.reset()
        val health = AlbionEntityEventMapper.map(
            event(6, mapOf(0 to 101L, 2 to 72.5f, 3 to 100f))
        ).single()
        assertEquals("health", health.kind)
        assertEquals(101L, health.entityId)
        assertEquals(72.5f, health.value)
        assertEquals(100f, health.secondaryValue)

        val energy = AlbionEntityEventMapper.map(
            event(8, mapOf(0 to 101L, 2 to 41f, 3 to 80f))
        ).single()
        assertEquals("energy", energy.kind)
        assertEquals(101L, energy.entityId)
        assertEquals(41f, energy.value)
        assertEquals(80f, energy.secondaryValue)
    }

    @Test
    fun mapsHealthAndEnergyRegenerationSeparately() {
        AlbionEntityEventMapper.reset()
        val health = AlbionEntityEventMapper.map(
            event(91, mapOf(6 to 202L, 2 to 12f, 3 to 100f))
        ).single()
        assertEquals("health_regeneration", health.kind)
        assertEquals(202L, health.entityId)
        assertEquals(12f, health.value)

        val energy = AlbionEntityEventMapper.map(
            event(92, mapOf(6 to 202L, 2 to 7f, 3 to 80f))
        ).single()
        assertEquals("energy_regeneration", energy.kind)
        assertEquals(202L, energy.entityId)
        assertEquals(7f, energy.value)
    }

    @Test
    fun leavesMultiHealthUpdateAsRawOnlyUntilItsShapeIsProven() {
        AlbionEntityEventMapper.reset()
        assertTrue(AlbionEntityEventMapper.map(
            event(7, mapOf(0 to listOf(101L, 102L), 1 to listOf(80f, 60f)))
        ).isEmpty())
    }
}
