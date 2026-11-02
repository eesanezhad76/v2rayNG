package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Deterministic offline replay of the repository's production PCAP decoder path.
 *
 * Scope: PCAP -> IP/UDP/Photon -> Protocol18 -> Albion event mapper -> observations/report.
 * This first replay layer does not mutate the daemon's live stores or claim that an
 * Android overlay received entities; those are separately reported as unverified.
 */
object AlbionCaptureReplayEngine {
    data class ReplayResult(
        val sourceName: String,
        val sourceSha256: String,
        val report: AlbionCaptureAnalyzer.Report,
        val fingerprint: String
    )

    data class ReplayComparison(
        val identicalFingerprint: Boolean,
        val sameSourceCapture: Boolean,
        val comparisonNote: String,
        val ipPacketDelta: Int,
        val validPhotonPacketDelta: Int,
        val malformedPhotonPacketDelta: Int,
        val qualityScoreDelta: Int,
        val eventCodeDeltas: Map<Int, Int>,
        val entityKindDeltas: Map<String, Int>,
        val regressionSignals: List<String>
    ) {
        fun asText(): String = buildString {
            appendLine("Replay comparison")
            appendLine("Identical fingerprint: $identicalFingerprint")
            appendLine("Same source capture: $sameSourceCapture")
            appendLine(comparisonNote)
            appendLine("IP packet delta: $ipPacketDelta")
            appendLine("Valid Photon packet delta: $validPhotonPacketDelta")
            appendLine("Malformed Photon packet delta: $malformedPhotonPacketDelta")
            appendLine("Capture quality score delta: $qualityScoreDelta")
            eventCodeDeltas.toSortedMap().forEach { (code, delta) -> appendLine("Event $code delta: $delta") }
            entityKindDeltas.toSortedMap().forEach { (kind, delta) -> appendLine("Entity $kind delta: $delta") }
            regressionSignals.forEach { appendLine("Regression signal: $it") }
        }
    }

    /** Decoder singletons are stateful; serialize replays to prevent cross-capture contamination. */
    @Synchronized
    fun replay(file: File): ReplayResult = replay(file, null, null)

    /** Pure-JVM replay hook; Android stores can consume decoded messages without coupling this engine to Android. */
    @Synchronized
    fun replay(
        file: File,
        onDecodedMessage: ((Long, AlbionProtocol18Decoder.Message, List<AlbionEntityEventMapper.Observation>) -> Unit)?,
        nativeTapDrops: Long? = null
    ): ReplayResult {
        require(file.isFile) { "Capture file does not exist: $file" }
        return replayResult(file, AlbionCaptureAnalyzer.analyze(file, onDecodedMessage, nativeTapDrops))
    }

