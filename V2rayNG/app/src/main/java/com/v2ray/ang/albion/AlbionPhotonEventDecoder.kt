package com.v2ray.ang.albion

/**
 * Normalizes already-decoded Photon event parameters into auditable Albion observations.
 *
 * This is not a raw-IP/ENet/Photon byte-stream decoder. The capture layer must first provide
 * a validated Photon event code and parameter table. Unknown or malformed fields are not guessed.
 * Resource state updates must be joined by exact object ID, never by proximity alone.
 */
object AlbionPhotonEventDecoder {
    const val EVENT_HARVESTABLE_LIST = 39
    const val EVENT_HARVESTABLE_SINGLE = 40
    const val EVENT_HARVESTABLE_STATE = 46
    const val EVENT_LIVING_ENTITY = 123

    data class Resource(
        val id: Long,
        val typeId: Int?,
        val tier: Int?,
        val x: Double?,
        val y: Double?,
        val amount: Int?,
        val enchantmentRaw: Int? = null
    )

    data class LivingEntity(val id: Long, val typeId: Int?, val x: Double?, val y: Double?)
    data class StateUpdate(val id: Long, val amountOrState: Int?, val enchantmentRaw: Int?)
    data class Result(
        val resources: List<Resource> = emptyList(),
        val livingEntities: List<LivingEntity> = emptyList(),
        val stateUpdate: StateUpdate? = null,
        val recognized: Boolean = false
    )

    /** Parameter keys may be Byte or Int, as found in Photon decoders. */
    fun decode(eventCode: Int, parameters: Map<*, *>): Result = when (eventCode) {
        EVENT_HARVESTABLE_LIST -> Result(resources = decodeResourceList(parameters), recognized = true)
        EVENT_HARVESTABLE_SINGLE -> decodeSingleResource(parameters)
        EVENT_HARVESTABLE_STATE -> decodeState(parameters)
        EVENT_LIVING_ENTITY -> decodeLivingEntity(parameters)
        else -> Result()
    }

    private fun decodeResourceList(p: Map<*, *>): List<Resource> {
        val ids = longs(p.value(0)) ?: return emptyList()
        val types = ints(p.value(1))
        val tiers = ints(p.value(2))
        val coords = numbers(p.value(3))
        val amounts = ints(p.value(4))
        return ids.mapIndexed { i, id ->
            Resource(id, types?.getOrNull(i), tiers?.getOrNull(i),
                coords?.getOrNull(i * 2), coords?.getOrNull(i * 2 + 1), amounts?.getOrNull(i))
        }
    }

    private fun decodeSingleResource(p: Map<*, *>): Result {
        // Single-resource object-ID location is protocol-version dependent; accept only explicit IDs.
        val id = p.value(0).longOrNull() ?: return Result(recognized = true)
        val pos = numbers(p.value(8))
        return Result(resources = listOf(Resource(
            id = id, typeId = p.value(5).intOrNull(), tier = p.value(7).intOrNull(),
            x = pos?.getOrNull(0), y = pos?.getOrNull(1),
            amount = p.value(10).intOrNull(), enchantmentRaw = p.value(11).intOrNull()
        )), recognized = true)
    }

    private fun decodeState(p: Map<*, *>): Result {
        val id = p.value(0).longOrNull() ?: return Result(recognized = true)
        return Result(stateUpdate = StateUpdate(id, p.value(1).intOrNull(), p.value(2).intOrNull()), recognized = true)
    }

    private fun decodeLivingEntity(p: Map<*, *>): Result {
        val id = p.value(0).longOrNull() ?: return Result(recognized = true)
        val pos = numbers(p.value(7))
        return Result(livingEntities = listOf(LivingEntity(
            id, p.value(1).intOrNull(), pos?.getOrNull(0), pos?.getOrNull(1)
        )), recognized = true)
    }

    private fun Map<*, *>.value(id: Int): Any? =
        entries.firstOrNull { it.key.intOrNull() == id }?.value

    private fun Any?.intOrNull(): Int? = when (this) {
        is Number -> toInt().takeIf { toDouble() == it.toDouble() }
        else -> null
    }

    private fun Any?.longOrNull(): Long? = when (this) {
        is Number -> toLong().takeIf { toDouble() == it.toDouble() }
        else -> null
    }

    private fun ints(value: Any?): List<Int>? {
        val values = sequence(value).toList()
        val converted = values.map { it.intOrNull() }
        return if (converted.any { it == null }) null else converted.filterNotNull()
    }

    private fun longs(value: Any?): List<Long>? {
        val values = sequence(value).toList()
        val converted = values.map { it.longOrNull() }
        return if (converted.any { it == null }) null else converted.filterNotNull()
    }

    private fun numbers(value: Any?): List<Double>? {
        val values = sequence(value).toList()
        val converted = values.map { (it as? Number)?.toDouble() }
        return if (converted.any { it == null }) null else converted.filterNotNull()
    }

    private fun sequence(value: Any?): Sequence<Any?> = when (value) {
        is Iterable<*> -> value.asSequence()
        is Array<*> -> value.asSequence()
        is ByteArray -> value.asSequence().map { it.toInt() }
        is IntArray -> value.asSequence().map { it }
        is LongArray -> value.asSequence().map { it }
        is FloatArray -> value.asSequence().map { it }
        is DoubleArray -> value.asSequence().map { it }
        else -> emptySequence()
    }
}
