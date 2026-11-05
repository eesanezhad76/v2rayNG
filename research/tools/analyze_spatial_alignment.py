#!/usr/bin/env python3
"""Compare own-player and typed resource coordinates within each replayed PCAP only."""
import argparse
import csv
import json
import math
import pathlib
import re
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_INPUT = ROOT / "research/analysis/2026-11-02-all-inbox-observations.jsonl"
DEFAULT_JSON = ROOT / "research/analysis/2026-11-02-spatial-alignment.json"
DEFAULT_MD = ROOT / "research/analysis/2026-11-02-spatial-alignment.md"
FIELD_RE = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)=([^\s]+)")


def parse_fields(detail):
    return {match.group(1): match.group(2) for match in FIELD_RE.finditer(str(detail or ""))}


def finite_coordinate(value):
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(number) or abs(number) > 10000:
        return None
    return number


def percentile(values, fraction):
    ordered = sorted(values)
    if not ordered:
        return None
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))]


def analyze_rows(rows, temporal_tolerance_ms=5000, region_transitions=None):
    rows = list(rows)
    timelines = defaultdict(list)
    if region_transitions is not None:
        for transition in region_transitions:
            key = (str(transition.get("archive") or ""), str(transition.get("pcap") or ""))
            try:
                timestamp = int(transition.get("timestamp_ms"))
            except (TypeError, ValueError):
                continue
            region_id = str(transition.get("region_id") or "").strip()
            if region_id:
                timelines[key].append((timestamp, region_id))
    else:
        # Derive confirmed region changes directly from the same capture. A
        # successful op=41 response carries the new region in param0_value;
        # a successful op=2 join response repeats it in parameter 8.
        for row in rows:
            if not isinstance(row, dict):
                continue
            archive = str(row.get("archive") or "")
            pcap = str(row.get("pcap") or "")
            fields = parse_fields(row.get("detail"))
            try:
                timestamp = int(fields.get("t") or row.get("time_ms"))
            except (TypeError, ValueError):
                continue
            if fields.get("kind") != "operation_response":
                continue
            operation = fields.get("op")
            region_id = None
            if operation == "41":
                region_id = fields.get("param0_value")
            elif operation == "2":
                params = re.search(r"params=\{([^}]*)\}", str(row.get("detail") or ""))
                if params:
                    match = re.search(r"(?:^|\s)8=([^\s]+)", params.group(1))
                    if match:
                        region_id = match.group(1)
            if region_id and region_id not in {"null", ""}:
                timelines[(archive, pcap)].append((timestamp, region_id))
    for timeline in timelines.values():
        timeline.sort()

    def region_at(archive, pcap, timestamp):
        if timestamp is None:
            return None
        region = None
        for transition_ms, region_id in timelines.get((archive, pcap), []):
            if transition_ms > timestamp:
                break
            region = region_id
        return region

    grouped = defaultdict(lambda: {"player": [], "nodes": defaultdict(list), "rows": 0})
    for row in rows:
        if not isinstance(row, dict):
            continue
        archive = str(row.get("archive") or "")
        pcap = str(row.get("pcap") or "")
        if not archive or not pcap:
            continue
        fields = parse_fields(row.get("detail"))
        kind = fields.get("kind")
        x = finite_coordinate(fields.get("x"))
        y = finite_coordinate(fields.get("y"))
        try:
            timestamp = int(fields.get("t") or row.get("time_ms"))
        except (TypeError, ValueError):
            timestamp = None
        region_id = region_at(archive, pcap, timestamp)
        group = grouped[(archive, pcap, region_id)]
        group["rows"] += 1
        if x is None or y is None:
            continue
        if kind == "own_player_move":
            group["player"].append({"time_ms": timestamp, "x": x, "y": y})
        elif kind == "harvestable":
            entity_id = fields.get("id")
            if entity_id not in (None, "", "null"):
                group["nodes"][entity_id].append({"time_ms": timestamp, "x": round(x, 3), "y": round(y, 3)})

    captures = []
    for (archive, pcap, region_id), group in sorted(grouped.items(), key=lambda item: (item[0][0], item[0][1], str(item[0][2] or ""))):
        player = group["player"]
        node_observations = group["nodes"]
        conflicts = sorted(
            entity_id for entity_id, observations in node_observations.items()
            if len({(p["x"], p["y"]) for p in observations}) > 1
        )
        stable_nodes = {
            entity_id: observations[0]
            for entity_id, observations in node_observations.items()
            if len({(p["x"], p["y"]) for p in observations}) == 1
        }
        nodes = list(stable_nodes.values())
        timed_player = [p for p in player if p["time_ms"] is not None]
        timed_pairs = []
        for node in nodes:
            if node["time_ms"] is None:
                continue
            nearby = [p for p in timed_player if abs(p["time_ms"] - node["time_ms"]) <= temporal_tolerance_ms]
            if nearby:
                nearest = min(nearby, key=lambda p: abs(p["time_ms"] - node["time_ms"]))
                timed_pairs.append({
                    "distance": math.hypot(node["x"] - nearest["x"], node["y"] - nearest["y"]),
                    "time_gap_ms": abs(node["time_ms"] - nearest["time_ms"]),
                })

        if len(player) < 10 or len(nodes) < 3 or len(timed_pairs) < 3:
            status = "NOT_VERIFIED"
            distances = []
            player_bounds = None
            node_bounds = None
            overlap = False
        else:
            px = [point["x"] for point in player]
            py = [point["y"] for point in player]
            nx = [point["x"] for point in nodes]
            ny = [point["y"] for point in nodes]
            player_bounds = {"min_x": min(px), "max_x": max(px), "min_y": min(py), "max_y": max(py)}
            node_bounds = {"min_x": min(nx), "max_x": max(nx), "min_y": min(ny), "max_y": max(ny)}
            overlap = not (max(px) < min(nx) or max(nx) < min(px) or max(py) < min(ny) or max(ny) < min(py))
            distances = [pair["distance"] for pair in timed_pairs]
            median = percentile(distances, 0.5)
            p90 = percentile(distances, 0.9)
            plausible = median <= 100.0 and p90 <= 250.0
            status = "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE" if plausible else "NOT_VERIFIED"

        median = percentile(distances, 0.5)
        p90 = percentile(distances, 0.9)
        captures.append({
            "archive": archive,
            "pcap": pcap,
            "region_id": region_id,
            "status": status,
            "evidence_grade": "same_capture_same_region_time_aligned_spatial_heuristic_only",
            "replay_observation_rows": group["rows"],
            "player_position_samples": len(player),
            "typed_resource_nodes": len(nodes),
            "resource_entity_ids_with_coordinate_conflicts": conflicts,
            "player_bounds": player_bounds,
            "resource_bounds": node_bounds,
            "bounding_boxes_overlap": overlap,
            "temporal_player_node_pairs": len(timed_pairs),
            "temporal_tolerance_ms": temporal_tolerance_ms,
            "median_nearest_player_distance_at_node_observation": round(median, 3) if median is not None else None,
            "p90_nearest_player_distance_at_node_observation": round(p90, 3) if p90 is not None else None,
            "warning": "Candidate evidence only. Coordinates are compared only within the same archive, PCAP, and confirmed region; no cross-region or cross-capture joins are allowed."
        })

    overall = "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE" if any(
        capture["status"] == "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE" for capture in captures
    ) else "NOT_VERIFIED"
    return {
        "schema_version": 3,
        "overall_status": overall,
        "capture_scope": "archive+pcap+confirmed_region grouped independently; time-aligned player/node observations only; no cross-region or cross-capture joins",
        "captures": captures,
        "warning": "A plausible result supports local coordinate alignment only inside one confirmed region. It is not a world-map or zone-identity proof."
    }

