#!/usr/bin/env python3
"""Generate the runtime research knowledge index from the current audited CSV catalogs."""
from __future__ import annotations

import csv
import hashlib
import json
import os
from collections import Counter, defaultdict
from pathlib import Path

RESOURCE_CSV = Path("research/database/albion-resource-node-catalog-2026-11-03.csv")
MOBILE_CSV = Path("research/database/albion-mobile-entity-catalog-2026-11-03.csv")
DESTINATION = Path("V2rayNG/app/src/main/assets/albion-research-knowledge-index.json")
COORDINATE_POLICY = (
    "Historical coordinates are intentionally excluded from this knowledge index. "
    "Do not place observations from different captures onto the live player-centered map "
    "without a verified zone/session transform."
)


def blob_sha1(path: Path) -> str:
    data = path.read_bytes()
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def present(value: object) -> str:
    text = str(value or "").strip()
    return "" if text.lower() in {"", "null", "none", "nan"} else text


def add(counter: Counter, value: object) -> None:
    text = present(value)
    if text:
        counter[text] += 1


def as_int(value: object) -> int | None:
    text = present(value)
    if not text:
        return None
    try:
        return int(float(text))
    except (TypeError, ValueError):
        return None


def read_rows(root: Path, relative: Path) -> list[dict[str, str]]:
    with (root / relative).open("r", encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


def build(root: Path) -> dict:
    resource_rows = read_rows(root, RESOURCE_CSV)
    mobile_rows = read_rows(root, MOBILE_CSV)

    families: dict[str, dict] = {}
    for row in resource_rows:
        family = as_int(row.get("type_number"))
        if family is None:
            continue
        key = str(family)
        item = families.setdefault(key, {
            "family": family,
            "resource_types": Counter(),
            "display_names": Counter(),
            "mapping_statuses": Counter(),
            "tiers": set(),
            "mobile_type_ids": set(),
            "captures": set(),
            "pcaps": set(),
            "observation_count": 0,
            "confirmed_count": 0,
        })
        item["observation_count"] += 1
        add(item["resource_types"], row.get("resource_type") or "unknown")
        add(item["display_names"], row.get("resource_display_name"))
        add(item["mapping_statuses"], row.get("mapping_status"))
        tier = as_int(row.get("effective_tier")) or as_int(row.get("tier"))
        if tier is not None:
            item["tiers"].add(tier)
        type_id = as_int(row.get("mobile_type_id"))
        if type_id is not None and type_id > 0:
            item["mobile_type_ids"].add(type_id)
        archive, pcap = present(row.get("archive")), present(row.get("pcap"))
        if archive:
            item["captures"].add(archive)
        if pcap:
            item["pcaps"].add(pcap)
        if present(row.get("user_confirmed_target")).lower() in {"true", "1", "yes"}:
            item["confirmed_count"] += 1

    resource_families = {}
    for key, item in sorted(families.items(), key=lambda pair: int(pair[0])):
        resource_families[key] = {
            "family": item["family"],
            "resource_types": dict(sorted(item["resource_types"].items())),
            "display_names": dict(sorted(item["display_names"].items())),
            "mapping_statuses": dict(sorted(item["mapping_statuses"].items())),
            "tiers": sorted(item["tiers"]),
            "mobile_type_ids": sorted(item["mobile_type_ids"]),
            "captures": sorted(item["captures"]),
            "pcaps": sorted(item["pcaps"]),
            "observation_count": item["observation_count"],
            "confirmed_count": item["confirmed_count"],
        }

    mobile: dict[str, dict] = {}
    for row in mobile_rows:
        type_id = as_int(row.get("mobile_type_id"))
        if type_id is None or type_id <= 0:
            continue
        key = str(type_id)
        item = mobile.setdefault(key, {
            "mobile_type_id": type_id,
            "names": Counter(),
            "unique_names": Counter(),
            "tiers": set(),
            "categories": Counter(),
            "dangers": Counter(),
            "loot_types": Counter(),
            "harvestable_values": Counter(),
            "captures": set(),
            "observation_count": 0,
        })
        item["observation_count"] += 1
        add(item["names"], row.get("resolved_mob_name") or row.get("name"))
        add(item["unique_names"], row.get("mobile_type_unique_name"))
        for field in ("dictionary_tier",):
            tier = as_int(row.get(field))
            if tier is not None and tier > 0:
                item["tiers"].add(tier)
        add(item["categories"], row.get("dictionary_category"))
        add(item["dangers"], row.get("dictionary_danger"))
        add(item["loot_types"], row.get("dictionary_loot_type"))
        add(item["harvestable_values"], row.get("dictionary_is_harvestable"))
        archive = present(row.get("archive"))
        if archive:
            item["captures"].add(archive)

    mobile_types = {}
    for key, item in sorted(mobile.items(), key=lambda pair: int(pair[0])):
        mobile_types[key] = {
            "mobile_type_id": item["mobile_type_id"],
            "names": dict(sorted(item["names"].items())),
            "unique_names": dict(sorted(item["unique_names"].items())),
            "tiers": sorted(item["tiers"]),
            "categories": dict(sorted(item["categories"].items())),
            "dangers": dict(sorted(item["dangers"].items())),
            "loot_types": dict(sorted(item["loot_types"].items())),
            "harvestable_values": dict(sorted(item["harvestable_values"].items())),
            "observation_count": item["observation_count"],
            "captures": sorted(item["captures"]),
        }

    confirmed = sum(item["confirmed_count"] for item in families.values())
    output = {
        "schema_version": 1,
        "generated_from": {
            "resource_catalog": RESOURCE_CSV.as_posix(),
            "mobile_entity_catalog": MOBILE_CSV.as_posix(),
            "source_branch": os.environ.get("GITHUB_REF_NAME", "local"),
            "resource_csv_blob_sha": blob_sha1(root / RESOURCE_CSV),
            "mobile_csv_blob_sha": blob_sha1(root / MOBILE_CSV),
            "coordinate_policy": COORDINATE_POLICY,
        },
        "totals": {
            "resource_observations": len(resource_rows),
            "resource_families": len(resource_families),
            "mobile_entity_observations": len(mobile_rows),
            "mobile_type_ids": len(mobile_types),
            "resource_confirmed_marker_rows": confirmed,
        },
        "resource_families": resource_families,
        "mobile_types": mobile_types,
    }
    destination = root / DESTINATION
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return output["totals"]


if __name__ == "__main__":
    print(json.dumps({"status": "PASS", **build(Path(__file__).resolve().parents[2])}, indent=2))
