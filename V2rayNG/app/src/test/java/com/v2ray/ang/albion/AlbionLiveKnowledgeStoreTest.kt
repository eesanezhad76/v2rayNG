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

    @Test fun attachesProvenanceAwareResourceMappingsWithoutOverwritingRawPacketFields() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 808L,
            typeId = 574, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )))
        var record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        var inference = record.getJSONObject("resource_inference")
        assertTrue(record.optInt("mobile_type_id") == 574, record.toString())
        assertTrue(inference.getJSONObject("resource_name").optString("value") == "Ore", inference.toString())
        assertTrue(inference.getJSONObject("resource_name").optString("status") == "CAPTURE_CORROBORATED", inference.toString())
        assertTrue(inference.getJSONObject("mobile_type_id").optString("status") == "PACKET_CONFIRMED", inference.toString())
        assertTrue(inference.getJSONObject("enchantment_name").optString("value") == "E1", inference.toString())

        store.observe(now + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 808L,
            typeId = 571, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        inference = record.getJSONObject("resource_inference")
        assertTrue(inference.getJSONObject("mobile_type_id").optString("status") == "CONFLICT", inference.toString())
        assertTrue(inference.getJSONObject("mobile_type_id").isNull("value"), inference.toString())
        assertTrue(record.optInt("mobile_type_id") == 571, "Raw latest packet value must remain available")
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

    @Test fun catalogMobileTypeConflictDowngradesLiveEntityAndExcludesItFromConfirmedClass() {
        val store = AlbionLiveKnowledgeStore(context())
        store.observe(System.currentTimeMillis() + 1_000L, listOf(
            AlbionEntityEventMapper.Observation(
                kind = "harvestable", eventCode = 40, entityId = 8804L,
                typeId = 574, typeNumber = 29, tier = 4, enchant = 1, x = 20f, y = 30f
            )
        ))
        val record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("classification") == "ambiguous_resource_candidate")
        assertTrue(record.optString("evidence_grade") == "conflicting_typed_observation")
        assertTrue(record.optInt("evidence_score") == 20)
        assertTrue(record.optString("evidence_conflicts").contains("resource_catalog_mobile_type_mapping"))
        val mobile = record.optJSONObject("resource_inference")?.optJSONObject("mobile_type_id")
        assertTrue(mobile?.optString("status") == "CONFLICT")
        assertTrue(!record.optString("classification").equals("typed_resource_observation"))
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

    @Test fun retainsHarvestLifecycleAndDiscoversInventoryShapesWithoutAssertingItemIdentity() {
        val evidence = AlbionProtocolEvidenceStore(context())
        val now = System.currentTimeMillis() + 1_000L
        evidence.observe(now, 59, mapOf(0 to 777L))
        evidence.observe(now + 100L, 60, mapOf(0 to 777L))
        evidence.observe(now + 200L, 61, mapOf(0 to 777L))
        evidence.observe(now + 250L, 26, mapOf(0 to 123, 1 to intArrayOf(574, 1, 5)))
        evidence.observe(now + 300L, 40, mapOf(
            0 to 777L, 5 to 29, 6 to 574, 7 to 5, 8 to floatArrayOf(20f, 30f), 11 to 1
        ))
        val summary = evidence.summaryText()
        assertTrue(summary.contains("HarvestStart events retained across captures: 1"), summary)
        assertTrue(summary.contains("HarvestCancel events retained across captures: 1"), summary)
        assertTrue(summary.contains("HarvestFinished events retained across captures: 1"), summary)
        assertTrue(summary.contains("Inventory parameter shapes observed: 1"), summary)
        assertTrue(summary.contains("0=Integer,1=IntArray[3]"), summary)
        assertTrue(summary.contains("With a nearby typed resource event (candidate only): 1"), summary)
        assertTrue(summary.contains("No item ID, stack delta, or Ore identity is asserted"), summary)
    }

    @Test fun corruptEvidenceCacheDoesNotCrashOrCreateFalseInventoryEvidence() {
        File(tempDir, "albion-protocol-evidence.json").writeText("{broken-json")
        val evidence = AlbionProtocolEvidenceStore(context())
        val summary = evidence.summaryText()
        assertTrue(summary.contains("Inventory events retained across captures: 0"), summary)
        assertTrue(summary.contains("No item ID, stack delta, or Ore identity is asserted"), summary)
    }

    @Test fun duplicateBurstDoesNotInflateRepeatedEvidenceGrade() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        val same = AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 2222L,
            typeId = 574, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )
        store.observe(now, listOf(same))
        store.observe(now + 100L, listOf(same))
        store.observe(now + 200L, listOf(same))
        var record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optInt("sightings") == 1)
        assertTrue(record.optString("evidence_grade") == "typed_single")
        store.observe(now + 1_000L, listOf(same))
        store.observe(now + 2_000L, listOf(same))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optInt("sightings") == 3)
        assertTrue(record.optString("evidence_grade") == "repeated_typed")
    }

    @Test fun conflictingTypedEvidenceBecomesAmbiguousAndLowersEvidenceScore() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 4321L,
            typeId = 574, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )))
        val first = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(first.optInt("evidence_score") >= 50)

        store.observe(now + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 4321L,
            typeId = 575, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )))
        val conflicted = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(conflicted.optString("classification") == "ambiguous_resource_candidate")
        assertTrue(conflicted.optString("evidence_grade") == "conflicting_typed_observation")
        assertTrue(conflicted.optInt("evidence_score") < first.optInt("evidence_score"))
        assertTrue(conflicted.optString("evidence_reason").contains("mobile_type_id"))
        assertTrue(store.summaryText().contains("Ambiguous entities due to conflicting typed evidence: 1"))
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

    @Test fun retainsBoundedOwnPlayerMovementTrailForRadarRendering() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L, x = 10f, y = 20f
        )))
        store.observe(now + 500L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L, x = 12f, y = 23f
        )))
        val snapshot = org.json.JSONArray(store.snapshotJson())
        val player = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("last_kind") == "own_player_move" }
        val history = player.optJSONArray("movement_history")
        assertTrue(history != null && history.length() >= 2, player.toString())
        assertTrue(history!!.getJSONObject(history.length() - 1).optDouble("x") == 12.0)
    }

    @Test fun startsNewPlayerTrailAtEachCaptureSessionBoundary() {
        val firstSession = System.currentTimeMillis() + 1_000L
        val first = AlbionLiveKnowledgeStore(context(), firstSession)
        first.observe(firstSession + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L, x = 10f, y = 20f
        )))
        first.observe(firstSession + 600L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L, x = 12f, y = 23f
        )))
        first.flush()

        val secondSession = firstSession + 2_000L
        val second = AlbionLiveKnowledgeStore(context(), secondSession)
        second.observe(secondSession + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L, x = -60f, y = 240f
        )))
        val snapshot = org.json.JSONArray(second.snapshotJson())
        val player = snapshot.getJSONObject(0)
        val history = player.optJSONArray("movement_history")
        assertTrue(history != null && history.length() == 1, player.toString())
        assertTrue(history!!.getJSONObject(0).optDouble("x") == -60.0)
    }
}
