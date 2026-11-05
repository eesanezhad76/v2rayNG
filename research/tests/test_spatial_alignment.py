import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research/tools"))
import analyze_spatial_alignment as spatial


class SpatialAlignmentTests(unittest.TestCase):
    def test_player_and_resource_positions_are_compared_at_matching_times(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip", "pcap": "aligned.pcap", "time_ms": index * 1000,
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y) in enumerate([(4, 5), (10, 11), (17, 18)]):
            rows.append({
                "archive": "same.zip", "pcap": "aligned.pcap", "time_ms": index * 1000,
                "detail": f"kind=harvestable id={700 + index} x={x} y={y}",
            })
        capture = spatial.analyze_rows(rows)["captures"][0]
        self.assertEqual("PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE", capture["status"])
        self.assertEqual(20, capture["player_position_samples"])
        self.assertEqual(3, capture["typed_resource_nodes"])
        self.assertEqual(3, capture["temporal_player_node_pairs"])
        self.assertIn("time_aligned", capture["evidence_grade"])

    def test_replay_jsonl_uses_timestamp_embedded_in_detail(self):
        # Production observations contain t=... inside detail and no top-level time_ms.
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip", "pcap": "production-schema.pcap",
                "detail": f"t={index * 1000} kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y, timestamp) in enumerate([(4, 5, 4000), (10, 11, 10000), (17, 18, 17000)]):
            rows.append({
                "archive": "same.zip", "pcap": "production-schema.pcap",
                "detail": f"t={timestamp} kind=harvestable id={700 + index} x={x} y={y}",
            })
        capture = spatial.analyze_rows(rows)["captures"][0]
        self.assertEqual(3, capture["temporal_player_node_pairs"])
        self.assertEqual("PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE", capture["status"])

    def test_distant_in_time_player_path_does_not_create_false_alignment(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip", "pcap": "same.pcap", "time_ms": index * 1000,
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y) in enumerate([(4, 5), (10, 11), (17, 18)]):
            rows.append({
                "archive": "same.zip", "pcap": "same.pcap", "time_ms": 100000 + index * 1000,
                "detail": f"kind=harvestable id={700 + index} x={x} y={y}",
            })
        capture = spatial.analyze_rows(rows)["captures"][0]
        self.assertEqual("NOT_VERIFIED", capture["status"])
        self.assertEqual(0, capture["temporal_player_node_pairs"])

    def test_coordinates_from_different_captures_are_never_joined(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "a.zip", "pcap": "player.pcap", "time_ms": index * 1000,
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y) in enumerate([(4, 5), (10, 11), (17, 18)]):
            rows.append({
                "archive": "b.zip", "pcap": "resources.pcap", "time_ms": index * 1000,
                "detail": f"kind=harvestable id={700 + index} x={x} y={y}",
            })
        result = spatial.analyze_rows(rows)
        self.assertEqual(2, len(result["captures"]))
        self.assertTrue(all(row["status"] == "NOT_VERIFIED" for row in result["captures"]))
        self.assertEqual("NOT_VERIFIED", result["overall_status"])

    def test_same_pcap_regions_are_never_spatially_joined(self):
        rows = [{
            "archive": "same.zip", "pcap": "region-switch.pcap",
            "detail": "t=0 kind=operation_response op=41 param0_value=MAP_A",
        }]
        for index in range(1, 15):
            rows.append({
                "archive": "same.zip", "pcap": "region-switch.pcap",
                "detail": f"t={index * 1000} kind=own_player_move id=-1 x={index} y={index}",
            })
        rows.append({
            "archive": "same.zip", "pcap": "region-switch.pcap",
            "detail": "t=15000 kind=operation_response op=41 param0_value=MAP_B",
        })
        for entity_id, x, y, timestamp in [
            (701, 13, 13, 15000), (702, 12, 12, 16000), (703, 11, 11, 17000)
        ]:
            rows.append({
                "archive": "same.zip", "pcap": "region-switch.pcap",
                "detail": f"t={timestamp} kind=harvestable id={entity_id} x={x} y={y}",
            })
        result = spatial.analyze_rows(rows)
        self.assertEqual(2, len(result["captures"]))
        by_region = {capture["region_id"]: capture for capture in result["captures"]}
        self.assertEqual({"MAP_A", "MAP_B"}, set(by_region))
        self.assertEqual("NOT_VERIFIED", by_region["MAP_A"]["status"])
        self.assertEqual("NOT_VERIFIED", by_region["MAP_B"]["status"])
        self.assertEqual(14, by_region["MAP_A"]["player_position_samples"])
        self.assertEqual(3, by_region["MAP_B"]["typed_resource_nodes"])
        self.assertEqual(0, by_region["MAP_B"]["player_position_samples"])

    def test_operation_2_join_response_reads_region_parameter_8(self):
        rows = [
            {
                "archive": "same.zip", "pcap": "join.pcap",
                "detail": "t=100 kind=operation_response op=2 params={0=123 8=4209 9=[1, 2]}",
            },
            {
                "archive": "same.zip", "pcap": "join.pcap",
                "detail": "t=200 kind=own_player_move id=-1 x=1 y=2",
            },
        ]
        result = spatial.analyze_rows(rows)
        self.assertEqual("4209", result["captures"][0]["region_id"])

    def test_conflicting_coordinates_for_one_runtime_id_are_not_silently_merged(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip", "pcap": "capture.pcap", "time_ms": index * 1000,
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for entity_id, x, y, timestamp in [(7, 4, 5, 1000), (7, 40, 50, 2000), (8, 10, 11, 3000), (9, 17, 18, 4000)]:
            rows.append({
                "archive": "same.zip", "pcap": "capture.pcap", "time_ms": timestamp,
                "detail": f"kind=harvestable id={entity_id} x={x} y={y}",
            })
        capture = spatial.analyze_rows(rows)["captures"][0]
        self.assertEqual(["7"], capture["resource_entity_ids_with_coordinate_conflicts"])
        self.assertEqual(2, capture["typed_resource_nodes"])


if __name__ == "__main__":
    unittest.main()
