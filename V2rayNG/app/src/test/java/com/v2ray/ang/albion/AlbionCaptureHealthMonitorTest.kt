package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlbionCaptureHealthMonitorTest {
    @Test fun progressesFromWaitingToActiveWhenEachPipelineStageReports() {
        val monitor = AlbionCaptureHealthMonitor()
        assertEquals("WAITING_FOR_PACKETS", monitor.snapshot(1_000L, true).status)
        monitor.recordPacket(1_000L)
        assertEquals("WAITING_FOR_DECODED_EVENTS", monitor.snapshot(1_000L, true).status)
        monitor.recordValidEvent(1_100L)
        assertEquals("WAITING_FOR_MAPPED_ENTITIES", monitor.snapshot(1_100L, true).status)
        monitor.recordMappedEntity(1_150L)
        assertEquals("WAITING_FOR_OVERLAY_SNAPSHOT", monitor.snapshot(1_150L, true).status)
        monitor.recordSnapshot(1_200L)
        assertEquals("WAITING_FOR_OVERLAY_ACCEPTANCE", monitor.snapshot(1_200L, true).status)
        monitor.recordOverlayAccepted(1_250L, 7)
        val active = monitor.snapshot(1_300L, true)
        assertEquals("PIPELINE_ACTIVE", active.status)
        assertEquals(7, active.overlayEntityCount)
        assertEquals(50L, active.lastOverlayAcceptedAgeMs)
    }


    @Test fun detectsMissingAndStaleMappedEntitiesSeparatelyFromPacketAndEventHealth() {
        val monitor = AlbionCaptureHealthMonitor()
        monitor.recordPacket(1_000L)
        monitor.recordValidEvent(1_100L)
        assertEquals("WAITING_FOR_MAPPED_ENTITIES", monitor.snapshot(1_200L, true).status)
        monitor.recordMappedEntity(1_250L)
        assertEquals("ENTITY_STALE", monitor.snapshot(40_000L, true).status)
    }

    @Test fun detectsStalePacketsAndInactiveCapture() {
        val monitor = AlbionCaptureHealthMonitor()
        monitor.recordPacket(1_000L)
        monitor.recordValidEvent(1_100L)
        monitor.recordMappedEntity(1_150L)
        monitor.recordSnapshot(1_200L)
        monitor.recordOverlayAccepted(1_250L, 3)
        assertEquals("PACKET_STALE", monitor.snapshot(20_000L, true).status)
        assertEquals("CAPTURE_INACTIVE", monitor.snapshot(20_000L, false).status)
    }
}
