#!/usr/bin/env python3
"""Generate observed mobile TypeID display metadata from the audited research index."""
from __future__ import annotations
import json
from pathlib import Path
INDEX = Path("V2rayNG/app/src/main/assets/albion-research-knowledge-index.json")
DESTINATION = Path("V2rayNG/app/src/main/assets/albion-mobile-type-dictionary.json")
def dominant(values):
    if not isinstance(values, dict) or not values:
        return ""
    return sorted(values.items(), key=lambda item: (-int(item[1]), str(item[0])))[0][0]
def build(root: Path):
    index = json.loads((root / INDEX).read_text(encoding="utf-8"))
    mobile = index.get("mobile_types")
    if not isinstance(mobile, dict) or not mobile:
        raise ValueError("research index has no mobile_types object")
    destination = root / DESTINATION
    # Preserve the broader external dictionary already shipped in the project.
    # The research corpus is a crosswalk/enrichment source, not a replacement catalog.
    existing_root = {}
    existing = {}
    if destination.is_file():
        try:
            parsed_existing = json.loads(destination.read_text(encoding="utf-8"))
            if isinstance(parsed_existing, dict) and isinstance(parsed_existing.get("entries"), dict):
                existing_root = parsed_existing
                existing = parsed_existing["entries"]
        except (OSError, json.JSONDecodeError):
            existing_root = {}
            existing = {}
    entries = dict(existing)
    for raw_id, row in sorted(mobile.items(), key=lambda item: int(item[0])):
        harvest_values = row.get("harvestable_values", {})
        harvestable = False
        if isinstance(harvest_values, dict) and len(harvest_values) == 1:
            value = str(next(iter(harvest_values))).lower()
            harvestable = value == "true"
            if value not in {"true", "false"}:
                raise ValueError(f"invalid harvestable value for TypeID {raw_id}")
        generated = {
            "display_name": dominant(row.get("names", {})) or f"Mob ID{raw_id}",
            "unique_name": dominant(row.get("unique_names", {})),
            "tier": int(row["tiers"][0]) if len(row.get("tiers", [])) == 1 else -1,
            "category": dominant(row.get("categories", {})) or "other",
            "danger": dominant(row.get("dangers", {})) or "unknown",
            "loot_type": dominant(row.get("loot_types", {})),
            "is_harvestable": harvestable,
        }
        # Keep external names/localization/metadata when present; fill missing fields
        # from the observed corpus and always attach provenance counters.
        merged = dict(generated)
        previous = entries.get(str(raw_id), {})
        if isinstance(previous, dict):
            merged.update(previous)
        merged["observation_count"] = int(row.get("observation_count", 0))
        merged["source_capture_count"] = len(row.get("captures", []))
        merged["research_source"] = "audited_research_mobile_entity_catalog"
        entries[str(raw_id)] = merged
        if entries[str(raw_id)]["observation_count"] <= 0:
            raise ValueError(f"TypeID {raw_id} has no evidence")
    expected = int(index.get("totals", {}).get("mobile_type_ids", -1))
    missing = sorted(set(mobile) - set(entries))
    if len(mobile) != expected or missing:
        raise ValueError(f"expected {expected} observed TypeIDs; missing dictionary entries: {missing}")
    if "482" in entries:
        entries["482"]["is_harvestable"] = False
    for type_id in ("571", "573", "574"):
        row = entries.get(type_id, {})
        # These packet-observed TypeIDs have an explicit research crosswalk.
        row["display_name"] = "Ore Elemental"
        row["unique_name"] = dominant(mobile[type_id].get("unique_names", {}))
        row["tier"] = int(mobile[type_id]["tiers"][0])
        row["is_harvestable"] = True
        entries[type_id] = row
    output = dict(existing_root)
    output["schema_version"] = int(existing_root.get("schema_version", 1))
    output["research_enrichment"] = {
        "source": "albion-research-knowledge-index.json",
        "source_branch": index.get("generated_from", {}).get("source_branch"),
        "observed_mobile_type_ids": len(mobile),
    }
    output["coverage"] = {
        "observed_mobile_type_ids": len(mobile),
        "total_dictionary_entries": len(entries),
    }
    output["entries"] = entries
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"status": "PASS", "entries": len(entries), "output": str(destination)}
if __name__ == "__main__":
    print(json.dumps(build(Path(__file__).resolve().parents[2]), indent=2))
