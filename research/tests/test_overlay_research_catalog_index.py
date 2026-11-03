import json
import math
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "V2rayNG" / "app" / "src" / "main" / "assets"
RESEARCH = ROOT / "research" / "database"


class AlbionResearchCatalogIndexTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.index = json.loads((ASSETS / "albion-research-catalog-index.json").read_text(encoding="utf-8"))
        cls.dictionary = json.loads((ASSETS / "albion-mobile-type-dictionary.json").read_text(encoding="utf-8"))
        cls.report = json.loads((RESEARCH / "albion-entity-discovery-report-2026-11-03.json").read_text(encoding="utf-8"))

    def test_research_dictionary_is_preserved_and_covers_all_discovered_mobile_ids(self):
        self.assertEqual(self.dictionary["entry_count"], len(self.dictionary["entries"]))
        self.assertGreater(self.dictionary["entry_count"], 5000)
        ids = {str(row["mobile_type_id"]) for row in self.report["mobile_type_profiles"]}
        self.assertEqual(len(ids), 64)
        self.assertTrue(ids.issubset(self.dictionary["entries"].keys()))
        self.assertEqual(self.report["mobile_type_dictionary_coverage"]["unmapped_type_ids"], [])

    def test_all_research_entities_are_grouped_by_source_capture(self):
        captures = self.index["captures"]
        self.assertEqual(self.index["capture_count"], len(captures))
        self.assertEqual(len(captures), 20)
        self.assertEqual(self.index["total_node_records"], 331)
        self.assertEqual(self.index["total_mob_records"], 2330)

        seen_ids = set()
        node_total = 0
        mob_total = 0
        for capture in captures:
            capture_id = capture["capture_id"]
            entities = capture["entities"]
            self.assertEqual(len(entities), capture["node_count"] + capture["mob_count"])
            self.assertGreater(len(capture["archive"]), 0)
            self.assertGreater(len(capture["pcap"]), 0)
            for entity in entities:
                self.assertEqual(entity["capture_id"], capture_id)
                self.assertEqual(entity["source_archive"], capture["archive"])
                self.assertEqual(entity["source_pcap"], capture["pcap"])
                self.assertIn("x", entity)
                self.assertIn("y", entity)
                self.assertTrue(math.isfinite(float(entity["x"])))
                self.assertTrue(math.isfinite(float(entity["y"])))
                self.assertIn(capture_id, entity["entity_id"])
                self.assertNotIn(entity["entity_id"], seen_ids)
                seen_ids.add(entity["entity_id"])
                if entity["last_kind"] == "mob":
                    mob_total += 1
                    self.assertIn(str(entity["mobile_type_id"]), self.dictionary["entries"])
                else:
                    node_total += 1
        self.assertEqual(node_total, 331)
        self.assertEqual(mob_total, 2330)

    def test_unknown_zone_coordinates_are_never_declared_globally_comparable(self):
        self.assertFalse(self.index["zone_identity_decoded"])
        self.assertIn("never merge or compare coordinates across captures", self.index["coordinate_semantics"])


if __name__ == "__main__":
    unittest.main()
