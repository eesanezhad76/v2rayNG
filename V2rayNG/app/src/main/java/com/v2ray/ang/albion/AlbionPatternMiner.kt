package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque

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
        val overlayTierLabel: String?,
        val overlayEnchantLabel: String?,
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
                appendLine("  overlay labels: tier=${p.overlayTierLabel ?: "unknown"}, enchant=${p.overlayEnchantLabel ?: "unknown"}")
                appendLine("  repeated samples: ${p.samples}; distinct entity IDs: ${p.distinctEntities}; observations: ${p.observationCount}")
                appendLine("  event codes: ${p.eventCodes.toSortedMap()}")
                appendLine("  complete label pairs: ${p.completePairs}")
            }
            warnings.forEach { appendLine("Warning: $it") }
        }
    }

    private data class Candidate(
        val signature: String, val activity: String, val kind: String,
        val eventCode: Int?, val typeId: Int?, val tier: Int?, val enchant: Int?,
        val overlayTierLabel: String?, val overlayEnchantLabel: String?,
        val ids: MutableSet<String>, val events: MutableMap<Int, Int>,
        var observations: Int = 0, val sampleIndexes: MutableSet<Int> = linkedSetOf()
    )

    private data class MutableSample(
        val activity: String,
        val startMs: Long,
        var endMs: Long? = null,
        val overlayTierLabel: String? = null,
        val overlayEnchantLabel: String? = null,
        val observations: MutableList<JSONObject> = mutableListOf()
    )

    fun analyze(file: File?): Report {
        if (file == null || !file.isFile) return Report(0, 0, 0, emptyList(), listOf("JSONL capture file is missing."))
        val active = linkedMapOf<String, MutableSample>()
        val closing = mutableListOf<MutableSample>()
        val completed = mutableListOf<Sample>()
        val rolling = ArrayDeque<Pair<Long, List<JSONObject>>>()
        var markerCount = 0
        var incomplete = 0
        var malformedLines = 0

        fun finish(sample: MutableSample) {
            completed += Sample(sample.activity, sample.startMs, sample.endMs ?: sample.startMs, sample.observations.toList())
        }
        file.useLines { lines ->
            lines.forEach { line ->
                try {
                    val row = JSONObject(line)
                    val time = row.optLong("time_ms", -1L)
                    if (row.has("manual_marker")) {
                        val m = row.optJSONObject("manual_marker") ?: return@forEach
                        val markerTime = m.optLong("timestamp_ms", time)
                        if (markerTime <= 0) return@forEach
                        markerCount++
                        val activity = m.optString("activity", "Unknown")
                        when (m.optString("event", "")) {
                            "target_found" -> {
                                active.remove(activity)?.let { incomplete++ }
                                val tierLabel = m.optString("tier", "").takeIf { it.isNotBlank() && it != "null" }
                                val enchantLabel = m.optString("enchantment", "").takeIf { it.isNotBlank() && it != "null" }
                                val sample = MutableSample(activity, markerTime, overlayTierLabel = tierLabel, overlayEnchantLabel = enchantLabel)
                                rolling.filter { it.first >= markerTime - EDGE_WINDOW_MS }.forEach { sample.observations.addAll(it.second) }
                                active[activity] = sample
                            }
                            "gathering_completed" -> {
                                val sample = active.remove(activity) ?: return@forEach
                                if (markerTime <= sample.startMs || markerTime - sample.startMs > MAX_SESSION_MS) {
                                    incomplete++
                                } else {
                                    sample.endMs = markerTime
                                    closing += sample
                                }
                            }
                        }
                    } else if (time > 0) {
                        val array = row.optJSONArray("entities") ?: JSONArray()
                        val observations = ArrayList<JSONObject>(array.length())
                        for (i in 0 until array.length()) array.optJSONObject(i)?.let { observations += it }
                        if (observations.isNotEmpty()) {
                            rolling.addLast(time to observations)
                            while (rolling.isNotEmpty() && rolling.first().first < time - EDGE_WINDOW_MS) rolling.removeFirst()
                            active.values.forEach { it.observations.addAll(observations) }
                            closing.forEach { sample ->
                                if (time <= (sample.endMs ?: time) + EDGE_WINDOW_MS) sample.observations.addAll(observations)
                            }
                        }
                        val iterator = closing.iterator()
                        while (iterator.hasNext()) {
                            val sample = iterator.next()
                            if (time > (sample.endMs ?: time) + EDGE_WINDOW_MS) {
                                finish(sample)
                                iterator.remove()
                            }
                        }
                    }
                } catch (_: Exception) { malformedLines++ }
            }
        }
        incomplete += active.size
        closing.forEach(::finish)

        val candidates = linkedMapOf<String, Candidate>()
        completed.forEachIndexed { sampleIndex, sample ->
            sample.observations.forEach { o ->
                val kind = o.optString("kind", "unknown")
                val code = o.optInt("event_code").takeIf { o.has("event_code") }
                val type = o.optInt("mobile_type_id").takeIf { o.has("mobile_type_id") }
                    ?: o.optInt("type_id").takeIf { o.has("type_id") }
                val tier = o.optInt("tier").takeIf { o.has("tier") }
                val enchant = o.optInt("enchant").takeIf { o.has("enchant") }
                    ?: o.optInt("enchantment_raw").takeIf { o.has("enchantment_raw") }
                val signature = listOf(sample.activity, kind, code ?: "-", type ?: "-", tier ?: "-", enchant ?: "-", sample.overlayTierLabel ?: "-", sample.overlayEnchantLabel ?: "-").joinToString("|")
                val c = candidates.getOrPut(signature) {
                    Candidate(signature, sample.activity, kind, code, type, tier, enchant, sample.overlayTierLabel, sample.overlayEnchantLabel, linkedSetOf(), linkedMapOf())
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
            val repeatScore = repeated.coerceAtMost(10) * 5
            val identityScore = if (c.ids.size >= 2) 15 else if (c.ids.size == 1) 8 else 0
            val typedScore = (if (c.eventCode != null) 10 else 0) +
                (if (c.typeId != null) 10 else 0) + (if (c.tier != null) 5 else 0) + (if (c.enchant != null) 5 else 0)
            val volumeScore = if (c.observations >= repeated.coerceAtLeast(1) * 2) 10 else 3
            val pairScore = if (repeated >= 2) 10 else 0
            val score = (repeatScore + identityScore + typedScore + volumeScore + pairScore).coerceAtMost(100)
            Pattern(c.signature, c.activity, c.kind, c.eventCode, c.typeId, c.tier, c.enchant,
                c.overlayTierLabel, c.overlayEnchantLabel, repeated, c.ids.size, c.observations, c.events.toMap(), repeated, score)
        }.filter { it.samples > 0 }.sortedWith(compareByDescending<Pattern> { it.score }.thenByDescending { it.samples }.thenByDescending { it.observationCount })
        val warnings = mutableListOf<String>()
        if (completed.isEmpty()) warnings += "No complete Found -> Done marker pairs. For each resource, tap Found before gathering and Done immediately after."
        if (completed.size < 10) warnings += "Fewer than 10 complete samples: pattern scores are preliminary; collect more labeled nodes."
        if (patterns.none { it.samples >= 3 }) warnings += "No signature has repeated across at least 3 separate samples yet."
        if (malformedLines > 0) warnings += "$malformedLines malformed JSONL lines were skipped."
        if (patterns.isEmpty()) warnings += "No decoded entity observations were available in the labeled windows; encrypted or unknown traffic cannot be semantically classified."
        return Report(markerCount, completed.size, incomplete, patterns, warnings)
    }
}

