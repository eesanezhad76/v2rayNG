import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research" / "tools"))
from generate_mobile_type_dictionary import build


class MobileTypeDictionaryGenerationTest(unittest.TestCase):
    def test_builds_display_dictionary_from_observed_type_crosswalk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "V2rayNG/app/src/main/assets/albion-research-knowledge-index.json"
            path.parent.mkdir(parents=True)
            path.write_text(json.dumps({
                "generated_from": {"source_branch": "test"},
                "totals": {"mobile_type_ids": 2},
                "mobile_types": {
                    "482": {"names": {"Mountain Rabbit": 4},
                            "unique_names": {"T1_MOB_HIDE_MOUNTAIN_RABBIT": 4},
                            "tiers": [1], "categories": {"harmless": 4},
                            "dangers": {"normal": 4}, "loot_types": {"HIDE": 4},
                            "harvestable_values": {"False": 4}, "observation_count": 4,
                            "captures": ["a", "b"]},
                    "571": {"names": {"Ore Elemental": 3},
                            "unique_names": {"T3_MOB_CRITTER_ORE_MOUNTAIN_RED": 3},
                            "tiers": [3], "categories": {"critter": 3},
                            "dangers": {"normal": 3}, "loot_types": {"ORE_CRITTER": 3},
                            "harvestable_values": {"True": 3}, "observation_count": 3,
                            "captures": ["a"]},
                },
            }), encoding="utf-8")
            report = build(root)
            output = json.loads((root / "V2rayNG/app/src/main/assets/albion-mobile-type-dictionary.json").read_text())
            self.assertEqual("PASS", report["status"])
            self.assertEqual(2, report["entries"])
            self.assertEqual("Mountain Rabbit", output["entries"]["482"]["display_name"])
            self.assertFalse(output["entries"]["482"]["is_harvestable"])
            self.assertEqual("T3_MOB_CRITTER_ORE_MOUNTAIN_RED", output["entries"]["571"]["unique_name"])
            self.assertTrue(output["entries"]["571"]["is_harvestable"])


if __name__ == "__main__":
    unittest.main()
