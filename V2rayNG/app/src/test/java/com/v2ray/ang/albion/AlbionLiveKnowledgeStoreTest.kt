package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionLiveKnowledgeStoreTest {
    @TempDir lateinit var tempDir: File

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
    }

    @Test fun persistsTypedResourceObservationsAcrossStoreInstances() {
        val first = AlbionLiveKnowledgeStore(context())
        first.observe(
            1_000L,
            listOf(
                AlbionEntityEventMapper.Observation(
                    kind = "harvestable",
                    eventCode = 40,
                    entityId = 1234L,
                    typeId = 574,
                    typeNumber = 29,
                    tier = 5,
                    enchant = 1,
                    x = 42.5f,
                    y = 17.25f
                )
            )
        )
        first.flush()

        val restored = AlbionLiveKnowledgeStore(context())
        val summary = restored.summaryText()
        assertTrue(summary.contains("Unique tracked entities: 1"), summary)
        assertTrue(summary.contains("Typed resource observations: 1"), summary)
    }

    @Test fun liveSnapshotIncludesTypedResourcesAndValidatedPlayerMovement() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(
            now,
            listOf(
                AlbionEntityEventMapper.Observation(
                    kind = "harvestable", eventCode = 40, entityId = 901L,
                    typeId = 574, typeNumber = 29, tier = 5, x = 20f, y = 30f
                ),
                AlbionEntityEventMapper.Observation(
                    kind = "player_move", eventCode = 3, entityId = 77L,
                    x = 21f, y = 31f
                )
            )
        )
        val snapshot = store.snapshotText()
        assertTrue(snapshot.contains("Resource T5"), snapshot)
        assertTrue(snapshot.contains("Player movement"), snapshot)
        assertTrue(snapshot.contains("proximity alone is not confirmation"), snapshot)
    }

    @Test fun gradesExactIdResourceStateAsCorroboratingEvidence() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(
            now,
            listOf(
                AlbionEntityEventMapper.Observation(
                    kind = "harvestable", eventCode = 40, entityId = 777L,
                    typeId = 574, typeNumber = 29, tier = 5, x = 20f, y = 30f
                )
            )
        )
        store.observe(
            now + 10L,
            listOf(
                AlbionEntityEventMapper.Observation(
                    kind = "resource_state", eventCode = 46, entityId = 777L,
                    removed = true
                )
            )
        )
        val snapshot = store.snapshotText()
        assertTrue(snapshot.contains("corroborated_same_id"), snapshot)
        assertTrue(snapshot.contains("[depleted]"), snapshot)
    }

    @Test fun correlatesInventoryEventWithHarvestFinishAndNearbyTypedResourceWithoutClaimingOreIdentity() {
        val evidence = AlbionProtocolEvidenceStore(context())
        val now = System.currentTimeMillis() + 1_000L
        evidence.observe(now, 40, mapOf(
            0 to 777L, 5 to 574, 7 to 5, 8 to floatArrayOf(20f, 30f), 11 to 1
        ))
        evidence.observe(now + 1_000L, 61, mapOf(0 to 777L))
        evidence.observe(now + 2_000L, 26, mapOf(0 to 123, 1 to intArrayOf(574, 1, 5)))
        val summary = evidence.summaryText()
        assertTrue(summary.contains("Inventory events retained across captures: 1"), summary)
        assertTrue(summary.contains("With a nearby typed resource event (candidate only): 1"), summary)
        assertTrue(summary.contains("type=574 tier=5"), summary)
        assertTrue(summary.contains("No item ID, stack delta, or Ore identity is asserted"), summary)
    }

    @Test fun rejectsInvalidCoordinatesAndDoesNotPromoteStaticCandidateToTypedOre() {
        val store = AlbionLiveKnowledgeStore(context())
        store.observe(
            2_000L,
            listOf(
                AlbionEntityEventMapper.Observation(
                    kind = "static_resource",
                    eventCode = 39,
                    entityId = 5678L,
                    x = Float.NaN,
                    y = 12f
                )
            )
        )
        store.flush()
        val restored = AlbionLiveKnowledgeStore(context())
        val summary = restored.summaryText()
        assertTrue(summary.contains("Static resource candidates (not confirmed Ore): 0"), summary)
        assertTrue(summary.contains("Typed resource observations: 0"), summary)
    }
}
