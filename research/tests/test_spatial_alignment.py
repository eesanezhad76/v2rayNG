import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research/tools"))
import analyze_spatial_alignment as spatial


class SpatialAlignmentTests(unittest.TestCase):
    def test_player_and_resource_positions_are_compared_within_each_capture(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip",
                "pcap": "aligned.pcap",
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y) in enumerate([(4, 5), (10, 11), (17, 18)]):
            rows.append({
                "archive": "same.zip",
                "pcap": "aligned.pcap",
                "detail": f"kind=harvestable id={700 + index} x={x} y={y}",
            })
        result = spatial.analyze_rows(rows)
        capture = result["captures"][0]
        self.assertEqual("PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE", capture["status"])
        self.assertEqual(20, capture["player_position_samples"])
        self.assertEqual(3, capture["typed_resource_nodes"])
        self.assertEqual("single_capture_spatial_heuristic_only", capture["evidence_grade"])

    def test_coordinates_from_different_captures_are_never_joined(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "a.zip",
                "pcap": "player.pcap",
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for index, (x, y) in enumerate([(4, 5), (10, 11), (17, 18)]):
            rows.append({
                "archive": "b.zip",
                "pcap": "resources.pcap",
                "detail": f"kind=harvestable id={700 + index} x={x} y={y}",
            })
        result = spatial.analyze_rows(rows)
        self.assertEqual(2, len(result["captures"]))
        self.assertTrue(all(row["status"] == "NOT_VERIFIED" for row in result["captures"]))
        self.assertEqual("NOT_VERIFIED", result["overall_status"])

    def test_conflicting_coordinates_for_one_runtime_id_are_not_silently_merged(self):
        rows = []
        for index in range(20):
            rows.append({
                "archive": "same.zip",
                "pcap": "capture.pcap",
                "detail": f"kind=own_player_move id=-1 x={index} y={index}",
            })
        for entity_id, x, y in [(7, 4, 5), (7, 40, 50), (8, 10, 11), (9, 17, 18)]:
            rows.append({
                "archive": "same.zip",
                "pcap": "capture.pcap",
                "detail": f"kind=harvestable id={entity_id} x={x} y={y}",
            })
        capture = spatial.analyze_rows(rows)["captures"][0]
        self.assertEqual(["7"], capture["resource_entity_ids_with_coordinate_conflicts"])
        self.assertEqual(2, capture["typed_resource_nodes"])


if __name__ == "__main__":
    unittest.main()
