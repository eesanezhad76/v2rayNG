package com.v2ray.ang.albion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Discovers repeated numeric parameter paths associated with independent HarvestFinished events.
 * Results are deliberately unclassified: they are not Item IDs or stack deltas.
 */
object AlbionInventoryEvidenceAnalyzer {
    data class Candidate(
        val eventCode: Int,
        val parameterPath: String,
        val independentHarvests: Int,
        val observations: Int,
        val distinctValues: Int,
        val minimum: Double,
        val maximum: Double,
        val positiveDeltaCount: Int,
        val negativeDeltaCount: Int,
        val zeroDeltaCount: Int,
        val minimumDelta: Double?,
        val maximumDelta: Double?
    )

    private data class NumericObservation(val inventoryTimeMs: Long, val value: Double)

    private data class Accumulator(
        val harvestTimes: MutableSet<Long> = linkedSetOf(),
        val values: MutableList<NumericObservation> = mutableListOf()
    )

    fun analyze(
        events: List<JSONObject>,
        pairWindowMs: Long = 15_000L,
        minimumIndependentHarvests: Int = 3
    ): List<Candidate> {
        if (pairWindowMs < 0L || minimumIndependentHarvests < 1) return emptyList()
        val finishes = events.filter {
            it.optString("kind") == "harvest" && it.optInt("event_code") == 61
        }
        if (finishes.isEmpty()) return emptyList()
        val accumulators = linkedMapOf<String, Accumulator>()
        events.filter { it.optString("kind") == "inventory" }
            .sortedBy { it.optLong("time_ms", Long.MAX_VALUE) }
            .forEach { inventory ->
            val inventoryTime = inventory.optLong("time_ms", -1L)
            if (inventoryTime < 0L) return@forEach
            val finish = finishes.minByOrNull { abs(it.optLong("time_ms") - inventoryTime) }
                ?: return@forEach
            val finishTime = finish.optLong("time_ms")
            if (abs(finishTime - inventoryTime) > pairWindowMs) return@forEach
            val eventCode = inventory.optInt("event_code", -1)
            val parameters = inventory.optJSONObject("parameters") ?: return@forEach
            val keys = parameters.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
            keys.forEach { key ->
                collectNumeric(parameters.opt(key), key, eventCode, inventoryTime, finishTime, accumulators)
            }
        }
        return accumulators.mapNotNull { (key, accumulator) ->
            if (accumulator.harvestTimes.size < minimumIndependentHarvests || accumulator.values.isEmpty()) {
                return@mapNotNull null
            }
            val split = key.split("|", limit = 2)
            val values = accumulator.values.sortedBy { it.inventoryTimeMs }.map { it.value }
            val deltas = values.zipWithNext { previous, next -> next - previous }
            Candidate(
                eventCode = split[0].toInt(),
                parameterPath = split[1],
                independentHarvests = accumulator.harvestTimes.size,
                observations = values.size,
                distinctValues = values.toSet().size,
                minimum = values.minOrNull() ?: return@mapNotNull null,
                maximum = values.maxOrNull() ?: return@mapNotNull null,
                positiveDeltaCount = deltas.count { it > 0.0 },
                negativeDeltaCount = deltas.count { it < 0.0 },
                zeroDeltaCount = deltas.count { it == 0.0 },
                minimumDelta = deltas.minOrNull(),
                maximumDelta = deltas.maxOrNull()
            )
        }.sortedWith(compareByDescending<Candidate> { it.independentHarvests }
            .thenByDescending { it.observations }
            .thenBy { it.eventCode }
            .thenBy { it.parameterPath })
    }

    private fun collectNumeric(
        value: Any?,
        path: String,
        eventCode: Int,
        inventoryTime: Long,
        harvestTime: Long,
        accumulators: MutableMap<String, Accumulator>
    ) {
        when (value) {
            is Number -> {
                val number = value.toDouble()
                if (number.isFinite()) {
                    val key = "$eventCode|$path"
                    val accumulator = accumulators.getOrPut(key) { Accumulator() }
                    accumulator.harvestTimes.add(harvestTime)
                    accumulator.values.add(NumericObservation(inventoryTime, number))
                }
            }
            is JSONArray -> {
                for (index in 0 until minOf(value.length(), 128)) {
                    collectNumeric(value.opt(index), "$path[$index]", eventCode, inventoryTime, harvestTime, accumulators)
                }
            }
        }
    }
}
