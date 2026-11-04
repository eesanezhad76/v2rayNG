package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class AlbionKnowledgeBundleTest {
    @TempDir lateinit var tempDir: File

    private fun context() = object : ContextWrapper(null as Context?) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = tempDir
        override fun getExternalFilesDir(type: String?): File = tempDir
        override fun getPackageName(): String = "com.v2ray.ang.radar"
    }

    @Test
    fun exportsPersistentEntityAndProtocolEvidenceWithManifestAndNoVpnSecrets() {
        File(tempDir, "albion-live-knowledge.json").writeText(
            JSONArray().put(JSONObject().put("entity_id", "77").put("last_kind", "harvestable")).toString()
        )
        File(tempDir, "albion-protocol-evidence.json").writeText(
            JSONArray().put(JSONObject().put("event_code", 40)).toString()
        )
        File(tempDir, "albion-autonomous-patterns.json").writeText(
            JSONArray().put(JSONObject().put("signature", "sig-40").put("status", "unknown_candidate")).toString()
        )
        File(tempDir, "v2ray-profile.json").writeText("{\"password\":\"must-not-export\"}")
        File(tempDir, "albion-learned-patterns.json").writeText(
            JSONObject().put("schema_version", 1).put("patterns", JSONArray().put(
                JSONObject().put("signature", "Ore|harvestable|40")
            )).toString()
        )
        val captureDir = File(tempDir, "AlbionCaptures").apply { mkdirs() }
        File(captureDir, "latest.report.txt").writeText("Pattern Miner: new resource family candidate")
        val scanDir = File(tempDir, "albion-region-scans").apply { mkdirs() }
        File(scanDir, "region-123.json").writeText("""{"scan_id":"region-123","nodes":[]}""")

        val bytes = ByteArrayOutputStream()
        val summary = AlbionKnowledgeBundle.write(context(), bytes)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }

        val manifest = JSONObject(entries.getValue("manifest.json"))
        assertEquals(1, summary.entityRecords)
        assertEquals(1, summary.protocolEvents)
        assertTrue(entries.containsKey("live-knowledge.json"))
        assertTrue(entries.containsKey("protocol-evidence.json"))
        assertTrue(entries.containsKey("learned-patterns.json"))
        assertTrue(entries.containsKey("autonomous-patterns.json"))
        assertTrue(entries.containsKey("pattern-learning-report.txt"))
        assertEquals(5, manifest.getJSONObject("files").length())
        assertEquals(1, manifest.getJSONObject("files").getJSONArray("region_scans").length())
        assertTrue(entries.containsKey("region-scans/region-123.json"))
        assertTrue(summary.patternReportIncluded)
        assertTrue(summary.patternDatabaseIncluded)
        assertEquals(AlbionKnowledgeBundle.SCHEMA_VERSION, manifest.optInt("schema_version"))
        assertEquals(1, manifest.optInt("entity_records"))
        assertEquals(1, manifest.optInt("protocol_events"))
        assertEquals(1, manifest.optInt("autonomous_patterns"))
        val expectedAutonomousHash = manifest.getJSONObject("files")
            .getJSONObject("autonomous-patterns.json").getString("sha256")
        val actualAutonomousHash = MessageDigest.getInstance("SHA-256")
            .digest(entries.getValue("autonomous-patterns.json").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expectedAutonomousHash, actualAutonomousHash)
        assertFalse(entries.values.any { it.contains("must-not-export") })
    }

    @Test
    fun exportsEmptyValidBundleWhenNoKnowledgeHasBeenCollected() {
        val bytes = ByteArrayOutputStream()
        val summary = AlbionKnowledgeBundle.write(context(), bytes)
        assertEquals(0, summary.entityRecords)
        assertEquals(0, summary.protocolEvents)
        assertFalse(summary.patternDatabaseIncluded)
        assertTrue(bytes.size() > 0)
    }
}
