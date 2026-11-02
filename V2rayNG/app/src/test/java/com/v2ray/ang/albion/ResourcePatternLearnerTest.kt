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

    @Test fun doesNotGuessUnknownFamilyOrMissingEnchantment() {
        val result = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 27, tier = 5, enchantRaw = null, mobileTypeId = -1)
        )
        assertNull(result.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.resourceName.status)
        assertNull(result.mobileTypeId.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, result.enchantmentName.status)
    }
}
