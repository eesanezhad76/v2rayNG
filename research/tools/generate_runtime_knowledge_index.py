#!/usr/bin/env python3
"""Regenerate the coordinate-free runtime research index from audited CSV catalogs."""
from __future__ import annotations

import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path


RESOURCE_CSV = "research/database/albion-resource-node-catalog-2026-11-03.csv"
MOBILE_CSV = "research/database/albion-mobile-entity-catalog-2026-11-03.csv"
OUTPUT = "V2rayNG/app/src/main/assets/albion-research-knowledge-index.json"
COORDINATE_POLICY = (
    "Historical coordinates are intentionally excluded from this knowledge index. "
    "Do not place observations from different captures onto the live player-centered "
    "map without a verified zone/session transform."
)


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def git_blob_sha1(path: Path) -> str:
    data = path.read_bytes()
    header = f"blob {len(data)}\\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def counts(rows: list[dict[str, str]], field: str, fallback: str | None = None) -> dict[str, int]:
    result: collections.Counter[str] = collections.Counter()
    for row in rows:
        value = row.get(field, "") or fallback
        if value:
            result[value] += 1
    return dict(sorted(result.items()))


def unique_ints(rows: list[dict[str, str]], field: str) -> list[int]:
    values = set()
    for row in rows:
        try:
            value = int(row.get(field, ""))
        except (TypeError, ValueError):
            continue
        if value > 0:
            values.add(value)
    return sorted(values)


def build_index(root: Path, source_branch: str = "albion-db-runtime-wiring-2026-11-05") -> dict:
    resource_path = root / RESOURCE_CSV
    mobile_path = root / MOBILE_CSV
    resource_rows = read_rows(resource_path)
    mobile_rows = read_rows(mobile_path)

    resource_groups: dict[str, list[dict[str, str]]] = collections.defaultdict(list)
    for row in resource_rows:
        resource_groups[row.get("type_number", "")].append(row)

    resource_families = {}
    for family in sorted(resource_groups, key=lambda value: int(value)):
        rows = resource_groups[family]
        resource_families[family] = {
            "family": int(family),
            "resource_types": counts(rows, "resource_type", "unknown"),
            "display_names": counts(rows, "resource_display_name", "unknown"),
            "mapping_statuses": counts(rows, "mapping_status", "UNRESOLVED"),
            "tiers": unique_ints(rows, "effective_tier") or unique_ints(rows, "tier"),
            "mobile_type_ids": unique_ints(rows, "mobile_type_id"),
            "captures": sorted({row["archive"] for row in rows if row.get("archive")}),
            "pcaps": sorted({row["pcap"] for row in rows if row.get("pcap")}),
            "observation_count": len(rows),
            "confirmed_count": sum(row.get("user_confirmed_target", "").lower() == "true" for row in rows),
        }

    mobile_groups: dict[str, list[dict[str, str]]] = collections.defaultdict(list)
    for row in mobile_rows:
        mobile_groups[row.get("mobile_type_id", "")].append(row)

    mobile_types = {}
    for type_id in sorted(mobile_groups, key=lambda value: int(value)):
        rows = mobile_groups[type_id]
        mobile_types[type_id] = {
            "mobile_type_id": int(type_id),
            "names": counts(rows, "resolved_mob_name"),
            "unique_names": counts(rows, "mobile_type_unique_name"),
            "tiers": unique_ints(rows, "dictionary_tier"),
            "categories": counts(rows, "dictionary_category"),
            "dangers": counts(rows, "dictionary_danger"),
            "loot_types": counts(rows, "dictionary_loot_type"),
            "harvestable_values": counts(rows, "dictionary_is_harvestable"),
            "observation_count": len(rows),
            "captures": sorted({row["archive"] for row in rows if row.get("archive")}),
        }

    return {
        "schema_version": 1,
        "generated_from": {
            "resource_catalog": RESOURCE_CSV,
            "mobile_entity_catalog": MOBILE_CSV,
            "source_branch": source_branch,
            "resource_csv_blob_sha": git_blob_sha1(resource_path),
            "mobile_csv_blob_sha": git_blob_sha1(mobile_path),
            "coordinate_policy": COORDINATE_POLICY,
        },
        "totals": {
            "resource_observations": len(resource_rows),
            "resource_families": len(resource_families),
            "mobile_entity_observations": len(mobile_rows),
            "mobile_type_ids": len(mobile_types),
            "resource_confirmed_marker_rows": sum(
                row.get("user_confirmed_target", "").lower() == "true" for row in resource_rows
            ),
        },
        "resource_families": resource_families,
        "mobile_types": mobile_types,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-branch", default="albion-db-runtime-wiring-2026-11-05")
    parser.add_argument("--check", action="store_true", help="fail if the shipped asset differs")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    generated = build_index(root, args.source_branch)
    output = root / OUTPUT
    serialized = json.dumps(generated, ensure_ascii=False, indent=2) + "\\n"
    if args.check:
        if not output.is_file() or output.read_text(encoding="utf-8") != serialized:
            raise SystemExit("runtime knowledge index is stale; regenerate it from the source CSV catalogs")
    else:
        output.write_text(serialized, encoding="utf-8")
    print(json.dumps(generated["totals"], sort_keys=True))


if __name__ == "__main__":
    main()
