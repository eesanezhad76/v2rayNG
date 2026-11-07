import csv
import json
import pathlib
import unittest
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[2]
DB = ROOT / "research/database"
ANALYSIS = ROOT / "research/analysis"


def read_csv(path):
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))


class CaptureDatabaseIntegrityTests(unittest.TestCase):
    def test_event46_location_flags_use_archive_pcap_region_entity_key(self):
        nodes = read_csv(DB / "2026-11-02-pcap-harvestable-nodes.csv")
        states = read_csv(DB / "2026-11-02-pcap-resource-state-timelines.csv")
        node_keys = {
            (r["archive"], r["pcap"], r["region_id"], r["entity_id"])
            for r in nodes
        }
        for row in states:
            key = (row["archive"], row["pcap"], row["region_id"], row["entity_id"])
            expected = key in node_keys
            actual = row["has_event40_location"].lower() == "true"
            self.assertEqual(expected, actual, msg=f"incorrect Event 46 join flag for {key}")

    def test_marker_correlation_csv_columns_are_aligned(self):
        path = ANALYSIS / "2026-11-02-overlay-mob-attack-correlation.csv"
        with path.open("r", encoding="utf-8-sig", newline="") as source:
            rows = list(csv.reader(source))
        self.assertTrue(rows)
        width = len(rows[0])
        self.assertEqual(21, width)
        self.assertTrue(all(len(row) == width for row in rows[1:]))
        records = read_csv(path)
        self.assertEqual(Counter({"Troll": 12, "Mobs": 1}), Counter(r["activity"] for r in records))
        self.assertEqual(12, sum(bool(r["region_id"]) for r in records))

    def test_curated_troll_candidates_remain_unproven_and_separate(self):
        rows = read_csv(DB / "albion-troll-target-correlation-2026-11-03.csv")
        self.assertEqual(12, len(rows))
        self.assertEqual(0, sum(r["identity_proven"].lower() == "true" for r in rows))
        self.assertEqual(
            Counter({"Ore Elemental": 11, "Roaming Morgana Crossbowman": 1}),
            Counter(r["resolved_mob_name"] for r in rows),
        )
        self.assertEqual(12, sum(bool(r["region_id"]) for r in rows))

    def test_ore_critter_typeids_match_the_capture_crosswalk(self):
        rows = read_csv(DB / "albion-mobile-type-crosswalk-2026-11-03.csv")
        ore = {
            int(r["mobile_type_id"])
            for r in rows
            if r["loot_type"] == "ORE_CRITTER" and r["is_harvestable"].lower() == "true"
        }
        self.assertEqual({570, 571, 572, 573, 574, 576}, ore)

    def test_aggregate_manifest_counts_match_the_evidence_tables(self):
        manifest = json.loads((DB / "albion-complete-database-manifest-2026-11-03.json").read_text(encoding="utf-8"))
        states = read_csv(DB / "2026-11-02-pcap-resource-state-timelines.csv")
        nodes = read_csv(DB / "2026-11-02-pcap-harvestable-nodes.csv")
        attacks = read_csv(DB / "2026-11-02-pcap-attack-target-tracks.csv")
        markers = read_csv(ANALYSIS / "2026-11-02-overlay-manual-markers.csv")
        mobs = read_csv(DB / "2026-11-02-pcap-mob-lifecycle.csv")
        state_keys = {
            (r["archive"], r["pcap"], r["region_id"], r["entity_id"])
            for r in states
        }
        node_keys = {
            (r["archive"], r["pcap"], r["region_id"], r["entity_id"])
            for r in nodes
        }
        resource = manifest["resource_nodes"]
        self.assertEqual(len(state_keys), resource["state_ids"])
        self.assertEqual(len(state_keys & node_keys), resource["state_exact_event40_joins"])
        self.assertEqual(len(state_keys - node_keys), resource["state_only_ids_without_event40_location"])
        self.assertEqual(sum(int(r["state_event_count"]) for r in states), resource["event46_update_observations"])
        self.assertEqual(sum(int(r["event3_position_samples"] or 0) for r in attacks), manifest["combat"]["attack_target_position_samples"])
        self.assertEqual(len(markers), manifest["manual_markers"]["total"])
        self.assertEqual(
            sum(r["event"] in {"gathering_completed", "node_depleted"} for r in markers),
            manifest["manual_markers"]["completed_lifecycle_samples"],
        )
        mobile_ids = {r["mobile_type_id"] for r in mobs if r["mobile_type_id"] not in {"", "-1"}}
        self.assertEqual(len(mobile_ids), manifest["mobile_entities"]["distinct_mobile_type_ids"])


if __name__ == "__main__":
    unittest.main()
