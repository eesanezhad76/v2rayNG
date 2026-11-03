import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research" / "tools"))

from validate_runtime_knowledge_assets import validate


class RuntimeKnowledgeAssetIntegrationTest(unittest.TestCase):
    def test_shipped_assets_cover_complete_research_index(self):
        report = validate(ROOT)
        self.assertEqual("PASS", report["status"])
        self.assertEqual(331, report["resource_observations"])
        self.assertEqual(2330, report["mobile_entity_observations"])
        self.assertEqual("64/64", report["dictionary_coverage"])
        self.assertEqual(83, report["saved_catalog"]["nodes"])
        self.assertEqual("2788", report["confirmed_t53_target"])
        self.assertFalse(report["historical_coordinates_projected"])


if __name__ == "__main__":
    unittest.main()
