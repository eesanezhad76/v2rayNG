package com.v2ray.ang.albion

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import com.v2ray.ang.ui.albion.AlbionRadarSnapshotParser
import java.io.File

/** Android adapter that replays decoded observations into isolated copies of the production stores. */
object AlbionCaptureReplayPipeline {
    data class Result(
        val replay: AlbionCaptureReplayEngine.ReplayResult,
        val knowledgeSnapshotJson: String,
        val protocolEvidenceSummary: String,
        val entityCount: Int,
        val overlayAcceptedEntityCount: Int,
        val invalidCoordinateCount: Int,
        val overlaySnapshotValid: Boolean
    )

    @Synchronized
    fun replay(file: File, context: Context, nativeTapDrops: Long? = null): Result {
        require(file.isFile) { "Capture file does not exist: $file" }
        val replayDir = File(context.cacheDir, "albion-replay-${System.nanoTime()}")
        check(replayDir.mkdirs()) { "Cannot create isolated replay directory" }
        val isolatedContext = object : ContextWrapper(context.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = replayDir
        }
        try {
            val knowledge = AlbionLiveKnowledgeStore(isolatedContext, Long.MIN_VALUE)
            val evidence = AlbionProtocolEvidenceStore(isolatedContext, Long.MIN_VALUE)
            val replay = AlbionCaptureReplayEngine.replay(
                file,
                onDecodedMessage = { timestamp, message, observations ->
                    if (message.kind == "event" && message.realCode != null) {
                        evidence.observe(timestamp, message.realCode, message.parameters)
                    }
                    knowledge.observe(timestamp, observations)
                },
                nativeTapDrops = nativeTapDrops
            )
            val snapshot = knowledge.snapshotJson()
            val parsedSnapshot = AlbionRadarSnapshotParser.parse(snapshot)
            return Result(
                replay = replay,
                knowledgeSnapshotJson = snapshot,
                protocolEvidenceSummary = evidence.summaryText(),
                entityCount = JSONArray(snapshot).length(),
                overlayAcceptedEntityCount = parsedSnapshot?.entities?.size ?: 0,
                invalidCoordinateCount = parsedSnapshot?.invalidCoordinateCount ?: 0,
                overlaySnapshotValid = parsedSnapshot != null
            )
        } finally {
            replayDir.deleteRecursively()
        }
    }

    fun writeJson(
        result: Result,
        destination: File,
        liveKnowledgeJson: String? = null,
        liveHealthSummary: String? = null
    ) {
        destination.parentFile?.mkdirs()
        val liveParsed = liveKnowledgeJson?.let { AlbionRadarSnapshotParser.parse(it) }
        val liveAccepted = JSONArray().also { array ->
            liveParsed?.entities?.forEach { array.put(JSONObject(it.toString())) }
        }
        val root = JSONObject()
            .put("schema_version", 1)
            .put("replay_scope", "pcap_to_photon_to_protocol18_to_event_mapper_to_isolated_knowledge_and_evidence_stores_to_overlay_snapshot")
            .put("isolated_knowledge_store_replayed", true)
            .put("daemon_live_store_mutated", false)
            .put("overlay_delivery_verified", false)
            .put("live_knowledge_snapshot_valid", liveParsed != null)
            .put("live_knowledge_entity_count", liveParsed?.entities?.size ?: 0)
            .put("live_invalid_coordinate_count", liveParsed?.invalidCoordinateCount ?: 0)
            .put("live_knowledge_snapshot", liveAccepted)
            .put("live_overlay_health_summary", liveHealthSummary ?: "not_available")
            .put("source_name", result.replay.sourceName)
            .put("source_sha256", result.replay.sourceSha256)
            .put("fingerprint", result.replay.fingerprint)
            .put("entity_count", result.entityCount)
            .put("overlay_accepted_entity_count", result.overlayAcceptedEntityCount)
            .put("invalid_coordinate_count", result.invalidCoordinateCount)
            .put("overlay_snapshot_valid", result.overlaySnapshotValid)
            .put("knowledge_snapshot", JSONArray(result.knowledgeSnapshotJson))
            .put("protocol_evidence_summary", result.protocolEvidenceSummary)
            .put("capture_analysis_report", result.replay.report.asText())
            .put("manual_checkpoints", JSONArray(result.replay.report.checkpoints))
            .put("replay_warnings", JSONArray(result.replay.report.warnings))
            .put("event_code_counts", JSONObject().apply {
                result.replay.report.eventCodes.forEach { (code, count) -> put(code.toString(), count) }
            })
            .put("entity_kind_counts", JSONObject().apply {
                result.replay.report.entityKinds.forEach { (kind, count) -> put(kind, count) }
            })
            .put("capture_quality", JSONObject().apply {
                val quality = result.replay.report.quality
                put("packet_records", quality.packetRecords)
                put("captured_bytes", quality.capturedBytes)
                put("duration_ms", quality.durationMs)
                put("timestamp_regressions", quality.timestampRegressions)
                put("truncated_records", quality.truncatedRecords)
                put("duplicate_payloads", quality.duplicatePayloads)
                put("gaps_over_one_second", quality.gapsOverOneSecond)
                put("maximum_gap_ms", quality.maximumGapMs)
                put("native_tap_drops", quality.nativeTapDrops ?: JSONObject.NULL)
                put("tap_drop_rate_percent", quality.tapDropRatePercent ?: JSONObject.NULL)
                put("score", quality.score)
                put("grade", quality.grade)
                put("limitations", JSONArray(quality.limitations))
            })
        destination.writeText(root.toString(2), Charsets.UTF_8)
    }
}
