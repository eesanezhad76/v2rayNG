#!/usr/bin/env python3
"""Validate and merge privacy-minimal Albion Radar knowledge ZIP exports.

This importer is intentionally separate from raw-PCAP ingest. It preserves session-scoped
entity identity, deduplicates cumulative exports, and never treats runtime IDs as global.
"""
import argparse
import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
DATABASE = ROOT / "research/database"
ANALYSIS = ROOT / "research/analysis"
FORMAT = "albion-radar-knowledge"
SCHEMA_VERSION = 1
REQUIRED_FILES = ("live-knowledge.json", "protocol-evidence.json", "learned-patterns.json")
OPTIONAL_FILES = ("autonomous-patterns.json",)
MAX_MEMBER_BYTES = 64 * 1024 * 1024
MAX_TOTAL_BYTES = 128 * 1024 * 1024
OUTPUT = DATABASE / "albion-knowledge-bundle-import.json"


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_bundle(path):
    archive_hash = sha256_file(path)
    with zipfile.ZipFile(path) as archive:
        members = [item for item in archive.infolist() if not item.is_dir()]
        names = [item.filename for item in members]
        if len(names) != len(set(names)):
            raise ValueError(f"{path.name}: duplicate ZIP member names")
        by_name = {item.filename: item for item in members}
        if "manifest.json" not in by_name:
            return None
        manifest_info = by_name["manifest.json"]
        if manifest_info.file_size > 1024 * 1024:
            raise ValueError(f"{path.name}: manifest exceeds size limit")
        manifest = json.loads(archive.read(manifest_info).decode("utf-8"))
        if manifest.get("format") != FORMAT:
            return None
        if manifest.get("schema_version") != SCHEMA_VERSION:
            raise ValueError(f"{path.name}: unsupported bundle schema {manifest.get('schema_version')}")
        total = sum(item.file_size for item in members)
        if total > MAX_TOTAL_BYTES:
            raise ValueError(f"{path.name}: expanded bundle exceeds size limit")
        files = manifest.get("files")
        if not isinstance(files, dict):
            raise ValueError(f"{path.name}: manifest files object missing")
        payloads = {}
        for name in REQUIRED_FILES + tuple(name for name in OPTIONAL_FILES if name in by_name or name in files):
            info = by_name.get(name)
            metadata = files.get(name)
            if info is None or not isinstance(metadata, dict):
                raise ValueError(f"{path.name}: required member missing: {name}")
            if info.file_size > MAX_MEMBER_BYTES or info.file_size != metadata.get("bytes"):
                raise ValueError(f"{path.name}: invalid byte count for {name}")
            raw = archive.read(info)
            if sha256(raw) != metadata.get("sha256"):
                raise ValueError(f"{path.name}: SHA-256 mismatch for {name}")
            payloads[name] = raw
        entities = json.loads(payloads["live-knowledge.json"].decode("utf-8"))
        protocol = json.loads(payloads["protocol-evidence.json"].decode("utf-8"))
        patterns = json.loads(payloads["learned-patterns.json"].decode("utf-8"))
        autonomous = json.loads(payloads.get("autonomous-patterns.json", b"[]").decode("utf-8"))
        if not isinstance(entities, list) or not isinstance(protocol, list):
            raise ValueError(f"{path.name}: entity/protocol payloads must be arrays")
        if not isinstance(patterns, dict) or not isinstance(patterns.get("patterns", []), list):
            raise ValueError(f"{path.name}: learned-pattern payload must be an object with a patterns array")
        if not isinstance(autonomous, list):
            raise ValueError(f"{path.name}: autonomous-pattern payload must be an array")
        return {
            "archive": path.name,
            "archive_sha256": archive_hash,
            "exported_at_ms": manifest.get("exported_at_ms"),
            "entity_records": entities,
            "protocol_events": protocol,
            "patterns": patterns.get("patterns", []),
            "pattern_database": patterns,
            "autonomous_patterns": autonomous,
        }


def load_existing(path=OUTPUT):
    if not path.is_file():
        return {"schema_version": 1, "imported_bundles": [], "entity_observations": [],
                "protocol_evidence": [], "learned_patterns": [], "autonomous_patterns": []}
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schema_version") != 1:
        raise ValueError("Existing central knowledge database has an unsupported schema")
    document.setdefault("autonomous_patterns", [])
    for key in ("imported_bundles", "entity_observations", "protocol_evidence", "learned_patterns", "autonomous_patterns"):
        if not isinstance(document.get(key), list):
            raise ValueError(f"Existing central database field is invalid: {key}")
    return document


