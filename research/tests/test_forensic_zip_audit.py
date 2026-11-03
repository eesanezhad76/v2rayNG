import hashlib
import importlib.util
import io
import json
import pathlib
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "research/tools/forensic_zip_audit.py"
SPEC = importlib.util.spec_from_file_location("forensic_zip_audit", SCRIPT)
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class ForensicZipAuditTests(unittest.TestCase):
    def test_large_text_member_is_preserved_without_truncation(self):
        payload = ("Troll resource node " + "x" * 2_100_000).encode("utf-8")
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            archive = root / "capture.zip"
            output = root / "analysis"
            with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
                z.writestr("pattern-learning-report.txt", payload)
            previous = audit.OUT
            audit.OUT = output
            try:
                with redirect_stdout(io.StringIO()):
                    audit.inspect(archive)
                inventory = json.loads((output / "capture-forensic-inventory.json").read_text())
                note = inventory["notes"][0]
                self.assertEqual(len(payload), note["bytes"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), note["sha256"])
                self.assertEqual(payload.decode("utf-8"), note["text"])
                self.assertEqual("utf-8", note["encoding"])
                self.assertEqual(note["sha256"], inventory["members"][0]["sha256"])
                markdown = (output / "capture-forensic-inventory.md").read_text()
                self.assertIn("Full text is preserved without truncation", markdown)
            finally:
                audit.OUT = previous


if __name__ == "__main__":
    unittest.main()
