package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResourcePatternLearnerTest {
    @Test fun infersOreMobileTypeAndProvisionalEnchantmentFromKnownPattern() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 4, enchantRaw = 2, mobileTypeId = null)
        )
        assertEquals("Ore", result.resourceName.value)
        assertEquals("573", result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.CAPTURE_CORROBORATED, result.mobileTypeId.status)
        assertEquals("E2", result.enchantmentName.value)
        assertEquals(ResourcePatternLearner.Status.PROVISIONAL_MAPPING, result.enchantmentName.status)
    }

    @Test fun preservesPacketMobileTypeAndFlagsContradictoryCatalogEvidence() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 4, enchantRaw = 0, mobileTypeId = 574)
        )
        assertNull(result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.CONFLICT, result.mobileTypeId.status)
    }

    @Test fun treatsNegativeMobileTypeSentinelAsMissingAndUsesOnlySupportedTierRule() {
        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 4, enchantRaw = 0, mobileTypeId = -1)
        )
        assertEquals("573", inferred.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.CAPTURE_CORROBORATED, inferred.mobileTypeId.status)

        val unsupportedTier = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 6, enchantRaw = 0, mobileTypeId = -1)
        )
        assertNull(unsupportedTier.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, unsupportedTier.mobileTypeId.status)
    }

    @Test fun mapsRawEnchantmentThreeToProvisionalE3() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 5, enchantRaw = 3, mobileTypeId = null)
        )
        assertEquals("E3", result.enchantmentName.value)
        assertEquals(ResourcePatternLearner.Status.PROVISIONAL_MAPPING, result.enchantmentName.status)
    }

    @Test fun mapsStaticTypeNumberRangesToResourceKinds() {
        val expected = mapOf(7 to "Rock", 14 to "Fiber", 20 to "Hide", 27 to "Ore")
        expected.forEach { (typeNumber, resource) ->
            val result = ResourcePatternLearner.infer(
                ResourcePatternLearner.Observation(typeNumber, 5, 1, -1)
            )
            assertEquals(resource, result.resourceName.value, "type number " + typeNumber)
            assertEquals(ResourcePatternLearner.Status.PROVISIONAL_MAPPING, result.resourceName.status)
        }
    }

    @Test fun nonHarvestableMobileTypeDoesNotOverrideStaticHideTypeNumber() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 20, tier = 1, enchantRaw = 0, mobileTypeId = 482)
        )
        assertEquals("Hide", result.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.PROVISIONAL_MAPPING, result.resourceName.status)
        assertNull(result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.mobileTypeId.status)
    }

    @Test fun harvestableMobileTypeOverridesStaticTypeNumber() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 20, tier = 4, enchantRaw = 1, mobileTypeId = 573)
        )
        assertEquals("Ore", result.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.CAPTURE_CORROBORATED, result.resourceName.status)
        assertEquals("573", result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.PACKET_CONFIRMED, result.mobileTypeId.status)
    }

    @Test fun leavesTypeNumber28Unresolved() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 28, tier = 5, enchantRaw = 1, mobileTypeId = -1)
        )
        assertNull(result.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.resourceName.status)
    }

    @Test fun doesNotGuessUnknownFamilyOrMissingEnchantment() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = null, tier = 5, enchantRaw = null, mobileTypeId = -1)
        )
        assertNull(result.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.resourceName.status)
        assertNull(result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.enchantmentName.status)
    }
}
