package com.v2ray.ang.ui.albion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.LinkedHashMap

/** Compact coordinate radar. Off-range entities are projected to the rim with distance labels. */
class AlbionMiniRadarView(context: Context) : View(context) {
    private var entities: List<JSONObject> = emptyList()
    private var catalogEntities: List<JSONObject> = emptyList()
    private var enabledFilters: Set<String> = DEFAULT_FILTERS
    private var catalogOverviewMode = false
    private var lockedTargetId: String? = null
    // Filtering is independent of player position. Cache it between snapshots/filter changes
    // so onDraw does not repeatedly walk and classify every entity on each invalidation.
    private var visibleCache: List<JSONObject> = emptyList()
    private var catalogVisibleCache: List<JSONObject> = emptyList()
    private var filterCacheDirty = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val range = 260.0

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
        catalogEntities = parsed.entities
        entities = catalogEntities
        filterCacheDirty = true
        invalidate()
        return catalogEntities.size
    }

    fun setLiveSnapshot(json: String): Int? {
        // Snapshot JSON parsing can be expensive on large sessions; callers may invoke this
        // from the capture worker. Only publish the immutable parsed list on the UI thread.
        val parsed = AlbionRadarSnapshotParser.parse(json) ?: return null
        val spatiallyDeduplicated = AlbionSpatialIntelligence.deduplicate(parsed.entities)
        val acceptedCount = spatiallyDeduplicated.size
        post {
            // Live coordinates belong to the active capture. Never merge historical catalog
            // coordinates into the player-centered map until zone identity is verified.
            entities = spatiallyDeduplicated
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

    // Albion's world-map presentation is rotated 45 degrees clockwise; rotate the
    // radar projection 45 degrees counter-clockwise to keep navigation intuitive.
    private fun rotateRadarX(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return cx + (dx * ROTATION_COS - dy * ROTATION_SIN)
    }

    private fun rotateRadarY(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return cy + (dx * ROTATION_SIN + dy * ROTATION_COS)
    }

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
                item.optString("type_unique_name").contains("ORE_MOUNTAIN", ignoreCase = true) ||
                item.optString("localization_tag").contains("OREELEMENTAL", ignoreCase = true)
            )
        val markerEnchant = item.optInt("marker_enchant", -1)
        val inferredEnchant = item.optJSONObject("resource_inference")
            ?.optJSONObject("enchantment_name")?.optString("value")
        val canonicalEnchant = when (inferredEnchant) {
            "Normal" -> 0
            "E1" -> 1
            "E2" -> 2
            "E3" -> 3
            else -> item.optInt("enchant", -1)
        }
        val enchant = if (markerEnchant >= 0) markerEnchant else canonicalEnchant
        val resourceName = item.optJSONObject("resource_inference")
            ?.optJSONObject("resource_name")?.optString("value")?.lowercase().orEmpty()
        val rawName = (item.optString("name") + " " + resourceName + " " +
            AlbionRadarSnapshotParser.labelForEntity(item)).lowercase()
        val markerActivity = item.optString("marker_activity").ifBlank {
            item.optString("marker_activity_candidate")
        }.lowercase()
        val resourceCategory = when {
            item.optBoolean("user_confirmed_target", false) || item.optInt("family", -1) == 29 ||
                rawName.contains("ore") || rawName.contains("ore candidate") || markerActivity == "ore" -> "ore"
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
        // Event 39 candidates can arrive before typed tier metadata.
        // Unknown tier is not proof that a node is outside the selected tier range.
        if (tierFilters.isNotEmpty() && effectiveTier >= 0 && effectiveTier !in tierFilters) return false
        val enchantFilters = enabledFilters.filter { it.startsWith("enchant_") }
            .mapNotNull { it.removePrefix("enchant_").toIntOrNull() }.toSet()
        // Enchantment is a resource-node property; never hide mobs (e.g. blue golems/trolls) because they have no enchantment.
        // Unknown resource enchantment (-1) remains visible until the packet/catalog provides a reliable value.
        if (node && enchantFilters.isNotEmpty() && enchant >= 0 && enchant !in enchantFilters) return false
        return true
    }

    private fun visibleEntities(playerX: Double? = null, playerY: Double? = null): List<JSONObject> {
        if (filterCacheDirty) {
            val filtered = entities.filter {
                it.optString("last_kind") in DISPLAY_KINDS && validPoint(it) && passesFilters(it)
            }
            visibleCache = filtered
            catalogVisibleCache = catalogEntities.filter {
                it.optString("last_kind") in DISPLAY_KINDS &&
                    it.optString("last_kind") != "own_player_move" && validPoint(it) && passesFilters(it)
            }
            filterCacheDirty = false
        }
        return if (playerX != null && playerY != null)
            AlbionSpatialIntelligence.limitUnclassifiedCandidates(visibleCache, playerX, playerY, MAX_UNCLASSIFIED_CANDIDATE_PINS)
        else visibleCache
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

        if (catalogOverviewMode) {
            drawCatalogOverview(canvas, cx, cy, radius)
            return
        }

        val you = entities.asSequence()
            .filter { it.optString("last_kind") == "own_player_move" && validPoint(it) }
            .maxByOrNull { it.optLong("last_seen_ms") }
        if (you == null) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE3EDF5.toInt()
            paint.textSize = 9f * density
            canvas.drawText("No live player position", 8f * density, cy - 8f * density, paint)
            drawCatalogOverview(canvas, cx, cy, radius)
            return
        }
        // Median of a few recent samples suppresses single-frame coordinate jitter
        // without the lag of a long moving average.
        val history = you.optJSONArray("movement_history")
        val recentPlayerPoints = ArrayList<Pair<Double, Double>>()
        val historyCutoff = System.currentTimeMillis() - 3000L
        if (history != null) {
            for (i in (history.length() - 5).coerceAtLeast(0) until history.length()) {
                val point = history.optJSONObject(i) ?: continue
                if (point.optLong("time_ms", System.currentTimeMillis()) < historyCutoff) continue
                val hx = point.optDouble("x", Double.NaN)
                val hy = point.optDouble("y", Double.NaN)
                if (hx.isFinite() && hy.isFinite()) recentPlayerPoints.add(hx to hy)
            }
        }
        recentPlayerPoints.add(you.optDouble("x") to you.optDouble("y"))
        fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }
        val px = if (recentPlayerPoints.size >= 3) median(recentPlayerPoints.map { it.first }) else you.optDouble("x")
        val py = if (recentPlayerPoints.size >= 3) median(recentPlayerPoints.map { it.second }) else you.optDouble("y")
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
                val rawX = cx + (dx / range * radius).toFloat()
                val rawY = cy - (dy / range * radius).toFloat()
                val x = rotateRadarX(rawX, rawY, cx, cy)
                val y = rotateRadarY(rawX, rawY, cx, cy)
                if (oldX != null && oldY != null) canvas.drawLine(oldX, oldY, x, y, paint)
                oldX = x; oldY = y
            }
        }

        val visible = visibleEntities(px, py)
        if (lockedTargetId != null && visible.none {
                it.optString("entity_id") == lockedTargetId && AlbionSpatialIntelligence.isNavigableResource(it)
            }) {
            lockedTargetId = null
        }
        val outside = ArrayList<Pair<JSONObject, Double>>()
        val nearbyNodes = ArrayList<Pair<JSONObject, Double>>()
        var mobLabelsDrawn = 0
        visible.forEach { entity ->
            val dx = entity.optDouble("x") - px
            val dy = entity.optDouble("y") - py
            val distance = hypot(dx, dy)
            val factor = if (distance > range) range / distance else 1.0
            val rawX = cx + (dx * factor / range * radius).toFloat()
            val rawY = cy - (dy * factor / range * radius).toFloat()
            val x = rotateRadarX(rawX, rawY, cx, cy)
            val y = rotateRadarY(rawX, rawY, cx, cy)
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
                kind in RESOURCE_KINDS && entity.optInt("enchant", 0) > 0 -> 0xFFB58CFF.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, (if (distance > range) 2.5f else 3.2f) * density, paint)
            if (AlbionSpatialIntelligence.isNavigableResource(entity) && distance <= range) nearbyNodes.add(entity to distance)
            if (kind == "mob" && distance <= range && mobLabelsDrawn < MAX_MOB_LABELS) {
                paint.style = Paint.Style.FILL
                paint.textSize = 7f * density
                paint.color = 0xFFFFD5D5.toInt()
                val mobName = AlbionRadarSnapshotParser.labelForEntity(entity).take(14)
                val hp = if (entity.has("health_value")) " ${entity.optDouble("health_value").roundToInt()}HP" else ""
                canvas.drawText(mobName + hp, x + 5f * density, y + 9f * density, paint)
                mobLabelsDrawn++
            }
            if (entity.optBoolean("user_confirmed_target", false)) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = 0xFF55E6FF.toInt()
                canvas.drawCircle(x, y, 7f * density, paint)
            }
            if (distance > range) outside.add(entity to distance)
        }

        var locked: Pair<JSONObject, Double>? = null
        var nearestTarget: Pair<JSONObject, Double>? = null
        // One linear pass replaces sorting the entire resource set every frame.
        visible.forEach { entity ->
            if (AlbionSpatialIntelligence.isNavigableResource(entity)) {
                val candidate = entity to hypot(entity.optDouble("x") - px, entity.optDouble("y") - py)
                if (entity.optString("entity_id") == lockedTargetId) locked = candidate
                if (nearestTarget == null || candidate.second < nearestTarget!!.second) nearestTarget = candidate
            }
        }
        // Freeze the mutable scan results into stable locals before smart-cast decisions.
        val nearest = nearestTarget
        val currentLock = locked
        // Keep a target stable while approaching, but switch when another resource is
        // materially closer (12 world units) or the locked resource is no longer available.
        val chosenTarget: Pair<JSONObject, Double>? = when {
            nearest == null -> null
            currentLock == null -> nearest
            nearest.first.optString("entity_id") != currentLock.first.optString("entity_id") &&
                nearest.second + 12.0 < currentLock.second -> nearest
            else -> currentLock
        }
        lockedTargetId = chosenTarget?.first?.optString("entity_id")
        if (chosenTarget != null) {
            val target = chosenTarget.first
            val dx = target.optDouble("x") - px
            val dy = target.optDouble("y") - py
            val distance = chosenTarget.second
            val factor = if (distance > range) range / distance else 1.0
            val rawTx = cx + (dx * factor / range * radius).toFloat()
            val rawTy = cy - (dy * factor / range * radius).toFloat()
            val tx = rotateRadarX(rawTx, rawTy, cx, cy)
            val ty = rotateRadarY(rawTx, rawTy, cx, cy)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f * density
            paint.color = 0xFF55E6FF.toInt()
            canvas.drawLine(cx, cy, tx, ty, paint)
            canvas.drawCircle(tx, ty, 8f * density, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 8f * density
            paint.color = 0xFF55E6FF.toInt()
            canvas.drawText("NEAREST " + distance.roundToInt(), 6f * density, 22f * density, paint)
        } else lockedTargetId = null
        val nearestNodes = nearbyNodes.sortedBy { it.second }.take(5)
        paint.style = Paint.Style.FILL
        paint.textSize = 7f * density
        paint.color = 0xFFFFE0A6.toInt()
        nearestNodes.forEachIndexed { index, pair ->
            val node = pair.first
            val label = AlbionRadarSnapshotParser.labelForEntity(node)
            val text = "$label ${pair.second.roundToInt()}"
            canvas.drawText(text.take(22), 5f * density,
                (12f + index * 9f) * density, paint)
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
            val rawX = cx + (dx * factor / range * radius).toFloat()
            val rawY = cy - (dy * factor / range * radius).toFloat()
            val x = rotateRadarX(rawX, rawY, cx, cy)
            val y = rotateRadarY(rawX, rawY, cx, cy)
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
        canvas.drawText("${visible.size} pins • ${nearbyNodes.size} navigable • candidates≤$MAX_UNCLASSIFIED_CANDIDATE_PINS • zone?", 6f * density, height - 5f * density, paint)
    }

    private fun drawCatalogOverview(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        visibleEntities()
        val points = catalogVisibleCache
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
            val rawX = cx + ((entity.optDouble("x") - centerX) * scale).toFloat()
            val rawY = cy - ((entity.optDouble("y") - centerY) * scale).toFloat()
            val x = rotateRadarX(rawX, rawY, cx, cy)
            val y = rotateRadarY(rawX, rawY, cx, cy)
            val kind = entity.optString("last_kind")
            paint.style = Paint.Style.FILL
            paint.color = when {
                entity.optBoolean("user_confirmed_target", false) -> 0xFF55E6FF.toInt()
                entity.optBoolean("depleted", false) || entity.optBoolean("left", false) -> 0xFF697782.toInt()
                kind == "mob" -> if (isTroll(entity)) 0xFFFF9A55.toInt() else 0xFFFF6666.toInt()
                else -> 0xFFFFC45C.toInt()
            }
            canvas.drawCircle(x, y, if (entity.optBoolean("user_confirmed_target", false)) 4f * density else 2.4f * density, paint)
            if (entity.optBoolean("user_confirmed_target", false)) {
                paint.color = 0xFF55E6FF.toInt()
                paint.textSize = 8f * density
                canvas.drawText(AlbionRadarSnapshotParser.labelForEntity(entity),
                    (x + 5f * density).coerceAtMost(width - 48f * density),
                    (y - 5f * density).coerceAtLeast(10f * density), paint)
            }
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFB8C8D5.toInt()
        paint.textSize = 7f * density
        canvas.drawText("SAVED CATALOG • NOT PLAYER-CENTERED • ${points.size} pins", 6f * density, height - 6f * density, paint)
    }

    companion object {
        val DEFAULT_FILTERS = setOf("category_ore")
        private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")
        private val DISPLAY_KINDS = RESOURCE_KINDS + setOf("mob")
        private const val MAX_UNCLASSIFIED_CANDIDATE_PINS = 40
        private const val MAX_MOB_LABELS = 24
        private const val ROTATION_COS = 0.70710677f
        private const val ROTATION_SIN = -0.70710677f
    }
}
