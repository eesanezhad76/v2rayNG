package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlbionPhotonEventDecoderTest {
    @Test fun decodesHarvestableSingleWithPositionTierAndEnchant() {
        val result = AlbionPhotonEventDecoder.decode(
            AlbionPhotonEventDecoder.EVENT_HARVESTABLE_SINGLE,
            mapOf(0 to 123L, 5 to 530, 7 to 6, 8 to floatArrayOf(12.5f, 40.25f), 10 to 3, 11 to 2)
        )
        assertTrue(result.recognized)
        assertEquals(1, result.resources.size)
        val resource = result.resources.single()
        assertEquals(123L, resource.id)
        assertEquals(530, resource.typeId)
        assertEquals(6, resource.tier)
        assertEquals(12.5, resource.x)
        assertEquals(40.25, resource.y)
        assertEquals(2, resource.enchantmentRaw)
    }

    @Test fun decodesHarvestableStateByExactEntityId() {
        val result = AlbionPhotonEventDecoder.decode(
            AlbionPhotonEventDecoder.EVENT_HARVESTABLE_STATE,
            mapOf(0 to 987L, 1 to 4, 2 to 3)
        )
        assertTrue(result.recognized)
        assertEquals(987L, result.stateUpdate?.id)
        assertEquals(4, result.stateUpdate?.amountOrState)
        assertEquals(3, result.stateUpdate?.enchantmentRaw)
    }

    @Test fun decodesLivingEntityCoordinates() {
        val result = AlbionPhotonEventDecoder.decode(
            AlbionPhotonEventDecoder.EVENT_LIVING_ENTITY,
            mapOf(0 to 77L, 1 to 530, 7 to floatArrayOf(8f, 9f))
        )
        assertTrue(result.recognized)
        assertEquals(77L, result.livingEntities.single().id)
        assertEquals(8.0, result.livingEntities.single().x)
        assertEquals(9.0, result.livingEntities.single().y)
    }
}
