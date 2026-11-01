package com.v2ray.ang.albion

/** Maps decoded Protocol18 events into conservative radar observations. */
object AlbionEntityEventMapper {
    private val knownNonPlayerEntities = LinkedHashSet<Long>()
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
        val name: String? = null
    )

    fun map(message: AlbionProtocol18Decoder.Message): List<Observation> {
        if (message.kind != "event") return emptyList()
        val p = message.parameters
        val code = message.realCode ?: return emptyList()
        fun number(key: Int) = (p[key] as? Number)?.toLong()
        fun int(key: Int) = number(key)?.toInt()
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
            3 -> {
                val id = number(0) ?: return emptyList()
                if (!knownNonPlayerEntities.contains(id)) return emptyList()
                val xy = if (p[4] is Number && p[5] is Number) (p[4] as Number).toFloat() to (p[5] as Number).toFloat() else null
                listOf(Observation("move", code, id, x = xy?.first, y = xy?.second))
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
            46 -> listOf(Observation("resource_state", code, number(0), size = int(1)))
            123 -> {
                number(0)?.let { knownNonPlayerEntities.add(it) }
                val xy = pair(p[7])
                listOf(Observation(
                    "mob", code, number(0), int(1), null, int(34),
                    xy?.first, xy?.second, name = p[31] as? String
                ))
            }
            else -> emptyList()
        }
    }
}
