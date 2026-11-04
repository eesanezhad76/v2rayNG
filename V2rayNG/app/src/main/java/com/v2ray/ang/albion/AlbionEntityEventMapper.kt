package com.v2ray.ang.albion

/**
 * Converts the best-understood Albion events into typed observations.
 * Unknown events are deliberately left to the event inventory/log rather than
 * being guessed as entities.
 */
object AlbionEntityEventMapper {
    private const val OP_JOIN = 2
    private const val OP_CHANGE_CLUSTER = 41
    private val knownNonPlayerEntities = LinkedHashSet<Long>()
    private val knownPlayerEntities = LinkedHashMap<Long, String?>()
    fun reset() { knownNonPlayerEntities.clear(); knownPlayerEntities.clear() }

    data class Observation(
        val kind: String,
        val eventCode: Int,
        val entityId: Long? = null,
        val sourceEntityId: Long? = null,
        val typeId: Int? = null,
        val typeNumber: Int? = null,
        val tier: Int? = null,
        val enchant: Int? = null,
        val x: Float? = null,
        val y: Float? = null,
        val size: Int? = null,
        val name: String? = null,
        val removed: Boolean = false,
        val value: Float? = null,
        val secondaryValue: Float? = null,
        val heading: Float? = null,
        val speed: Float? = null,
        val targetX: Float? = null,
        val targetY: Float? = null,
        val regionId: String? = null,
        val candidateEntityIds: List<Long> = emptyList()
    )

