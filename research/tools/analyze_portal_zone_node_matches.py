#!/usr/bin/env python3
"""Cross-capture Portal node matching. Exact successful Operation 41 strings alone define zones."""
from __future__ import annotations
import json, math, re
from collections import defaultdict
from pathlib import Path

TARGET_ARCHIVE = "Albion2.zip"
TARGET_PCAP_FRAGMENT = "went from yellow 5 region to fort sterling"
RESOURCE_KINDS = {"static_resource", "harvestable"}
OBSERVATIONS = Path("research/analysis/2026-11-02-all-inbox-observations.jsonl")
OUT_JSON = Path("research/analysis/2026-11-04-portal-zone-cross-capture-matches.json")
OUT_MD = Path("research/analysis/2026-11-04-portal-zone-cross-capture-matches.md")


def parse_fields(detail):
    return {m.group(1): m.group(2) for m in re.finditer(r"(?:^|\s)([A-Za-z_][A-Za-z0-9_]*)=([^\s]+)", detail)}


def key(row):
    return str(row.get("archive", "")), str(row.get("pcap", ""))


def operation41(row):
    detail = str(row.get("detail", ""))
    if "kind=operation_response op=41" not in detail:
        return None
    f = parse_fields(detail)
    t = re.search(r"\bt=(\d+)", detail)
    p0 = re.search(r"\bparam0_value=(.*?)\s+params=\{", detail)
    return {
        "timestamp_ms": int(t.group(1)) if t else None,
        "return_code": f.get("return_code"),
        "param0_type": f.get("param0_type"),
        "param0_value": p0.group(1) if p0 else f.get("param0_value"),
        "archive": row.get("archive"), "pcap": row.get("pcap"),
    }


def node(row):
    f = parse_fields(str(row.get("detail", "")))
    if f.get("kind") not in RESOURCE_KINDS:
        return None
    try:
        t, entity_id = int(f["t"]), str(f["id"])
        x, y = float(f["x"]), float(f["y"])
    except (KeyError, ValueError, TypeError):
        return None
    if not (math.isfinite(x) and math.isfinite(y)):
        return None
    return {"timestamp_ms": t, "entity_id": entity_id, "kind": f["kind"],
            "x": x, "y": y, "capture": key(row)}


def analyze(rows):
    responses, nodes = defaultdict(list), []
    target = None
    for row in rows:
        k = key(row)
        if k[0] == TARGET_ARCHIVE and TARGET_PCAP_FRAGMENT in k[1].lower():
            target = k
        r = operation41(row)
        if r:
            responses[k].append(r)
        n = node(row)
        if n:
            nodes.append(n)
    if target is None:
        raise ValueError("Target Portal-to-Fort-Sterling capture not found")
    for items in responses.values():
        items.sort(key=lambda r: r["timestamp_ms"] or -1)

    def zone_at(k, t):
        eligible = [r for r in responses.get(k, [])
                    if r["return_code"] == "0" and r["param0_type"] == "String"
                    and r["param0_value"] and r["timestamp_ms"] is not None
                    and r["timestamp_ms"] <= t]
        return max(eligible, key=lambda r: r["timestamp_ms"])["param0_value"] if eligible else None

    for n in nodes:
        n["zone_id"] = zone_at(n["capture"], n["timestamp_ms"])
    target_nodes = sorted((n for n in nodes if n["capture"] == target), key=lambda n: n["timestamp_ms"])
    others = [n for n in nodes if n["capture"] != target]
    output_nodes = []
    for n in target_nodes:
        same_id, near, same_zone = [], [], []
        for c in others:
            d = math.hypot(c["x"] - n["x"], c["y"] - n["y"])
            item = {"archive": c["capture"][0], "pcap": c["capture"][1],
                    "entity_id": c["entity_id"], "x": c["x"], "y": c["y"],
                    "distance": round(d, 4), "zone_id": c["zone_id"]}
            if c["entity_id"] == n["entity_id"]:
                same_id.append(item)
            if c["kind"] == n["kind"] and d <= 3.0:
                near.append(item)
                if n["zone_id"] and c["zone_id"] == n["zone_id"]:
                    same_zone.append(item)
        output_nodes.append({"timestamp_ms": n["timestamp_ms"], "entity_id": n["entity_id"],
            "kind": n["kind"], "x": n["x"], "y": n["y"], "zone_id": n["zone_id"],
            "same_zone_coordinate_matches": same_zone,
            "near_coordinate_matches_any_zone": near,
            "same_numeric_id_matches_any_zone": same_id})

    zone_groups = defaultdict(lambda: {"captures": set(), "resource_observations": 0, "responses": 0})
    for k, items in responses.items():
        for r in items:
            if r["return_code"] == "0" and r["param0_type"] == "String" and r["param0_value"]:
                z = zone_groups[r["param0_value"]]
                z["captures"].add(f"{k[0]} :: {k[1]}")
                z["responses"] += 1
    for n in nodes:
        if n["zone_id"]:
            zone_groups[n["zone_id"]]["resource_observations"] += 1
    summary = [{"zone_id": z, "capture_count": len(v["captures"]),
                "captures": sorted(v["captures"]), "resource_observations": v["resource_observations"],
                "operation41_responses": v["responses"]}
               for z, v in sorted(zone_groups.items())]
    return {"schema_version": 1,
        "method": "Only exact successful Operation 41 param0 String values establish zone identity.",
        "target_capture": {"archive": target[0], "pcap": target[1]},
        "target_operation41_responses": responses.get(target, []),
        "target_resource_observation_count": len(target_nodes),
        "target_nodes": output_nodes, "zone_capture_summary": summary,
        "limitations": [
            "Captures without a decoded successful Operation 41 string remain zone-unverified.",
            "Coordinate matches without the same exact zone ID are candidates, not proof.",
            "Numeric entity IDs can be reused; distant ID matches are reported as collision candidates."]}


