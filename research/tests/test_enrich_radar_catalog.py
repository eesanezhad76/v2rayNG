import csv
import json
import pathlib
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research/tools"))
import enrich_radar_catalog as radar


class RadarEnrichmentTests(unittest.TestCase):
    def test_attack_target_is_joined_to_troll_marker_and_written_to_android_asset(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            db = root / "database"
            analysis = root / "analysis"
            assets = root / "assets"
            db.mkdir(); analysis.mkdir(); assets.mkdir()
            old_db, old_analysis, old_asset = radar.DB, radar.ANALYSIS, radar.ASSET
            radar.DB, radar.ANALYSIS = db, analysis
            radar.ASSET = assets / "albion-static-catalog.json"
            try:
                catalog = {
                    "dataset": radar.DATASET, "node_count": 83, "node_count": 83,
                    "mob_count": 0, "marker_count": 1,
                    "entities": [{"entity_id": "catalog:node:2788", "source_entity_id": 2788,
                                  "last_kind": "static_resource", "x": -379, "y": -24}],
                    "markers": [{
                        "activity": "Troll", "tier": "T5", "enchantment": "E1",
                        "found_ms": 1000, "mob_killed_ms": 2000,
                        "status": "TARGET_FOUND_TO_KILLED"
                    }]
                }
                (db / (radar.DATASET + "-catalog.json")).write_text(json.dumps(catalog), encoding="utf-8")
                attack_fields = [
                    "archive", "pcap", "target_entity_id", "attack_action_count", "first_attack_ms",
                    "last_attack_ms", "last_position_x", "last_position_y", "event123_mobile_type",
                    "event123_name", "known_player_spawn", "leave_observed", "leave_after_last_attack",
                    "health_observations", "min_current_health", "last_current_health", "interpretation"
                ]
                with (db / "2026-11-02-pcap-attack-target-tracks.csv").open("w", newline="", encoding="utf-8") as f:
                    writer = csv.DictWriter(f, fieldnames=attack_fields); writer.writeheader()
                    writer.writerow({
                        "archive": radar.ARCHIVE, "pcap": radar.PCAP, "target_entity_id": "57301",
                        "attack_action_count": "8", "first_attack_ms": "1100", "last_attack_ms": "1700",
                        "last_position_x": "10.5", "last_position_y": "20.5", "event123_mobile_type": "573",
                        "event123_name": "", "known_player_spawn": "false", "leave_observed": "true",
                        "leave_after_last_attack": "true", "health_observations": "12",
                        "min_current_health": "0", "last_current_health": "0",
                        "interpretation": "health_at_or_below_5_then_leave_after_attack_strong_defeat_candidate"
                    })
                    writer.writerow({
                        "archive": "another.zip", "pcap": radar.PCAP, "target_entity_id": "999",
                        "attack_action_count": "1", "first_attack_ms": "1100", "last_attack_ms": "1700"
                    })
                with (db / "2026-11-02-pcap-mob-lifecycle.csv").open("w", newline="", encoding="utf-8") as f:
                    writer = csv.DictWriter(f, fieldnames=["archive", "pcap", "entity_id", "mobile_type_id", "x", "y"])
                    writer.writeheader()
                    writer.writerow({"archive": radar.ARCHIVE, "pcap": radar.PCAP, "entity_id": "57301",
                                     "mobile_type_id": "573", "x": "9.0", "y": "19.0"})
                result = radar.enrich()
                self.assertEqual(1, result["attack_targets"])
                self.assertEqual(1, result["correlated_markers"])
                self.assertEqual(1, result["troll_targets"])
                saved = json.loads((db / (radar.DATASET + "-catalog.json")).read_text(encoding="utf-8"))
                target = next(x for x in saved["entities"] if x.get("source_entity_id") == "57301")
                self.assertEqual("Troll", target["marker_activity"])
                self.assertEqual("Troll #573", target["name"])
                self.assertEqual(10.5, target["x"])
                self.assertTrue(target["targeted_by_player"])
                self.assertFalse(saved["markers"][0]["attack_target_correlation"]["identity_proven"])
                self.assertTrue((assets / "albion-static-catalog.json").is_file())
            finally:
                radar.DB, radar.ANALYSIS, radar.ASSET = old_db, old_analysis, old_asset


if __name__ == "__main__":
    unittest.main()