    fun map(message: AlbionProtocol18Decoder.Message): List<Observation> {
        val p = message.parameters
        if (message.kind == "response") {
            val operation = message.operationCode ?: message.realCode ?: return emptyList()
            val regionId = when (operation) {
                OP_CHANGE_CLUSTER -> p[0] as? String
                OP_JOIN -> p[8] as? String
                else -> null
            }?.takeIf { it.isNotBlank() } ?: return emptyList()
            return listOf(Observation(kind = "region_change", eventCode = operation, regionId = regionId))
        }
        if (message.kind != "event" && message.kind != "request") return emptyList()
        val code = message.realCode ?: return emptyList()
        fun number(key: Int) = (p[key] as? Number)?.toLong()
        fun int(key: Int) = number(key)?.toInt()
        fun float(key: Int) = (p[key] as? Number)?.toFloat()
        fun floatList(value: Any?): List<Float> = when (value) {
            is FloatArray -> value.toList()
            is DoubleArray -> value.map { it.toFloat() }
            is IntArray -> value.map { it.toFloat() }
            is LongArray -> value.map { it.toFloat() }
            is ShortArray -> value.map { it.toFloat() }
            is ByteArray -> value.map { it.toFloat() }
            is List<*> -> value.mapNotNull { (it as? Number)?.toFloat() }
            else -> emptyList()
        }
        fun pair(value: Any?): Pair<Float, Float>? {
            val values = floatList(value)
            return if (values.size >= 2) values[0] to values[1] else null
        }
        fun intList(value: Any?): List<Int> = when (value) {
            is ShortArray -> value.map { it.toInt() }
            is IntArray -> value.toList()
            is LongArray -> value.map { it.toInt() }
            is FloatArray -> value.map { it.toInt() }
            is DoubleArray -> value.map { it.toInt() }
            is List<*> -> value.mapNotNull { (it as? Number)?.toInt() }
            else -> emptyList()
        }
        if (message.kind == "request") {
            if (code == 23) {
                // Attack request: keep numeric candidates, but the store will accept
                // only a unique value that exactly matches a currently known mob ID.
                val candidates = p.values.flatMap { value ->
                    // Keep only integral values. The store later intersects these with
                    // exact, currently-known mob runtime IDs; unrelated parameters are
                    // therefore not accepted as targets, while Int/Short ID encodings
                    // are no longer silently discarded.
                    when (value) {
                        is Byte -> listOf(value.toLong())
                        is Short -> listOf(value.toLong())
                        is Int -> listOf(value.toLong())
                        is Long -> listOf(value)
                        is ShortArray -> value.map { it.toLong() }
                        is IntArray -> value.map { it.toLong() }
                        is LongArray -> value.toList()
                        is List<*> -> value.mapNotNull { item ->
                            when (item) {
                                is Byte -> item.toLong()
                                is Short -> item.toLong()
                                is Int -> item.toLong()
                                is Long -> item
                                else -> null
                            }
                        }
                        else -> emptyList()
                    }
                }.filter { it > 0L }.distinct()
                return listOf(Observation(
                    kind = "target_request", eventCode = code,
                    candidateEntityIds = candidates
                ))
            }
            if (code != 22) return emptyList()
            val position = pair(p[1]) ?: return emptyList()
            val target = pair(p[3])
            val x = position.first
            val y = position.second
            if (!x.isFinite() || !y.isFinite() ||
                kotlin.math.abs(x) > 10000f || kotlin.math.abs(y) > 10000f) return emptyList()
            val heading = float(2)?.takeIf { it.isFinite() && it >= 0f && it <= 360f }
            val speed = float(4)?.takeIf { it.isFinite() && it >= 0f && it <= 100f }
            return listOf(Observation(
                kind = "own_player_move", eventCode = code, entityId = -1L,
                x = x, y = y, name = "YOU", heading = heading, speed = speed,
                targetX = target?.first?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 10000f },
                targetY = target?.second?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 10000f }
            ))
        }
        if (message.kind != "event") return emptyList()
        fun idList(value: Any?): List<Long> = when (value) {
            is ShortArray -> value.map { it.toLong() }
            is IntArray -> value.map { it.toLong() }
            is LongArray -> value.toList()
            is List<*> -> value.mapNotNull { (it as? Number)?.toLong() }
            else -> emptyList()
        }
        return when (code) {
            1 -> {
                val id = number(0) ?: return emptyList()
                knownNonPlayerEntities.remove(id)
                knownPlayerEntities.remove(id)
                listOf(Observation("leave", code, entityId = id, removed = true))
            }
            3 -> {
                val id = number(0) ?: return emptyList()
                val xy = if (p[4] is Number && p[5] is Number)
                    (p[4] as Number).toFloat() to (p[5] as Number).toFloat() else null
                if (knownPlayerEntities.containsKey(id)) {
                    listOf(Observation("player_move", code, id, x = xy?.first, y = xy?.second, name = knownPlayerEntities[id]))
                } else if (knownNonPlayerEntities.contains(id)) {
                    listOf(Observation("move", code, id, x = xy?.first, y = xy?.second))
                } else emptyList()
            }
            6 -> {
                val id = number(0) ?: number(6) ?: return emptyList()
                listOf(Observation("health", code, entityId = id, sourceEntityId = number(6),
                    value = float(2), secondaryValue = float(3)))
            }
            7 -> {
                // HealthUpdates is batched for one entity (key 0). Key 6 contains
                // the source/attacker IDs for the aligned health-change samples,
                // not the entity whose health changed.
                val targetId = number(0) ?: return emptyList()
                val sourceIds = idList(p[6])
                val deltas = floatList(p[2])
                val resultingHealth = floatList(p[3])
                val count = maxOf(sourceIds.size, deltas.size, resultingHealth.size)
                if (count == 0) return emptyList()
                List(count) { index ->
                    Observation(
                        "health", code, entityId = targetId,
                        sourceEntityId = sourceIds.getOrNull(index),
                        value = deltas.getOrNull(index),
                        secondaryValue = resultingHealth.getOrNull(index)
                    )
                }
            }
            8 -> {
                val id = number(0) ?: number(6) ?: return emptyList()
                listOf(Observation("energy", code, entityId = id, value = float(2), secondaryValue = float(3)))
            }
            29 -> {
                val id = number(0) ?: return emptyList()
                val playerName = p[1] as? String
                knownPlayerEntities[id] = playerName
                listOf(Observation("player_spawn", code, entityId = id, name = playerName))
            }
            39 -> {
                val ids = idList(p[0])
                // Batch spawn arrays are aligned: family, tier, flattened XY, remaining amount.
                val types = intList(p[1])
                val tiers = intList(p[2])
                val flat = floatList(p[3]).toFloatArray()
                val amounts = intList(p[4])
                knownNonPlayerEntities.addAll(ids)
                ids.mapIndexed { i, id ->
                    val at = i * 2
                    Observation(
                        "static_resource", code, id,
                        typeNumber = types.getOrNull(i), tier = tiers.getOrNull(i),
                        x = flat.getOrNull(at), y = flat.getOrNull(at + 1),
                        size = amounts.getOrNull(i)
                    )
                }
            }
            40 -> {
                number(0)?.let { knownNonPlayerEntities.add(it) }
                val xy = pair(p[8])
                listOf(Observation(
                    kind = "harvestable", eventCode = code, entityId = number(0),
                    typeId = int(6), typeNumber = int(5), tier = int(7), enchant = int(11),
                    x = xy?.first, y = xy?.second, size = int(10)
                ))
            }
            46 -> {
                val id = number(0) ?: return emptyList()
                val remaining = (p[1] as? Number)?.toInt()
                // A zero remaining amount is the terminal point of the observed
                // 5→4→3→2→1→0 harvest sequence. Keep the entity as a grey historical
                // record; only exclude it from active navigation. Missing amount is
                // also treated as depleted by the upstream lifecycle.
                val depleted = remaining == null || remaining <= 0
                if (depleted) knownNonPlayerEntities.remove(id)
                listOf(Observation(
                    "resource_state", code, id, size = remaining,
                    enchant = int(2), removed = depleted
                ))
            }
            47 -> {
                val id = number(0) ?: return emptyList()
                listOf(Observation("mob_state", code, entityId = id, value = float(1)))
            }
            90 -> {
                val id = number(0) ?: return emptyList()
                listOf(Observation("player_equipment", code, entityId = id))
            }
            91 -> {
                val id = number(0) ?: number(6) ?: return emptyList()
                listOf(Observation("health_regeneration", code, entityId = id, value = float(2), secondaryValue = float(3)))
            }
            92 -> {
                val id = number(0) ?: number(6) ?: return emptyList()
                listOf(Observation("energy_regeneration", code, entityId = id, value = float(2), secondaryValue = float(3)))
            }
            521 -> {
                // Current upstream MistsPlayerJoinedInfo: confirmed transition at p3,
                // destination map/cluster identity at p2.
                val newRegion = p[2] as? String
                if (p[3] == true && !newRegion.isNullOrBlank()) {
                    listOf(Observation(kind = "region_change", eventCode = code, regionId = newRegion))
                } else emptyList()
            }
            123 -> {
                val id = number(0) ?: return emptyList()
                knownNonPlayerEntities.add(id)
                val xy = pair(p[7])
                listOf(Observation(
                    kind = "mob", eventCode = code, entityId = id,
                    typeId = int(1), enchant = int(34),
                    x = xy?.first, y = xy?.second, name = p[33] as? String
                ))
            }
            210 -> {
                val id = number(0) ?: return emptyList()
                // Preserve unknown payloads as unknown; a missing/non-boolean flag must
                // never be mistaken for a confirmed dismount.
                val mounted = when (val raw = p[1]) {
                    true -> 1f
                    false -> 0f
                    is Number -> raw.toInt().takeIf { it == 0 || it == 1 }?.toFloat()
                    else -> null
                }
                listOf(Observation("mount_state_candidate", code, entityId = id, value = mounted))
            }
            324 -> {
                val xy = pair(p[1])
                listOf(Observation("dungeon_exit", code, x = xy?.first, y = xy?.second,
                    name = p[3] as? String, enchant = int(9)))
            }
            else -> emptyList()
        }
    }
}
