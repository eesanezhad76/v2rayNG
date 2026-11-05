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
    def test_enrich_all_uses_matching_new_capture_instead_of_historical_defaults(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            db = root / "database"
            analysis = root / "analysis"
            assets = root / "assets"
            db.mkdir(); analysis.mkdir(); assets.mkdir()
            old_db, old_analysis, old_asset = radar.DB, radar.ANALYSIS, radar.ASSET
            radar.DB, radar.ANALYSIS = db, analysis
            radar.ASSET = assets / "albion-static-catalog.json"
            dataset = "albion-1793769140776"
            try:
                (db / "albion-mobile-type-dictionary.json").write_text(json.dumps({
                    "entries": {"573": {"display_name": "Ore Elemental", "tier": 3}}
                }), encoding="utf-8")
                (db / (dataset + "-catalog.json")).write_text(json.dumps({
                    "dataset": dataset, "node_count": 1, "mob_count": 0,
                    "entities": [], "markers": [
                        {
                            "activity": "Troll", "tier": "T4", "enchantment": "E1",
                            "found_ms": 1000, "superseded_by_ms": 2000,
                            "status": "TARGET_FOUND_SUPERSEDED_NOT_KILLED"
                        },
                        {
                            "activity": "Troll", "tier": "T4", "enchantment": "E1",
                            "found_ms": 5000, "mob_killed_ms": 9000,
                            "status": "TARGET_FOUND_TO_KILLED"
                        }
                    ]
                }), encoding="utf-8")
                attack_fields = [
                    "archive", "pcap", "region_id", "target_entity_id", "attack_action_count",
                    "first_attack_ms", "last_attack_ms", "last_position_x", "last_position_y",
                    "event123_mobile_type", "event123_name", "known_player_spawn",
                    "leave_observed", "leave_after_last_attack", "health_observations",
                    "min_current_health", "last_current_health", "interpretation"
                ]
                with (db / "2026-11-02-pcap-attack-target-tracks.csv").open(
                    "w", newline="", encoding="utf-8"
                ) as f:
                    writer = csv.DictWriter(f, fieldnames=attack_fields); writer.writeheader()
                    writer.writerow({
                        "archive": "archiveNew-extracted.zip", "pcap": dataset + ".pcap", "region_id": "MAP_A",
                        "target_entity_id": "104009", "attack_action_count": "8",
                        "first_attack_ms": "1000", "last_attack_ms": "8000",
                        "last_position_x": "12.5", "last_position_y": "-4.5",
                        "event123_mobile_type": "573", "known_player_spawn": "false",
                        "leave_observed": "true", "leave_after_last_attack": "true",
                        "health_observations": "5", "min_current_health": "0",
                        "last_current_health": "0", "interpretation": "defeat_candidate"
                    })
                (db / "2026-11-02-pcap-mob-lifecycle.csv").write_text(
                    "archive,pcap,region_id,entity_id,mobile_type_id,x,y\n"
                    "archiveNew-extracted.zip," + dataset + ".pcap,MAP_A,104009,573,12.5,-4.5\n",
                    encoding="utf-8"
                )
                (db / "2026-11-02-region-transition-evidence.csv").write_text(
                    "archive,pcap,timestamp_ms,operation_code,region_id,detail\n"
                    "archiveNew-extracted.zip," + dataset + ".pcap,0,2,MAP_A,op2\n",
                    encoding="utf-8"
                )
                result = radar.enrich_all()
                self.assertEqual(1, len(result))
                self.assertEqual(1, result[0]["attack_targets"])
                self.assertEqual(1, result[0]["correlated_markers"])
                self.assertEqual(1, result[0]["troll_targets"])
                saved = json.loads((db / (dataset + "-catalog.json")).read_text(encoding="utf-8"))
                self.assertEqual("SUPERSEDED", saved["markers"][0]["attack_target_correlation"]["confidence"])
                self.assertNotIn("target_entity_id", saved["markers"][0]["attack_target_correlation"])
                self.assertEqual("104009", saved["markers"][1]["attack_target_correlation"]["target_entity_id"])
                self.assertEqual("HIGH", saved["markers"][1]["attack_target_correlation"]["confidence"])
            finally:
                radar.DB, radar.ANALYSIS, radar.ASSET = old_db, old_analysis, old_asset

    def test_enrichment_never_pairs_markers_with_attack_targets_from_another_region(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            db = root / "database"; analysis = root / "analysis"; assets = root / "assets"
            db.mkdir(); analysis.mkdir(); assets.mkdir()
            old_db, old_analysis, old_asset = radar.DB, radar.ANALYSIS, radar.ASSET
            radar.DB, radar.ANALYSIS, radar.ASSET = db, analysis, assets / "catalog.json"
            dataset = "albion-1793769140776"
            archive = "archiveNew-extracted.zip"
            pcap = dataset + ".pcap"
            try:
                (db / "albion-mobile-type-dictionary.json").write_text(json.dumps({"entries": {}}), encoding="utf-8")
                (db / (dataset + "-catalog.json")).write_text(json.dumps({
                    "dataset": dataset, "entities": [], "markers": [{
                        "activity": "Troll", "tier": "T4", "enchantment": "E1",
                        "found_ms": 5000, "mob_killed_ms": 9000, "status": "TARGET_FOUND_TO_KILLED"
                    }]
                }), encoding="utf-8")
                fields = ["archive", "pcap", "region_id", "target_entity_id", "attack_action_count",
                          "first_attack_ms", "last_attack_ms", "last_position_x", "last_position_y",
                          "event123_mobile_type", "event123_name", "known_player_spawn", "leave_observed",
                          "leave_after_last_attack", "health_observations", "min_current_health",
                          "last_current_health", "interpretation"]
                with (db / "2026-11-02-pcap-attack-target-tracks.csv").open("w", newline="", encoding="utf-8") as f:
                    writer = csv.DictWriter(f, fieldnames=fields); writer.writeheader()
                    writer.writerow({
                        "archive": archive, "pcap": pcap, "region_id": "MAP_B", "target_entity_id": "104009",
                        "attack_action_count": "8", "first_attack_ms": "5000", "last_attack_ms": "8000",
                        "last_position_x": "12.5", "last_position_y": "-4.5", "event123_mobile_type": "573",
                        "known_player_spawn": "false", "leave_observed": "true", "leave_after_last_attack": "true",
                        "health_observations": "5", "min_current_health": "0", "last_current_health": "0",
                        "interpretation": "defeat_candidate"
                    })
                (db / "2026-11-02-pcap-mob-lifecycle.csv").write_text(
                    "archive,pcap,region_id,entity_id,mobile_type_id,x,y\n"
                    + archive + "," + pcap + ",MAP_B,104009,573,12.5,-4.5\n", encoding="utf-8"
                )
                (db / "2026-11-02-region-transition-evidence.csv").write_text(
                    "archive,pcap,timestamp_ms,operation_code,region_id,detail\n"
                    + archive + "," + pcap + ",0,2,MAP_A,op2\n", encoding="utf-8"
                )
                result = radar.enrich(dataset, None, pcap)
                self.assertEqual(0, result["correlated_markers"])
                self.assertEqual(0, result["troll_targets"])
                saved = json.loads((db / (dataset + "-catalog.json")).read_text(encoding="utf-8"))
                self.assertEqual("MAP_A", saved["markers"][0]["region_id"])
                self.assertEqual("NONE", saved["markers"][0]["attack_target_correlation"]["confidence"])
                self.assertEqual("MAP_B", saved["attack_targets"][0]["region_id"])
            finally:
                radar.DB, radar.ANALYSIS, radar.ASSET = old_db, old_analysis, old_asset

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
            (db / "albion-mobile-type-dictionary.json").write_text(json.dumps({
                "entries": {"573": {"display_name": "Ore Elemental", "tier": 3,
                                    "category": "critter", "unique_name": "T3_MOB_CRITTER_ORE_MOUNTAIN_RED",
                                    "localization_tag": "@MOB_T3_MOB_CRITTER_FOREST_RED_OREELEMENTAL",
                                    "max_health": 856, "danger": "normal", "is_harvestable": True}}
            }), encoding="utf-8")
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
                self.assertEqual("Ore Elemental", target["name"])
                self.assertEqual(10.5, target["x"])
                self.assertEqual(3, target["tier"])
                self.assertEqual(5, target["marker_tier"])
                self.assertEqual("critter", target["mob_category"])
                self.assertTrue(target["targeted_by_player"])
                self.assertFalse(saved["markers"][0]["attack_target_correlation"]["identity_proven"])
                self.assertTrue((assets / "albion-static-catalog.json").is_file())
            finally:
                radar.DB, radar.ANALYSIS, radar.ASSET = old_db, old_analysis, old_asset


if __name__ == "__main__":
    unittest.main()
