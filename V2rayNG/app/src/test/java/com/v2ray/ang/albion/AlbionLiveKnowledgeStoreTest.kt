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
        store.observe(
            3_000L,
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
