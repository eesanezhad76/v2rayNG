package com.v2ray.ang.albion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlbionPatternMinerTest {
    @TempDir lateinit var tempDir: File

    @Test fun nestedNumericPathsRequireThreeIndependentCaptureFiles() {
        val captures = (1..3).map { index ->
            File(tempDir, "capture-$index.jsonl").apply {
                writeText(
                    """{"time_ms":$index,"photon_event_analysis":[{"event_code":39,"resources":[{"id":1200,"tier":${5 + index},"metadata":{"level":2}}]}]}"""
                )
            }
        }

        val twoCaptureReport = AlbionPatternMiner.analyze(captures.take(2))
        assertTrue(twoCaptureReport.candidates.isEmpty())

        val report = AlbionPatternMiner.analyze(captures)
        val tier = report.candidates.single { it.fieldPath == "photon_event_analysis[].resources[].tier" }
        assertEquals(3, tier.independentCaptures)
        assertEquals(3, tier.occurrences)
        assertEquals(3, tier.distinctValues)
        assertTrue(tier.recurrenceScore > 0.0)
        assertFalse(report.candidates.any { it.fieldPath.contains("time_ms") })
    }

    @Test fun normalizesArrayIndicesAndSkipsMalformedJsonLines() {
        val captures = (1..3).map { index ->
            File(tempDir, "nested-$index.jsonl").apply {
                writeText(
                    "not-json\n" +
                        """{"entities":[{"event_code":123,"values":[{"amount":${index}},{"amount":9}]}]}"""
                )
            }
        }
        val report = AlbionPatternMiner.analyze(captures)
        assertEquals(3, report.filesAnalyzed)
        assertEquals(3, report.malformedLines)
        assertTrue(report.candidates.any { it.fieldPath == "entities[].values[].amount" })
    }

    @Test fun byteIdenticalCopiesDoNotCountAsIndependentCaptures() {
        val original = """{"entities":[{"id":17,"tier":6}]}"""
        val files = listOf(
            File(tempDir, "capture-original.jsonl").apply { writeText(original) },
            File(tempDir, "capture-copy-renamed.jsonl").apply { writeText(original) },
            File(tempDir, "capture-two.jsonl").apply { writeText("""{"entities":[{"id":17,"tier":7}]}""") },
            File(tempDir, "capture-three.jsonl").apply { writeText("""{"entities":[{"id":17,"tier":8}]}""") }
        )
        val report = AlbionPatternMiner.analyze(files)
        assertEquals(1, report.duplicateFilesSkipped)
        val tier = report.candidates.single { it.fieldPath == "entities[].tier" }
        assertEquals(3, tier.independentCaptures)
    }

    @Test fun enforcesRecordCapAndReportsTruncatedFiles() {
        val file = File(tempDir, "long.jsonl").apply {
            writeText(
                (1..3).joinToString("\n") {
                    """{"entities":[{"value":1}]}"""
                }
            )
        }
        val report = AlbionPatternMiner.analyze(listOf(file), minimumIndependentCaptures = 1, maxRecordsPerFile = 2)
        assertEquals(listOf("long.jsonl"), report.truncatedFiles)
        assertEquals(2, report.recordsAnalyzed)
    }
}
