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
    fun mapsOwnPlayerPositionFromOutboundMoveOperation22() {
        AlbionEntityEventMapper.reset()
        val move = AlbionProtocol18Decoder.Message(
            kind = "request",
            dispatchCode = 0,
            realCode = 22,
            parameters = mapOf(
                1 to listOf(-293.07f, 314.89f),
                2 to 303.76f,
                3 to listOf(-292.25f, 314.31f),
                4 to 11.0f,
                253 to 22
            ),
            operationCode = 22
        )
        val observation = AlbionEntityEventMapper.map(move).single()
        assertEquals("own_player_move", observation.kind)
        assertEquals(-1L, observation.entityId)
        assertEquals(-293.07f, observation.x)
        assertEquals(314.89f, observation.y)
        assertEquals(303.76f, observation.heading)
        assertEquals(11.0f, observation.speed)
    }

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

    @Test
    fun mapsTypedHarvestableAndMobEntitiesButLeavesUnknownEventsUnclassified() {
        AlbionEntityEventMapper.reset()
        val harvest = AlbionEntityEventMapper.map(
            event(40, mapOf(
                0 to 7001L,
                5 to 29,
                6 to 574,
                7 to 5,
                8 to listOf(20.5f, 30.25f),
                10 to 4,
                11 to 1
            ))
        ).single()
        assertEquals("harvestable", harvest.kind)
        assertEquals(7001L, harvest.entityId)
        assertEquals(574, harvest.typeId)
        assertEquals(29, harvest.typeNumber)
        assertEquals(5, harvest.tier)
        assertEquals(1, harvest.enchant)
        assertEquals(20.5f, harvest.x)
        assertEquals(30.25f, harvest.y)
        assertEquals(4, harvest.size)

        val mob = AlbionEntityEventMapper.map(
            event(123, mapOf(
                0 to 9001L,
                1 to 410,
                7 to listOf(-12.5f, 44.0f),
                31 to "TestMob",
                34 to 2
            ))
        ).single()
        assertEquals("mob", mob.kind)
        assertEquals(9001L, mob.entityId)
        assertEquals(410, mob.typeId)
        assertEquals(-12.5f, mob.x)
        assertEquals(44.0f, mob.y)
        assertEquals("TestMob", mob.name)
        assertEquals(2, mob.enchant)

        assertTrue(AlbionEntityEventMapper.map(event(999, mapOf(0 to 123L))).isEmpty())
    }


}
