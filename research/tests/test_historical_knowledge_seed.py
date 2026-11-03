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
                "entity_observations": [{}] * 9522,
                "protocol_evidence": [{}] * 4599,
                "learned_patterns": [{}] * 70,
                "autonomous_patterns": rows,
            }), encoding="utf-8")

            report = build(root)
            output = json.loads((root / "V2rayNG/app/src/main/assets/albion-historical-autonomous-patterns.json").read_text())
            self.assertEqual("PASS", report["status"])
            self.assertEqual(296, len(output))
            self.assertEqual(4, report["source_bundles"])
            self.assertTrue(all(row["evidence_semantics"].startswith("historical signature evidence only") for row in output))
            self.assertTrue(all(row["status"] == "repeated_candidate" for row in output))


if __name__ == "__main__":
    unittest.main()
