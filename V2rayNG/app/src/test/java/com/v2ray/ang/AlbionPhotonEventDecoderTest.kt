package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionPhotonEventDecoderTest {
    @Test
    fun decodesResourceBatchAndFlattenedCoordinates() {
        val result = AlbionPhotonEventDecoder.decode(39, mapOf(
            0.toByte() to longArrayOf(2970, 2979),
            1.toByte() to intArrayOf(23, 24),
            2.toByte() to intArrayOf(4, 5),
            3.toByte() to floatArrayOf(-236f, 367f, -201f, 363f),
            4.toByte() to intArrayOf(3, 0)
        ))
        assertEquals(2, result.resources.size)
        assertEquals(2970L, result.resources[0].id)
        assertEquals(-236.0, result.resources[0].x)
        assertEquals(367.0, result.resources[0].y)
        assertEquals(5, result.resources[1].tier)
        assertNull(result.resources[0].enchantmentRaw)
    }

    @Test
    fun stateUpdateKeepsRawEnchantmentAndExactObjectId() {
        val result = AlbionPhotonEventDecoder.decode(46, mapOf(0.toByte() to 2900, 1.toByte() to 2, 2.toByte() to 1))
        assertEquals(2900L, result.stateUpdate?.id)
        assertEquals(2, result.stateUpdate?.amountOrState)
        assertEquals(1, result.stateUpdate?.enchantmentRaw)
    }

    @Test
    fun decodesLivingEntityPosition() {
        val result = AlbionPhotonEventDecoder.decode(123, mapOf(
            0.toByte() to 645014L, 1.toByte() to 2474,
            7.toByte() to floatArrayOf(30.26f, 359.09f)
        ))
        assertEquals(645014L, result.livingEntities.single().id)
        assertEquals(2474, result.livingEntities.single().typeId)
        assertEquals(30.26, result.livingEntities.single().x!!, 0.001)
    }

    @Test
    fun unknownEventIsNotGuessed() {
        val result = AlbionPhotonEventDecoder.decode(999, emptyMap<Any, Any>())
        assertFalse(result.recognized)
        assertTrue(result.resources.isEmpty())
        assertNull(result.stateUpdate)
    }
}
