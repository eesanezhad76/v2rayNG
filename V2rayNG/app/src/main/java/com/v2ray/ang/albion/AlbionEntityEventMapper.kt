package com.v2ray.ang.albion

/**
 * Converts the best-understood Albion events into typed observations.
 * Unknown events are deliberately left to the event inventory/log rather than
 * being guessed as entities.
 */
object AlbionEntityEventMapper {
    private val knownNonPlayerEntities = LinkedHashSet<Long>()
    private val knownPlayerEntities = LinkedHashMap<Long, String?>()
    fun reset() { knownNonPlayerEntities.clear(); knownPlayerEntities.clear() }

    data class Observation(
        val kind: String,
        val eventCode: Int,
        val entityId: Long? = null,
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
        val secondaryValue: Float? = null
    )

    fun map(message: AlbionProtocol18Decoder.Message): List<Observation> {
        if (message.kind != "event") return emptyList()
        val p = message.parameters
        val code = message.realCode ?: return emptyList()
        fun number(key: Int) = (p[key] as? Number)?.toLong()
        fun int(key: Int) = number(key)?.toInt()
        fun float(key: Int) = (p[key] as? Number)?.toFloat()
        fun pair(value: Any?): Pair<Float, Float>? = when (value) {
            is FloatArray -> if (value.size >= 2) value[0] to value[1] else null
            is DoubleArray -> if (value.size >= 2) value[0].toFloat() to value[1].toFloat() else null
            is List<*> -> if (value.size >= 2 && value[0] is Number && value[1] is Number)
                (value[0] as Number).toFloat() to (value[1] as Number).toFloat() else null
            else -> null
        }
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
                listOf(Observation("health", code, entityId = id, value = float(2), secondaryValue = float(3)))
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
                val positions = p[3]
                val flat = when (positions) {
                    is FloatArray -> positions
                    is DoubleArray -> FloatArray(positions.size) { positions[it].toFloat() }
                    is List<*> -> positions.mapNotNull { (it as? Number)?.toFloat() }.toFloatArray()
                    else -> floatArrayOf()
                }
                knownNonPlayerEntities.addAll(ids)
                ids.mapIndexed { i, id ->
                    val at = i * 2
                    Observation("static_resource", code, id, x = flat.getOrNull(at), y = flat.getOrNull(at + 1))
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
                val depleted = !p.containsKey(1) || p[1] == null
                if (depleted) knownNonPlayerEntities.remove(id)
                listOf(Observation("resource_state", code, id, size = int(1), removed = depleted))
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
                val id = number(6) ?: number(0) ?: return emptyList()
                listOf(Observation("health_regeneration", code, entityId = id, value = float(2), secondaryValue = float(3)))
            }
            92 -> {
                val id = number(6) ?: number(0) ?: return emptyList()
                listOf(Observation("energy_regeneration", code, entityId = id, value = float(2), secondaryValue = float(3)))
            }
            123 -> {
                val id = number(0) ?: return emptyList()
                knownNonPlayerEntities.add(id)
                val xy = pair(p[7])
                listOf(Observation(
                    kind = "mob", eventCode = code, entityId = id,
                    typeId = int(1), enchant = int(34),
                    x = xy?.first, y = xy?.second, name = p[31] as? String
                ))
            }
            211 -> {
                val id = number(0) ?: return emptyList()
                listOf(Observation("player_mounted", code, entityId = id, value = if (p[1] == true) 1f else 0f))
            }
            325 -> {
                val xy = pair(p[1])
                listOf(Observation("dungeon_exit", code, x = xy?.first, y = xy?.second,
                    name = p[3] as? String, enchant = int(9)))
            }
            else -> emptyList()
        }
    }
}
