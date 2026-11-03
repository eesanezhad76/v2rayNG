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
    header = f"blob {len(data)}\\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def validate(root: Path) -> dict:
    assets = root / "V2rayNG/app/src/main/assets"
    research = root / "research/database"
    index_path = assets / "albion-research-knowledge-index.json"
    dictionary_path = assets / "albion-mobile-type-dictionary.json"
    static_path = assets / "albion-static-catalog.json"
    index = _json(index_path)
    dictionary = _json(dictionary_path).get("entries")
    static = _json(static_path)

    if not isinstance(dictionary, dict):
        raise ValueError("mobile type dictionary has no entries object")

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
    if dictionary_ids != index_ids:
        missing = sorted(index_ids - dictionary_ids)
        extra = sorted(dictionary_ids - index_ids)
        raise ValueError(f"mobile dictionary/index ID mismatch; missing={missing}, extra={extra}")

    crosswalk_path = research / "albion-mobile-type-crosswalk-2026-11-03.csv"
    with crosswalk_path.open("r", encoding="utf-8-sig", newline="") as stream:
        crosswalk = list(csv.DictReader(stream))
    crosswalk_ids = {row["mobile_type_id"] for row in crosswalk}
    if crosswalk_ids != index_ids or len(crosswalk) != EXPECTED_MOBILE_TYPE_IDS:
        raise ValueError("mobile type crosswalk does not cover the exact 64 indexed IDs")
    if any(row.get("dictionary_match", "").lower() != "true" for row in crosswalk):
        raise ValueError("one or more observed mobile IDs do not match the external dictionary")

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
        "saved_catalog": {"entities": len(entities), "nodes": len(resource_rows), "mobs": len(mob_rows), "markers": len(markers)},
        "confirmed_t53_target": "2788",
        "historical_coordinates_projected": False,
    }


if __name__ == "__main__":
    repository_root = Path(__file__).resolve().parents[2]
    print(json.dumps(validate(repository_root), indent=2, sort_keys=True))
