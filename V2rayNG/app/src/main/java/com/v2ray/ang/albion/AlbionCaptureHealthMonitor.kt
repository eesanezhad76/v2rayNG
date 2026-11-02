package com.v2ray.ang.albion

/** Thread-safe timestamps for the live Packet -> Overlay handoff. */
class AlbionCaptureHealthMonitor {
    data class Snapshot(
        val status: String,
        val lastPacketAgeMs: Long?,
        val lastEventAgeMs: Long?,
        val lastEntityAgeMs: Long?,
        val lastSnapshotAgeMs: Long?,
        val lastOverlayAcceptedAgeMs: Long?,
        val overlayEntityCount: Int
    ) {
        fun asText(): String = buildString {
            appendLine("Overlay pipeline health: $status")
            appendLine("Last packet age (ms): ${lastPacketAgeMs ?: "never"}")
            appendLine("Last valid event age (ms): ${lastEventAgeMs ?: "never"}")
            appendLine("Last mapped entity age (ms): ${lastEntityAgeMs ?: "never"}")
            appendLine("Last snapshot age (ms): ${lastSnapshotAgeMs ?: "never"}")
            appendLine("Last overlay acceptance age (ms): ${lastOverlayAcceptedAgeMs ?: "never"}")
            appendLine("Coordinate records accepted by overlay view: $overlayEntityCount")
        }
    }

    @Volatile private var lastPacketAtMs = 0L
    @Volatile private var lastEventAtMs = 0L
    @Volatile private var lastEntityAtMs = 0L
    @Volatile private var lastSnapshotAtMs = 0L
    @Volatile private var lastOverlayAcceptedAtMs = 0L
    @Volatile private var overlayEntityCount = 0

    fun recordPacket(timestampMs: Long) { lastPacketAtMs = timestampMs }
    fun recordValidEvent(timestampMs: Long) { lastEventAtMs = timestampMs }
    fun recordMappedEntity(timestampMs: Long) { lastEntityAtMs = timestampMs }
    fun recordSnapshot(timestampMs: Long) { lastSnapshotAtMs = timestampMs }
    fun recordOverlayAccepted(timestampMs: Long, entityCount: Int) {
        lastOverlayAcceptedAtMs = timestampMs
        overlayEntityCount = entityCount.coerceAtLeast(0)
    }

    fun snapshot(nowMs: Long, captureRunning: Boolean): Snapshot {
        fun age(timestamp: Long): Long? =
            if (timestamp <= 0L) null else (nowMs - timestamp).coerceAtLeast(0L)
        val packetAge = age(lastPacketAtMs)
        val eventAge = age(lastEventAtMs)
        val entityAge = age(lastEntityAtMs)
        val snapshotAge = age(lastSnapshotAtMs)
        val overlayAge = age(lastOverlayAcceptedAtMs)
        val status = when {
            !captureRunning -> "CAPTURE_INACTIVE"
            packetAge == null -> "WAITING_FOR_PACKETS"
            packetAge > PACKET_STALE_MS -> "PACKET_STALE"
            eventAge == null -> "WAITING_FOR_DECODED_EVENTS"
            eventAge > EVENT_STALE_MS -> "EVENT_STALE"
            entityAge == null -> "WAITING_FOR_MAPPED_ENTITIES"
            entityAge > EVENT_STALE_MS -> "ENTITY_STALE"
            snapshotAge == null -> "WAITING_FOR_OVERLAY_SNAPSHOT"
            overlayAge == null -> "WAITING_FOR_OVERLAY_ACCEPTANCE"
            overlayEntityCount == 0 -> "OVERLAY_EMPTY"
            overlayAge > OVERLAY_STALE_MS -> "OVERLAY_STALE"
            else -> "PIPELINE_ACTIVE"
        }
        return Snapshot(status, packetAge, eventAge, entityAge, snapshotAge, overlayAge, overlayEntityCount)
    }

    companion object {
        private const val PACKET_STALE_MS = 15_000L
        private const val EVENT_STALE_MS = 30_000L
        private const val OVERLAY_STALE_MS = 10_000L
    }
}
