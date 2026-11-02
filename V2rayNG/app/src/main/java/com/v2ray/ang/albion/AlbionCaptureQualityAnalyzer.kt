package com.v2ray.ang.albion

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Independent integrity/continuity pass over a classic PCAP file.
 *
 * This measures what can be proven from the saved file. Tap-drop counters and
 * decoder-side event loss are intentionally not fabricated when unavailable.
 */
data class AlbionCaptureQualityReport(
    val packetRecords: Int,
    val capturedBytes: Long,
    val firstTimestampMs: Long?,
    val lastTimestampMs: Long?,
    val durationMs: Long,
    val timestampRegressions: Int,
    val invalidTimestampFractions: Int,
    val malformedRecordHeaders: Int,
    val truncatedRecords: Int,
    val duplicatePayloads: Int,
    val gapsOverOneSecond: Int,
    val maximumGapMs: Long,
    val score: Int,
    val grade: String,
    val limitations: List<String>
) {
    fun asText(): String = buildString {
        appendLine("Capture file quality: $grade ($score/100)")
        appendLine("PCAP records: $packetRecords")
        appendLine("Captured bytes: $capturedBytes")
        appendLine("Duration (ms): $durationMs")
        appendLine("Timestamp regressions: $timestampRegressions")
        appendLine("Invalid timestamp fractions: $invalidTimestampFractions")
        appendLine("Malformed record headers: $malformedRecordHeaders")
        appendLine("Truncated records: $truncatedRecords")
        appendLine("Repeated packet payloads: $duplicatePayloads")
        appendLine("Gaps over 1 second: $gapsOverOneSecond")
        appendLine("Maximum observed gap (ms): $maximumGapMs")
        limitations.forEach { appendLine("Limitation: $it") }
    }
}

object AlbionCaptureQualityAnalyzer {
    private const val MAX_CAPTURED_PACKET = 2_000_000

    fun analyze(file: File): AlbionCaptureQualityReport {
        var records = 0
        var bytes = 0L
        var first: Long? = null
        var last: Long? = null
        var regressions = 0
        var invalidFractions = 0
        var malformedHeaders = 0
        var truncated = 0
        var duplicates = 0
        var gaps = 0
        var maxGap = 0L
        val seen = HashSet<String>()
        var nanoPrecision = false

        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val magic = try { input.readInt() } catch (_: EOFException) {
                return emptyReport()
            }
            val little = when (magic) {
                0xd4c3b2a1.toInt(), 0x4d3cb2a1 -> true
                0xa1b2c3d4.toInt(), 0xa1b23c4d.toInt() -> false
                else -> throw IllegalArgumentException("Unsupported PCAP magic: %08x".format(magic))
            }
            nanoPrecision = magic == 0x4d3cb2a1 || magic == 0xa1b23c4d.toInt()
            try {
                repeat(5) { readInt(input, little) }
            } catch (_: EOFException) {
                malformedHeaders++
                return@use
            }

            while (true) {
                val seconds: Int
                val fraction: Int
                val captured: Int
                val original: Int
                if (input.available() == 0) break
                if (input.available() < 16) {
                    malformedHeaders++
                    break
                }
                try {
                    seconds = readInt(input, little)
                    fraction = readInt(input, little)
                    captured = readInt(input, little)
                    original = readInt(input, little)
                } catch (_: EOFException) {
                    break
                }
                if (captured !in 1..MAX_CAPTURED_PACKET || original < captured) {
                    malformedHeaders++
                    break
                }
                if (input.available() < captured) {
                    truncated++
                    break
                }
                val packet = try {
                    ByteArray(captured).also { input.readFully(it) }
                } catch (_: EOFException) {
                    truncated++
                    break
                }
                records++
                bytes += captured
                val fractionLimit = if (nanoPrecision) 1_000_000_000L else 1_000_000L
                if (fraction < 0 || fraction.toLong() >= fractionLimit) invalidFractions++
                val normalizedFraction = fraction.toLong().coerceIn(0L, fractionLimit - 1L)
                val timestamp = seconds.toLong() * 1000L +
                    if (nanoPrecision) normalizedFraction / 1_000_000L else normalizedFraction / 1_000L
                if (first == null) first = timestamp
                last?.let { previous ->
                    val delta = timestamp - previous
                    if (delta < 0) regressions++
                    else {
                        if (delta > 1_000L) gaps++
                        if (delta > maxGap) maxGap = delta
                    }
                }
                last = timestamp
                val digest = MessageDigest.getInstance("SHA-256").digest(packet)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!seen.add(digest)) duplicates++
            }
        }

        val integrityPenalty = if (records == 0) 100 else
            ((malformedHeaders + truncated).toDouble() / (records + malformedHeaders + truncated).coerceAtLeast(1) * 35.0).toInt()
        val regressionPenalty = if (records == 0) 0 else
            (regressions.toDouble() / records * 20.0).toInt()
        val duplicatePenalty = if (records == 0) 0 else
            (duplicates.toDouble() / records * 8.0).toInt()
        val gapPenalty = (gaps * 2).coerceAtMost(20)
        val timestampPenalty = if (records == 0) 0 else
            (invalidFractions.toDouble() / records * 10.0).toInt()
        val score = (100 - integrityPenalty - regressionPenalty - duplicatePenalty - gapPenalty - timestampPenalty)
            .coerceIn(0, 100)
        val grade = when {
            records == 0 -> "NO_DATA"
            records < 5 -> "INSUFFICIENT_SAMPLE"
            score >= 90 -> "GOOD"
            score >= 70 -> "FAIR"
            else -> "POOR"
        }
        val limitations = listOf(
            "Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.",
            "Tap-drop counters are external to PCAP and must be supplied by capture metadata.",
            "A long timestamp gap is a continuity warning, not proof that packets were dropped."
        )
        return AlbionCaptureQualityReport(
            records, bytes, first, last,
            if (first != null && last != null) (last - first).coerceAtLeast(0L) else 0L,
            regressions, invalidFractions, malformedHeaders, truncated, duplicates,
            gaps, maxGap, score, grade, limitations
        )
    }

    private fun emptyReport() = AlbionCaptureQualityReport(
        0, 0L, null, null, 0L, 0, 0, 0, 0, 0, 0, 0L,
        0, "NO_DATA", listOf("The file is empty or has no complete PCAP global header.")
    )

    private fun readInt(input: DataInputStream, little: Boolean): Int =
        if (little) Integer.reverseBytes(input.readInt()) else input.readInt()

}
