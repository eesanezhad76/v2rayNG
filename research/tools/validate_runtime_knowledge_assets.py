#!/usr/bin/env python3
"""Validate that the complete research corpus is wired into the shipped radar assets.

This is a CI gate. It checks cardinalities, source provenance, type dictionary coverage,
known exceptions, and the intentionally capture-local saved map catalog.
"""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path


EXPECTED_FAMILIES = {
    "7": ("Rock", "REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL", 87),
    "14": ("Fiber", "REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL", 47),
    "20": ("Hide", "REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL", 7),
    "27": ("Ore", "REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL", 88),
    "28": ("unknown", "UNRESOLVED", 2),
    "29": ("Ore", "PACKET_CONFIRMED_LIVING_RESOURCE", 100),
}
EXPECTED_MOBILE_OBSERVATIONS = 2330
EXPECTED_MOBILE_TYPE_IDS = 64
EXPECTED_RESOURCE_OBSERVATIONS = 331


def _json(path: Path):
    with path.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def _git_blob_sha1(path: Path) -> str:
    data = path.read_bytes()
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def validate(root: Path) -> dict:
    assets = root / "V2rayNG/app/src/main/assets"
    research = root / "research/database"
    index_path = assets / "albion-research-knowledge-index.json"
    dictionary_path = assets / "albion-mobile-type-dictionary.json"
    static_path = assets / "albion-static-catalog.json"
    index = _json(index_path)
    dictionary_root = _json(dictionary_path)
    dictionary = dictionary_root.get("entries")
    static = _json(static_path)
    bundle_path = research / "albion-knowledge-bundle-import.json"
    bundle_database = _json(bundle_path)
    historical_seed = _json(assets / "albion-historical-autonomous-patterns.json")
    historical_entities = _json(assets / "albion-historical-entity-evidence.json")
    historical_protocol = _json(assets / "albion-historical-protocol-shapes.json")
    historical_learned = _json(assets / "albion-historical-learned-patterns.json")
    historical_manifest = _json(assets / "albion-historical-knowledge-manifest.json")

    if not isinstance(dictionary, dict):
        raise ValueError("mobile type dictionary has no entries object")
    dictionary_source = dictionary_root.get("source", {})
    if dictionary_source.get("repository") != "Nouuu/Albion-Online-OpenRadar":
        raise ValueError("external OpenRadar dictionary provenance is missing")
    if dictionary_source.get("mapping") != "server TypeID = array index + 16":
        raise ValueError("external TypeID offset provenance is missing")

    totals = index.get("totals", {})
    families = index.get("resource_families", {})
    mobile = index.get("mobile_types", {})
    generated = index.get("generated_from", {})

    if index.get("schema_version") != 1:
        raise ValueError("unsupported research knowledge index schema")
    if totals.get("resource_observations") != EXPECTED_RESOURCE_OBSERVATIONS:
        raise ValueError("resource observation total mismatch")
    if totals.get("mobile_entity_observations") != EXPECTED_MOBILE_OBSERVATIONS:
        raise ValueError("mobile observation total mismatch")
    if totals.get("resource_families") != len(EXPECTED_FAMILIES) or set(families) != set(EXPECTED_FAMILIES):
        raise ValueError("resource family coverage mismatch")
    if totals.get("mobile_type_ids") != EXPECTED_MOBILE_TYPE_IDS or len(mobile) != EXPECTED_MOBILE_TYPE_IDS:
        raise ValueError("mobile type ID cardinality mismatch")

    family_observations = 0
    for family_id, (expected_type, expected_status, expected_count) in EXPECTED_FAMILIES.items():
        row = families[family_id]
        family_observations += row.get("observation_count", 0)
        if row.get("observation_count") != expected_count:
            raise ValueError(f"family {family_id} observation count mismatch")
        if row.get("resource_types") != {expected_type: expected_count}:
            raise ValueError(f"family {family_id} type mapping mismatch")
        if row.get("mapping_statuses") != {expected_status: expected_count}:
            raise ValueError(f"family {family_id} evidence status mismatch")
    if family_observations != EXPECTED_RESOURCE_OBSERVATIONS:
        raise ValueError("resource family observations do not sum to total")

    mobile_observations = sum(row.get("observation_count", 0) for row in mobile.values())
    if mobile_observations != EXPECTED_MOBILE_OBSERVATIONS:
        raise ValueError("mobile per-type observations do not sum to total")

    index_ids = set(mobile)
    dictionary_ids = set(dictionary)
    missing_dictionary_ids = sorted(index_ids - dictionary_ids)
    if missing_dictionary_ids:
        raise ValueError(f"mobile dictionary is missing observed IDs: {missing_dictionary_ids}")
    # The external dictionary is intentionally broader than the 64 IDs observed
    # in this capture corpus. Coverage is measured against observed IDs only.

    crosswalk_path = research / "albion-mobile-type-crosswalk-2026-11-03.csv"
    with crosswalk_path.open("r", encoding="utf-8-sig", newline="") as stream:
        crosswalk = list(csv.DictReader(stream))
    crosswalk_ids = {row["mobile_type_id"] for row in crosswalk}
    # The crosswalk is a broader audited catalog (67 rows); the runtime index
    # intentionally contains only the 64 IDs observed in the selected corpus.
    # Require complete coverage of indexed IDs without discarding valid extra rows.
    missing_crosswalk_ids = sorted(index_ids - crosswalk_ids)
    if missing_crosswalk_ids:
        raise ValueError(f"mobile type crosswalk is missing indexed IDs: {missing_crosswalk_ids}")
    if len(crosswalk_ids) != len(crosswalk):
        raise ValueError("mobile type crosswalk contains duplicate TypeIDs")
    if any(row.get("dictionary_match", "").lower() != "true" for row in crosswalk):
        raise ValueError("one or more crosswalk mobile IDs do not match the external dictionary")

    historical_mobile_ids = {
        str(row["mobile_type_id"]) for row in bundle_database.get("entity_observations", [])
        if row.get("last_kind") == "mob" and row.get("mobile_type_id") is not None
    }
    missing_historical_ids = sorted(historical_mobile_ids - dictionary_ids)
    if missing_historical_ids:
        raise ValueError(f"external dictionary is missing historical mobile TypeIDs: {missing_historical_ids}")
    historical_resource_families = {
        str(row["family"]) for row in bundle_database.get("entity_observations", [])
        if row.get("last_kind") in {"static_resource", "harvestable", "resource_state"}
        and row.get("family") is not None
    }
    if not historical_resource_families.issubset(set(families)):
        raise ValueError("historical resource families are not covered by the runtime research index")

    rabbit = mobile.get("482", {})
    if rabbit.get("names") != {"Mountain Rabbit": 469} or rabbit.get("harvestable_values") != {"False": 469}:
        raise ValueError("Mountain Rabbit ID 482 must remain explicitly non-harvestable")
    ore_family = families["29"]
    if ore_family.get("mobile_type_ids") != [571, 573, 574]:
        raise ValueError("family 29 Ore Elemental crosswalk mismatch")
    for type_id, tier in (("571", 3), ("573", 4), ("574", 5)):
        row = mobile.get(type_id, {})
        if row.get("names") != {"Ore Elemental": row.get("observation_count")}:
            raise ValueError(f"Ore Elemental name mismatch for type {type_id}")
        if row.get("tiers") != [tier] or row.get("harvestable_values") != {"True": row.get("observation_count")}:
            raise ValueError(f"Ore Elemental tier/harvestability mismatch for type {type_id}")

    resource_csv = research / "albion-resource-node-catalog-2026-11-03.csv"
    mobile_csv = research / "albion-mobile-entity-catalog-2026-11-03.csv"
    if generated.get("resource_csv_blob_sha") != _git_blob_sha1(resource_csv):
        raise ValueError("resource CSV source blob SHA does not match the runtime index")
    if generated.get("mobile_csv_blob_sha") != _git_blob_sha1(mobile_csv):
        raise ValueError("mobile CSV source blob SHA does not match the runtime index")

    entities = static.get("entities", [])
    resource_rows = [row for row in entities if row.get("last_kind") in {"static_resource", "harvestable", "resource_state"}]
    mob_rows = [row for row in entities if row.get("last_kind") == "mob"]
    markers = static.get("markers", [])
    if len(entities) != 739 or len(resource_rows) != static.get("node_count") or static.get("node_count") != 83:
        raise ValueError("saved static catalog resource/entity count mismatch")
    if len(mob_rows) != static.get("mob_count") or static.get("mob_count") != 656:
        raise ValueError("saved static catalog mob count mismatch")
    if len(markers) != static.get("marker_count") or static.get("marker_count") != 56:
        raise ValueError("saved static catalog marker count mismatch")

    confirmed = [row for row in resource_rows if row.get("user_confirmed_target") is True]
    target = next((row for row in confirmed if str(row.get("source_entity_id")) == "2788"), None)
    if len(confirmed) != 1 or target is None:
        raise ValueError("the single user-confirmed T5.3 target 2788 was lost or duplicated")
    if (target.get("family"), target.get("tier"), target.get("enchant"), target.get("x"), target.get("y")) != (27, 5, 3, -379, -24):
        raise ValueError("user-confirmed T5.3 target 2788 provenance/coordinates changed")

    expected_bundle_counts = {
        "imported_bundles": 4,
        "entity_observations": 9522,
        "protocol_evidence": 4599,
        "learned_patterns": 70,
        "autonomous_patterns": 296,
    }
    for key, expected in expected_bundle_counts.items():
        if len(bundle_database.get(key, [])) != expected:
            raise ValueError(f"historical knowledge bundle {key} count mismatch")
    seed_signatures = {row.get("signature") for row in historical_seed if isinstance(row, dict)}
    source_signatures = {row.get("signature") for row in bundle_database["autonomous_patterns"] if isinstance(row, dict)}
    if len(historical_seed) != 296 or seed_signatures != source_signatures:
        raise ValueError("historical autonomous seed does not exactly cover imported signatures")
    if any(row.get("evidence_semantics") !=
           "historical signature evidence only; no semantic event classification" for row in historical_seed):
        raise ValueError("historical seed contains an unsupported semantic promotion")

    if historical_entities.get("entity_record_count") != 9522 or historical_entities.get("source_bundle_count") != 4:
        raise ValueError("historical entity evidence index count mismatch")
    if historical_manifest.get("entity_records") != 9522 or historical_manifest.get("protocol_events") != 4599:
        raise ValueError("historical knowledge manifest count mismatch")
    if historical_manifest.get("learned_patterns") != 70 or historical_manifest.get("autonomous_signatures") != 296:
        raise ValueError("historical pattern manifest count mismatch")
    if historical_manifest.get("source_sha256") != hashlib.sha256(bundle_path.read_bytes()).hexdigest():
        raise ValueError("historical runtime assets were generated from a different bundle database")
    if len(historical_learned) != 70:
        raise ValueError("historical learned-pattern reference asset is incomplete")
    if sum(row.get("observations", 0) for row in historical_protocol) > 4599:
        raise ValueError("historical protocol shape observations exceed source event count")

    coordinate_policy = generated.get("coordinate_policy", "")
    if "verified zone/session transform" not in coordinate_policy:
        raise ValueError("historical coordinate safety policy is missing")

    return {
        "status": "PASS",
        "resource_observations": EXPECTED_RESOURCE_OBSERVATIONS,
        "resource_families": len(families),
        "mobile_entity_observations": EXPECTED_MOBILE_OBSERVATIONS,
        "mobile_type_ids": len(index_ids),
        "dictionary_coverage": f"{len(dictionary_ids & index_ids)}/{len(index_ids)}",
        "external_dictionary_entries": len(dictionary),
        "historical_mobile_type_ids": len(historical_mobile_ids),
        "historical_mobile_dictionary_coverage": f"{len(historical_mobile_ids - set(missing_historical_ids))}/{len(historical_mobile_ids)}",
        "historical_resource_families": sorted(historical_resource_families, key=int),
        "saved_catalog": {"entities": len(entities), "nodes": len(resource_rows), "mobs": len(mob_rows), "markers": len(markers)},
        "confirmed_t53_target": "2788",
        "historical_knowledge": {
            "bundles": len(bundle_database["imported_bundles"]),
            "entity_records": len(bundle_database["entity_observations"]),
            "protocol_events": len(bundle_database["protocol_evidence"]),
            "learned_patterns": len(bundle_database["learned_patterns"]),
            "autonomous_signatures_seeded": len(historical_seed),
            "mobile_type_groups": len(historical_entities.get("mobile_types", {})),
            "resource_family_groups": len(historical_entities.get("resource_families", {})),
            "protocol_shape_groups": len(historical_protocol),
            "learned_pattern_reference_rows": len(historical_learned),
        },
        "historical_coordinates_projected": False,
    }


if __name__ == "__main__":
    repository_root = Path(__file__).resolve().parents[2]
    print(json.dumps(validate(repository_root), indent=2, sort_keys=True))
