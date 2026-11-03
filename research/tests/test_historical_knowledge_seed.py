import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research" / "tools"))

from generate_historical_knowledge_seed import build


class HistoricalKnowledgeSeedTest(unittest.TestCase):
    def test_builds_bounded_seed_without_semantic_promotion(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "research/database/albion-knowledge-bundle-import.json"
            source.parent.mkdir(parents=True)
            rows = []
            for index in range(296):
                rows.append({
                    "signature": f"{index:064x}",
                    "event_code": 40,
                    "parameter_shape": "0:integer,6:integer",
                    "sightings": index + 1,
                    "capture_sessions": ["capture-a", "capture-b"],
                    "labels": [],
                    "status": "repeated_candidate",
                })
            source.write_text(json.dumps({
                "imported_bundles": [{}, {}, {}, {}],
                "entity_observations": [
                    {"last_kind": "mob", "mobile_type_id": 571, "name": "Ore Elemental",
                     "session_started_ms": 10, "source_bundles": ["bundle-a"]},
                    {"last_kind": "harvestable", "family": 29, "tier": 3, "enchant": 1,
                     "session_started_ms": 10, "source_bundles": ["bundle-a"]},
                ] + [{}] * 9520,
                "protocol_evidence": [
                    {"event_code": 40, "kind": "resource", "parameter_schema": {"0": "Long", "6": "Integer"},
                     "source_bundles": ["bundle-a"]}
                ] + [{}] * 4598,
                "learned_patterns": [
                    {"signature": "b" * 64, "activity": "Ore", "kind": "harvestable",
                     "family": 29, "tier": 3, "enchant": 1, "samples": 3,
                     "complete_pairs": 3, "source_bundles": ["bundle-a"]}
                ] + [{}] * 69,
                "autonomous_patterns": rows,
            }), encoding="utf-8")

            report = build(root)
            output = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-autonomous-patterns.json").read_text())
            self.assertEqual("PASS", report["status"])
            self.assertEqual(296, len(output))
            self.assertEqual(4, report["source_bundles"])
            self.assertTrue(all(row["evidence_semantics"].startswith("historical signature evidence only") for row in output))
            self.assertTrue(all(row["status"] == "repeated_candidate" for row in output))
            entity_index = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-entity-evidence.json").read_text())
            self.assertEqual(9522, entity_index["entity_record_count"])
            self.assertEqual(1, entity_index["mobile_types"]["571"]["record_count"])
            self.assertEqual(1, entity_index["resource_families"]["29"]["record_count"])
            protocol_index = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-protocol-shapes.json").read_text())
            self.assertEqual(1, protocol_index[0]["observations"])
            learned_index = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-learned-patterns.json").read_text())
            self.assertEqual(1, len(learned_index))
            manifest = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-knowledge-manifest.json").read_text())
            self.assertEqual(70, manifest["learned_patterns"])
            self.assertEqual(4599, manifest["protocol_events"])


if __name__ == "__main__":
    unittest.main()
