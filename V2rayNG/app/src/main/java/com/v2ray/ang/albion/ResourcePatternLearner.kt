package com.v2ray.ang.albion

/**
 * Conservative, evidence-backed inference for resource fields.
 * Predictions are kept separate from packet facts. This is a deterministic
 * first-stage catalog, not a statistically trained classifier. Confidence values
 * are heuristic evidence strengths, not calibrated probabilities.
 */
object ResourcePatternLearner {
    const val CATALOG_VERSION = "2026-11-03.1"

    data class Observation(
        val family: Int?,
        val tier: Int?,
        val enchantRaw: Int?,
        val mobileTypeId: Int?
    )

    data class FieldInference(
        val value: String?,
        val status: Status,
        val confidence: Double,
        val reason: String
    )

    enum class Status {
        PACKET_CONFIRMED,
        CAPTURE_CORROBORATED,
        PROVISIONAL_MAPPING,
        CONFLICT,
        UNKNOWN
    }

    data class Result(
        val resourceName: FieldInference,
        val mobileTypeId: FieldInference,
        val enchantmentName: FieldInference
    )

    private val oreMobileTypes = mapOf(3 to 571, 4 to 573, 5 to 574)
    private val enchantmentNames = mapOf(0 to "Normal", 1 to "E1", 2 to "E2", 3 to "E3")
    private val learnedEnchantmentNames = LinkedHashMap<String, String>()

    @Synchronized
    fun registerLearnedEnchantment(family: Int, tier: Int, raw: Int, label: String) {
        if (family <= 0 || tier !in 1..8 || raw !in 0..15 || label !in setOf("Normal", "E1", "E2", "E3")) return
        learnedEnchantmentNames["$family|$tier|$raw"] = label
    }

    fun infer(observation: Observation): Result {
        val resource = when (observation.family) {
            29 -> FieldInference(
                value = "Ore",
                status = Status.CAPTURE_CORROBORATED,
                confidence = 0.98,
                reason = "Family 29 is repeatedly observed in independently labelled Ore captures."
            )
            null -> unknown("Family number is absent.")
            else -> unknown("Family " + observation.family + " has no verified semantic name in the current capture set.")
        }

        val expectedMobile = if (observation.family == 29) {
            observation.tier?.let(oreMobileTypes::get)
        } else null
        // -1 is the decoder's observed "unknown/unmapped" sentinel, not a real mobile type.
        val observedMobileType = observation.mobileTypeId?.takeIf { it > 0 }
        val mobile = when {
            observedMobileType != null && expectedMobile != null && observedMobileType != expectedMobile ->
                FieldInference(
                    value = null,
                    status = Status.CONFLICT,
                    confidence = 0.0,
                    reason = "Observed mobile type " + observedMobileType + " conflicts with family 29 / T" + observation.tier + ", for which captures show " + expectedMobile + "."
                )
            observedMobileType != null ->
                FieldInference(
                    value = observedMobileType.toString(),
                    status = Status.PACKET_CONFIRMED,
                    confidence = 1.0,
                    reason = "Mobile type was directly decoded from the packet."
                )
            expectedMobile != null ->
                FieldInference(
                    value = expectedMobile.toString(),
                    status = Status.CAPTURE_CORROBORATED,
                    confidence = 0.95,
                    reason = "Family 29 tier-to-mobile mapping is corroborated across independent captures; this value was inferred, not read from this node's packet."
                )
            else -> unknown("No verified family/tier rule supplies a mobile type.")
        }

        val learnedEnchant = if (observation.family != null && observation.tier != null && observation.enchantRaw != null)
            learnedEnchantmentNames["${observation.family}|${observation.tier}|${observation.enchantRaw}"] else null
        val enchantment = learnedEnchant?.let { name ->
            FieldInference(name, Status.CAPTURE_CORROBORATED, 0.98,
                "Validated from at least three manually labelled gathering samples across two capture sessions.")
        } ?: observation.enchantRaw?.let(enchantmentNames::get)?.let { name ->
            FieldInference(
                value = name,
                status = if (observation.enchantRaw == 0) Status.CAPTURE_CORROBORATED else Status.PROVISIONAL_MAPPING,
                confidence = if (observation.enchantRaw == 0) 0.95 else 0.85,
                reason = "Raw enchantment " + observation.enchantRaw + " has a provisional catalog mapping; manual cross-session validation is pending."
            )
        } ?: unknown(
            if (observation.enchantRaw == null) "Enchantment raw value is absent; it is not inferred from location, tier, or state count."
            else "Raw enchantment " + observation.enchantRaw + " has no validated label."
        )

        return Result(resource, mobile, enchantment)
    }

    private fun unknown(reason: String) = FieldInference(
        value = null,
        status = Status.UNKNOWN,
        confidence = 0.0,
        reason = reason
    )
}
