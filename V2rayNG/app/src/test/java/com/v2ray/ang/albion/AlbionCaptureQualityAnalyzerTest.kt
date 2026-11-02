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
        writePcap(file, listOf(Record(1_000_000, byteArrayOf(1, 2)), Record(1_100_000, byteArrayOf(3)), Record(1_200_000, byteArrayOf(1, 2))))
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(3, report.packetRecords)
        assertEquals(1, report.duplicatePayloads)
        assertEquals(0, report.timestampRegressions)
        assertEquals("INSUFFICIENT_SAMPLE", report.grade)
    }

    @Test fun detectsTimestampRegressionAndLongContinuityGap() {
        val file = File(tempDir, "gaps.pcap")
        writePcap(file, listOf(
            Record(3_000_000, byteArrayOf(1)),
            Record(1_000_000, byteArrayOf(2)),
            Record(5_500_000, byteArrayOf(3))
        ))
        val report = AlbionCaptureQualityAnalyzer.analyze(file)
        assertEquals(1, report.timestampRegressions)
        assertEquals(1, report.gapsOverOneSecond)
        assertEquals(4_500L, report.maximumGapMs)
    }

    @Test fun doesNotCrashOnTruncatedRecordAndReportsIt() {
        val file = File(tempDir, "truncated.pcap")
        writePcap(file, listOf(Record(1_000_000, byteArrayOf(1))))
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

    @Test fun reportsEmptyFileWithoutThrowing() {
        val report = AlbionCaptureQualityAnalyzer.analyze(File(tempDir, "empty.pcap").apply { writeBytes(byteArrayOf()) })
        assertEquals("NO_DATA", report.grade)
        assertEquals(0, report.packetRecords)
    }

    private data class Record(val timestampMicros: Int, val payload: ByteArray)

    private fun writePcap(file: File, records: List<Record>) {
        DataOutputStream(FileOutputStream(file)).use { out ->
            writeIntLe(out, 0xa1b2c3d4.toInt())
            writeShortLe(out, 2); writeShortLe(out, 4)
            writeIntLe(out, 0); writeIntLe(out, 0); writeIntLe(out, 65535); writeIntLe(out, 101)
            for (record in records) {
                writeIntLe(out, 1)
                writeIntLe(out, record.timestampMicros)
                writeIntLe(out, record.payload.size)
                writeIntLe(out, record.payload.size)
                out.write(record.payload)
            }
        }
    }

    private fun writeIntLe(out: DataOutputStream, value: Int) = out.writeInt(Integer.reverseBytes(value))
    private fun writeShortLe(out: DataOutputStream, value: Int) = out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
}
