package com.v2ray.ang.ui.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlbionRadarSnapshotParserTest {
    @Test fun acceptsWellFormedCoordinateSnapshotsIncludingAnEmptySnapshot() {
        val parsed = AlbionRadarSnapshotParser.parse(
            """[{"entity_id":"-1","last_kind":"own_player_move","x":12.5,"y":-8.0}]"""
        )
        assertEquals(1, parsed?.entities?.size)
        assertEquals(0, parsed?.invalidCoordinateCount)
        assertEquals(0, AlbionRadarSnapshotParser.parse("[]")?.entities?.size)
    }

    @Test fun rejectsMalformedJsonAndMalformedEntityRecords() {
        assertNull(AlbionRadarSnapshotParser.parse("{broken"))
        assertNull(AlbionRadarSnapshotParser.parse("""[{"entity_id":"1","x":1,"y":2}]"""))
        assertNull(AlbionRadarSnapshotParser.parse("""[null]"""))
    }

    @Test fun prefersEvidenceBackedLabelsAndKeepsUnknownMobTypesUnclassified() {
        val mappedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"mobile_type_id":574,"resource_inference":{"resource_name":{"value":"Ore","status":"CAPTURE_CORROBORATED","confidence":0.98}}}"""
        )
        assertEquals("Ore*", AlbionRadarSnapshotParser.labelForEntity(mappedOre))
        val packetConfirmedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","resource_inference":{"resource_name":{"value":"Ore","status":"PACKET_CONFIRMED","confidence":1.0}}}"""
        )
        assertEquals("Ore", AlbionRadarSnapshotParser.labelForEntity(packetConfirmedOre))
        val provisionalOre = org.json.JSONObject(
            """{"last_kind":"harvestable","resource_inference":{"resource_name":{"value":"Ore","status":"PROVISIONAL_MAPPING","confidence":0.85}}}"""
        )
        assertEquals("Ore?", AlbionRadarSnapshotParser.labelForEntity(provisionalOre))
        val conflictedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"resource_inference":{"resource_name":{"value":null,"status":"CONFLICT","confidence":0.0}}}"""
        )
        assertEquals("Resource F29", AlbionRadarSnapshotParser.labelForEntity(conflictedOre))
        assertEquals("Mob ID482", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482}""")
        ))
        assertEquals("Golem", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482,"name":"Golem"}""")
        ))
    }

    @Test fun dropsInvalidOrOutOfRangeCoordinatesWithoutPassingThemToTheView() {
        val parsed = AlbionRadarSnapshotParser.parse(
            """[
              {"entity_id":"1","last_kind":"harvestable","x":10,"y":20},
              {"entity_id":"2","last_kind":"harvestable","x":10001,"y":20},
              {"entity_id":"3","last_kind":"harvestable","x":"NaN","y":20}
            ]"""
        )
        assertEquals(1, parsed?.entities?.size)
        assertEquals(2, parsed?.invalidCoordinateCount)
        assertEquals("1", parsed?.entities?.single()?.optString("entity_id"))
    }
}
