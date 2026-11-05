import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research" / "tools"))

from validate_runtime_knowledge_assets import validate
from analyze_spatial_alignment import parse_fields


class RuntimeKnowledgeAssetIntegrationTest(unittest.TestCase):
    def test_spatial_detail_parser_preserves_numeric_field_names(self):
        fields = parse_fields("t=1793403804041 param0_type=String param0_value=4000 kind=region")
        self.assertEqual("String", fields["param0_type"])
        self.assertEqual("4000", fields["param0_value"])
        self.assertEqual("1793403804041", fields["t"])

    def test_shipped_assets_cover_complete_research_index(self):
        report = validate(ROOT)
        self.assertEqual("PASS", report["status"])
        self.assertEqual(331, report["resource_observations"])
        self.assertEqual(2330, report["mobile_entity_observations"])
        self.assertEqual("64/64", report["dictionary_coverage"])
        self.assertGreater(report["external_dictionary_entries"], 64)
        self.assertEqual(54, report["historical_mobile_type_ids"])
        self.assertEqual("54/54", report["historical_mobile_dictionary_coverage"])
        self.assertEqual(["7", "14", "20", "27", "29"], report["historical_resource_families"])
        self.assertEqual(83, report["saved_catalog"]["nodes"])
        self.assertEqual("2788", report["confirmed_t53_target"])
        self.assertEqual(9522, report["historical_knowledge"]["entity_records"])
        self.assertEqual(4599, report["historical_knowledge"]["protocol_events"])
        self.assertEqual(70, report["historical_knowledge"]["learned_patterns"])
        self.assertEqual(296, report["historical_knowledge"]["autonomous_signatures_seeded"])
        self.assertEqual(70, report["historical_knowledge"]["learned_pattern_reference_rows"])
        self.assertGreater(report["historical_knowledge"]["protocol_shape_groups"], 0)
        self.assertFalse(report["historical_coordinates_projected"])


if __name__ == "__main__":
    unittest.main()
