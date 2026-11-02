package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Android adapter that replays decoded observations into isolated copies of the production stores. */
object AlbionCaptureReplayPipeline {
    data class Result(
        val replay: AlbionCaptureReplayEngine.ReplayResult,
        val knowledgeSnapshotJson: String,
        val protocolEvidenceSummary: String,
        val entityCount: Int
    )

    @Synchronized
    fun replay(file: File, context: Context): Result {
        require(file.isFile) { "Capture file does not exist: $file" }
        val replayDir = File(context.cacheDir, "albion-replay-${System.nanoTime()}")
        check(replayDir.mkdirs()) { "Cannot create isolated replay directory" }
        val isolatedContext = object : ContextWrapper(context.applicationContext) {
            override fun getFilesDir(): File = replayDir
        }
        try {
            val knowledge = AlbionLiveKnowledgeStore(isolatedContext, Long.MIN_VALUE)
            val evidence = AlbionProtocolEvidenceStore(isolatedContext, Long.MIN_VALUE)
            val replay = AlbionCaptureReplayEngine.replay(file) { timestamp, message, observations ->
                if (message.kind == "event" && message.realCode != null) {
                    evidence.observe(timestamp, message.realCode, message.parameters)
                }
                knowledge.observe(timestamp, observations)
            }
            val snapshot = knowledge.snapshotJson()
            return Result(replay, snapshot, evidence.summaryText(), JSONArray(snapshot).length())
        } finally {
            replayDir.deleteRecursively()
        }
    }

    fun writeJson(result: Result, destination: File) {
        destination.parentFile?.mkdirs()
        val root = JSONObject()
            .put("schema_version", 1)
            .put("replay_scope", "pcap_to_photon_to_protocol18_to_event_mapper_to_isolated_knowledge_and_evidence_stores_to_overlay_snapshot")
            .put("isolated_knowledge_store_replayed", true)
            .put("daemon_live_store_mutated", false)
            .put("overlay_delivery_verified", false)
            .put("source_name", result.replay.sourceName)
            .put("source_sha256", result.replay.sourceSha256)
            .put("fingerprint", result.replay.fingerprint)
            .put("entity_count", result.entityCount)
            .put("knowledge_snapshot", JSONArray(result.knowledgeSnapshotJson))
            .put("protocol_evidence_summary", result.protocolEvidenceSummary)
        destination.writeText(root.toString(2), Charsets.UTF_8)
    }
}