def merge_bundle(database, bundle):
    bundle_hash = bundle["archive_sha256"]
    existing_imports = {row.get("archive_sha256") for row in database["imported_bundles"]}
    if bundle_hash in existing_imports:
        return False

    database["imported_bundles"].append({
        "archive": bundle["archive"],
        "archive_sha256": bundle_hash,
        "exported_at_ms": bundle["exported_at_ms"],
        "entity_records_in_export": len(bundle["entity_records"]),
        "protocol_events_in_export": len(bundle["protocol_events"]),
        "patterns_in_export": len(bundle["patterns"]),
        "autonomous_patterns_in_export": len(bundle.get("autonomous_patterns", [])),
    })

    entities = {row["record_key"]: row for row in database["entity_observations"]}
    for index, record in enumerate(bundle["entity_records"]):
        if not isinstance(record, dict):
            continue
        entity_id = str(record.get("entity_id", ""))
        session_id = str(record.get("session_started_ms", "unknown-session"))
        record_key = str(record.get("record_key") or f"{session_id}:{entity_id}")
        if not entity_id:
            continue
        current = entities.get(record_key)
        if current is None:
            current = dict(record)
            current["record_key"] = record_key
            current["source_bundles"] = [bundle_hash]
            entities[record_key] = current
        else:
            sources = set(current.get("source_bundles", []))
            sources.add(bundle_hash)
            # Cumulative app exports repeat old observations. Keep the newest snapshot;
            # do not add sightings or merge IDs across different capture sessions.
            if int(record.get("last_seen_ms", 0) or 0) >= int(current.get("last_seen_ms", 0) or 0):
                source_list = sorted(sources)
                current.clear()
                current.update(record)
                current["record_key"] = record_key
                current["source_bundles"] = source_list
            else:
                current["source_bundles"] = sorted(sources)

    protocol = {row["evidence_key"]: row for row in database["protocol_evidence"]}
    for event in bundle["protocol_events"]:
        if not isinstance(event, dict):
            continue
        canonical = json.dumps(event, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        key = sha256(canonical.encode("utf-8"))
        if key not in protocol:
            protocol[key] = {"evidence_key": key, **event, "source_bundles": [bundle_hash]}
        else:
            sources = set(protocol[key].get("source_bundles", []))
            sources.add(bundle_hash)
            protocol[key]["source_bundles"] = sorted(sources)

    patterns = {row["signature"]: row for row in database["learned_patterns"]}
    for pattern in bundle["patterns"]:
        if not isinstance(pattern, dict) or not pattern.get("signature"):
            continue
        signature = str(pattern["signature"])
        current = patterns.get(signature)
        if current is None:
            patterns[signature] = {**pattern, "source_bundles": [bundle_hash]}
        else:
            sources = set(current.get("source_bundles", []))
            sources.add(bundle_hash)
            # Pattern exports are cumulative snapshots: retain the strongest snapshot,
            # never sum sample counts from repeated exports of the same evidence.
            if int(pattern.get("samples", 0) or 0) > int(current.get("samples", 0) or 0):
                patterns[signature] = {**pattern, "source_bundles": sorted(sources)}
            else:
                current["source_bundles"] = sorted(sources)


    autonomous = {row["signature"]: row for row in database["autonomous_patterns"] if row.get("signature")}
    for pattern in bundle.get("autonomous_patterns", []):
        if not isinstance(pattern, dict) or not pattern.get("signature"):
            continue
        signature = str(pattern["signature"])
        current = autonomous.get(signature)
        if current is None:
            autonomous[signature] = {**pattern, "source_bundles": [bundle_hash]}
            continue
        sources = set(current.get("source_bundles", []))
        sources.add(bundle_hash)
        # Cumulative exports are snapshots: union session provenance, but never sum sightings.
        sessions = set(current.get("capture_sessions", []))
        sessions.update(pattern.get("capture_sessions", []))
        # Preserve the strongest completed label evidence per label key.
        labels = {row.get("label_key"): row for row in current.get("labels", []) if isinstance(row, dict)}
        for label in pattern.get("labels", []):
            if not isinstance(label, dict) or not label.get("label_key"):
                continue
            key = label["label_key"]
            if key not in labels or int(label.get("completed_samples", 0) or 0) > int(labels[key].get("completed_samples", 0) or 0):
                labels[key] = dict(label)
        if int(pattern.get("sightings", 0) or 0) > int(current.get("sightings", 0) or 0):
            current = {**pattern, "source_bundles": sorted(sources)}
            autonomous[signature] = current
        current["capture_sessions"] = sorted(sessions)
        current["source_bundles"] = sorted(sources)
        current["labels"] = [labels[key] for key in sorted(labels)]

    database["autonomous_patterns"] = sorted(autonomous.values(), key=lambda row: row["signature"])
    database["entity_observations"] = sorted(entities.values(), key=lambda row: row["record_key"])
    database["protocol_evidence"] = sorted(protocol.values(), key=lambda row: (row.get("time_ms", 0), row["evidence_key"]))
    database["learned_patterns"] = sorted(patterns.values(), key=lambda row: row["signature"])
    return True


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("archives", nargs="+", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=OUTPUT)
    args = parser.parse_args(argv)
    database = load_existing(args.output)
    imported = skipped = 0
    for path in args.archives:
        if not path.is_file() or path.suffix.lower() != ".zip":
            continue
        bundle = read_bundle(path)
        if bundle is None:
            skipped += 1
            continue
        if merge_bundle(database, bundle):
            imported += 1
        else:
            skipped += 1
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(database, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = {
        "imported_bundles_this_run": imported,
        "skipped_non_bundle_or_duplicate_archives": skipped,
        "total_bundles": len(database["imported_bundles"]),
        "session_scoped_entity_records": len(database["entity_observations"]),
        "deduplicated_protocol_events": len(database["protocol_evidence"]),
        "unique_pattern_signatures": len(database["learned_patterns"]),
        "autonomous_event_signatures": len(database["autonomous_patterns"]),
        "database": str(args.output),
    }
    report = ANALYSIS / "albion-knowledge-bundle-import.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text("# Albion knowledge bundle import\n\n" +
        "\n".join(f"- {key}: {value}" for key, value in summary.items()) +
        "\n\nRuntime entity IDs remain scoped by capture session. Repeated cumulative exports are deduplicated; sample counts are never summed across exports.\n",
        encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
