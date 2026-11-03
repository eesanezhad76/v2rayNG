#!/usr/bin/env python3
"""Build a bounded runtime seed from the four imported historical knowledge bundles.

Only autonomous event-shape evidence is copied into the APK. It remains an unknown /
candidate signature; no event semantics or game names are inferred from this file.
"""
from __future__ import annotations

import json
from pathlib import Path

SOURCE = Path("research/database/albion-knowledge-bundle-import.json")
DESTINATION = Path("V2rayNG/app/src/main/assets/albion-historical-autonomous-patterns.json")
EXPECTED_SIGNATURES = 296

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
    destination = root / DESTINATION
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
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    return {
        "status": "PASS",
        "source_bundles": len(database.get("imported_bundles", [])),
        "entity_records_preserved_in_archive": len(database.get("entity_observations", [])),
        "protocol_events_preserved_in_archive": len(database.get("protocol_evidence", [])),
        "learned_patterns_preserved_in_archive": len(database.get("learned_patterns", [])),
        "autonomous_signatures_seeded_to_runtime": len(output),
        "output": str(DESTINATION),
    }


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[2]
    print(json.dumps(build(root), indent=2, sort_keys=True))
