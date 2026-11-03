package com.v2ray.ang.albion

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlbionHistoricalKnowledgeIndexTest {
    private fun snapshot(): AlbionHistoricalKnowledgeIndex.Snapshot {
        val entities = """{
          "schema_version":1,
          "source_bundle_count":4,
          "entity_record_count":100,
          "mobile_types":{"571":{"record_count":5,"capture_session_count":2,"source_bundle_count":2,"names":{"Ore Elemental":5}}},
          "resource_families":{"29":{"record_count":6,"capture_session_count":3,"source_bundle_count":2,"tiers":[3,4,5],"enchantments":[0,1]}}
        }"""
        val protocols = """[{"event_code":40,"kind":"resource","parameter_schema":"0=Long","observations":9,"source_bundle_count":2}]"""
        val learned = """[{"signature":"pattern-a","activity":"Ore","family":29,"tier":5,"enchant":1,
          "overlay_enchant_label":"E1","samples":3,"complete_pairs":3,"heuristic_score":72,"source_bundles":["a","b"]}]"""
        val manifest = """{"schema_version":1,"entity_records":100,"protocol_events":200,
          "learned_patterns":1,"autonomous_signatures":4}"""
        return AlbionHistoricalKnowledgeIndex.parse(entities, protocols, learned, manifest)!!
    }

    @Test
    fun annotatesMobileEvidenceWithoutOverwritingPacketName() {
        val index = snapshot()
        val mob = JSONObject("""{"last_kind":"mob","mobile_type_id":571,"name":"Packet Name"}""")
        index.annotate(mob)
        assertEquals(5, mob.optInt("historical_bundle_mobile_records"))
        assertEquals(2, mob.optInt("historical_bundle_mobile_sessions"))
        assertEquals("Packet Name", mob.optString("name"))
        assertFalse(mob.has("classification"))
    }

    @Test
    fun attachesExactLearnedPatternAsReferenceOnlyAndPreservesCoordinates() {
        val index = snapshot()
        val resource = JSONObject("""{"last_kind":"harvestable","family":29,"tier":5,"enchant":1,"x":12.5,"y":-8.0}""")
        index.annotate(resource)
        assertEquals(6, resource.optInt("historical_bundle_family_records"))
        assertEquals(12.5, resource.optDouble("x"), 0.0001)
        assertEquals(-8.0, resource.optDouble("y"), 0.0001)
        assertEquals(1, resource.optInt("historical_learned_pattern_count"))
        assertTrue(resource.optString("historical_learned_pattern_semantics").contains("not used to infer"))
        assertFalse(resource.has("resource_inference"))
    }

    @Test
    fun reportsHistoricalCorpusWithoutAssigningProtocolSemantics() {
        val summary = snapshot().summaryText()
        assertTrue(summary.contains("100 entities"))
        assertTrue(summary.contains("200 protocol events"))
        assertTrue(summary.contains("1 learned patterns"))
        assertTrue(summary.contains("4 autonomous signatures"))
        assertTrue(summary.contains("Evidence only"))
    }
}
