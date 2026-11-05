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

    @Test fun coordinateSnapshotIncludesMoreThanTwoThousandKnownNodesFromTheCurrentSession() {
        val store = AlbionLiveKnowledgeStore(context(), 50_000L)
        val observations = (1L..2_105L).map { id ->
            AlbionEntityEventMapper.Observation(
                kind = "harvestable", eventCode = 40, entityId = id,
                typeId = 574, typeNumber = 29, tier = 5, enchant = 1,
                x = id.toFloat(), y = (id % 97).toFloat()
            )
        }
        store.observe(51_000L, observations)
        val snapshot = org.json.JSONArray(store.snapshotJson())
        assertTrue(snapshot.length() == 2_105, "Expected every current-session node, got ${snapshot.length()}")
    }

    @Test fun resourceDepletionPersistsUntilARefillIsObserved() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 7001L,
            typeId = 574, typeNumber = 29, tier = 5, size = 3, x = 20f, y = 30f
        )))
        store.observe(now + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 7001L,
            size = null, removed = true
        )))
        store.observe(now + 150L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 7001L,
            size = 0, removed = false
        )))
        var record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optBoolean("depleted"), record.toString())

        store.observe(now + 200L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 7001L,
            typeId = 574, typeNumber = 29, tier = 5, size = 2, x = 20f, y = 30f
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(!record.optBoolean("depleted"), record.toString())
        assertTrue(record.optInt("resource_remaining") == 2, record.toString())
    }

    @Test fun preservesDepletedE2AndRecordsARefilledSpawnAsANewGeneration() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now - 1L, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 7002L,
            typeId = 574, typeNumber = 29, tier = 6, enchant = 2, size = 3, x = 20f, y = 30f
        )))
        store.observe(now + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 7002L, size = 0, removed = true
        )))
        store.observe(now + 200L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 7002L,
            typeId = 574, typeNumber = 29, tier = 6, enchant = 2, size = 2, x = 20f, y = 30f
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        assertTrue(rows.length() == 2, "Expected old depleted node plus new spawn: $rows")
        val old = (0 until rows.length()).map { rows.getJSONObject(it) }
            .first { it.optInt("spawn_generation") == 0 }
        val fresh = (0 until rows.length()).map { rows.getJSONObject(it) }
            .first { it.optInt("spawn_generation") == 1 }
        assertTrue(old.optBoolean("depleted"), old.toString())
        assertTrue(old.optString("superseded_by_record_key") == fresh.optString("record_key"))
        assertTrue(fresh.optString("record_key").contains(":MAP_A:7002:spawn:1"), fresh.toString())
        assertTrue(!fresh.optBoolean("depleted"), fresh.toString())
        assertTrue(fresh.optInt("resource_remaining") == 2, fresh.toString())
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

    @Test fun retainsMountEvent210RawEvidenceForMountAndDismountAudit() {
        val evidence = AlbionProtocolEvidenceStore(context())
        val now = System.currentTimeMillis() + 1_000L
        evidence.observe(now, 210, mapOf(0 to 44L, 1 to true))
        evidence.observe(now + 1L, 210, mapOf(0 to 44L, 1 to false))
        val summary = evidence.summaryText()
        assertTrue(summary.contains("Mount/dismount event 210 observations retained: 2"), summary)
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

    @Test fun retainsMovementHeadingAndMountStateWithoutBlockingOnFootMovement() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "player_mounted", eventCode = 210, entityId = 44L, value = 1f
        )))
        store.observe(now + 500L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L,
            x = 10f, y = 20f, heading = 303.76f, speed = 11f
        )))
        store.observe(now + 1_000L, listOf(AlbionEntityEventMapper.Observation(
            kind = "player_mounted", eventCode = 210, entityId = 44L, value = 0f
        )))
        store.observe(now + 1_500L, listOf(AlbionEntityEventMapper.Observation(
            kind = "own_player_move", eventCode = 22, entityId = -1L,
            x = 12f, y = 23f, heading = 303.76f, speed = 11f
        )))
        val snapshot = org.json.JSONArray(store.snapshotJson())
        val player = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("last_kind") == "own_player_move" }
        val history = player.getJSONArray("movement_history")
        assertTrue(history.length() == 2, player.toString())
        assertTrue(history.getJSONObject(0).optDouble("heading") == 303.76, history.toString())
        assertTrue(history.getJSONObject(0).optBoolean("mounted"), history.toString())
        assertTrue(!history.getJSONObject(1).optBoolean("mounted", true), history.toString())
        assertTrue(history.getJSONObject(1).optDouble("speed") == 11.0, history.toString())
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

    @Test fun doesNotMergeSameRuntimeEntityIdAcrossCaptureSessionsWithoutZoneIdentity() {
        val first = AlbionLiveKnowledgeStore(context(), 10_000L)
        first.observe(10_100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 777L,
            typeId = 574, typeNumber = 29, tier = 5, enchant = 1, x = 20f, y = 30f
        )))
        first.flush()

        val second = AlbionLiveKnowledgeStore(context(), 20_000L)
        second.observe(20_100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 777L,
            typeId = 571, typeNumber = 29, tier = 3, enchant = 0, x = -20f, y = -30f
        )))
        val current = org.json.JSONArray(second.snapshotJson())
        assertTrue(current.length() == 1)
        val record = current.getJSONObject(0)
        assertTrue(record.optString("entity_id") == "777")
        assertTrue(record.optInt("tier") == 3)
        assertTrue(record.optInt("evidence_conflicts") == 0 || record.optJSONArray("evidence_conflicts")?.length() == 0)
        assertTrue(second.summaryText().contains("Unique tracked entities: 2"))
    }
    @Test fun keepsKilledMobIdentityAndHealthAfterLeaveEvent() {
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 9901L,
            typeId = 701, enchant = 1, x = 42f, y = -17f, name = "Forest Troll"
        )))
        store.observe(now + 100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "health", eventCode = 7, entityId = 9901L,
            sourceEntityId = 77L, value = -25f, secondaryValue = 75f
        )))
        store.observe(now + 200L, listOf(AlbionEntityEventMapper.Observation(
            kind = "leave", eventCode = 1, entityId = 9901L, removed = true
        )))
        val snapshot = org.json.JSONArray(store.snapshotJson())
        val mob = snapshot.getJSONObject(0)
        assertTrue(mob.optString("last_kind") == "mob", mob.toString())
        assertTrue(mob.optString("name") == "Forest Troll", mob.toString())
        assertTrue(mob.optBoolean("left"), mob.toString())
        assertTrue(mob.optBoolean("health_observed"), mob.toString())
        assertTrue(mob.optDouble("health_value") == 75.0, mob.toString())
        assertTrue(mob.optDouble("health_delta") == -25.0, mob.toString())
        assertTrue(mob.optString("last_health_source_entity_id") == "77", mob.toString())
    }

    @Test fun verifiedRegionChangeStartsAutomaticScanAndHidesPreviousRegionNodes() {
        val store = AlbionLiveKnowledgeStore(context(), 10_000L)
        store.observe(10_100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(10_200L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 11L,
            typeId = 574, typeNumber = 29, tier = 5, enchant = 2, size = 3, x = 10f, y = 20f
        )))
        store.observe(11_000L, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_B"
        )))
        store.observe(11_100L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 11L,
            typeId = 574, typeNumber = 29, tier = 6, enchant = 3, size = 2, x = 90f, y = 80f
        )))
        val current = org.json.JSONArray(store.snapshotJson())
        assertTrue(current.length() == 1, "Previous-region nodes must not leak into live map: $current")
        assertTrue(current.getJSONObject(0).optString("region_id") == "MAP_B")
        assertTrue(current.getJSONObject(0).optDouble("x") == 90.0)
        val scan = org.json.JSONArray(store.regionScanSnapshotJson(11_200L))
        assertTrue(scan.length() == 1, "Auto scan must be scoped to the active region: $scan")
        assertTrue(scan.getJSONObject(0).optString("region_id") == "MAP_B")
    }

    @Test fun targetRequestBeforeMobSpawnCanStillAttachExactTrollMarker() {
        val store = AlbionLiveKnowledgeStore(context(), 35_000L)
        val now = 35_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(now + 1L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(9901L)
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 2L)
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 9901L, typeId = 573, x = 12f, y = 18f
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val mob = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "9901" }
        assertEquals("MAP_A", mob.optString("region_id"))
        assertEquals("Troll", mob.optString("manual_activity"))
        assertTrue(mob.optBoolean("manual_marker_identity_proven"), mob.toString())
    }

    @Test fun targetRequestAfterTrollMarkerCanAttachBeforeMobSpawnArrives() {
        val store = AlbionLiveKnowledgeStore(context(), 36_000L)
        val now = 36_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 1L)
        store.observe(now + 2L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(9902L)
        )))
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 9902L, typeId = 573, x = -4f, y = 9f
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val mob = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "9902" }
        assertEquals("Troll", mob.optString("manual_activity"))
        assertTrue(mob.optBoolean("manual_marker_identity_proven"), mob.toString())
    }

    @Test fun multipleUnseenTargetCandidatesAreNotGuessedFromLaterMobSpawn() {
        val store = AlbionLiveKnowledgeStore(context(), 37_000L)
        val now = 37_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(now + 1L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(9903L, 9904L)
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 2L)
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 9903L, typeId = 573, x = 5f, y = 7f
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val mob = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "9903" }
        assertFalse(mob.has("manual_activity"), mob.toString())
        assertFalse(mob.optBoolean("manual_marker_identity_proven"), mob.toString())
    }

    @Test fun delayedTrollMarkerJoinNeverLabelsANonMobResource() {
        val store = AlbionLiveKnowledgeStore(context(), 38_000L)
        val now = 38_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(now + 1L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(9905L)
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 2L)
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 9905L,
            typeId = 574, typeNumber = 32, tier = 4, enchant = 2, size = 30, x = -375f, y = 103f
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val resource = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "9905" }
        assertFalse(resource.has("manual_activity"), resource.toString())
        assertFalse(resource.optBoolean("manual_marker_identity_proven"), resource.toString())
    }

    @Test fun regionChangeClearsOldCurrentTargetBeforeAttachingNewRegionTrollMarker() {
        val store = AlbionLiveKnowledgeStore(context(), 40_000L)
        val now = 40_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observe(now + 1L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 777L, typeId = 573, x = 10f, y = 20f
        )))
        store.observe(now + 2L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(777L)
        )))
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_B"
        )))
        // Runtime entity IDs can be reused after a cluster transition.
        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 777L, typeId = 574, x = -5f, y = 8f
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 5L)
        store.observe(now + 6L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(777L)
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val current = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "777" }
        assertTrue(current.optString("region_id") == "MAP_B", current.toString())
        assertTrue(current.optString("manual_activity") == "Troll", current.toString())
        assertTrue(current.optBoolean("manual_marker_identity_proven"), current.toString())
    }

    @Test fun regionChangeDiscardsUnresolvedTrollMarkerInsteadOfApplyingItToTheNextRegion() {
        val store = AlbionLiveKnowledgeStore(context(), 50_000L)
        val now = 50_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_A"
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "T4"), now + 1L)
        store.observe(now + 2L, listOf(AlbionEntityEventMapper.Observation(
            kind = "region_change", eventCode = 41, regionId = "MAP_B"
        )))
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 888L, typeId = 573, x = 4f, y = 9f
        )))
        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(888L)
        )))
        val rows = org.json.JSONArray(store.snapshotJson())
        val current = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.optString("entity_id") == "888" }
        assertTrue(!current.has("manual_activity"), current.toString())
        assertTrue(!current.optBoolean("manual_marker_identity_proven"), current.toString())
    }

    @Test fun separatesTrollMarkerFromOreElementalAndTracksAliveDeadHarvestedLifecycle() {
        val store = AlbionLiveKnowledgeStore(context(), 20_000L)
        val now = 20_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 777L,
            typeId = 571, x = 12f, y = 34f
        )))
        var record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("name") == "Ore Elemental", record.toString())
        assertTrue(record.optString("resource_role") == "ore_elemental", record.toString())
        assertTrue(record.optBoolean("is_harvestable_mob"), record.toString())

        // A label-only marker without target_id must never be attached by proximity.
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "3").put("enchantment", "E1"), now + 1L)
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(!record.optBoolean("manual_marker_identity_proven"), record.toString())

        // Exact entity ID is the only accepted way to attach the user's Troll activity label.
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found")
            .put("tier", "3").put("enchantment", "E1"), now + 2L)
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(777L, 99L)
        )))
        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "health", eventCode = 6, entityId = 777L, value = -856f,
            secondaryValue = null
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optBoolean("mob_dead"), record.toString())
        assertTrue(record.optString("mob_state") == "dead", record.toString())
        assertTrue(record.optString("manual_activity") == "Troll", record.toString())
        assertTrue(record.optBoolean("manual_marker_identity_proven"), record.toString())
        assertTrue(record.optInt("marker_enchant") == 1, record.toString())

        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 777L,
            typeId = 571, typeNumber = 29, tier = 3, size = 2, x = 12f, y = 34f
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("mob_state") == "dead_harvestable", record.toString())

        store.observe(now + 5L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 777L,
            size = null, removed = true
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("mob_state") == "mined", record.toString())
        assertTrue(record.optBoolean("depleted"), record.toString())
    }

    @Test fun mapsExactOreElementalIdAndKeepsTrollMarkerSeparateAcrossDeathAndMining() {
        val store = AlbionLiveKnowledgeStore(context(), 20_000L)
        val now = 20_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 777L,
            typeId = 571, x = 12f, y = 34f
        )))
        var record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("name") == "Ore Elemental", record.toString())
        assertTrue(record.optString("resource_role") == "ore_elemental", record.toString())
        assertTrue(record.optBoolean("is_harvestable_mob"), record.toString())

        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "3").put("enchantment", "E1"), now + 1L)
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(!record.optBoolean("manual_marker_identity_proven"), record.toString())

        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("target_id", "777")
            .put("tier", "3").put("enchantment", "E1"), now + 2L)
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "health", eventCode = 6, entityId = 777L, value = -856f,
            secondaryValue = null
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optBoolean("mob_dead"), record.toString())
        assertTrue(record.optString("mob_state") == "dead", record.toString())
        assertTrue(record.optString("manual_activity") == "Troll", record.toString())
        assertTrue(record.optBoolean("manual_marker_identity_proven"), record.toString())
        assertTrue(record.optInt("marker_enchant") == 1, record.toString())

        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 777L,
            typeId = 571, typeNumber = 29, tier = 3, size = 2, x = 12f, y = 34f
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("mob_state") == "dead_harvestable", record.toString())

        store.observe(now + 5L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 777L,
            size = null, removed = true
        )))
        record = org.json.JSONArray(store.snapshotJson()).getJSONObject(0)
        assertTrue(record.optString("mob_state") == "mined", record.toString())
        assertTrue(record.optBoolean("depleted"), record.toString())
    }

    @Test fun linksDeadOreElementalToUniqueNearbyLivingHarvestableAndMarksBothMined() {
        val store = AlbionLiveKnowledgeStore(context(), 30_000L)
        val now = 30_100L
        store.observe(now, listOf(AlbionEntityEventMapper.Observation(
            kind = "mob", eventCode = 123, entityId = 777L,
            typeId = 571, x = 12f, y = 34f
        )))
        store.observeMarker(org.json.JSONObject().put("activity", "Troll")
            .put("event", "target_found").put("tier", "3").put("enchantment", "E2"), now + 1L)
        store.observe(now + 2L, listOf(AlbionEntityEventMapper.Observation(
            kind = "target_request", eventCode = 23, candidateEntityIds = listOf(777L)
        )))
        store.observe(now + 3L, listOf(AlbionEntityEventMapper.Observation(
            kind = "health", eventCode = 6, entityId = 777L,
            value = -100f, secondaryValue = null
        )))
        store.observe(now + 4L, listOf(AlbionEntityEventMapper.Observation(
            kind = "harvestable", eventCode = 40, entityId = 888L,
            typeId = 571, typeNumber = 29, tier = 3, enchant = 2, size = 2, x = 13f, y = 34f
        )))
        var snapshot = org.json.JSONArray(store.snapshotJson())
        val node = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("entity_id") == "888" }
        val mob = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("entity_id") == "777" }
        assertTrue(node.optString("source_mob_entity_id") == "777", node.toString())
        assertTrue(node.optString("mob_state") == "dead_harvestable", node.toString())
        assertTrue(node.optString("manual_activity") == "Troll", node.toString())
        assertTrue(mob.optString("superseded_by_harvestable_entity_id") == "888", mob.toString())

        store.observe(now + 5L, listOf(AlbionEntityEventMapper.Observation(
            kind = "resource_state", eventCode = 46, entityId = 888L,
            size = null, removed = true
        )))
        snapshot = org.json.JSONArray(store.snapshotJson())
        val minedNode = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("entity_id") == "888" }
        val minedMob = (0 until snapshot.length()).map { snapshot.getJSONObject(it) }
            .first { it.optString("entity_id") == "777" }
        assertTrue(minedNode.optString("mob_state") == "mined", minedNode.toString())
        assertTrue(minedMob.optString("mob_state") == "mined", minedMob.toString())
    }

}