def render_markdown(result):
    lines = ["# Portal-to-Fort-Sterling cross-capture node audit", "",
        f"- Target: {result['target_capture']['archive']} / {result['target_capture']['pcap']}",
        f"- Target resource observations: {result['target_resource_observation_count']}", "",
        "## Operation 41 responses", ""]
    if not result["target_operation41_responses"]:
        lines.append("- No Operation 41 responses decoded.")
    for r in result["target_operation41_responses"]:
        lines.append(f"- t={r['timestamp_ms']} return={r['return_code']} type={r['param0_type']} param0={r['param0_value']!r}")
    lines += ["", "## Node matching", "",
        "| ID | Coordinates | Zone ID | Same-zone nearby | Nearby any zone | Same ID any zone |",
        "|---:|---:|---|---:|---:|---:|"]
    for n in result["target_nodes"]:
        lines.append(f"| {n['entity_id']} | ({n['x']}, {n['y']}) | {n['zone_id'] or 'UNVERIFIED'} | "
            f"{len(n['same_zone_coordinate_matches'])} | {len(n['near_coordinate_matches_any_zone'])} | "
            f"{len(n['same_numeric_id_matches_any_zone'])} |")
    lines += ["", "## Captures grouped by exact zone ID", ""]
    for z in result["zone_capture_summary"]:
        lines.append(f"- {z['zone_id']}: {z['capture_count']} captures; {z['resource_observations']} resource observations; {z['operation41_responses']} responses")
        lines.extend(f"  - {c}" for c in z["captures"])
    lines += ["", "## Limitations", ""]
    lines.extend(f"- {x}" for x in result["limitations"])
    return "\n".join(lines) + "\n"


def main():
    if not OBSERVATIONS.is_file():
        raise FileNotFoundError(f"Missing {OBSERVATIONS}; run PCAP audit first")
    with OBSERVATIONS.open(encoding="utf-8") as stream:
        result = analyze(json.loads(line) for line in stream if line.strip())
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    OUT_MD.write_text(render_markdown(result), encoding="utf-8")
    print(f"PORTAL_ZONE_NODE_AUDIT nodes={result['target_resource_observation_count']} op41={len(result['target_operation41_responses'])} zones={len(result['zone_capture_summary'])}")


if __name__ == "__main__":
    main()
