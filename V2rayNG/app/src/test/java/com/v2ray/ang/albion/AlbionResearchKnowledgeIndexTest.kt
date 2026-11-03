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
          "totals": {
            "resource_observations": 331,
            "resource_families": 2,
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
              "categories": {"harmless": 469},
              "harvestable_values": {"False": 469}
            },
            "571": {
              "observation_count": 167,
              "captures": ["capture-a", "capture-b"],
              "tiers": [3],
              "names": {"Ore Elemental": 167},
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
    fun annotatesResourceWithCrossCaptureEvidenceWithoutChangingCoordinates() {
        val snapshot = AlbionResearchKnowledgeIndex.parse(fixture())!!
        val entity = JSONObject("""{"last_kind":"static_resource","family":29,"x":-10.5,"y":22.0}""")
        snapshot.annotate(entity)
        assertEquals(100, entity.optInt("research_catalog_family_observations"))
        assertEquals(3, entity.optInt("research_catalog_family_capture_count"))
        assertEquals(-10.5, entity.optDouble("x"), 0.0001)
        assertEquals(22.0, entity.optDouble("y"), 0.0001)
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
