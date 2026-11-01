package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Learns repeatable, label-correlated packet observations from the JSONL capture.
 * Scores are heuristic evidence scores, NOT calibrated probabilities or proof of protocol meaning.
 */
object AlbionPatternMiner {
    private const val EDGE_WINDOW_MS = 10_000L
    private const val MAX_SESSION_MS = 15 * 60_000L

    data class Sample(
        val activity: String,
        val startMs: Long,
        val endMs: Long,
        val observations: List<JSONObject>
    )

    data class Pattern(
        val signature: String,
        val activity: String,
        val kind: String,
        val eventCode: Int?,
        val typeId: Int?,
        val tier: Int?,
        val enchant: Int?,
        val samples: Int,
        val distinctEntities: Int,
        val observationCount: Int,
        val eventCodes: Map<Int, Int>,
        val completePairs: Int,
        val score: Int
    )

    data class Report(
        val markers: Int,
        val completeSamples: Int,
        val incompleteStarts: Int,
        val patterns: List<Pattern>,
        val warnings: List<String>
    ) {
        fun asText(): String = buildString {
            appendLine()
            appendLine("=== Marker-correlated pattern mining ===")
            appendLine("Overlay markers read: $markers")
            appendLine("Complete Found -> Done sample windows: $completeSamples")
            appendLine("Unclosed sample starts: $incompleteStarts")
            appendLine("Confidence is a heuristic evidence score (0-100), NOT a probability.")
            if (patterns.isEmpty()) appendLine("No repeatable labeled pattern found yet.")
            patterns.take(30).forEachIndexed { index, p ->
                appendLine("#${index + 1} [${p.score}/100] ${p.activity} / ${p.kind}")
                appendLine("  signature: ${p.signature}")
                appendLine("  repeated samples: ${p.samples}; distinct entity IDs: ${p.distinctEntities}; observations: ${p.observationCount}")
                appendLine("  event codes: ${p.eventCodes.toSortedMap()}")
                appendLine("  complete label pairs: ${p.completePairs}")
            }
            warnings.forEach { appendLine("Warning: $it") }
        }
    }

    private data class Marker(val time: Long, val activity: String, val event: String)
    private data class Candidate(
        val signature: String, val activity: String, val kind: String,
        val eventCode: Int?, val typeId: Int?, val tier: Int?, val enchant: Int?,
        val ids: MutableSet<String>, val events: MutableMap<Int, Int>,
        var observations: Int = 0, val sampleIndexes: MutableSet<Int> = linkedSetOf()
    )

