package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Cross-capture numeric pattern discovery.
 *
 * This deliberately reports recurring field paths, not guessed item IDs or meanings.
 * A candidate must appear in at least [minimumIndependentCaptures] distinct capture files.
 */
object AlbionPatternMiner {
    data class Candidate(
        val fieldPath: String,
        val independentCaptures: Int,
        val occurrences: Int,
        val distinctValues: Int,
        val examples: List<String>,
        val recurrenceScore: Double
    )

    data class Report(
        val filesAnalyzed: Int,
        val recordsAnalyzed: Int,
        val malformedLines: Int,
        val truncatedFiles: List<String>,
        val minimumIndependentCaptures: Int,
        val candidates: List<Candidate>
    ) {
        fun asText(): String = buildString {
            appendLine("Cross-capture numeric pattern discovery")
            appendLine("Capture files analyzed: $filesAnalyzed")
            appendLine("JSONL records analyzed: $recordsAnalyzed")
            appendLine("Malformed JSONL records skipped: $malformedLines")
            appendLine("Minimum independent captures per candidate: $minimumIndependentCaptures")
            if (truncatedFiles.isNotEmpty()) {
                appendLine("Capture files limited by record cap: ${truncatedFiles.joinToString()}")
            }
            if (candidates.isEmpty()) {
                appendLine("No numeric field paths met the independent-capture threshold.")
            } else {
                appendLine("Recurring numeric field paths (descriptive only; semantics are unconfirmed):")
                candidates.forEach { candidate ->
                    appendLine(
                        "  ${candidate.fieldPath} | captures=${candidate.independentCaptures}" +
                            " | occurrences=${candidate.occurrences} | distinct_values_observed_at_most_32=${candidate.distinctValues}" +
                            " | recurrence_score=${String.format(Locale.US, "%.2f", candidate.recurrenceScore)}" +
                            " | examples=${candidate.examples.joinToString(prefix = "[", postfix = "]")}"
                    )
                }
            }
            appendLine("A recurring numeric path is not proof of an item ID, inventory count, or game mechanic.")
        }
    }

    private data class MutableCandidate(
        val captures: MutableSet<String> = linkedSetOf(),
        var occurrences: Int = 0,
        val values: MutableSet<String> = linkedSetOf()
    )

    fun analyze(
        captureFiles: List<File>,
        minimumIndependentCaptures: Int = 3,
        maxFiles: Int = 10,
        maxRecordsPerFile: Int = 20_000
    ): Report {
        require(minimumIndependentCaptures >= 1) { "minimumIndependentCaptures must be positive" }
        require(maxFiles >= 1) { "maxFiles must be positive" }
        require(maxRecordsPerFile >= 1) { "maxRecordsPerFile must be positive" }

        val selected = captureFiles
            .filter { it.isFile && it.extension.equals("jsonl", ignoreCase = true) }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedBy { it.lastModified() }
            .takeLast(maxFiles)
        val observations = LinkedHashMap<String, MutableCandidate>()
        var records = 0
        var malformed = 0
        val truncated = mutableListOf<String>()

        selected.forEach { file ->
            val captureId = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            var fileRecords = 0
            var hitLimit = false
            try {
                file.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (fileRecords >= maxRecordsPerFile) {
                            hitLimit = true
                            break
                        }
                        fileRecords++
                        if (line.length > MAX_LINE_LENGTH) {
                            malformed++
                            continue
                        }
                        val root = try {
                            JSONObject(line)
                        } catch (_: Exception) {
                            malformed++
                            continue
                        }
                        var relevant = false
                        for (key in ANALYZED_ROOTS) {
                            if (root.has(key)) {
                                relevant = true
                                walk(root.opt(key), key, captureId, observations, 0)
                            }
                        }
                        if (relevant) records++
                    }
                }
            } catch (_: Exception) {
                malformed++
            }
            if (hitLimit) truncated += file.name
        }

        val candidates = observations.mapNotNull { (path, value) ->
            if (value.captures.size < minimumIndependentCaptures) return@mapNotNull null
            val score = (
                0.25 + 0.12 * minOf(value.captures.size, 5) +
                    0.01 * minOf(value.occurrences, 15)
                ).coerceAtMost(0.95)
            Candidate(
                fieldPath = path,
                independentCaptures = value.captures.size,
                occurrences = value.occurrences,
                distinctValues = value.values.size,
                examples = value.values.take(MAX_EXAMPLES).toList(),
                recurrenceScore = score
            )
        }.sortedWith(
            compareByDescending<Candidate> { it.independentCaptures }
                .thenByDescending { it.occurrences }
                .thenBy { it.fieldPath }
        )

        return Report(
            filesAnalyzed = selected.size,
            recordsAnalyzed = records,
            malformedLines = malformed,
            truncatedFiles = truncated,
            minimumIndependentCaptures = minimumIndependentCaptures,
            candidates = candidates
        )
    }

    private fun walk(
        value: Any?,
        path: String,
        captureId: String,
        output: MutableMap<String, MutableCandidate>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        when (value) {
            is Number -> {
                if (output.size >= MAX_PATHS && path !in output) return
                val candidate = output.getOrPut(path) { MutableCandidate() }
                candidate.captures += captureId
                candidate.occurrences++
                if (candidate.values.size < MAX_STORED_VALUES) {
                    candidate.values += value.toString().take(MAX_VALUE_LENGTH)
                }
            }
            is JSONObject -> {
                if (value.optString("type") == "bytes") return
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.isNotBlank()) walk(value.opt(key), "$path.$key", captureId, output, depth + 1)
                }
            }
            is JSONArray -> {
                for (index in 0 until minOf(value.length(), MAX_ARRAY_ITEMS)) {
                    walk(value.opt(index), "$path[]", captureId, output, depth + 1)
                }
            }
            is Iterable<*> -> value.take(MAX_ARRAY_ITEMS).forEach { walk(it, "$path[]", captureId, output, depth + 1) }
            is Array<*> -> value.take(MAX_ARRAY_ITEMS).forEach { walk(it, "$path[]", captureId, output, depth + 1) }
        }
    }

    private val ANALYZED_ROOTS = listOf("photon_event_analysis", "event_state_changes", "entities")
    private const val MAX_FILES = 10
    private const val MAX_RECORDS_PER_FILE = 20_000
    private const val MAX_LINE_LENGTH = 1_000_000
    private const val MAX_DEPTH = 12
    private const val MAX_PATHS = 5_000
    private const val MAX_ARRAY_ITEMS = 256
    private const val MAX_EXAMPLES = 5
    private const val MAX_STORED_VALUES = 32
    private const val MAX_VALUE_LENGTH = 80
}
