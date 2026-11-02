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

    data class Summary(
        val entityRecords: Int,
        val protocolEvents: Int,
        val bytesWritten: Long,
        val sha256: String
    )

    fun write(context: Context, output: OutputStream): Summary {
        val dir = context.applicationContext.filesDir
        val live = readJsonArray(dir.resolve(LIVE_FILE))
        val protocol = readJsonArray(dir.resolve(PROTOCOL_FILE))
        val liveBytes = live.toByteArray(Charsets.UTF_8)
        val protocolBytes = protocol.toByteArray(Charsets.UTF_8)
        val manifest = JSONObject()
            .put("format", "albion-radar-knowledge")
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at_ms", System.currentTimeMillis())
            .put("app_id", context.packageName)
            .put("entity_records", JSONArray(live).length())
            .put("protocol_events", JSONArray(protocol).length())
            .put("files", JSONObject()
                .put("live-knowledge.json", JSONObject()
                    .put("sha256", sha256(liveBytes))
                    .put("bytes", liveBytes.size))
                .put("protocol-evidence.json", JSONObject()
                    .put("sha256", sha256(protocolBytes))
                    .put("bytes", protocolBytes.size)))
            .put("privacy", "No VPN profiles, credentials, raw packet payloads, or PCAP files are included.")
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
        zip.finish()
        zip.flush()
        return Summary(
            entityRecords = JSONArray(live).length(),
            protocolEvents = JSONArray(protocol).length(),
            bytesWritten = liveBytes.size.toLong() + protocolBytes.size,
            sha256 = sha256(manifest.toString().toByteArray(Charsets.UTF_8))
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
