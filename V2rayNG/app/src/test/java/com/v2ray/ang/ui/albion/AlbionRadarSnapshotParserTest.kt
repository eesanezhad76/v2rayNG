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
        assertEquals("Ore candidate F29", AlbionRadarSnapshotParser.labelForEntity(conflictedOre))
        assertEquals("Mob ID482", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482}""")
        ))
        assertEquals("Golem", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482,"name":"Golem"}""")
        ))
        assertEquals("T! T3 Ore Elemental", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","name":"Ore Elemental","tier":3,"marker_activity":"Troll"}""")
        ))
    }

    @Test fun displaysNodeTierAndProvisionalEnchantmentOnRadarLabel() {
        val node = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"tier":5,"enchant":3,"resource_inference":{"resource_name":{"value":"Ore","status":"CAPTURE_CORROBORATED","confidence":0.98},"enchantment_name":{"value":"E3","status":"PROVISIONAL_MAPPING","confidence":0.85}}}"""
        )
        assertEquals("Ore* T5 E3?", AlbionRadarSnapshotParser.labelForEntity(node))
    }

    @Test fun displaysResearchCatalogResourceNamesWithTheirEvidenceStatus() {
        val provisional = org.json.JSONObject(
            """{"last_kind":"static_resource","name":"Ore","mapping_status":"REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL","tier":5,"enchant":1}"""
        )
        assertEquals("Ore? T5 E1?", AlbionRadarSnapshotParser.labelForEntity(provisional))

        val packetConfirmed = org.json.JSONObject(
            """{"last_kind":"harvestable","name":"Ore Elemental","mapping_status":"PACKET_CONFIRMED_LIVING_RESOURCE","tier":4,"enchant":0}"""
        )
        assertEquals("Ore Elemental T4", AlbionRadarSnapshotParser.labelForEntity(packetConfirmed))
    }

    @Test fun labelsThePreviouslyUserConfirmedT5Enchant3CatalogNode() {
        val node = org.json.JSONObject(
            """{"last_kind":"static_resource","tier":5,"enchant":3,"user_confirmed_target":true,"x":-379,"y":-24}"""
        )
        assertEquals("T5.3 ✓", AlbionRadarSnapshotParser.labelForEntity(node))
    }

    @Test fun rotatesRadarOffsetsByExactlyFortyFiveDegrees() {
        val east = AlbionMiniRadarView.rotateOffset(10.0, 0.0)
        // The new calibration rotates the prior result 90 degrees clockwise.
        assertEquals(kotlin.math.sqrt(50.0), east.first, 1e-9)
        assertEquals(kotlin.math.sqrt(50.0), east.second, 1e-9)
        val north = AlbionMiniRadarView.rotateOffset(0.0, 10.0)
        assertEquals(kotlin.math.sqrt(50.0), north.first, 1e-9)
        assertEquals(-kotlin.math.sqrt(50.0), north.second, 1e-9)
    }

    @Test fun assignsDistinctColorsToNormalAndEnchantmentsOneToThree() {
        assertEquals(0xFFFFC45C.toInt(), AlbionMiniRadarView.colorForEnchantment(0))
        assertEquals(0xFF63D471.toInt(), AlbionMiniRadarView.colorForEnchantment(1))
        assertEquals(0xFF55B8FF.toInt(), AlbionMiniRadarView.colorForEnchantment(2))
        assertEquals(0xFFB58CFF.toInt(), AlbionMiniRadarView.colorForEnchantment(3))
        assertEquals(0xFF9AA6AF.toInt(), AlbionMiniRadarView.colorForEnchantment(-1))
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
