package com.v2ray.ang.ui.albion

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionSpatialIntelligenceTest {
    private fun resource(
        id: String,
        kind: String,
        x: Double,
        y: Double,
        typeNumber: Int? = null,
        typeId: Int? = null,
        tier: Int? = null,
        enchant: Int? = null,
        lastSeen: Long = 1L
    ): JSONObject = JSONObject().apply {
        put("entity_id", id)
        put("last_kind", kind)
        put("x", x)
        put("y", y)
        put("last_seen_ms", lastSeen)
        typeNumber?.let { put("family", it) }
        typeId?.let { put("mobile_type_id", it) }
        tier?.let { put("tier", it) }
        enchant?.let { put("enchant", it) }
    }

    @Test
    fun knownActiveRegionRejectsMissingOrDifferentRegionTargets() {
        assertTrue(AlbionSpatialIntelligence.isInActiveRegion("MAP_A", "MAP_A"))
        assertFalse(AlbionSpatialIntelligence.isInActiveRegion("MAP_A", "MAP_B"))
        assertFalse(AlbionSpatialIntelligence.isInActiveRegion("MAP_A", null))
        assertFalse(AlbionSpatialIntelligence.isInActiveRegion("MAP_A", ""))
        assertFalse(AlbionSpatialIntelligence.isInActiveRegion(null, "MAP_B"))
        assertTrue(AlbionSpatialIntelligence.isInActiveRegion(null, null))
        assertTrue(AlbionSpatialIntelligence.isInActiveRegion("", ""))
    }

    @Test
    fun oreElementalTypeIdWinsOverConflictingTrollDisplayName() {
        val elemental = resource("ore-elemental", "mob", 12.0, 18.0, typeId = 571)
            .put("name", "Troll")
            .put("type_unique_name", "T3_MOB_CRITTER_ORE_MOUNTAIN_RED")
        assertTrue(AlbionSpatialIntelligence.isOreElemental(elemental))
        assertFalse(AlbionSpatialIntelligence.isTrollMob(elemental))
    }

    @Test
    fun allObservedOreElementalTypeIdsAreRecognizedWithoutNameMatch() {
        listOf(571, 573, 574).forEach { typeId ->
            val elemental = resource("ore-$typeId", "mob", 1.0, 2.0, typeId = typeId)
                .put("name", "Unknown mob")
            assertTrue(AlbionSpatialIntelligence.isOreElemental(elemental))
            assertFalse(AlbionSpatialIntelligence.isTrollMob(elemental))
        }
    }

    @Test
    fun researchConfirmedOreElementalMobCanBeGatheringTarget() {
        val elemental = resource("ore-elemental", "mob", 10.0, 12.0, typeId = 574, tier = 5)
            .put("research_catalog_harvestable", true)
        assertTrue(AlbionSpatialIntelligence.isNavigableResource(elemental))
        elemental.put("depleted", true)
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(elemental))
    }

    @Test
    fun externalDictionaryHarvestableMobCanBeNavigatedWithoutResearchRow() {
        val mob = resource("external-harvestable", "mob", 4.0, 5.0, typeId = 9001, tier = 6)
            .put("is_harvestable_mob", true)
        assertTrue(AlbionSpatialIntelligence.isNavigableResource(mob))
    }

    @Test
    fun researchConfirmedNonHarvestableOverridesExternalDictionaryFlag() {
        val rabbit = resource("rabbit", "mob", 4.0, 5.0, typeId = 482, tier = 1)
            .put("is_harvestable_mob", true)
            .put("research_catalog_harvestable", false)
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(rabbit))
    }

    @Test
    fun trollLabelDoesNotTurnOreElementalIntoTroll() {
        val elemental = resource("ore", "mob", 1.0, 2.0, typeId = 574)
            .put("marker_activity", "Troll")
            .put("marker_match_confidence", "CONFIRMED")
        assertFalse(AlbionSpatialIntelligence.isTrollMob(elemental))
    }

    @Test
    fun typedResourceSupersedesEvent39CandidateAtSameCoordinate() {
        val candidate = resource("event39", "static_resource", 12.0, 18.0)
        val typed = resource("event40", "harvestable", 12.0, 18.0, 29, 574, 5, 1, 10L)
        val result = AlbionSpatialIntelligence.deduplicate(listOf(candidate, typed))
        assertEquals(1, result.size)
        assertEquals("event40", result.single().optString("entity_id"))
    }

    @Test
    fun repeatedUntypedCandidatesAtSameCoordinateCollapseToNewestObservation() {
        val older = resource("candidate-a", "static_resource", 12.0, 18.0, lastSeen = 10L)
        val newer = resource("candidate-b", "static_resource", 12.1, 18.1, lastSeen = 20L)
        val result = AlbionSpatialIntelligence.deduplicate(listOf(older, newer))
        assertEquals(1, result.size)
        assertEquals("candidate-b", result.single().optString("entity_id"))
    }

    @Test
    fun differentTypedResourcesAtSameCoordinateAreNotSilentlyMerged() {
        val ore = resource("ore", "harvestable", 12.0, 18.0, 29, 574, 5, 1)
        val stone = resource("stone", "harvestable", 12.0, 18.0, 7, 701, 5, 0)
        val result = AlbionSpatialIntelligence.deduplicate(listOf(ore, stone))
        assertEquals(2, result.size)
    }

    @Test
    fun distinctTypedRuntimeIdsAtSameCoordinateArePreserved() {
        val first = resource("node-a", "harvestable", 12.0, 18.0, 29, 574, 5, 1)
        val second = resource("node-b", "harvestable", 12.0, 18.0, 29, 574, 5, 1)
        val result = AlbionSpatialIntelligence.deduplicate(listOf(first, second))
        assertEquals(2, result.size)
    }

    @Test
    fun event40PacketTypeNumberAndTierAreEnoughToNavigateWhenMobileTypeIdIsUnknown() {
        val familyTyped = resource("family32", "harvestable", -375.0, 103.0, typeNumber = 32, tier = 4)
        val untyped = resource("unknown", "harvestable", -375.0, 103.0, tier = 4)
        assertTrue(AlbionSpatialIntelligence.isNavigableResource(familyTyped))
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(untyped))
    }

    @Test
    fun untypedEvent39CandidateCannotBecomeGatheringNavigationTarget() {
        val candidate = resource("candidate", "static_resource", 10.0, 10.0)
        val typed = resource("typed", "harvestable", 11.0, 10.0, 29, 574, 5, 1)
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(candidate))
        assertTrue(AlbionSpatialIntelligence.isNavigableResource(typed))
    }

    @Test
    fun rawTypeNumberKeyWithoutStoreNormalizationIsNotRuntimeSnapshotSchema() {
        // The raw event field is type_number=32 / UNRESOLVED. The live Store normalizes
        // Event 40 typeNumber into the snapshot key family; a raw type_number key alone
        // is not the runtime snapshot schema and must not bypass that normalization.
        val unresolved = JSONObject()
            .put("entity_id", "877646")
            .put("last_kind", "harvestable")
            .put("type_number", 32)
            .put("tier", 4)
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(unresolved))
    }

    @Test
    fun conflictedOrDepletedTypedResourcesCannotBecomeNavigationTargets() {
        val conflicted = resource("conflict", "harvestable", 1.0, 2.0, 29, 574, 5, 1)
            .put("evidence_conflicts", org.json.JSONArray().put("tier"))
        val depleted = resource("depleted", "harvestable", 1.0, 2.0, 29, 574, 5, 1)
            .put("depleted", true)
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(conflicted))
        assertFalse(AlbionSpatialIntelligence.isNavigableResource(depleted))
    }

    @Test
    fun candidateLayerIsCappedToNearestForty() {
        val candidates = (0 until 60).map { index ->
            resource("c$index", "static_resource", index.toDouble() + 1.0, 0.0)
        }
        val result = AlbionSpatialIntelligence.limitUnclassifiedCandidates(candidates, 0.0, 0.0, 40)
        assertEquals(40, result.size)
        assertEquals("c0", result.first().optString("entity_id"))
        assertTrue(result.none { it.optString("entity_id") == "c59" })
    }
}