    private fun replayResult(file: File, report: AlbionCaptureAnalyzer.Report): ReplayResult {
        val sourceHash = sha256(file)
        val canonical = buildString {
            append(report.ipPackets).append('|')
            append(report.albionUdpPackets).append('|')
            append(report.validPhotonPackets).append('|')
            append(report.malformedPhotonPackets).append('|')
            append(report.encryptedPhotonPackets).append('|')
            report.eventCodes.toSortedMap().forEach { (key, value) -> append(key).append(':').append(value).append(';') }
            report.entityKinds.toSortedMap().forEach { (key, value) -> append(key).append(':').append(value).append(';') }
            append(report.quality.score).append('|').append(report.quality.grade)
            append('|').append(report.quality.timestampRegressions)
            append('|').append(report.quality.truncatedRecords)
            report.checkpoints.forEach { append("|M:").append(it) }
            report.detailedObservations.forEach { append("|D:").append(it) }
            report.warnings.forEach { append("|W:").append(it) }
        }
        return ReplayResult(file.name, sourceHash, report, sha256(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun compare(previous: ReplayResult, current: ReplayResult): ReplayComparison {
        val sameSource = previous.sourceSha256 == current.sourceSha256
        val eventDeltas = (previous.report.eventCodes.keys + current.report.eventCodes.keys)
            .associateWith { (current.report.eventCodes[it] ?: 0) - (previous.report.eventCodes[it] ?: 0) }
            .filterValues { it != 0 }
        val entityDeltas = (previous.report.entityKinds.keys + current.report.entityKinds.keys)
            .associateWith { (current.report.entityKinds[it] ?: 0) - (previous.report.entityKinds[it] ?: 0) }
            .filterValues { it != 0 }
        val packetDelta = current.report.ipPackets - previous.report.ipPackets
        val photonDelta = current.report.validPhotonPackets - previous.report.validPhotonPackets
        val malformedDelta = current.report.malformedPhotonPackets - previous.report.malformedPhotonPackets
        val qualityDelta = current.report.quality.score - previous.report.quality.score
        val signals = if (!sameSource) emptyList() else buildList {
            if (packetDelta < 0) add("IP packet count decreased by ${-packetDelta}.")
            if (photonDelta < 0) add("Valid Photon packet count decreased by ${-photonDelta}.")
            if (malformedDelta > 0) add("Malformed Photon packet count increased by $malformedDelta.")
            if (qualityDelta < 0) add("Capture quality score decreased by ${-qualityDelta}.")
            eventDeltas.filterValues { it < 0 }.forEach { (code, delta) ->
                add("Decoded event $code count decreased by ${-delta}.")
            }
            entityDeltas.filterValues { it < 0 }.forEach { (kind, delta) ->
                add("Mapped entity kind $kind count decreased by ${-delta}.")
            }
        }
        val note = if (sameSource)
            "Same source PCAP: output deltas are eligible for regression evaluation."
        else
            "Different source PCAPs: deltas are descriptive only, not algorithm regressions."
        return ReplayComparison(
            previous.fingerprint == current.fingerprint,
            sameSource, note, packetDelta, photonDelta, malformedDelta, qualityDelta,
            eventDeltas, entityDeltas, signals
        )
    }

    fun writeJson(result: ReplayResult, destination: File) {
        destination.parentFile?.mkdirs()
        val report = result.report
        val root = JSONObject()
            .put("schema_version", 1)
            .put("replay_scope", "pcap_photon_protocol18_event_mapper")
            .put("live_store_replayed", false)
            .put("overlay_delivery_verified", false)
            .put("source_name", result.sourceName)
            .put("source_sha256", result.sourceSha256)
            .put("fingerprint", result.fingerprint)
            .put("ip_packets", report.ipPackets)
            .put("albion_udp_packets", report.albionUdpPackets)
            .put("valid_photon_packets", report.validPhotonPackets)
            .put("malformed_photon_packets", report.malformedPhotonPackets)
            .put("encrypted_photon_packets", report.encryptedPhotonPackets)
            .put("malformed_protocol18_messages", report.malformedProtocol18Messages)
            .put("pending_fragments", report.pendingFragments)
            .put("event_codes", intMap(report.eventCodes))
            .put("operation_requests", intMap(report.operationRequests))
            .put("operation_responses", intMap(report.operationResponses))
            .put("entity_kinds", stringMap(report.entityKinds))
            .put("quality", JSONObject()
                .put("score", report.quality.score)
                .put("grade", report.quality.grade)
                .put("records", report.quality.packetRecords)
                .put("duplicate_payloads", report.quality.duplicatePayloads)
                .put("timestamp_regressions", report.quality.timestampRegressions)
                .put("truncated_records", report.quality.truncatedRecords)
                .put("gaps_over_one_second", report.quality.gapsOverOneSecond)
                .put("maximum_gap_ms", report.quality.maximumGapMs)
                .put("native_tap_drops", report.quality.nativeTapDrops ?: JSONObject.NULL)
                .put("tap_drop_rate_percent", report.quality.tapDropRatePercent ?: JSONObject.NULL))
            .put("warnings", JSONArray(report.warnings))
        destination.writeText(root.toString(2), Charsets.UTF_8)
    }

    private fun intMap(values: Map<Int, Int>) = JSONObject().also { out ->
        values.toSortedMap().forEach { (key, value) -> out.put(key.toString(), value) }
    }

    private fun stringMap(values: Map<String, Int>) = JSONObject().also { out ->
        values.toSortedMap().forEach { (key, value) -> out.put(key, value) }
    }

    private fun sha256(file: File): String = file.inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
