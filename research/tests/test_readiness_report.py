import json
import tempfile
import unittest
from pathlib import Path
import sys

TOOLS = Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
import generate_readiness_report as readiness


class ReadinessReportTests(unittest.TestCase):
    def test_junit_summary_passes_only_when_no_failures_or_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "TEST-AlbionDecoder.xml").write_text(
                '<testsuite name="AlbionPhotonDecoderTest" tests="3" failures="0" errors="0" skipped="0"></testsuite>',
                encoding="utf-8",
            )
            result = readiness.summarize_junit(path)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(3, result["tests"])
            self.assertEqual("PASS", result["decoder_status"])

    def test_replay_requires_baseline_and_rejects_review_required_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "2026-11-02-pcap-replay-index.json").write_text(json.dumps({
                "captures": [
                    {"status": "STABLE", "quality_score": 90, "quality_grade": "GOOD"},
                    {"status": "DUPLICATE_SKIPPED"},
                ]
            }), encoding="utf-8")
            self.assertEqual("NOT_VERIFIED", readiness.summarize_replay(path)["status"])
            (path / "2026-11-02-pcap-replay-baseline.json").write_text('{"captures":[]}', encoding="utf-8")
            self.assertEqual("PASS", readiness.summarize_replay(path)["status"])
            (path / "2026-11-02-pcap-replay-index.json").write_text(json.dumps({
                "captures": [{"status": "OUTPUT_CHANGED_REVIEW_REQUIRED", "quality_score": 90, "quality_grade": "GOOD"}]
            }), encoding="utf-8")
            self.assertEqual("NOT_VERIFIED", readiness.summarize_replay(path)["status"])


if __name__ == "__main__":
    unittest.main()
