package com.v2ray.ang.albion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Compact, coordinate-free index built from the complete research CSV catalogs.
 *
 * This index enriches current-session observations with cross-capture evidence only.
 * Historical coordinates are deliberately absent: they must never be projected onto
 * the live player-centered map without a verified zone transform.
 */
object AlbionResearchKnowledgeIndex {
    private const val ASSET_NAME = "albion-research-knowledge-index.json"
    private val RESOURCE_KINDS = setOf("static_resource", "harvestable", "resource_state")

    data class Snapshot internal constructor(
        val resourceObservationCount: Int,
        val mobileEntityObservationCount: Int,
        val resourceFamilyCount: Int,
        val mobileTypeCount: Int,
        private val resourceFamilies: JSONObject,
        private val mobileTypes: JSONObject
    ) {
        fun statusLabel(): String =
            "KNOW ${resourceObservationCount}R/${mobileEntityObservationCount}M"

        /** Adds provenance/evidence counters; never replaces packet facts or display names. */
        fun annotate(entity: JSONObject) {
            when (entity.optString("last_kind")) {
                in RESOURCE_KINDS -> annotateResource(entity)
                "mob" -> annotateMob(entity)
            }
        }

        private fun annotateResource(entity: JSONObject) {
            val family = entity.optInt("family", Int.MIN_VALUE)
            if (family == Int.MIN_VALUE) return
            val evidence = resourceFamilies.optJSONObject(family.toString()) ?: return
            entity.put("research_catalog_family_observations", evidence.optInt("observation_count"))
            entity.put("research_catalog_family_capture_count", evidence.optJSONArray("captures")?.length() ?: 0)
            entity.put("research_catalog_family_types", evidence.optJSONObject("resource_types")?.names() ?: JSONArray())
            entity.put("research_catalog_family_mapping_statuses", evidence.optJSONObject("mapping_statuses")?.names() ?: JSONArray())
            entity.put("research_catalog_family_tiers", evidence.optJSONArray("tiers") ?: JSONArray())
            entity.put("research_catalog_source", "complete_research_resource_catalog")
        }

        private fun annotateMob(entity: JSONObject) {
            val typeId = entity.optInt("mobile_type_id", Int.MIN_VALUE)
            if (typeId == Int.MIN_VALUE) return
            val evidence = mobileTypes.optJSONObject(typeId.toString()) ?: return
            entity.put("research_catalog_mobile_observations", evidence.optInt("observation_count"))
            entity.put("research_catalog_mobile_capture_count", evidence.optJSONArray("captures")?.length() ?: 0)
            entity.put("research_catalog_mobile_tiers", evidence.optJSONArray("tiers") ?: JSONArray())
            entity.put("research_catalog_mobile_names", evidence.optJSONObject("names")?.names() ?: JSONArray())
            entity.put("research_catalog_mobile_categories", evidence.optJSONObject("categories")?.names() ?: JSONArray())
            val harvestable = evidence.optJSONObject("harvestable_values")
            if (harvestable != null && harvestable.length() == 1) {
                val value = harvestable.keys().asSequence().first()
                if (value.equals("true", true) || value.equals("false", true)) {
                    entity.put("research_catalog_harvestable", value.equals("true", true))
                }
            }
            entity.put("research_catalog_source", "complete_research_mobile_entity_catalog")
        }
    }

    fun load(context: Context): Snapshot? = try {
        val json = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        parse(json)
    } catch (_: Exception) {
        null
    }

    /** Public for deterministic unit tests and offline validation. */
    fun parse(json: String): Snapshot? = try {
        val root = JSONObject(json)
        if (root.optInt("schema_version", -1) != 1) return null
        val totals = root.optJSONObject("totals") ?: return null
        val resources = root.optJSONObject("resource_families") ?: return null
        val mobs = root.optJSONObject("mobile_types") ?: return null
        val resourceCount = totals.optInt("resource_observations", -1)
        val mobileCount = totals.optInt("mobile_entity_observations", -1)
        val familyCount = totals.optInt("resource_families", -1)
        val typeCount = totals.optInt("mobile_type_ids", -1)
        if (resourceCount <= 0 || mobileCount <= 0 || familyCount != resources.length() ||
            typeCount != mobs.length()) return null
        Snapshot(resourceCount, mobileCount, familyCount, typeCount, resources, mobs)
    } catch (_: Exception) {
        null
    }
}