    fun analyze(file: File?): Report {
        if (file == null || !file.isFile) return Report(0, 0, 0, emptyList(), listOf("JSONL capture file is missing."))
        val markers = mutableListOf<Marker>()
        val packets = mutableListOf<Pair<Long, List<JSONObject>>>()
        var malformedLines = 0
        file.useLines { lines ->
            lines.forEach { line ->
                try {
                    val row = JSONObject(line)
                    val time = row.optLong("time_ms", -1L)
                    if (row.has("manual_marker")) {
                        val m = row.optJSONObject("manual_marker") ?: return@forEach
                        val markerTime = m.optLong("timestamp_ms", time)
                        if (markerTime > 0) markers += Marker(markerTime, m.optString("activity", "Unknown"), m.optString("event", ""))
                    } else if (time > 0) {
                        val array = row.optJSONArray("entities") ?: JSONArray()
                        val observations = ArrayList<JSONObject>(array.length())
                        for (i in 0 until array.length()) array.optJSONObject(i)?.let { observations += it }
                        if (observations.isNotEmpty()) packets += time to observations
                    }
                } catch (_: Exception) { malformedLines++ }
            }
        }
        markers.sortBy { it.time }
        val samples = mutableListOf<Sample>()
        val open = mutableMapOf<String, Marker>()
        var incomplete = 0
        markers.forEach { marker ->
            when (marker.event) {
                "target_found" -> {
                    if (open.containsKey(marker.activity)) incomplete++
                    open[marker.activity] = marker
                }
                "gathering_completed" -> {
                    val start = open.remove(marker.activity) ?: return@forEach
                    if (marker.time <= start.time || marker.time - start.time > MAX_SESSION_MS) {
                        incomplete++
                    } else {
                        val from = (start.time - EDGE_WINDOW_MS).coerceAtLeast(0L)
                        val to = marker.time + EDGE_WINDOW_MS
                        val obs = packets.asSequence().filter { it.first in from..to }.flatMap { it.second.asSequence() }.toList()
                        samples += Sample(marker.activity, start.time, marker.time, obs)
                    }
                }
            }
        }
        incomplete += open.size
        val candidates = linkedMapOf<String, Candidate>()
        samples.forEachIndexed { sampleIndex, sample ->
            sample.observations.forEach { o ->
                val kind = o.optString("kind", "unknown")
                val code = o.optInt("event_code").takeIf { o.has("event_code") }
                val type = o.optInt("mobile_type_id").takeIf { o.has("mobile_type_id") }
                    ?: o.optInt("type_id").takeIf { o.has("type_id") }
                val tier = o.optInt("tier").takeIf { o.has("tier") }
                val enchant = o.optInt("enchant").takeIf { o.has("enchant") }
                    ?: o.optInt("enchantment_raw").takeIf { o.has("enchantment_raw") }
                val signature = listOf(sample.activity, kind, code ?: "-", type ?: "-", tier ?: "-", enchant ?: "-").joinToString("|")
                val c = candidates.getOrPut(signature) {
                    Candidate(signature, sample.activity, kind, code, type, tier, enchant, linkedSetOf(), linkedMapOf())
                }
                c.observations++
                c.sampleIndexes += sampleIndex
                if (o.has("entity_id")) c.ids += o.opt("entity_id").toString()
                if (o.has("id")) c.ids += o.opt("id").toString()
                if (code != null) c.events[code] = (c.events[code] ?: 0) + 1
            }
        }
        val patterns = candidates.values.map { c ->
            val repeated = c.sampleIndexes.size
            val repeatScore = (repeated.coerceAtMost(10) * 5)
            val identityScore = if (c.ids.size >= 2) 15 else if (c.ids.size == 1) 8 else 0
            val typedScore = (if (c.eventCode != null) 10 else 0) +
                (if (c.typeId != null) 10 else 0) + (if (c.tier != null) 5 else 0) + (if (c.enchant != null) 5 else 0)
            val volumeScore = if (c.observations >= repeated.coerceAtLeast(1) * 2) 10 else 3
            val pairScore = if (repeated >= 2) 10 else 0
            val score = (repeatScore + identityScore + typedScore + volumeScore + pairScore).coerceAtMost(100)
            Pattern(c.signature, c.activity, c.kind, c.eventCode, c.typeId, c.tier, c.enchant,
                repeated, c.ids.size, c.observations, c.events.toMap(), repeated, score)
        }.filter { it.samples > 0 }.sortedWith(compareByDescending<Pattern> { it.score }.thenByDescending { it.samples }.thenByDescending { it.observationCount })
        val warnings = mutableListOf<String>()
        if (samples.isEmpty()) warnings += "No complete Found -> Done marker pairs. For each resource, tap Found before gathering and Done immediately after."
        if (samples.size < 10) warnings += "Fewer than 10 complete samples: pattern scores are preliminary; collect more labeled nodes."
        if (patterns.none { it.samples >= 3 }) warnings += "No signature has repeated across at least 3 separate samples yet."
        if (malformedLines > 0) warnings += "$malformedLines malformed JSONL lines were skipped."
        if (packets.isEmpty()) warnings += "No decoded entity observations were available in JSONL; encrypted or unknown traffic cannot be semantically classified."
        return Report(markers.size, samples.size, incomplete, patterns, warnings)
    }
}
