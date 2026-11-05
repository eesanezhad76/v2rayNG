package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionPipelineSelfTest {
    @TempDir lateinit var tempDir: File

    private fun context(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
    }

    private fun event(code: Int, parameters: Map<Int, Any?>) =
        AlbionProtocol18Decoder.Message(
            kind = "event", dispatchCode = code, realCode = code, parameters = parameters
        )

    @Test fun harvestLifecycleAndInventoryChangesAreRetainedAsRawEvidence() {
        val evidence = AlbionProtocolEvidenceStore(context())
        val now = System.currentTimeMillis() + 2_000L
        evidence.observe(now, 59, mapOf(0 to 777L))
        evidence.observe(now + 500L, 60, mapOf(0 to 777L))
        evidence.observe(now + 1_000L, 61, mapOf(0 to 777L))
        evidence.observe(now + 1_100L, 26, mapOf(0 to 123456, 1 to 1))
        evidence.flush()
        val summary = evidence.summaryText()
        assertTrue(summary.contains("HarvestStart events retained across captures: 1"))
        assertTrue(summary.contains("HarvestCancel events retained across captures: 1"))
        assertTrue(summary.contains("HarvestFinished events retained across captures: 1"))
        assertTrue(summary.contains("Inventory events retained across captures: 1"))
        assertTrue(summary.contains("No item ID, stack delta, or Ore identity is asserted"))
    }

    @Test fun corruptProtocolEvidenceCacheIsIgnoredAndRebuiltWithoutCrashing() {
        File(tempDir, "albion-protocol-evidence.json").writeText("{corrupt-json")
        val evidence = AlbionProtocolEvidenceStore(context())
        evidence.observe(System.currentTimeMillis() + 2_000L, 61, mapOf(0 to 77L))
        evidence.flush()
        val persisted = JSONArray(File(tempDir, "albion-protocol-evidence.json").readText())
        assertEquals(1, persisted.length())
        assertEquals(61, persisted.getJSONObject(0).optInt("event_code"))
    }

    @Test fun playerMovementResourceSpawnDepletionReachOverlaySnapshotWithExactEntityId() {
        AlbionEntityEventMapper.reset()
        val store = AlbionLiveKnowledgeStore(context())
        val now = System.currentTimeMillis() + 1_000L
        val playerSpawn = AlbionEntityEventMapper.map(event(29, mapOf(0 to 77L, 1 to "TestPlayer")))
        val playerMove = AlbionEntityEventMapper.map(event(3, mapOf(0 to 77L, 4 to 21f, 5 to 31f)))
        val ownMove = AlbionEntityEventMapper.map(
            AlbionProtocol18Decoder.Message(
                kind = "request",
                dispatchCode = 0,
                realCode = 22,
                operationCode = 22,
                parameters = mapOf(
                    1 to listOf(20f, 30f), 2 to 90f, 3 to listOf(21f, 31f), 4 to 5f
                )
            )
        )
        val resourceSpawn = AlbionEntityEventMapper.map(event(40, mapOf(
            0 to 777L, 5 to 29, 6 to 574, 7 to 5, 8 to floatArrayOf(40f, 50f), 11 to 1
        )))
        store.observe(now, playerSpawn + playerMove + ownMove + resourceSpawn)

        val before = JSONArray(store.snapshotJson())
        fun records(array: JSONArray) = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        val beforeRows = records(before)
        val resource = beforeRows.single { it.optString("entity_id") == "777" }
        assertEquals("harvestable", resource.optString("last_kind"))
        assertEquals(574, resource.optInt("mobile_type_id"))
        assertEquals(29, resource.optInt("family"))
        assertEquals(5, resource.optInt("tier"))
        assertEquals(1, resource.optInt("enchant"))
        assertTrue(beforeRows.any { it.optString("entity_id") == "-1" && it.optString("last_kind") == "own_player_move" })
        assertTrue(beforeRows.any { it.optString("entity_id") == "77" && it.optString("last_kind") == "player_move" })

        // Missing remaining amount is unknown, not evidence that the node is depleted.
        val missingAmount = AlbionEntityEventMapper.map(event(46, mapOf(0 to 777L)))
        store.observe(now + 5L, missingAmount)
        val unknownRows = records(JSONArray(store.snapshotJson()))
        val unknownResource = unknownRows.single { it.optString("entity_id") == "777" }
        assertEquals("resource_state", unknownResource.optString("last_kind"))
        assertTrue(!unknownResource.optBoolean("depleted"))

        // An explicit zero remaining amount is sufficient to confirm depletion.
        val explicitZero = AlbionEntityEventMapper.map(event(46, mapOf(0 to 777L, 1 to 0)))
        store.observe(now + 10L, explicitZero)
        val afterRows = records(JSONArray(store.snapshotJson()))
        val afterResource = afterRows.single { it.optString("entity_id") == "777" }
        assertEquals("resource_state", afterResource.optString("last_kind"))
        assertTrue(afterResource.optBoolean("depleted"))
        assertEquals("corroborated_same_id", afterResource.optString("evidence_grade"))
    }
}
