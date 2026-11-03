package com.v2ray.ang.albion

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionResearchKnowledgeIndexTest {
    private fun fixture(): String = """
        {
          "schema_version": 1,
          "generated_from": {
            "source_branch": "albion-pattern-mining",
            "resource_csv_blob_sha": "resource-sha",
            "mobile_csv_blob_sha": "mobile-sha"
          },
          "totals": {
            "resource_observations": 331,
            "resource_families": 3,
            "mobile_entity_observations": 2330,
            "mobile_type_ids": 2
          },
          "resource_families": {
            "20": {
              "observation_count": 7,
              "captures": ["capture-a", "capture-b"],
              "resource_types": {"Hide": 7},
              "mapping_statuses": {"REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL": 7},
              "tiers": [1]
            },
            "28": {
              "observation_count": 2,
              "captures": ["capture-a"],
              "resource_types": {"unknown": 2},
              "mapping_statuses": {"UNRESOLVED": 2},
              "tiers": [5]
            },
            "29": {
              "observation_count": 100,
              "captures": ["capture-a", "capture-b", "capture-c"],
              "resource_types": {"Ore": 100},
              "mapping_statuses": {"PACKET_CONFIRMED_LIVING_RESOURCE": 100},
              "tiers": [3, 4, 5]
            }
          },
          "mobile_types": {
            "482": {
              "observation_count": 469,
              "captures": ["capture-a", "capture-b"],
              "tiers": [1],
              "names": {"Mountain Rabbit": 469},
              "unique_names": {"T1_MOB_HIDE_MOUNTAIN_RABBIT": 469},
              "categories": {"harmless": 469},
              "harvestable_values": {"False": 469}
            },
            "571": {
              "observation_count": 167,
              "captures": ["capture-a", "capture-b"],
              "tiers": [3],
              "names": {"Ore Elemental": 167},
              "unique_names": {"T3_MOB_CRITTER_ORE_MOUNTAIN_RED": 167},
              "categories": {"critter": 167},
              "harvestable_values": {"True": 167}
            }
          }
        }
    """.trimIndent()

    @Test
    fun parsesCompleteResearchIndexAndReportsSourceCounts() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())
        assertEquals(331, snapshot?.resourceObservationCount)
        assertEquals(2330, snapshot?.mobileEntityObservationCount)
        assertEquals(2, snapshot?.resourceFamilyCount)
        assertEquals(2, snapshot?.mobileTypeCount)
        assertEquals("KNOW 331R/2330M", snapshot?.statusLabel())
    }

    @Test
    fun exposesExactMobileDictionaryCoverageAndSourceProvenance() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val dictionary = JSONObject("""{"482":{"display_name":"Mountain Rabbit"}}""")
        val coverage = snapshot.mobileDictionaryCoverage(dictionary)
        assertEquals(1, coverage.matched)
        assertEquals(2, coverage.expected)
        assertEquals("1/2", coverage.label())
        assertEquals(setOf("482", "571"), snapshot.mobileTypeIds())

        val rabbit = JSONObject("""{"last_kind":"mob","mobile_type_id":482}""")
        snapshot.annotate(rabbit)
        assertEquals(1, rabbit.optInt("research_catalog_schema_version"))
        assertEquals("albion-pattern-mining", rabbit.optString("research_catalog_source_branch"))
        assertEquals("mobile-sha", rabbit.optString("research_catalog_source_blob_sha"))
    }

    @Test
    fun buildsRuntimeDictionaryFallbackForEveryObservedTypeId() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val dictionary = snapshot.mobileTypeDictionary()
        assertEquals("Mountain Rabbit", dictionary.optJSONObject("482")?.optString("display_name"))
        assertEquals("T1_MOB_HIDE_MOUNTAIN_RABBIT", dictionary.optJSONObject("482")?.optString("unique_name"))
        assertFalse(dictionary.optJSONObject("482")?.optBoolean("is_harvestable", true) ?: true)
        assertEquals("Ore Elemental", dictionary.optJSONObject("571")?.optString("display_name"))
        assertTrue(dictionary.optJSONObject("571")?.optBoolean("is_harvestable") ?: false)
        assertEquals(2, dictionary.length())
    }

    @Test
    fun annotatesResourceWithCrossCaptureEvidenceWithoutChangingCoordinates() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val entity = JSONObject("""{"last_kind":"static_resource","family":29,"x":-10.5,"y":22.0}""")
        snapshot.annotate(entity)
        assertEquals(100, entity.optInt("research_catalog_family_observations"))
        assertEquals(3, entity.optInt("research_catalog_family_capture_count"))
        assertEquals("Ore", entity.optString("research_catalog_resource_type"))
        assertEquals("PACKET_CONFIRMED_LIVING_RESOURCE", entity.optString("research_catalog_resource_mapping_status"))
        assertEquals(-10.5, entity.optDouble("x"), 0.0001)
        assertEquals(22.0, entity.optDouble("y"), 0.0001)
    }

    @Test
    fun preservesUnresolvedFamilyAsAnExplicitUnclassifiedResource() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val unresolved = JSONObject("""{"last_kind":"static_resource","family":28,"x":1.0,"y":2.0}""")
        snapshot.annotate(unresolved)
        assertEquals("unknown", unresolved.optString("research_catalog_resource_type"))
        assertEquals("UNRESOLVED", unresolved.optString("research_catalog_resource_mapping_status"))
        assertNull(snapshot.resourceTypeEvidence(28))
    }

    @Test
    fun keepsHideFamilySeparateFromNonHarvestableMountainRabbitType() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val resource = JSONObject("""{"last_kind":"static_resource","family":20,"mobile_type_id":482}""")
        snapshot.annotate(resource)
        assertEquals(7, resource.optInt("research_catalog_family_observations"))
        assertEquals("Hide", resource.optJSONArray("research_catalog_family_types")?.optString(0))
        assertFalse(resource.has("research_catalog_harvestable"))
    }

    @Test
    fun annotatesMountainRabbitAsNonHarvestableAndDoesNotPromoteItsName() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val rabbit = JSONObject("""{"last_kind":"mob","mobile_type_id":482}""")
        snapshot.annotate(rabbit)
        assertEquals(469, rabbit.optInt("research_catalog_mobile_observations"))
        assertFalse(rabbit.optBoolean("research_catalog_harvestable", true))
        assertFalse(rabbit.has("name"))
    }

    @Test
    fun annotatesOreElementalAsHarvestableWithoutOverwritingPacketFacts() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val ore = JSONObject("""{"last_kind":"mob","mobile_type_id":571,"name":"Packet Name"}""")
        snapshot.annotate(ore)
        assertTrue(ore.optBoolean("research_catalog_harvestable"))
        assertEquals("Packet Name", ore.optString("name"))
        assertEquals(167, ore.optInt("research_catalog_mobile_observations"))
    }

    @Test
    fun researchIndexEvidenceFeedsResolverWithoutPromotingProvisionalMappings() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val hide = ResourcePatternLearner.infer(
            ResourcePatternLearner.Observation(family = 20, tier = 1, enchantRaw = 0, mobileTypeId = 482),
            researchIndex = snapshot
        )
        assertEquals("Hide", hide.resourceName.value)
        assertEquals(ResourcePatternLearner.Status.PROVISIONAL_MAPPING, hide.resourceName.status)
        assertEquals(0.90, hide.resourceName.confidence, 0.0001)
        assertTrue(hide.resourceName.reason.contains("7 observations across 2 captures"))
        assertNull(AlbionResearchKnowledgeIndex.parse(fixture())!!.resourceTypeEvidence(28))
    }

    @Test
    fun rejectsWrongSchemaAndIncompleteCounts() {
        assertNull(AlbionResearchKnowledgeIndex.parse("""{"schema_version":2}"""))
        val incomplete = fixture().replace("\"mobile_type_ids\": 2", "\"mobile_type_ids\": 1")
        assertNull(AlbionResearchKnowledgeIndex.parse(incomplete))
    }
}
