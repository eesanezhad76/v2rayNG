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

    data class ResourceTypeEvidence(
        val value: String,
        val status: String,
        val confidence: Double,
        val observationCount: Int,
        val captureCount: Int
    )

    data class Snapshot internal constructor(
        val resourceObservationCount: Int,
        val mobileEntityObservationCount: Int,
        val resourceFamilyCount: Int,
        val mobileTypeCount: Int,
        val schemaVersion: Int,
        val sourceBranch: String?,
        val resourceCatalogBlobSha: String?,
        val mobileCatalogBlobSha: String?,
        private val resourceFamilies: JSONObject,
        private val mobileTypes: JSONObject
    ) {
        data class DictionaryCoverage(val matched: Int, val expected: Int) {
            fun label(): String = "$matched/$expected"
        }

        /** Exact IDs required by the research corpus; used to audit the display dictionary. */
        fun mobileTypeIds(): Set<String> =
            mobileTypes.keys().asSequence().toSet()

        fun mobileDictionaryCoverage(dictionary: JSONObject): DictionaryCoverage {
            val expected = mobileTypeIds()
            return DictionaryCoverage(expected.count { dictionary.has(it) }, expected.size)
        }
        fun statusLabel(): String =
            "KNOW ${resourceObservationCount}R/${mobileEntityObservationCount}M"

        /**
         * Returns only unanimous, explicitly classified resource-family evidence.
         * Unknown or mixed mappings stay unresolved; provisional reference mappings
         * remain provisional and are never promoted by observation count alone.
         */
        fun resourceTypeEvidence(family: Int?): ResourceTypeEvidence? {
            if (family == null) return null
            val evidence = resourceFamilies.optJSONObject(family.toString()) ?: return null
            val types = evidence.optJSONObject("resource_types") ?: return null
            if (types.length() != 1) return null
            val type = types.keys().asSequence().firstOrNull()?.takeIf { it != "unknown" } ?: return null
            val statuses = evidence.optJSONObject("mapping_statuses") ?: return null
            if (statuses.length() != 1) return null
            val status = statuses.keys().asSequence().firstOrNull() ?: return null
            if (status !in setOf("REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL", "PACKET_CONFIRMED_LIVING_RESOURCE")) return null
            val observations = evidence.optInt("observation_count", 0)
            val captures = evidence.optJSONArray("captures")?.length() ?: 0
            if (observations <= 0 || captures <= 0) return null
            return ResourceTypeEvidence(
                value = type,
                status = status,
                confidence = if (status == "PACKET_CONFIRMED_LIVING_RESOURCE") 0.98 else 0.90,
                observationCount = observations,
                captureCount = captures
            )
        }

        /** Adds provenance/evidence counters; never replaces packet facts or display names. */
        fun annotate(entity: JSONObject) {
            entity.put("research_catalog_schema_version", schemaVersion)
            sourceBranch?.let { entity.put("research_catalog_source_branch", it) }
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
            val types = evidence.optJSONObject("resource_types")
            val statuses = evidence.optJSONObject("mapping_statuses")
            if (types != null && types.length() == 1 && statuses != null && statuses.length() == 1) {
                val type = types.keys().asSequence().first()
                val status = statuses.keys().asSequence().first()
                entity.put("research_catalog_resource_type", type)
                entity.put("research_catalog_resource_mapping_status", status)
                entity.put("research_catalog_resource_mapping_observations", evidence.optInt("observation_count"))
                entity.put("research_catalog_resource_mapping_capture_count", evidence.optJSONArray("captures")?.length() ?: 0)
                resourceTypeEvidence(family)?.let {
                    entity.put("research_catalog_resource_mapping_confidence", it.confidence)
                }
            }
            entity.put("research_catalog_source", "complete_research_resource_catalog")
            resourceCatalogBlobSha?.let { entity.put("research_catalog_source_blob_sha", it) }
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
            mobileCatalogBlobSha?.let { entity.put("research_catalog_source_blob_sha", it) }
        }
    }

    fun load(context: Context): Snapshot? = try {
        val json = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        parse(json)
    } catch (_: Exception) {
        null
    }

    /** Public for deterministic unit tests and offline validation. */
    fun parse(json: String): Snapshot? {
        return try {
            val root = JSONObject(json)
            val totals = root.optJSONObject("totals")
            val generatedFrom = root.optJSONObject("generated_from")
            val resources = root.optJSONObject("resource_families")
            val mobs = root.optJSONObject("mobile_types")
            if (root.optInt("schema_version", -1) != 1 || totals == null ||
                resources == null || mobs == null) return null
            val resourceCount = totals.optInt("resource_observations", -1)
            val mobileCount = totals.optInt("mobile_entity_observations", -1)
            val familyCount = totals.optInt("resource_families", -1)
            val typeCount = totals.optInt("mobile_type_ids", -1)
            if (resourceCount <= 0 || mobileCount <= 0 || familyCount != resources.length() ||
                typeCount != mobs.length()) return null
            Snapshot(
                resourceObservationCount = resourceCount,
                mobileEntityObservationCount = mobileCount,
                resourceFamilyCount = familyCount,
                mobileTypeCount = typeCount,
                schemaVersion = root.optInt("schema_version"),
                sourceBranch = generatedFrom?.optString("source_branch")?.takeIf { it.isNotBlank() && it != "null" },
                resourceCatalogBlobSha = generatedFrom?.optString("resource_csv_blob_sha")?.takeIf { it.isNotBlank() && it != "null" },
                mobileCatalogBlobSha = generatedFrom?.optString("mobile_csv_blob_sha")?.takeIf { it.isNotBlank() && it != "null" },
                resourceFamilies = resources,
                mobileTypes = mobs
            )
        } catch (_: Exception) {
            null
        }
    }
}
