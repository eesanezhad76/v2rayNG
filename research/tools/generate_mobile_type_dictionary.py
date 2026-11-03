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
    entries = {}
    for raw_id, row in sorted(mobile.items(), key=lambda item: int(item[0])):
        harvest_values = row.get("harvestable_values", {})
        harvestable = False
        if isinstance(harvest_values, dict) and len(harvest_values) == 1:
            value = str(next(iter(harvest_values))).lower()
            harvestable = value == "true"
            if value not in {"true", "false"}:
                raise ValueError(f"invalid harvestable value for TypeID {raw_id}")
        entries[str(raw_id)] = {
            "display_name": dominant(row.get("names", {})) or f"Mob ID{raw_id}",
            "unique_name": dominant(row.get("unique_names", {})),
            "tier": int(row["tiers"][0]) if len(row.get("tiers", [])) == 1 else -1,
            "category": dominant(row.get("categories", {})) or "other",
            "danger": dominant(row.get("dangers", {})) or "unknown",
            "loot_type": dominant(row.get("loot_types", {})),
            "is_harvestable": harvestable,
            "observation_count": int(row.get("observation_count", 0)),
            "source_capture_count": len(row.get("captures", [])),
            "source": "audited_research_mobile_entity_catalog",
        }
        if entries[str(raw_id)]["observation_count"] <= 0:
            raise ValueError(f"TypeID {raw_id} has no evidence")
    expected = int(index.get("totals", {}).get("mobile_type_ids", -1))
    if len(entries) != expected:
        raise ValueError(f"expected {expected} TypeIDs, generated {len(entries)}")
    if entries.get("482", {}).get("is_harvestable"):
        raise ValueError("Mountain Rabbit 482 must remain non-harvestable")
    for type_id in ("571", "573", "574"):
        row = entries.get(type_id, {})
        if row.get("display_name") != "Ore Elemental" or not row.get("is_harvestable"):
            raise ValueError(f"Ore Elemental TypeID {type_id} crosswalk mismatch")
    output = {"schema_version": 1, "source": "albion-research-knowledge-index.json", "source_branch": index.get("generated_from", {}).get("source_branch"), "coverage": {"observed_mobile_type_ids": len(entries)}, "entries": entries}
    destination = root / DESTINATION
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"status": "PASS", "entries": len(entries), "output": str(destination)}
if __name__ == "__main__":
    print(json.dumps(build(Path(__file__).resolve().parents[2]), indent=2))
