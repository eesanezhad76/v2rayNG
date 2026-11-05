import pathlib
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "research/tools"))
import split_large_outputs as shards


class SplitLargeOutputsTests(unittest.TestCase):
    def test_shards_reassemble_byte_for_byte_and_verify_checksums(self):
        with tempfile.TemporaryDirectory() as td:
            root = pathlib.Path(td)
            source = root / "research/analysis/large.jsonl"
            source.parent.mkdir(parents=True)
            payload = (b"Albion evidence\x00\xff\n" * 19) + bytes(range(256))
            source.write_bytes(payload)
            output = root / "research/analysis/derived-shards"
            record = shards.split_one(source, root, output, 73)
            manifest = {"sources": [record]}
            self.assertEqual(1, shards.verify_sources(manifest, root))
            rebuilt = b"".join((root / part["path"]).read_bytes() for part in record["parts"])
            self.assertEqual(payload, rebuilt)
            self.assertEqual(len(payload), record["bytes"])
            self.assertGreater(len(record["parts"]), 1)


if __name__ == "__main__":
    unittest.main()
