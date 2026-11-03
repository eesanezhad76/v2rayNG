#!/usr/bin/env python3
"""Build a bounded runtime seed from the four imported historical knowledge bundles.

Only autonomous event-shape evidence is copied into the APK. It remains an unknown /
candidate signature; no event semantics or game names are inferred from this file.
"""
from __future__ import annotations

import json
from pathlib import Path

SOURCE = Path("research/database/albion-knowledge-bundle-import.json")
ASSETS = Path("V2rayNG/app/src/main/assets")
DESTINATION = ASSETS / "albion-historical-autonomous-patterns.json"
EXPECTED_SIGNATURES = 296
EXPECTED_ENTITY_RECORDS = 9522
EXPECTED_PROTOCOL_EVENTS = 4599
EXPECTED_LEARNED_PATTERNS = 70

ALLOWED_FIELDS = (
    "signature",
    "event_code",
    "parameter_shape",
    "first_seen_ms",
    "last_seen_ms",
    "sightings",
    "capture_sessions",
    "labels",
    "status",
    "heuristic_evidence",
    "evidence_semantics",
    "source_bundles",
)


def build(root: Path) -> dict:
    source = root / SOURCE
    assets_dir = root / ASSETS
    destination = assets_dir / "albion-historical-autonomous-patterns.json"
    database = json.loads(source.read_text(encoding="utf-8"))
    rows = database.get("autonomous_patterns")
    if not isinstance(rows, list):
        raise ValueError("historical knowledge database has no autonomous_patterns array")

    by_signature = {}
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("autonomous pattern row is not an object")
        signature = row.get("signature")
        if not isinstance(signature, str) or len(signature) != 64:
            raise ValueError("autonomous pattern has an invalid signature")
        if signature in by_signature:
            raise ValueError(f"duplicate autonomous signature: {signature}")
        if not isinstance(row.get("event_code"), int) or not isinstance(row.get("parameter_shape"), str):
            raise ValueError(f"autonomous signature {signature} lacks event shape fields")
        safe = {key: row[key] for key in ALLOWED_FIELDS if key in row}
        safe["status"] = str(safe.get("status") or "unknown_candidate")
        safe["evidence_semantics"] = "historical signature evidence only; no semantic event classification"
        safe["capture_sessions"] = sorted({str(value) for value in safe.get("capture_sessions", [])})
        safe["source_bundles"] = sorted({str(value) for value in safe.get("source_bundles", [])})
        safe["labels"] = [label for label in safe.get("labels", []) if isinstance(label, dict)]
        by_signature[signature] = safe

    if len(by_signature) != EXPECTED_SIGNATURES:
        raise ValueError(
            f"expected {EXPECTED_SIGNATURES} historical autonomous signatures, found {len(by_signature)}"
        )

    output = [by_signature[key] for key in sorted(by_signature)]
    entities = database.get("entity_observations", [])
    protocol_events = database.get("protocol_evidence", [])
    learned_patterns = database.get("learned_patterns", [])
    imported_bundles = database.get("imported_bundles", [])
    if len(entities) != EXPECTED_ENTITY_RECORDS:
        raise ValueError(f"expected {EXPECTED_ENTITY_RECORDS} entity records, found {len(entities)}")
    if len(protocol_events) != EXPECTED_PROTOCOL_EVENTS:
        raise ValueError(f"expected {EXPECTED_PROTOCOL_EVENTS} protocol events, found {len(protocol_events)}")
    if len(learned_patterns) != EXPECTED_LEARNED_PATTERNS:
        raise ValueError(f"expected {EXPECTED_LEARNED_PATTERNS} learned patterns, found {len(learned_patterns)}")
    if len(imported_bundles) != 4:
        raise ValueError(f"expected four imported bundles, found {len(imported_bundles)}")

    def provenance(row):
        sessions = {str(row.get("session_started_ms"))} if row.get("session_started_ms") is not None else set()
        bundles = {str(value) for value in row.get("source_bundles", [])}
        return sessions, bundles

    mobile_groups = {}
    family_groups = {}
    for row in entities:
        if not isinstance(row, dict):
            continue
        kind = row.get("last_kind")
        if kind == "mob" and row.get("mobile_type_id") is not None:
            key = str(row["mobile_type_id"])
            group = mobile_groups.setdefault(key, {"record_count": 0, "sessions": set(), "bundles": set(), "names": {}})
            group["record_count"] += 1
            sessions, bundles = provenance(row)
            group["sessions"].update(sessions)
            group["bundles"].update(bundles)
            name = row.get("name")
            if isinstance(name, str) and name:
                group["names"][name] = group["names"].get(name, 0) + 1
        if kind in {"static_resource", "harvestable", "resource_state"} and row.get("family") is not None:
            key = str(row["family"])
            group = family_groups.setdefault(key, {"record_count": 0, "sessions": set(), "bundles": set(), "tiers": set(), "enchantments": set()})
            group["record_count"] += 1
            sessions, bundles = provenance(row)
            group["sessions"].update(sessions)
            group["bundles"].update(bundles)
            if row.get("tier") is not None:
                group["tiers"].add(int(row["tier"]))
            if row.get("enchant") is not None:
                group["enchantments"].add(int(row["enchant"]))

    def serialise_groups(groups, include_names=False):
        result = {}
        for key, group in groups.items():
            item = {
                "record_count": group["record_count"],
                "capture_session_count": len(group["sessions"]),
                "source_bundle_count": len(group["bundles"]),
            }
            if include_names:
                item["names"] = dict(sorted(group["names"].items()))
            else:
                item["tiers"] = sorted(group["tiers"])
                item["enchantments"] = sorted(group["enchantments"])
            result[key] = item
        return dict(sorted(result.items(), key=lambda pair: int(pair[0])))

    entity_index = {
        "schema_version": 1,
        "source_bundle_count": len(imported_bundles),
        "entity_record_count": len(entities),
        "mobile_types": serialise_groups(mobile_groups, include_names=True),
        "resource_families": serialise_groups(family_groups),
    }

    protocol_shapes = {}
    for row in protocol_events:
        if not isinstance(row, dict):
            continue
        event_code = row.get("event_code")
        schema = row.get("parameter_schema")
        if event_code is None or not isinstance(schema, dict):
            continue
        shape = ",".join(f"{key}={schema[key]}" for key in sorted(schema, key=lambda value: str(value)))
        key = f"{event_code}|{row.get('kind', 'unknown')}|{shape}"
        group = protocol_shapes.setdefault(key, {
            "event_code": int(event_code), "kind": str(row.get("kind", "unknown")),
            "parameter_schema": shape, "observations": 0, "source_bundles": set()
        })
        group["observations"] += 1
        group["source_bundles"].update(str(value) for value in row.get("source_bundles", []))
    protocol_index = [
        {**{key: value for key, value in row.items() if key != "source_bundles"},
         "source_bundle_count": len(row["source_bundles"])}
        for _, row in sorted(protocol_shapes.items())
    ]

    learned_output = []
    for row in learned_patterns:
        if not isinstance(row, dict) or not row:
            continue
        learned_output.append({
            key: row[key] for key in (
                "signature", "activity", "kind", "event_code", "type_id", "family", "tier",
                "enchant", "overlay_tier_label", "overlay_enchant_label", "parameter_keys",
                "samples", "distinct_entities_max_within_one_capture", "observation_count",
                "event_codes", "complete_pairs", "heuristic_score", "source_bundles"
            ) if key in row
        })

    assets_dir.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (assets_dir / "albion-historical-entity-evidence.json").write_text(
        json.dumps(entity_index, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (assets_dir / "albion-historical-protocol-shapes.json").write_text(
        json.dumps(protocol_index, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    (assets_dir / "albion-historical-learned-patterns.json").write_text(
        json.dumps(learned_output, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    source_sha256 = __import__("hashlib").sha256(source.read_bytes()).hexdigest()
    manifest = {
        "schema_version": 1, "source_sha256": source_sha256,
        "source_bundle_count": len(imported_bundles), "entity_records": len(entities),
        "protocol_events": len(protocol_events), "learned_patterns": len(learned_patterns),
        "autonomous_signatures": len(output), "entity_mobile_type_groups": len(mobile_groups),
        "entity_resource_family_groups": len(family_groups), "protocol_shape_groups": len(protocol_index),
        "policy": "Historical data is evidence-only. Runtime annotations never promote semantic labels or historical coordinates."
    }
    (assets_dir / "albion-historical-knowledge-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {
        "status": "PASS",
        "source_bundles": len(imported_bundles),
        "entity_records_preserved": len(entities),
        "protocol_events_preserved": len(protocol_events),
        "learned_patterns_preserved": len(learned_output),
        "autonomous_signatures_seeded_to_runtime": len(output),
        "entity_mobile_type_groups": len(mobile_groups),
        "entity_resource_family_groups": len(family_groups),
        "protocol_shape_groups": len(protocol_index),
        "outputs": [
            str(destination),
            str(assets_dir / "albion-historical-entity-evidence.json"),
            str(assets_dir / "albion-historical-protocol-shapes.json"),
            str(assets_dir / "albion-historical-learned-patterns.json"),
            str(assets_dir / "albion-historical-knowledge-manifest.json"),
        ],
    }

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    print(json.dumps(build(root), indent=2, sort_keys=True))
