import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "research/tools/ingest_knowledge_bundle.py"
SPEC = importlib.util.spec_from_file_location("ingest_knowledge_bundle", SCRIPT)
ingest = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ingest)


def make_bundle(path, entities, protocol, patterns, exported_at=100, autonomous=None):
    payloads = {
        "live-knowledge.json": json.dumps(entities).encode(),
        "protocol-evidence.json": json.dumps(protocol).encode(),
        "learned-patterns.json": json.dumps({"schema_version": 1, "patterns": patterns}).encode(),
    }
    if autonomous is not None:
        payloads["autonomous-patterns.json"] = json.dumps(autonomous).encode()
    manifest = {
        "format": "albion-radar-knowledge",
        "schema_version": 1,
        "exported_at_ms": exported_at,
        "files": {name: {"sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
                  for name, data in payloads.items()},
    }
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", json.dumps(manifest))
        for name, data in payloads.items():
            archive.writestr(name, data)


class KnowledgeBundleImportTests(unittest.TestCase):
    def test_valid_bundle_imports_and_repeated_cumulative_export_deduplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            first = root / "first.zip"
            second = root / "second.zip"
            output = root / "central.json"
            entity = {"record_key": "1000:77", "entity_id": "77", "session_started_ms": 1000,
                      "last_seen_ms": 5000, "sightings": 4, "classification": "typed_resource_observation"}
            event = {"time_ms": 4000, "event_code": 40, "kind": "resource", "parameters": {"0": 77}}
            pattern = {"signature": "event40|type574|T5", "samples": 3}
            make_bundle(first, [entity], [event], [pattern], 6000)
            make_bundle(second, [dict(entity, last_seen_ms=7000, sightings=6)], [event], [dict(pattern, samples=4)], 8000)

            self.assertEqual(0, ingest.main([str(first), "--output", str(output)]))
            self.assertEqual(0, ingest.main([str(second), "--output", str(output)]))
            database = json.loads(output.read_text())
            self.assertEqual(2, len(database["imported_bundles"]))
            self.assertEqual(1, len(database["entity_observations"]))
            self.assertEqual(7000, database["entity_observations"][0]["last_seen_ms"])
            self.assertEqual(2, len(database["entity_observations"][0]["source_bundles"]))
            self.assertEqual(1, len(database["protocol_evidence"]))
            self.assertEqual(2, len(database["protocol_evidence"][0]["source_bundles"]))
            self.assertEqual(4, database["learned_patterns"][0]["samples"])
            self.assertEqual(2, len(database["learned_patterns"][0]["source_bundles"]))

    def test_same_runtime_id_in_different_sessions_remains_two_entities(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            first, second, output = root / "a.zip", root / "b.zip", root / "central.json"
            make_bundle(first, [{"record_key": "1000:77", "entity_id": "77", "session_started_ms": 1000}],
                        [], [], 1000)
            make_bundle(second, [{"record_key": "2000:77", "entity_id": "77", "session_started_ms": 2000}],
                        [], [], 2000)
            ingest.main([str(first), str(second), "--output", str(output)])
            database = json.loads(output.read_text())
            self.assertEqual(2, len(database["entity_observations"]))
            self.assertEqual({"1000:77", "2000:77"},
                             {row["record_key"] for row in database["entity_observations"]})


    def test_autonomous_signatures_import_and_cumulative_exports_do_not_inflate_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            first, second, output = root / "first.zip", root / "second.zip", root / "central.json"
            signature = {
                "signature": "abc123", "event_code": 40, "sightings": 12,
                "capture_sessions": ["session-a"], "status": "repeated_single_session_candidate",
                "labels": [{"label_key": "Ore|T3|E1", "completed_samples": 2}],
            }
            make_bundle(first, [], [], [], 1000, [signature])
            newer = dict(signature, sightings=15, capture_sessions=["session-a", "session-b"])
            make_bundle(second, [], [], [], 2000, [newer])
            ingest.main([str(first), str(second), "--output", str(output)])
            database = json.loads(output.read_text())
            self.assertEqual(1, len(database["autonomous_patterns"]))
            imported = database["autonomous_patterns"][0]
            self.assertEqual(15, imported["sightings"])
            self.assertEqual(["session-a", "session-b"], imported["capture_sessions"])
            self.assertEqual(2, imported["labels"][0]["completed_samples"])
            self.assertEqual(2, len(imported["source_bundles"]))

    def test_rebuild_ignores_existing_database_and_reconstructs_from_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            source, output = root / "source.zip", root / "central.json"
            make_bundle(source, [{"record_key": "1000:77", "entity_id": "77",
                                  "session_started_ms": 1000}], [], [], 1000)
            output.write_text(json.dumps({
                "schema_version": 1, "imported_bundles": [{"archive": "stale"}],
                "entity_observations": [], "protocol_evidence": [],
                "learned_patterns": [], "autonomous_patterns": []
            }))
            report = ingest.ANALYSIS / "albion-knowledge-bundle-import.md"
            before = report.read_bytes() if report.exists() else None
            self.assertEqual(0, ingest.main(["--rebuild", str(source), "--output", str(output)]))
            database = json.loads(output.read_text())
            self.assertEqual(1, len(database["imported_bundles"]))
            self.assertEqual("source.zip", database["imported_bundles"][0]["archive"])
            self.assertEqual(1, len(database["entity_observations"]))
            after = report.read_bytes() if report.exists() else None
            self.assertEqual(before, after)
            self.assertTrue(output.with_suffix(".md").exists())

    def test_checksum_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            path = root / "bad.zip"
            make_bundle(path, [], [], [])
            with zipfile.ZipFile(path, "r") as archive:
                members = {name: archive.read(name) for name in archive.namelist()}
            manifest = json.loads(members["manifest.json"])
            manifest["files"]["live-knowledge.json"]["sha256"] = "0" * 64
            members["manifest.json"] = json.dumps(manifest).encode()
            with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
                for name, data in members.items():
                    archive.writestr(name, data)
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                ingest.read_bundle(path)


if __name__ == "__main__":
    unittest.main()
