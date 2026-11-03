package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionGatheringPatternLearnerTest {
    @TempDir lateinit var tempDir: File

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
    }

    private fun start(learner: AlbionGatheringPatternLearner, time: Long) {
        learner.observeMarker(org.json.JSONObject()
            .put("event", "target_found").put("activity", "Ore")
            .put("tier", "T5").put("enchantment", "E3"), time)
    }

    private fun sample(id: Long) = AlbionEntityEventMapper.Observation(
        kind = "harvestable", eventCode = 40, entityId = id,
        typeId = 999, typeNumber = 29, tier = 5, enchant = 7,
        x = id.toFloat(), y = 20f
    )

    private fun end(learner: AlbionGatheringPatternLearner, time: Long) {
        learner.observeMarker(org.json.JSONObject().put("event", "gathering_completed"), time)
    }

    @Test fun learnsUnknownRawEnchantmentOnlyAfterRepeatedSamplesAcrossCaptureSessions() {
        val first = AlbionGatheringPatternLearner(context(), "capture-A")
        start(first, 1_000L)
        first.observe(1_100L, listOf(sample(1)))
        first.observe(1_200L, listOf(sample(2)))
        end(first, 2_000L)

        val second = AlbionGatheringPatternLearner(context(), "capture-B")
        start(second, 3_000L)
        second.observe(3_100L, listOf(sample(3)))
        end(second, 4_000L)

        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 5, enchantRaw = 7, mobileTypeId = null)
        ).enchantmentName
        assertEquals("E3", inferred.value)
        assertEquals(ResourcePatternLearner.Status.CAPTURE_CORROBORATED, inferred.status)
    }

    @Test fun keepsMarkerSessionOpenUntilExplicitEndAndIgnoresOutOfOrderObservations() {
        val learner = AlbionGatheringPatternLearner(context(), "capture-window")
        start(learner, 10_000L)
        learner.observe(9_999L, listOf(sample(10).copy(enchant = 8)))
        learner.observe(10_000L + 16 * 60_000L, listOf(sample(11).copy(enchant = 7)))
        end(learner, 10_000L + 16 * 60_000L + 1L)

        val second = AlbionGatheringPatternLearner(context(), "capture-window-B")
        start(second, 20_000L + 16 * 60_000L)
        second.observe(20_001L + 16 * 60_000L, listOf(sample(12).copy(enchant = 7)))
        end(second, 20_002L + 16 * 60_000L)

        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 5, enchantRaw = 7, mobileTypeId = null)
        ).enchantmentName
        assertEquals("E3", inferred.value)
        assertEquals(ResourcePatternLearner.Status.CAPTURE_CORROBORATED, inferred.status)
    }

    @Test fun combatMarkersDiscardAnUnfinishedGatheringCycle() {
        val learner = AlbionGatheringPatternLearner(context(), "capture-interrupted")
        start(learner, 30_000L)
        learner.observe(30_100L, listOf(sample(30).copy(enchant = 9)))
        learner.observeMarker(org.json.JSONObject().put("event", "target_found").put("activity", "Mob"), 30_200L)
        learner.observeMarker(org.json.JSONObject().put("event", "mob_killed"), 31_000L)

        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 5, enchantRaw = 9, mobileTypeId = null)
        ).enchantmentName
        assertEquals(null, inferred.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, inferred.status)
    }

    @Test fun ignoresUnverifiedFamiliesAndTierMismatches() {
        val learner = AlbionGatheringPatternLearner(context(), "capture-mismatch")
        start(learner, 20_000L)
        learner.observe(20_100L, listOf(
            sample(20).copy(typeNumber = 7, enchant = 9),
            sample(21).copy(tier = 4, enchant = 9)
        ))
        end(learner, 21_000L)

        val inferred = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 29, tier = 5, enchantRaw = 9, mobileTypeId = null)
        ).enchantmentName
        assertEquals(null, inferred.value)
        assertEquals(ResourcePatternLearner.Status.UNKNOWN, inferred.status)
    }

}
