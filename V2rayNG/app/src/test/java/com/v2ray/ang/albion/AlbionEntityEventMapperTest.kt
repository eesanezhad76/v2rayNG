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
            event(91, mapOf(0 to 202L, 2 to 12f, 3 to 100f, 6 to 639290000715162322L))
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
    fun decodesBatchedHealthUpdatesByExactEntityId() {
        AlbionEntityEventMapper.reset()
        val updates = AlbionEntityEventMapper.map(
            event(7, mapOf(
                0 to 999L,
                2 to listOf(-51f, -76f),
                3 to listOf(2436f, 821f),
                6 to listOf(630243L, 640358L)
            ))
        )
        assertEquals(2, updates.size)
        assertEquals(listOf(999L, 999L), updates.map { it.entityId })
        assertEquals(listOf(630243L, 640358L), updates.map { it.sourceEntityId })
        assertEquals(listOf(-51f, -76f), updates.map { it.value })
        assertEquals(listOf(2436f, 821f), updates.map { it.secondaryValue })
        assertTrue(updates.all { it.kind == "health" && it.eventCode == 7 })
    }
}
