package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Coordinate-based navigation radar; terrain and roads are never fabricated. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private var targetTier: Int = 4
    private var targetEnchant: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val range = 60.0

    fun setTargetFilter(tier: Int, enchant: Int) {
        targetTier = tier.coerceIn(1, 8)
        targetEnchant = enchant.coerceIn(0, 4)
        invalidate()
    }

    /** Returns the number of coordinate records installed, or null for a malformed snapshot. */
    fun setSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json)
        if (parsed == null) {
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

    private fun chooseTarget(player: JSONObject?): JSONObject? {
        if (player == null || !validPoint(player)) return null
        val px = player.optDouble("x")
        val py = player.optDouble("y")
        return entities.asSequence()
            .filter { it.optString("last_kind") in setOf("harvestable", "resource_state") }
            .filter { it.optString("classification") == "typed_resource_observation" }
            .filter { it.optString("evidence_grade") != "conflicting_typed_observation" }
            .filter { !it.optBoolean("depleted", false) && validPoint(it) }
            .filter { it.optInt("tier", -1) == targetTier && it.optInt("enchant", 0) == targetEnchant }
            .minByOrNull { hypot(it.optDouble("x") - px, it.optDouble("y") - py) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.39f
        paint.style = Paint.Style.FILL
        paint.color = 0xE9111B25.toInt()
        canvas.drawRoundRect(2f, 2f, width - 2f, height - 2f, 9f * density, 9f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = 0x665F8295
        for (ring in 1..3) canvas.drawCircle(cx, cy + 5f * density, radius * ring / 3f, paint)
        canvas.drawLine(cx - radius, cy + 5f * density, cx + radius, cy + 5f * density, paint)
        canvas.drawLine(cx, cy + 5f * density - radius, cx, cy + 5f * density + radius, paint)

        val you = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you == null) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE3EDF5.toInt()
            paint.textSize = 10f * density
            canvas.drawText("Waiting for your position (move in game)", 8f * density, cy, paint)
            return
        }
        val px = you.optDouble("x")
        val py = you.optDouble("y")
        val target = chooseTarget(you)

        entities.forEach { entity ->
            val kind = entity.optString("last_kind")
            if (kind !in setOf("static_resource", "harvestable", "resource_state", "mob", "player_move")) return@forEach
            if (entity.optBoolean("depleted", false) || !validPoint(entity)) return@forEach
            val dx = entity.optDouble("x") - px
            val dy = entity.optDouble("y") - py
            if (hypot(dx, dy) > range) return@forEach
            val x = cx + (dx / range * radius).toFloat()
            val y = cy + 5f * density - (dy / range * radius).toFloat()
            val ambiguousResource = entity.optString("classification") == "ambiguous_resource_candidate" ||
                entity.optString("evidence_grade") == "conflicting_typed_observation"
            val candidateOnly = kind == "static_resource" || ambiguousResource
            paint.style = Paint.Style.FILL
            paint.color = when {
                kind == "mob" -> 0xFFFF6666.toInt()
                kind == "player_move" -> 0xFF6AB8FF.toInt()
                candidateOnly -> 0xFF9AA6AF.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, 3.5f * density, paint)
            if (candidateOnly) {
                paint.textSize = 8f * density
                canvas.drawText("?", x + 4f * density, y - 3f * density, paint)
            }
            val tier = entity.optInt("tier", 0)
            if (tier in 2..8) {
                paint.textSize = 8f * density
                canvas.drawText("T$tier", x + 4f * density, y - 3f * density, paint)
            }
        }

        if (target != null) {
            val dx = target.optDouble("x") - px
            val dy = target.optDouble("y") - py
            val distance = hypot(dx, dy)
            val scale = if (distance > range) range / distance else 1.0
            val tx = cx + (dx * scale / range * radius).toFloat()
            val ty = cy + 5f * density - (dy * scale / range * radius).toFloat()
            paint.color = 0xFFFFD45C.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            canvas.drawLine(cx, cy + 5f * density, tx, ty, paint)
            canvas.drawCircle(tx, ty, 8f * density, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 10f * density
            val family = target.optInt("family", -1)
            val type = target.optInt("mobile_type_id", -1)
            val code = if (family >= 0) "F$family" else if (type >= 0) "ID$type" else "RESOURCE"
            canvas.drawText("$code  T$targetTier E$targetEnchant  ${distance.roundToInt()}m",
                7f * density, 15f * density, paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE3EDF5.toInt()
            paint.textSize = 9f * density
            canvas.drawText("No typed T$targetTier E$targetEnchant resource nearby",
                7f * density, 15f * density, paint)
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF56D6A0.toInt()
        canvas.drawCircle(cx, cy + 5f * density, 5f * density, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = 8f * density
        canvas.drawText("YOU", cx + 7f * density, cy + 8f * density, paint)
        paint.color = 0xFFB8C8D5.toInt()
        canvas.drawText("RANGE 60  •  relative coordinates", 7f * density, height - 6f * density, paint)
    }
}
