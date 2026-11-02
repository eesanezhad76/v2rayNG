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
