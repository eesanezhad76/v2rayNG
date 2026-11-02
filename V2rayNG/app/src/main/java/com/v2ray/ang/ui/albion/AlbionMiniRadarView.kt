package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min

/** Coordinate-only local radar; it does not invent a world-map background. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val range = 60.0

    fun setSnapshot(json: String) {
        entities = try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { add(JSONObject(it.toString())) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.43f
        paint.style = Paint.Style.FILL
        paint.color = 0xE9111B25.toInt()
        canvas.drawCircle(cx, cy, radius + 5f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = 0x665F8295
        for (ring in 1..3) canvas.drawCircle(cx, cy, radius * ring / 3f, paint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)

        val you = entities.filter { it.optString("last_kind") == "own_player_move" }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you == null) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE3EDF5.toInt()
            paint.textSize = 11f * density
            canvas.drawText("Waiting for own position", 8f * density, cy, paint)
            return
        }
        val px = you.optDouble("x")
        val py = you.optDouble("y")
        entities.forEach { entity ->
            val kind = entity.optString("last_kind")
            if (kind !in setOf("static_resource", "harvestable", "resource_state", "mob", "player_move")) return@forEach
            if (entity.optBoolean("depleted", false) || !entity.has("x") || !entity.has("y")) return@forEach
            val dx = entity.optDouble("x") - px
            val dy = entity.optDouble("y") - py
            if (hypot(dx, dy) > range) return@forEach
            val x = cx + (dx / range * radius).toFloat()
            val y = cy - (dy / range * radius).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = when (kind) {
                "mob" -> 0xFFFF6666.toInt()
                "player_move" -> 0xFF6AB8FF.toInt()
                "static_resource" -> 0xFF9AA6AF.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, 3.5f * density, paint)
            val tier = entity.optInt("tier", 0)
            if (tier in 2..8) {
                paint.textSize = 8f * density
                canvas.drawText("T$tier", x + 4f * density, y - 3f * density, paint)
            }
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        val heading = you.optDouble("heading", 0.0).toFloat()
        canvas.save()
        canvas.rotate(heading, cx, cy)
        val arrow = Path().apply {
            moveTo(cx, cy - 8f * density)
            lineTo(cx - 5f * density, cy + 5f * density)
            lineTo(cx, cy + 2f * density)
            lineTo(cx + 5f * density, cy + 5f * density)
            close()
        }
        canvas.drawPath(arrow, paint)
        canvas.restore()
        paint.textSize = 8f * density
        canvas.drawText("RANGE 60", 5f * density, height - 5f * density, paint)
    }
}
