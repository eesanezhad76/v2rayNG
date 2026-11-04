package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Transparent circular resource radar with fixed world-north orientation. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private var catalogEntities: List<JSONObject> = emptyList()
    private var enabledFilters: Set<String> = DEFAULT_FILTERS
    private var catalogOverviewMode = false
    private var lastLiveSnapshotJson: String? = null
    // Filtering is independent of player position. Cache it between snapshots/filter changes
    // so onDraw does not repeatedly walk and classify every entity on each invalidation.
    private var visibleCache: List<JSONObject> = emptyList()
    private var catalogVisibleCache: List<JSONObject> = emptyList()
    private var filterCacheDirty = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val minimumRange = 260.0
    // Fixed 90-degree coordinate correction: player movement must not rotate the radar.

    fun setCatalogOverviewMode(enabled: Boolean) {
        catalogOverviewMode = enabled
        invalidate()
    }

    fun setFilters(filters: Set<String>) {
        enabledFilters = filters.toSet()
        filterCacheDirty = true
        invalidate()
    }

    fun setSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: run {
            entities = emptyList()
            filterCacheDirty = true
            invalidate()
            return null
        }
        entities = parsed.entities
        filterCacheDirty = true
        invalidate()
        return entities.size
    }

    /** Parse static catalog once; live refreshes only reparse the current-session snapshot. */
    fun setCatalogSnapshot(json: String): Int? {
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: return null
        lastLiveSnapshotJson = null
        catalogEntities = parsed.entities
        entities = catalogEntities
        filterCacheDirty = true
        invalidate()
        return catalogEntities.size
    }

    fun setLiveSnapshot(json: String): Int? {
        // The daemon store returns the same cached snapshot string until an observation
        // changes. Skip JSON parsing and UI invalidation when the snapshot is unchanged.
        if (json == lastLiveSnapshotJson) return entities.size
        // Snapshot JSON parsing can be expensive on large sessions; callers may invoke this
        // from the capture worker. Only publish the immutable parsed list on the UI thread.
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: return null
        lastLiveSnapshotJson = json
        val acceptedCount = parsed.entities.size
        post {
            // Live coordinates belong to the active capture. Never merge historical catalog
            // coordinates into the player-centered map until zone identity is verified.
            entities = parsed.entities
            filterCacheDirty = true
            invalidate()
        }
        return acceptedCount
    }

    private fun entityMergeKey(item: JSONObject): String {
        val kind = if (item.optString("last_kind") in RESOURCE_KINDS) "resource" else item.optString("last_kind")
        val x = (item.optDouble("x") * 2.0).roundToInt()
        val y = (item.optDouble("y") * 2.0).roundToInt()
        return listOf(kind, item.optInt("family", -1), item.optInt("tier", -1),
            item.optInt("enchant", -1), x, y).joinToString(":")
    }

    private fun validPoint(item: JSONObject): Boolean =
        item.has("x") && item.has("y") &&
            item.optDouble("x").isFinite() && item.optDouble("y").isFinite() &&
            kotlin.math.abs(item.optDouble("x")) <= 10000.0 &&
            kotlin.math.abs(item.optDouble("y")) <= 10000.0

    /** Depleted and departed resource entities are retained in the knowledge store,
     * but are not rendered or offered as navigation targets. A fresh positive
     * harvestable amount clears depletion in the live store and makes the node visible again.
     */
    private fun isUnavailableResource(item: JSONObject): Boolean =
        item.optString("last_kind") in RESOURCE_KINDS &&
            (item.optBoolean("depleted", false) || item.optBoolean("left", false))

    private fun enchantmentOf(item: JSONObject): Int {
        val marker = item.optInt("marker_enchant", -1)
        if (marker in 0..3) return marker
        val inferred = item.optJSONObject("resource_inference")?.optJSONObject("enchantment_name")?.optString("value")
        return when (inferred) {
            "Normal" -> 0
            "E1" -> 1
            "E2" -> 2
            "E3" -> 3
            else -> item.optInt("enchant", -1).takeIf { it in 0..3 } ?: -1
        }
    }

    private fun resourceColor(item: JSONObject): Int = colorForEnchantment(enchantmentOf(item))

    private fun isTroll(item: JSONObject): Boolean {
        val label = (item.optString("name") + " " + AlbionRadarSnapshotParser.labelForEntity(item)).lowercase()
        if (label.contains("troll")) return true
        // A nearby/manual marker candidate is not proof of mob identity. Only promote
        // a marker match when the capture pipeline has an explicit high-confidence result.
        val marker = item.optString("marker_activity")
        val confidence = item.optString("marker_match_confidence").uppercase()
        return marker.equals("Troll", ignoreCase = true) &&
            confidence in setOf("HIGH", "CONFIRMED", "USER_CONFIRMED")
    }

    private fun passesFilters(item: JSONObject): Boolean {
        val kind = item.optString("last_kind")
        val node = kind in RESOURCE_KINDS
        val mob = kind == "mob"
        val troll = mob && isTroll(item)
        val targetedMob = mob && item.optBoolean("targeted_by_player", false)
        val oreElemental = mob && (
            item.optString("name").contains("Ore Elemental", ignoreCase = true) ||
                item.optString("type_unique_name").contains("ORE_ELEMENTAL", ignoreCase = true) ||
                item.optString("localization_tag").contains("OREELEMENTAL", ignoreCase = true)
            )
        val enchant = enchantmentOf(item)
        val resourceName = item.optJSONObject("resource_inference")
            ?.optJSONObject("resource_name")?.optString("value")?.lowercase().orEmpty()
        val rawName = (item.optString("name") + " " + resourceName + " " +
            AlbionRadarSnapshotParser.labelForEntity(item)).lowercase()
        val markerActivity = item.optString("marker_activity").ifBlank {
            item.optString("marker_activity_candidate")
        }.lowercase()
        val ambiguousResource = item.optString("classification") == "ambiguous_resource_candidate" ||
            item.optString("evidence_grade") == "conflicting_typed_observation"
        val hasTypedOreEvidence = item.optInt("family", -1) == 29 &&
            item.optString("name").isNotBlank() && item.optString("name") != "Resource"
        val resourceCategory = when {
            ambiguousResource -> "unknown_resource"
            item.optBoolean("user_confirmed_target", false) || hasTypedOreEvidence ||
                (rawName.contains("ore") && !rawName.contains("candidate")) || markerActivity == "ore" -> "ore"
            rawName.contains("wood") || rawName.contains("tree") || rawName.contains("logs") || markerActivity == "wood" -> "wood"
            rawName.contains("stone") || rawName.contains("rock") || markerActivity == "stone" -> "stone"
            rawName.contains("fiber") || rawName.contains("cotton") || rawName.contains("flax") || markerActivity == "fiber" -> "fiber"
            rawName.contains("hide") || rawName.contains("animal") || rawName.contains("beast") || markerActivity == "hide" -> "hide"
            else -> "unknown_resource"
        }
        val mobType = item.optString("mob_category").lowercase()
        val mobCategory = when {
            troll -> "troll"
            oreElemental -> "ore_elemental"
            mobType.contains("animal") || mobType.contains("harvest") ||
                rawName.contains("bird") || rawName.contains("wolf") || rawName.contains("boar") ||
                rawName.contains("bear") || rawName.contains("deer") || rawName.contains("animal") -> "animal"
            else -> "other_mob"
        }
        val selectedCategories = enabledFilters.filter { it.startsWith("category_") }
            .map { it.removePrefix("category_") }.toSet()
        // Unknown Event 39 candidates must never leak into selected resource categories.
        // They can be numerous (and are intentionally drawn grey), so show them only
        // when the user explicitly enables "Unclassified Resources". Once Event 40
        // supplies a type/family/name, the same entity is classified and follows its
        // real category filter normally.
        val categoryEnabled = when {
            node -> "category_$resourceCategory" in enabledFilters
            mob -> mobCategory in selectedCategories || (mobCategory == "other_mob" && "category_mobs" in enabledFilters)
            else -> false
        }
        if (!categoryEnabled) return false
        val tierFilters = enabledFilters.filter { it.startsWith("tier_") }
            .mapNotNull { it.removePrefix("tier_").toIntOrNull() }.toSet()
        val markerTier = item.optInt("marker_tier", -1)
        val effectiveTier = if (markerTier >= 0) markerTier else item.optInt("tier", -1)
        // Exact filtering excludes unknown metadata from a selected Tier/Enchantment subset.
        if (tierFilters.isNotEmpty() && (effectiveTier !in 2..8 || effectiveTier !in tierFilters)) return false
        val enchantFilters = enabledFilters.filter { it.startsWith("enchant_") }
            .mapNotNull { it.removePrefix("enchant_").toIntOrNull() }.filter { it in 0..3 }.toSet()
        // Enchantment applies only to resources; mobs remain unaffected.
        if (node && enchantFilters.isNotEmpty() && (enchant !in 0..3 || enchant !in enchantFilters)) return false
        return true
    }

    private fun visibleEntities(): List<JSONObject> {
        if (filterCacheDirty) {
            // Keep depleted/departed nodes visible as grey historical hints. They remain
            // excluded from nearest-target guidance, but must not silently disappear.
            visibleCache = entities.filter {
                it.optString("last_kind") in DISPLAY_KINDS &&
                    validPoint(it) && passesFilters(it)
            }
            catalogVisibleCache = catalogEntities.filter {
                it.optString("last_kind") in DISPLAY_KINDS &&
                    it.optString("last_kind") != "own_player_move" &&
                    validPoint(it) && passesFilters(it)
            }
            filterCacheDirty = false
        }
        return visibleCache
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (min(width, height) * 0.48f).coerceAtLeast(12f * density)
        val clip = android.graphics.Path().apply { addCircle(cx, cy, radius, android.graphics.Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)

        // The overlay window is translucent; draw no panel, grid, rings, crosshair, or trail.
        if (catalogOverviewMode) {
            drawCatalogOverview(canvas, cx, cy, radius)
            canvas.restoreToCount(save)
            return
        }

        val you = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you != null) {
            val history = you.optJSONArray("movement_history")
            // Use the newest authoritative player coordinates. Median smoothing over
            // the last 3 seconds displaced the player origin behind actual movement,
            // making every resource vector lag and swing while the player was walking.
            val px = you.optDouble("x")
            val py = you.optDouble("y")
            val trailPoints = ArrayList<Pair<Double, Double>>()
            if (history != null) {
                val first = (history.length() - 240).coerceAtLeast(0)
                for (i in first until history.length()) {
                    val point = history.optJSONObject(i) ?: continue
                    val tx = point.optDouble("x", Double.NaN)
                    val ty = point.optDouble("y", Double.NaN)
                    if (tx.isFinite() && ty.isFinite()) trailPoints.add(tx to ty)
                }
            }
            val resources = visibleEntities().filter { it.optString("last_kind") in RESOURCE_KINDS }
            // Fit both nearby resources and the retained movement path so the trail
            // remains a useful route, not a tiny 16-point trace around the center.
            // Keep the local radar at a stable scale. A single remote catalog node must
            // not zoom the entire local view out and make nearby nodes unreadable.
            // Remote targets are clamped to the rim; use the full map for long-range search.
            val displayRange = minimumRange

            // Keep a short live breadcrumb trail visible without adding a panel or grid.
            // Coordinates are translated to the current player origin and use the same
            // fixed north-up transform as resource markers.
            if (trailPoints.size > 1) {
                val trail = android.graphics.Path()
                var started = false
                trailPoints.forEach { (tx, ty) ->
                    val (trX, trY) = rotateOffset(tx - px, ty - py)
                    val sx = cx + (trX / displayRange * radius).toFloat()
                    val sy = cy + (trY / displayRange * radius).toFloat()
                    if (!started) { trail.moveTo(sx, sy); started = true } else trail.lineTo(sx, sy)
                }
                if (started) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2.0f * density
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.strokeJoin = Paint.Join.ROUND
                    paint.color = 0xCC56D6A0.toInt()
                    canvas.drawPath(trail, paint)
                }
            }

            resources.forEach { entity ->
                val dx = entity.optDouble("x") - px
                val dy = entity.optDouble("y") - py
                val distance = hypot(dx, dy)
                val factor = if (distance > displayRange) displayRange / distance else 1.0
                val (rx, ry) = rotateOffset(dx * factor, dy * factor)
                val x = cx + (rx / displayRange * radius).toFloat()
                val y = cy + (ry / displayRange * radius).toFloat()
                paint.style = Paint.Style.FILL
                paint.color = if (isUnavailableResource(entity)) 0xFF78838B.toInt() else resourceColor(entity)
                canvas.drawCircle(x, y, 3.3f * density, paint)
                // Distant nodes are clamped to the rim to preserve the local radar scale.
                // Show their actual game-coordinate distance so multiple remote bearings
                // are not mistaken for equally distant targets.
                if (distance > displayRange) {
                    paint.color = 0xFFE8EDF2.toInt()
                    paint.textSize = 7f * density
                    paint.textAlign = Paint.Align.CENTER
                    val labelRadius = (radius - 13f * density).coerceAtLeast(0f)
                    val labelX = cx + (rx / displayRange * labelRadius)
                    val labelY = cy + (ry / displayRange * labelRadius)
                    canvas.drawText(distance.roundToInt().toString(), labelX, labelY, paint)
                    paint.textAlign = Paint.Align.LEFT
                }
                val t = entity.optInt("marker_tier", -1).takeIf { it in 1..8 }
                    ?: entity.optInt("tier", -1).takeIf { it in 1..8 }
                if (t != null) {
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.textSize = 7f * density
                    canvas.drawText("T$t", x + 4f * density, y - 3f * density, paint)
                }
                if (entity.optBoolean("user_confirmed_target", false)) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.8f * density
                    paint.color = 0xFF55E6FF.toInt()
                    canvas.drawCircle(x, y, 7f * density, paint)
                }
            }
            // Small center point only; all other non-resource entities remain invisible.
            drawPlayerMarker(canvas, cx, cy, AlbionMapProjection.movementDirection(history))
        }
        canvas.restoreToCount(save)
    }

    private fun drawPlayerMarker(
        canvas: Canvas, cx: Float, cy: Float, direction: AlbionMapProjection.Point?
    ) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFF080B0E.toInt()
        canvas.drawCircle(cx, cy, 5.0f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 5.0f * density, paint)
        if (direction != null) {
            val length = 15f * density
            val tipX = cx + (direction.x * length).toFloat()
            val tipY = cy + (direction.y * length).toFloat()
            paint.color = 0xFF55E6FF.toInt()
            paint.strokeWidth = 2.2f * density
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, tipX, tipY, paint)
            val backX = cx - (direction.x * 4.5f * density).toFloat()
            val backY = cy - (direction.y * 4.5f * density).toFloat()
            val sideX = (-direction.y * 3.5f * density).toFloat()
            val sideY = (direction.x * 3.5f * density).toFloat()
            canvas.drawLine(tipX, tipY, backX + sideX, backY + sideY, paint)
            canvas.drawLine(tipX, tipY, backX - sideX, backY - sideY, paint)
        }
    }

    private fun drawCatalogOverview(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        visibleEntities()
        val points = catalogVisibleCache.filter { it.optString("last_kind") in RESOURCE_KINDS }
        if (points.isEmpty()) return
        val minX = points.minOf { it.optDouble("x") }
        val maxX = points.maxOf { it.optDouble("x") }
        val minY = points.minOf { it.optDouble("y") }
        val maxY = points.maxOf { it.optDouble("y") }
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val span = maxOf(maxX - minX, maxY - minY, 1.0)
        val scale = (radius * 1.65 / span).toFloat()
        points.forEach { entity ->
            val (rx, ry) = rotateOffset(entity.optDouble("x") - centerX, entity.optDouble("y") - centerY)
            val x = cx + (rx * scale).toFloat()
            val y = cy + (ry * scale).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = if (isUnavailableResource(entity)) 0xFF78838B.toInt() else resourceColor(entity)
            canvas.drawCircle(x, y, 2.8f * density, paint)
            val t = entity.optInt("marker_tier", -1).takeIf { it in 1..8 }
                ?: entity.optInt("tier", -1).takeIf { it in 1..8 }
            if (t != null) {
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 6.5f * density
                canvas.drawText("T$t", x + 3f * density, y - 2f * density, paint)
            }
        }
    }

    companion object {
        val DEFAULT_FILTERS = setOf(
            "category_ore", "category_wood", "category_stone", "category_fiber",
            "category_hide", "category_unknown_resource"
        )
        internal fun rotateOffset(dx: Double, dy: Double): Pair<Double, Double> {
            val point = AlbionMapProjection.worldToScreen(dx, dy)
            return point.x to point.y
        }
        internal fun colorForEnchantment(enchant: Int): Int = when (enchant) {
            0 -> 0xFFFFC45C.toInt()
            1 -> 0xFF63D471.toInt()
            2 -> 0xFF55B8FF.toInt()
            3 -> 0xFFB58CFF.toInt()
            else -> 0xFF9AA6AF.toInt()
        }
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob")
    }
}