def read_rows(path):
    with path.open(encoding="utf-8", errors="replace") as source:
        for line in source:
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=pathlib.Path, default=DEFAULT_INPUT)
    parser.add_argument("--json-output", type=pathlib.Path, default=DEFAULT_JSON)
    parser.add_argument("--markdown-output", type=pathlib.Path, default=DEFAULT_MD)
    args = parser.parse_args()
    if not args.input.is_file():
        raise SystemExit("Missing replay observations file: " + str(args.input))
    report = analyze_rows(read_rows(args.input))
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    lines = [
        "# Same-capture player/resource coordinate alignment",
        "",
        "Overall: " + report["overall_status"],
        "",
        report["warning"],
        "",
    ]
    for capture in report["captures"]:
        lines += [
            "## " + capture["archive"] + " / " + capture["pcap"] + " / region " + str(capture.get("region_id") or "unresolved"),
            "",
            "- Region ID: " + str(capture.get("region_id") or "unresolved"),
            "- Status: " + capture["status"],
            "- Player position samples: " + str(capture["player_position_samples"]),
            "- Typed Event-40 resource nodes: " + str(capture["typed_resource_nodes"]),
            "- Bounding boxes overlap: " + str(capture["bounding_boxes_overlap"]),
            "- Median nearest-node distance: " + str(capture["median_nearest_player_distance_at_node_observation"]),
            "- P90 nearest-node distance: " + str(capture["p90_nearest_player_distance_at_node_observation"]),
            "- Node IDs with coordinate conflicts: " + str(len(capture["resource_entity_ids_with_coordinate_conflicts"])),
            "- Warning: " + capture["warning"],
            "",
        ]
    args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
    args.markdown_output.write_text("\n".join(lines), encoding="utf-8")
    try:
        from enrich_radar_catalog import enrich_all
        radar_summary = enrich_all()
    except Exception as exc:
        raise SystemExit("Radar catalog enrichment failed: " + str(exc))
    print("RADAR_CATALOG_ENRICHMENT " + json.dumps(radar_summary, sort_keys=True))
    print(json.dumps({
        "overall_status": report["overall_status"],
        "captures": len(report["captures"]),
        "plausible_captures": sum(c["status"] == "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE" for c in report["captures"]),
        "json": str(args.json_output),
    }, sort_keys=True))


if __name__ == "__main__":
    main()
