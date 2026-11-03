package com.v2ray.ang.albion

/**
 * Conservative, evidence-backed inference for Event 40 resource fields.
 *
 * The wire type number is retained as "family" in the live store for backwards compatibility.
 * Static resource type numbers follow the reference HarvestablesDatabase ranges. For living
 * resources, a known harvestable mobile TypeID takes precedence over the static type number.
 * Non-harvestable mobile IDs (for example Mountain Rabbit 482) do not override the static
 * resource type number. Predictions remain separate from packet facts.
 */
object ResourcePatternLearner {
    const val CATALOG_VERSION = "2026-11-03.2"

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

    private data class LivingResource(val type: String, val tier: Int)

    // Verified harvestable mobile TypeIDs in the current 12-ZIP/30-PCAP corpus.
    // All 64 observed mobile TypeIDs were resolved against the game mob dictionary;
    // only these three are marked is_harvestable=true.
    private val harvestableMobileResources = mapOf(
        571 to LivingResource("Ore", 3),
        573 to LivingResource("Ore", 4),
        574 to LivingResource("Ore", 5)
    )
    private val oreMobileTypes = mapOf(3 to 571, 4 to 573, 5 to 574)
    private val enchantmentNames = mapOf(0 to "Normal", 1 to "E1", 2 to "E2", 3 to "E3")
    private val learnedEnchantmentNames = LinkedHashMap<String, String>()

    @Synchronized
    fun registerLearnedEnchantment(family: Int, tier: Int, raw: Int, label: String) {
        if (family <= 0 || tier !in 1..8 || raw !in 0..15 || label !in setOf("Normal", "E1", "E2", "E3")) return
        learnedEnchantmentNames[family.toString() + "|" + tier + "|" + raw] = label
    }

    fun infer(observation: Observation): Result {
        val observedMobileType = observation.mobileTypeId?.takeIf { it > 0 }
        val observedLivingResource = observedMobileType?.let(harvestableMobileResources::get)
        val staticResource = staticResourceType(observation.family)

        val resource = when {
            observedLivingResource != null -> FieldInference(
                value = observedLivingResource.type,
                status = Status.PACKET_CONFIRMED,
                confidence = 1.0,
                reason = "Mobile TypeID " + observedMobileType + " is directly decoded and independently catalogued as a harvestable " + observedLivingResource.type + " resource creature."
            )
            observation.family == 29 -> FieldInference(
                value = "Ore",
                status = Status.CAPTURE_CORROBORATED,
                confidence = 0.98,
                reason = "Type number 29 is repeatedly observed in independently labelled Ore captures and resolves to Ore Elemental TypeIDs 571/573/574."
            )
            staticResource != null -> FieldInference(
                value = staticResource,
                status = Status.PROVISIONAL_MAPPING,
                confidence = 0.90,
                reason = "Event 40 type number " + observation.family + " maps to " + staticResource + " in the reference HarvestablesDatabase range table."
            )
            observation.family == null -> unknown("Type number is absent.")
            else -> unknown("Type number " + observation.family + " is outside the verified static-resource ranges and has no confirmed living-resource mapping.")
        }

        val expectedMobile = if (observation.family == 29) {
            observation.tier?.let(oreMobileTypes::get)
        } else null
        val mobile = when {
            expectedMobile != null && observedMobileType != null && observedMobileType != expectedMobile ->
                FieldInference(
                    value = null,
                    status = Status.CONFLICT,
                    confidence = 0.0,
                    reason = "Observed mobile type " + observedMobileType + " conflicts with type number 29 / T" + observation.tier + ", for which captures show " + expectedMobile + "."
                )
            observedLivingResource != null ->
                FieldInference(
                    value = observedMobileType.toString(),
                    status = Status.PACKET_CONFIRMED,
                    confidence = 1.0,
                    reason = "Mobile TypeID was directly decoded and the game dictionary marks it as a harvestable resource creature."
                )
            expectedMobile != null ->
                FieldInference(
                    value = expectedMobile.toString(),
                    status = Status.CAPTURE_CORROBORATED,
                    confidence = 0.95,
                    reason = "Type number 29 tier-to-mobile mapping is corroborated across independent captures; this value was inferred, not read from this node's packet."
                )
            observedMobileType != null ->
                unknown("Mobile TypeID " + observedMobileType + " is not in the verified harvestable-mobile set; retain it as a packet fact but use the static type-number mapping for resource classification.")
            else -> unknown("No verified family/tier rule supplies a harvestable mobile type.")
        }

        val learnedKey = if (observation.family != null && observation.tier != null && observation.enchantRaw != null)
            observation.family.toString() + "|" + observation.tier + "|" + observation.enchantRaw else null
        val learnedEnchant = learnedKey?.let(learnedEnchantmentNames::get)
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

    private fun staticResourceType(typeNumber: Int?): String? = when {
        typeNumber == null -> null
        typeNumber in 0..5 -> "Wood"
        typeNumber in 6..10 -> "Rock"
        typeNumber in 11..15 -> "Fiber"
        typeNumber in 16..22 -> "Hide"
        typeNumber in 23..27 -> "Ore"
        else -> null
    }

    private fun unknown(reason: String) = FieldInference(
        value = null,
        status = Status.UNKNOWN,
        confidence = 0.0,
        reason = reason
    )
}
