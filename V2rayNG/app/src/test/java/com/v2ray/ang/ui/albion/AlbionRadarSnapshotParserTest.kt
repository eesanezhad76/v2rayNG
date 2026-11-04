package com.v2ray.ang.ui.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AlbionRadarSnapshotParserTest {
    @Test fun defaultRadarFiltersDoNotHideKnownResourceFamilies() {
        assertEquals(
            setOf("category_ore", "category_wood", "category_stone", "category_fiber", "category_hide", "category_unknown_resource", "category_troll", "category_ore_elemental"),
            AlbionMiniRadarView.DEFAULT_FILTERS
        )
    }

    @Test fun acceptsWellFormedCoordinateSnapshotsIncludingAnEmptySnapshot() {
        val parsed = AlbionRadarSnapshotParser.parse(
            """[{"entity_id":"-1","last_kind":"own_player_move","x":12.5,"y":-8.0}]"""
        )
        assertEquals(1, parsed?.entities?.size)
        assertEquals(0, parsed?.invalidCoordinateCount)
        assertEquals(0, AlbionRadarSnapshotParser.parse("[]")?.entities?.size)
    }

    @Test fun rejectsMalformedJsonButSkipsMalformedRowsWithoutLosingValidNodes() {
        assertNull(AlbionRadarSnapshotParser.parse("{broken"))
        val parsed = AlbionRadarSnapshotParser.parse(
            """[{"entity_id":"1","last_kind":"harvestable","x":10,"y":20},{"entity_id":"2","x":1,"y":2},null]"""
        )
        assertEquals(1, parsed?.entities?.size)
        assertEquals(2, parsed?.invalidCoordinateCount)
    }

    @Test fun prefersEvidenceBackedLabelsAndKeepsUnknownMobTypesUnclassified() {
        val mappedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"mobile_type_id":574,"resource_inference":{"resource_name":{"value":"Ore","status":"CAPTURE_CORROBORATED","confidence":0.98}}}"""
        )
        assertEquals("Resource", AlbionRadarSnapshotParser.labelForEntity(mappedOre))
        val packetConfirmedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","resource_inference":{"resource_name":{"value":"Ore","status":"PACKET_CONFIRMED","confidence":1.0}}}"""
        )
        assertEquals("Resource", AlbionRadarSnapshotParser.labelForEntity(packetConfirmedOre))
        val provisionalOre = org.json.JSONObject(
            """{"last_kind":"harvestable","resource_inference":{"resource_name":{"value":"Ore","status":"PROVISIONAL_MAPPING","confidence":0.85}}}"""
        )
        assertEquals("Resource", AlbionRadarSnapshotParser.labelForEntity(provisionalOre))
        val conflictedOre = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"resource_inference":{"resource_name":{"value":null,"status":"CONFLICT","confidence":0.0}}}"""
        )
        assertEquals("Resource", AlbionRadarSnapshotParser.labelForEntity(conflictedOre))
        assertEquals("Mob ID482", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482}""")
        ))
        assertEquals("Golem", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","mobile_type_id":482,"name":"Golem"}""")
        ))
        assertEquals("TG?3", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","name":"Ore Elemental","tier":3,"marker_activity":"Troll"}""")
        ))
        assertEquals("TG3", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","tier":3,"manual_activity":"Troll","manual_marker_identity_proven":true}""")
        ))
        assertEquals("TG4", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","tier":4,"type_unique_name":"T4_MOB_TROLL_FOREST"}""")
        ))
        assertEquals("OE3", AlbionRadarSnapshotParser.labelForEntity(
            org.json.JSONObject("""{"last_kind":"mob","tier":3,"mobile_type_id":571,"type_unique_name":"T3_MOB_CRITTER_ORE_MOUNTAIN_RED","display_name":"Ore Elemental","is_harvestable_mob":true}""")
        ))
    }

    @Test fun displaysNodeTierAndProvisionalEnchantmentOnRadarLabel() {
        val node = org.json.JSONObject(
            """{"last_kind":"harvestable","family":29,"tier":5,"enchant":3,"resource_inference":{"resource_name":{"value":"Ore","status":"CAPTURE_CORROBORATED","confidence":0.98},"enchantment_name":{"value":"E3","status":"PROVISIONAL_MAPPING","confidence":0.85}}}"""
        )
        assertEquals("T5", AlbionRadarSnapshotParser.labelForEntity(node))
    }

    @Test fun displaysResearchCatalogResourceNamesWithTheirEvidenceStatus() {
        val provisional = org.json.JSONObject(
            """{"last_kind":"static_resource","name":"Ore","mapping_status":"REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL","tier":5,"enchant":1}"""
        )
        assertEquals("T5", AlbionRadarSnapshotParser.labelForEntity(provisional))

        val packetConfirmed = org.json.JSONObject(
            """{"last_kind":"harvestable","name":"Ore Elemental","mapping_status":"PACKET_CONFIRMED_LIVING_RESOURCE","tier":4,"enchant":0}"""
        )
        assertEquals("T4", AlbionRadarSnapshotParser.labelForEntity(packetConfirmed))
    }

    @Test fun labelsThePreviouslyUserConfirmedT5Enchant3CatalogNode() {
        val node = org.json.JSONObject(
            """{"last_kind":"static_resource","tier":5,"enchant":3,"user_confirmed_target":true,"x":-379,"y":-24}"""
        )
        assertEquals("T5", AlbionRadarSnapshotParser.labelForEntity(node))
    }

    @Test fun keepsWorldNorthAtTopAndEastAtRight() {
        val north = AlbionMiniRadarView.rotateOffset(0.0, 10.0)
        assertEquals(0.0, north.first, 1e-9)
        assertEquals(-10.0, north.second, 1e-9)
        val east = AlbionMiniRadarView.rotateOffset(10.0, 0.0)
        assertEquals(10.0, east.first, 1e-9)
        assertEquals(0.0, east.second, 1e-9)
        val south = AlbionMiniRadarView.rotateOffset(0.0, -10.0)
        assertEquals(0.0, south.first, 1e-9)
        assertEquals(10.0, south.second, 1e-9)
        val west = AlbionMiniRadarView.rotateOffset(-10.0, 0.0)
        assertEquals(-10.0, west.first, 1e-9)
        assertEquals(0.0, west.second, 1e-9)
    }

    @Test fun movementDirectionUsesLatestDistinctWorldCoordinatesInScreenSpace() {
        val north = AlbionMapProjection.movementDirection(
            org.json.JSONArray("""[{"x":0,"y":0},{"x":0,"y":5}]""")
        )
        assertEquals(0.0, north?.x ?: Double.NaN, 1e-9)
        assertEquals(-1.0, north?.y ?: Double.NaN, 1e-9)

        val east = AlbionMapProjection.movementDirection(
            org.json.JSONArray("""[{"x":0,"y":0},{"x":5,"y":0}]""")
        )
        assertEquals(1.0, east?.x ?: Double.NaN, 1e-9)
        assertEquals(0.0, east?.y ?: Double.NaN, 1e-9)

        val south = AlbionMapProjection.movementDirection(
            org.json.JSONArray("""[{"x":0,"y":5},{"x":0,"y":0}]""")
        )
        assertEquals(0.0, south?.x ?: Double.NaN, 1e-9)
        assertEquals(1.0, south?.y ?: Double.NaN, 1e-9)

        val west = AlbionMapProjection.movementDirection(
            org.json.JSONArray("""[{"x":5,"y":0},{"x":0,"y":0}]""")
        )
        assertEquals(-1.0, west?.x ?: Double.NaN, 1e-9)
        assertEquals(0.0, west?.y ?: Double.NaN, 1e-9)
    }

    @Test fun returnsNoMovementDirectionForStationaryOrInsufficientHistory() {
        assertNull(AlbionMapProjection.movementDirection(null))
        assertNull(AlbionMapProjection.movementDirection(org.json.JSONArray("""[{"x":1,"y":1}]""")))
        assertNull(AlbionMapProjection.movementDirection(
            org.json.JSONArray("""[{"x":1,"y":1},{"x":1,"y":1}]""")
        ))
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
