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
    fun keepsEvent210AsUnverifiedMountStateCandidate() {
        AlbionEntityEventMapper.reset()
        val mounted = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L, 1 to true))).single()
        assertEquals("mount_state_candidate", mounted.kind)
        assertEquals(44L, mounted.entityId)
        assertEquals(1f, mounted.value)
        val dismounted = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L, 1 to false))).single()
        assertEquals(0f, dismounted.value)
        val unknown = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L))).single()
        assertEquals(null, unknown.value)
    }

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
    fun mapsResourceListCoordinatesFromAllProtocol18NumericArrayTypes() {
        AlbionEntityEventMapper.reset()
        val intArrayNodes = AlbionEntityEventMapper.map(
            event(39, mapOf(
                0 to longArrayOf(101L, 102L),
                1 to intArrayOf(23, 24),
                2 to intArrayOf(5, 6),
                3 to intArrayOf(10, 20, 30, 40),
                4 to intArrayOf(3, 2)
            ))
        )
        assertEquals(2, intArrayNodes.size)
        assertEquals(listOf(10f, 30f), intArrayNodes.map { it.x })
        assertEquals(listOf(20f, 40f), intArrayNodes.map { it.y })
        assertEquals(listOf(23, 24), intArrayNodes.map { it.typeNumber })
        assertEquals(listOf(5, 6), intArrayNodes.map { it.tier })
        assertEquals(listOf(3, 2), intArrayNodes.map { it.size })

        val longArrayNode = AlbionEntityEventMapper.map(
            event(39, mapOf(0 to intArrayOf(103), 3 to longArrayOf(-50L, 75L)))
        ).single()
        assertEquals(-50f, longArrayNode.x)
        assertEquals(75f, longArrayNode.y)

        val doubleArrayNode = AlbionEntityEventMapper.map(
            event(39, mapOf(0 to shortArrayOf(104), 3 to doubleArrayOf(1.25, 2.5)))
        ).single()
        assertEquals(1.25f, doubleArrayNode.x)
        assertEquals(2.5f, doubleArrayNode.y)
    }

    @Test
    fun mapsSingleResourceCoordinatesFromIntegerArrays() {
        AlbionEntityEventMapper.reset()
        val harvest = AlbionEntityEventMapper.map(
            event(40, mapOf(0 to 7002L, 5 to 29, 6 to 574, 7 to 5,
                8 to intArrayOf(120, -80), 10 to 3, 11 to 2))
        ).single()
        assertEquals("harvestable", harvest.kind)
        assertEquals(120f, harvest.x)
        assertEquals(-80f, harvest.y)
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
                31 to "@MISTS@NOT_A_MOB_NAME",
                33 to "MISTS_DUNGEON_PORTAL",
                34 to 2
            ))
        ).single()
        assertEquals("mob", mob.kind)
        assertEquals(9001L, mob.entityId)
        assertEquals(410, mob.typeId)
        assertEquals(-12.5f, mob.x)
        assertEquals(44.0f, mob.y)
        assertEquals("MISTS_DUNGEON_PORTAL", mob.name)
        assertEquals(2, mob.enchant)

        assertTrue(AlbionEntityEventMapper.map(event(999, mapOf(0 to 123L))).isEmpty())
    }


    @Test
    fun event46OnlyMissingAmountMeansDepletedAndCarriesEnchantment() {
        AlbionEntityEventMapper.reset()
        val zero = AlbionEntityEventMapper.map(event(46, mapOf(0 to 701L, 1 to 0, 2 to 2))).single()
        assertEquals(0, zero.size)
        assertEquals(2, zero.enchant)
        assertTrue(zero.removed)
        val depleted = AlbionEntityEventMapper.map(event(46, mapOf(0 to 702L, 2 to 3))).single()
        assertTrue(depleted.removed)
        assertEquals(3, depleted.enchant)
    }

    @Test
    fun mapsVerifiedRegionIdentityFromJoinAndChangeClusterResponses() {
        AlbionEntityEventMapper.reset()
        val changed = AlbionEntityEventMapper.map(AlbionProtocol18Decoder.Message(
            kind = "response", dispatchCode = 0, realCode = 41,
            parameters = mapOf(0 to "MAP_BLACK_01", 253 to 41), operationCode = 41
        )).single()
        assertEquals("region_change", changed.kind)
        assertEquals("MAP_BLACK_01", changed.regionId)

        val joined = AlbionEntityEventMapper.map(AlbionProtocol18Decoder.Message(
            kind = "response", dispatchCode = 0, realCode = 2,
            parameters = mapOf(8 to "MAP_RED_02", 253 to 2), operationCode = 2
        )).single()
        assertEquals("MAP_RED_02", joined.regionId)
    }

    @Test
    fun usesPostOctober2026EventNumbersForMountedAndDungeonExit() {
        AlbionEntityEventMapper.reset()
        val mounted = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L, 1 to true))).single()
        assertEquals("mount_state_candidate", mounted.kind)
        assertEquals(1f, mounted.value)
        val dismounted = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L, 1 to false))).single()
        assertEquals(0f, dismounted.value)
        val unknownMountState = AlbionEntityEventMapper.map(event(210, mapOf(0 to 44L))).single()
        assertEquals(null, unknownMountState.value)
        assertTrue(AlbionEntityEventMapper.map(event(211, mapOf(0 to 44L, 1 to true))).isEmpty())

        val exit = AlbionEntityEventMapper.map(event(324, mapOf(
            1 to listOf(12f, 34f), 3 to "Dungeon Exit", 9 to 2
        ))).single()
        assertEquals("dungeon_exit", exit.kind)
        assertEquals(12f, exit.x)
        assertEquals(34f, exit.y)
        assertTrue(AlbionEntityEventMapper.map(event(325, mapOf(
            1 to listOf(12f, 34f), 3 to "Not a dungeon exit", 9 to 2
        ))).isEmpty())
    }

    @Test
    fun mapsConfirmedMistsTransitionToVerifiedRegionChange() {
        AlbionEntityEventMapper.reset()
        val transition = AlbionEntityEventMapper.map(event(521, mapOf(
            2 to "@MISTS@MAP_123", 3 to true, 4 to "MAP_ORIGIN"
        ))).single()
        assertEquals("region_change", transition.kind)
        assertEquals("@MISTS@MAP_123", transition.regionId)
        assertTrue(AlbionEntityEventMapper.map(event(521, mapOf(
            2 to "@MISTS@MAP_456", 3 to false
        ))).isEmpty())
    }

    @Test
    fun mapsAttackRequestAsCandidateIdsWithoutGuessingParameterIndex() {
        val mapped = AlbionEntityEventMapper.map(AlbionProtocol18Decoder.Message(
            kind = "request", dispatchCode = 0, realCode = 23,
            parameters = mapOf(0 to 777L, 1 to 99, 253 to 23), operationCode = 23
        )).single()
        assertEquals("target_request", mapped.kind)
        assertEquals(23, mapped.eventCode)
        assertTrue(mapped.candidateEntityIds.contains(777L))
        val intEncoded = AlbionEntityEventMapper.map(AlbionProtocol18Decoder.Message(
            kind = "request", dispatchCode = 0, realCode = 23,
            parameters = mapOf(0 to 777, 253 to 23), operationCode = 23
        )).single()
        assertTrue(intEncoded.candidateEntityIds.contains(777L))
    }

}
