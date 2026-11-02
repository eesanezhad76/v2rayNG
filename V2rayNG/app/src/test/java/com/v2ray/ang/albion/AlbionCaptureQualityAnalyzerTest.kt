package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class AlbionCaptureQualityAnalyzerTest {
    @TempDir lateinit var tempDir: File

    @Test fun scoresAContinuousCaptureAndCountsExactDuplicatePayloads() {
        val file = File(tempDir, "continuous.pcap")
        writePcap(file, listOf(Record(1_000L, byteArrayOf(1, 2)), Record(1_100L, byteArrayOf(3)), Record(1_200L, byteArrayOf(1, 2))))
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(3, report.packetRecords)
        assertEquals(1, report.duplicatePayloads)
        assertEquals(0, report.timestampRegressions)
        assertEquals("INSUFFICIENT_SAMPLE", report.grade)
    }

    @Test fun incorporatesMeasuredNativeTapDropsIntoQualityScoreWithoutCallingThemNetworkLoss() {
        val file = File(tempDir, "tap-drops.pcap")
        writePcap(file, listOf(
            Record(1_000L, byteArrayOf(1)),
            Record(1_100L, byteArrayOf(2)),
            Record(1_200L, byteArrayOf(3))
        ))
        val withoutMetadata = AlbionCaptureQualityAnalyzer.analyze(file)
        val withDrops = AlbionCaptureQualityAnalyzer.analyze(file, nativeTapDrops = 100L)
        assertEquals(100L, withDrops.nativeTapDrops)
        assertTrue((withDrops.tapDropRatePercent ?: 0.0) > 90.0)
        assertTrue(withDrops.score < withoutMetadata.score)
        assertTrue(withDrops.asText().contains("Native tap dropped copies: 100"))
        assertTrue(withDrops.asText().contains("local tap-queue drops, not network packet loss"))
    }

    @Test fun detectsTimestampRegressionAndLongContinuityGap() {
        val file = File(tempDir, "gaps.pcap")
        writePcap(file, listOf(
            Record(3_000L, byteArrayOf(1)),
            Record(1_000L, byteArrayOf(2)),
            Record(5_500L, byteArrayOf(3))
        ))
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(1, report.timestampRegressions)
        assertEquals(1, report.gapsOverOneSecond)
        assertEquals(4_500L, report.maximumGapMs)
    }

    @Test fun doesNotCrashOnTruncatedRecordAndReportsIt() {
        val file = File(tempDir, "truncated.pcap")
        writePcap(file, listOf(Record(1_000L, byteArrayOf(1))))
        FileOutputStream(file, true).use { out ->
            val header = DataOutputStream(out)
            writeIntLe(header, 1); writeIntLe(header, 0)
            writeIntLe(header, 100); writeIntLe(header, 100)
            header.write(byteArrayOf(9, 8))
        }
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(1, report.packetRecords)
        assertEquals(1, report.truncatedRecords)
        assertTrue(report.score < 100)
    }

    @Test fun countsPartialRecordHeaderAsMalformed() {
        val file = File(tempDir, "partial-header.pcap")
        writePcap(file, listOf(Record(1_000L, byteArrayOf(1))))
        FileOutputStream(file, true).use { it.write(byteArrayOf(1, 2, 3)) }
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(1, report.packetRecords)
        assertEquals(1, report.malformedRecordHeaders)
    }

    @Test fun reportsEmptyFileWithoutThrowing() {
        val report = AlbionCaptureQualityAnalyzer.analyze(File(tempDir, "empty.pcap").apply { writeBytes(byteArrayOf()) })
        assertEquals("NO_DATA", report.grade)
        assertEquals(0, report.packetRecords)
    }

    private data class Record(val timestampMs: Long, val payload: ByteArray)

    private fun writePcap(file: File, records: List<Record>) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            writeIntLe(out, 0xa1b2c3d4.toInt())
            writeShortLe(out, 2); writeShortLe(out, 4)
            writeIntLe(out, 0); writeIntLe(out, 0); writeIntLe(out, 65535); writeIntLe(out, 101)
            for (record in records) {
                writeIntLe(out, (record.timestampMs / 1000L).toInt())
                writeIntLe(out, ((record.timestampMs % 1000L) * 1000L).toInt())
                writeIntLe(out, record.payload.size)
                writeIntLe(out, record.payload.size)
                out.write(record.payload)
            }
        }
    }

    private fun writeIntLe(out: DataOutputStream, value: Int) = out.writeInt(Integer.reverseBytes(value))
    private fun writeShortLe(out: DataOutputStream, value: Int) = out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
}
