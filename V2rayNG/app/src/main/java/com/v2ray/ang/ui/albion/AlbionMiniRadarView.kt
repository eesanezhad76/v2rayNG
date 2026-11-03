package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Compact coordinate radar. Off-range entities are projected to the rim with distance labels. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private var enabledFilters: Set<String> = DEFAULT_FILTERS
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val range = 90.0

    fun setFilters(filters: Set<String>) {
        enabledFilters = filters.toSet()
        invalidate()
    }

    fun setSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: run {
            entities = emptyList()
            invalidate()
            return null
        }
        entities = parsed.entities
        invalidate()
        return entities.size
    }

    private fun validPoint(item: JSONObject): Boolean =
        item.has("x") && item.has("y") &&
            item.optDouble("x").isFinite() && item.optDouble("y").isFinite() &&
            kotlin.math.abs(item.optDouble("x")) <= 10000.0 &&
            kotlin.math.abs(item.optDouble("y")) <= 10000.0

    private fun isTroll(item: JSONObject): Boolean {
        val label = (item.optString("name") + " " + AlbionRadarSnapshotParser.labelForEntity(item)).lowercase()
        return label.contains("troll") || item.optString("marker_activity") == "Troll" ||
            item.optString("marker_activity_candidate") == "Troll"
    }

    private fun passesFilters(item: JSONObject): Boolean {
        val kind = item.optString("last_kind")
        val node = kind in RESOURCE_KINDS
        val mob = kind == "mob"
        val troll = mob && isTroll(item)
        val enchant = item.optInt("enchant", item.optInt("marker_enchant", 0))
        val categoryEnabled = when {
            node && enchant > 0 -> "enchanted_nodes" in enabledFilters
            node -> "normal_nodes" in enabledFilters
            troll && enchant > 0 -> "enchanted_trolls" in enabledFilters
            troll -> "trolls" in enabledFilters
            mob -> "mobs" in enabledFilters
            else -> false
        }
        if (!categoryEnabled) return false
        val tierFilters = enabledFilters.filter { it.startsWith("tier_") }
            .mapNotNull { it.removePrefix("tier_").toIntOrNull() }.toSet()
        if (tierFilters.isNotEmpty() && item.optInt("tier", item.optInt("marker_tier", -1)) !in tierFilters) return false
        val enchantFilters = enabledFilters.filter { it.startsWith("enchant_") }
            .mapNotNull { it.removePrefix("enchant_").toIntOrNull() }.toSet()
        if (enchantFilters.isNotEmpty() && enchant !in enchantFilters) return false
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) * 0.39f).coerceAtLeast(12f * density)
        paint.style = Paint.Style.FILL
        paint.color = 0xE9111B25.toInt()
        canvas.drawRoundRect(2f, 2f, width - 2f, height - 2f, 8f * density, 8f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = 0x665F8295
        for (ring in 1..3) canvas.drawCircle(cx, cy, radius * ring / 3f, paint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)

        val you = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you == null) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE3EDF5.toInt()
            paint.textSize = 9f * density
            canvas.drawText("Waiting for live position", 8f * density, cy, paint)
            drawCatalogHint(canvas)
            return
        }
        val px = you.optDouble("x")
        val py = you.optDouble("y")
        val history = you.optJSONArray("movement_history")
        if (history != null && history.length() > 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * density
            paint.color = 0xCC56D6A0.toInt()
            var oldX: Float? = null
            var oldY: Float? = null
            for (i in 0 until history.length()) {
                val point = history.optJSONObject(i) ?: continue
                val dx = point.optDouble("x") - px
                val dy = point.optDouble("y") - py
                if (!dx.isFinite() || !dy.isFinite() || hypot(dx, dy) > range) {
                    oldX = null; oldY = null; continue
                }
                val x = cx + (dx / range * radius).toFloat()
                val y = cy - (dy / range * radius).toFloat()
                if (oldX != null && oldY != null) canvas.drawLine(oldX, oldY, x, y, paint)
                oldX = x; oldY = y
            }
        }

        val visible = entities.asSequence()
            .filter { it.optString("last_kind") in DISPLAY_KINDS && validPoint(it) && passesFilters(it) }
            .toList()
        val outside = ArrayList<Pair<JSONObject, Double>>()
        visible.forEach { entity ->
            val dx = entity.optDouble("x") - px
            val dy = entity.optDouble("y") - py
            val distance = hypot(dx, dy)
            val factor = if (distance > range) range / distance else 1.0
            val x = cx + (dx * factor / range * radius).toFloat()
            val y = cy - (dy * factor / range * radius).toFloat()
            val kind = entity.optString("last_kind")
            val depleted = entity.optBoolean("depleted", false)
            val candidate = kind == "static_resource" ||
                entity.optString("classification") == "ambiguous_resource_candidate" ||
                entity.optString("evidence_grade") == "conflicting_typed_observation"
            paint.style = Paint.Style.FILL
            paint.color = when {
                depleted || entity.optBoolean("left", false) -> 0xFF697782.toInt()
                kind == "mob" -> if (isTroll(entity)) 0xFFFF9A55.toInt() else 0xFFFF6666.toInt()
                candidate -> 0xFF9AA6AF.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, (if (distance > range) 2.5f else 3.2f) * density, paint)
            if (kind == "mob" && distance <= range) {
                paint.style = Paint.Style.FILL
                paint.textSize = 7f * density
                paint.color = 0xFFFFD5D5.toInt()
                val mobName = AlbionRadarSnapshotParser.labelForEntity(entity).take(14)
                val hp = if (entity.has("health_value")) " ${entity.optDouble("health_value").roundToInt()}HP" else ""
                canvas.drawText(mobName + hp, x + 5f * density, y + 9f * density, paint)
            }
            if (entity.optBoolean("user_confirmed_target", false)) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = 0xFF55E6FF.toInt()
                canvas.drawCircle(x, y, 7f * density, paint)
            }
            if (distance > range) outside.add(entity to distance)
        }

        val nearestOutside = outside.sortedBy { it.second }.take(4)
        paint.style = Paint.Style.FILL
        paint.textSize = 7.5f * density
        nearestOutside.forEachIndexed { index, pair ->
            val entity = pair.first
            val dx = entity.optDouble("x") - px
            val dy = entity.optDouble("y") - py
            val distance = pair.second
            val factor = range / distance
            val x = cx + (dx * factor / range * radius).toFloat()
            val y = cy - (dy * factor / range * radius).toFloat()
            paint.color = if (entity.optBoolean("user_confirmed_target", false)) 0xFF55E6FF.toInt() else 0xFFEAF1F5.toInt()
            val label = if (entity.optBoolean("user_confirmed_target", false)) "T5.3" else
                "${AlbionRadarSnapshotParser.labelForEntity(entity)} ${distance.roundToInt()}"
            canvas.drawText(label.take(20), (x + 4f * density).coerceAtMost(width - 60f * density),
                (y - index * 2f * density).coerceIn(12f * density, height - 12f * density), paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF56D6A0.toInt()
        canvas.drawCircle(cx, cy, 4.5f * density, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 7f * density
        canvas.drawText("YOU", cx + 6f * density, cy - 5f * density, paint)
        paint.color = 0xFFB8C8D5.toInt()
        paint.textSize = 7f * density
        canvas.drawText("${visible.size} pins  •  ${outside.size} beyond range", 6f * density, height - 5f * density, paint)
    }

    private fun drawCatalogHint(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB8C8D5.toInt()
        paint.textSize = 7f * density
        val count = entities.count { it.optString("last_kind") == "static_resource" }
        canvas.drawText("Saved catalog: $count nodes • move to locate", 6f * density, height - 6f * density, paint)
    }

    companion object {
        val DEFAULT_FILTERS = setOf("normal_nodes", "enchanted_nodes", "mobs", "trolls", "enchanted_trolls")
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob")
    }
}
