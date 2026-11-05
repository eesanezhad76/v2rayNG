import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from analyze_portal_zone_node_matches import analyze, parse_fields


def row(archive, pcap, detail):
    return {"archive": archive, "pcap": pcap, "detail": detail}


class PortalZoneNodeMatchTests(unittest.TestCase):
    def test_numeric_operation_parameter_fields_are_parsed(self):
        fields = parse_fields("kind=operation_response op=41 param0_type=String return_code=0")
        self.assertEqual("String", fields["param0_type"])
        self.assertEqual("0", fields["return_code"])

    def test_failed_operation_response_does_not_establish_zone_identity(self):
        rows = [
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=100 kind=operation_response op=41 return_code=1 param0_type=String param0_value=cluster_A params={0=cluster_A}"),
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=110 kind=static_resource id=101 x=10 y=20"),
        ]
        result = analyze(rows)
        node = result["target_nodes"][0]
        self.assertIsNone(node["zone_id"])
        self.assertEqual([], node["same_zone_coordinate_matches"])
        self.assertEqual([], result["zone_capture_summary"])

    def test_exact_zone_id_scopes_coordinate_matches(self):
        rows = [
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=100 kind=operation_response op=41 return_code=0 param0_type=String param0_value=cluster_A params={0=cluster_A}"),
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=110 kind=static_resource id=101 x=10 y=20"),
            row("other.zip", "same-zone.pcap",
                "t=90 kind=operation_response op=41 return_code=0 param0_type=String param0_value=cluster_A params={0=cluster_A}"),
            row("other.zip", "same-zone.pcap", "t=120 kind=static_resource id=202 x=10 y=20"),
            row("other.zip", "different-zone.pcap",
                "t=90 kind=operation_response op=41 return_code=0 param0_type=String param0_value=cluster_B params={0=cluster_B}"),
            row("other.zip", "different-zone.pcap", "t=120 kind=static_resource id=303 x=10 y=20"),
        ]
        node = analyze(rows)["target_nodes"][0]
        self.assertEqual("cluster_A", node["zone_id"])
        self.assertEqual(1, len(node["same_zone_coordinate_matches"]))
        self.assertEqual("202", node["same_zone_coordinate_matches"][0]["entity_id"])
        self.assertEqual(2, len(node["near_coordinate_matches_any_zone"]))

    def test_distant_reused_numeric_id_is_not_merged(self):
        rows = [
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=100 kind=operation_response op=41 return_code=0 param0_type=String param0_value=cluster_A params={0=cluster_A}"),
            row("Albion2.zip", "went from yellow 5 region to fort sterling.pcap",
                "t=110 kind=static_resource id=101 x=10 y=20"),
            row("other.zip", "unrelated.pcap", "t=120 kind=static_resource id=101 x=900 y=900"),
        ]
        node = analyze(rows)["target_nodes"][0]
        self.assertEqual(1, len(node["same_numeric_id_matches_any_zone"]))
        self.assertEqual(0, len(node["same_zone_coordinate_matches"]))
        self.assertIsNone(node["same_numeric_id_matches_any_zone"][0]["zone_id"])


if __name__ == "__main__":
    unittest.main()
