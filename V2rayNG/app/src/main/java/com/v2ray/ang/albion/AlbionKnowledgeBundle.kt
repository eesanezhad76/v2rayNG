package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Portable, privacy-minimal learned-knowledge export.
 *
 * It contains normalized local observations and protocol evidence, not VPN profiles,
 * credentials, packet payloads, or the user's full PCAP. Raw evidence remains in the
 * separate capture export so the user can choose what to share.
 */
object AlbionKnowledgeBundle {
    const val SCHEMA_VERSION = 1
    private const val LIVE_FILE = "albion-live-knowledge.json"
    private const val PROTOCOL_FILE = "albion-protocol-evidence.json"
    private const val AUTONOMOUS_FILE = "albion-autonomous-patterns.json"
    private const val MAX_PATTERN_REPORT_BYTES = 4L * 1024L * 1024L

    data class Summary(
        val entityRecords: Int,
        val protocolEvents: Int,
        val bytesWritten: Long,
        val sha256: String,
        val patternReportIncluded: Boolean,
        val patternDatabaseIncluded: Boolean
    )

    fun write(context: Context, output: OutputStream): Summary {
        val dir = context.applicationContext.filesDir
        val live = readJsonArray(dir.resolve(LIVE_FILE))
        val protocol = readJsonArray(dir.resolve(PROTOCOL_FILE))
        val liveBytes = live.toByteArray(Charsets.UTF_8)
        val protocolBytes = protocol.toByteArray(Charsets.UTF_8)
        val autonomousFile = dir.resolve(AUTONOMOUS_FILE)
        val autonomousText = if (autonomousFile.isFile) {
            readJsonArray(autonomousFile)
        } else if (context.assets.list("")?.contains("albion-historical-autonomous-patterns.json") == true) {
            context.assets.open("albion-historical-autonomous-patterns.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }.also { JSONArray(it) }
        } else {
            "[]"
        }
        val autonomousBytes = autonomousText.toByteArray(Charsets.UTF_8)
        val patternsFile = dir.resolve("albion-learned-patterns.json")
        val patternsText = if (patternsFile.isFile) patternsFile.readText(Charsets.UTF_8) else
            JSONObject().put("schema_version", 1).put("patterns", JSONArray()).toString()
        JSONObject(patternsText)
        val patternsBytes = patternsText.toByteArray(Charsets.UTF_8)
        val captureRoot = context.getExternalFilesDir(null) ?: dir
        val captureDir = captureRoot.resolve("AlbionCaptures")
        val latestPatternReport = captureDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".report.txt") && it.length() <= MAX_PATTERN_REPORT_BYTES }
            ?.maxByOrNull { it.lastModified() }
        val patternReportBytes = latestPatternReport?.readBytes()
        val manifest = JSONObject()
            .put("format", "albion-radar-knowledge")
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at_ms", System.currentTimeMillis())
            .put("app_id", context.packageName)
            .put("entity_records", JSONArray(live).length())
            .put("protocol_events", JSONArray(protocol).length())
            .put("autonomous_patterns", JSONArray(autonomousText).length())
            .put("files", JSONObject()
                .put("live-knowledge.json", JSONObject()
                    .put("sha256", sha256(liveBytes))
                    .put("bytes", liveBytes.size))
                .put("protocol-evidence.json", JSONObject()
                    .put("sha256", sha256(protocolBytes))
                    .put("bytes", protocolBytes.size))
                .put("autonomous-patterns.json", JSONObject()
                    .put("sha256", sha256(autonomousBytes))
                    .put("bytes", autonomousBytes.size))
                .put("learned-patterns.json", JSONObject()
                    .put("sha256", sha256(patternsBytes))
                    .put("bytes", patternsBytes.size))
                .put("pattern-learning-report.txt", if (patternReportBytes != null)
                    JSONObject().put("sha256", sha256(patternReportBytes)).put("bytes", patternReportBytes.size)
                    else JSONObject.NULL))
            .put("privacy", "No VPN profiles, credentials, raw packet payloads, or PCAP files are included. The text report contains decoded summaries and pattern-miner findings only.")
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("live-knowledge.json"))
        zip.write(liveBytes)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("protocol-evidence.json"))
        zip.write(protocolBytes)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("autonomous-patterns.json"))
        zip.write(autonomousBytes)
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("learned-patterns.json"))
        zip.write(patternsBytes)
        zip.closeEntry()
        if (patternReportBytes != null) {
            zip.putNextEntry(ZipEntry("pattern-learning-report.txt"))
            zip.write(patternReportBytes)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        return Summary(
            entityRecords = JSONArray(live).length(),
            protocolEvents = JSONArray(protocol).length(),
            bytesWritten = liveBytes.size.toLong() + protocolBytes.size + autonomousBytes.size + patternsBytes.size + (patternReportBytes?.size ?: 0),
            sha256 = sha256(manifest.toString().toByteArray(Charsets.UTF_8)),
            patternReportIncluded = patternReportBytes != null,
            patternDatabaseIncluded = patternsFile.isFile
        )
    }

    private fun readJsonArray(file: java.io.File): String {
        if (!file.isFile) return "[]"
        val text = file.readText(Charsets.UTF_8)
        // Fail closed on corrupt local data: do not silently export a malformed bundle.
        JSONArray(text)
        return text
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
