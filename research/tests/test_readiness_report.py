import json
import tempfile
import unittest
from unittest import mock
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

    def test_pcap_audit_reads_summary_and_rejects_analysis_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            report = path / "2026-11-02-all-inbox-pcap-analysis.md"
            report.write_text(
                "Archives: 4\nDiscovered PCAP files: 22\nUnique PCAP files analyzed: 21\nByte-identical duplicates skipped: 1\nAnalysis errors: 0\n",
                encoding="utf-8",
            )
            result = readiness.summarize_pcap_audit(path)
            self.assertEqual("PASS", result["status"])
            self.assertEqual(21, result["unique_pcaps"])
            report.write_text(
                "Archives: 4\nDiscovered PCAP files: 22\nUnique PCAP files analyzed: 21\nByte-identical duplicates skipped: 1\nAnalysis errors: 1\n",
                encoding="utf-8",
            )
            self.assertEqual("FAIL", readiness.summarize_pcap_audit(path)["status"])

    def test_signature_requires_apksigner_verifies_and_a_signer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / "V2rayNG/app/build/reports/albion/apk-signature-verification.txt"
            report.parent.mkdir(parents=True)
            digest = "AB:CD:" + ":".join(["12"] * 30)
            report.write_text(f"Verifies\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: {digest}\n", encoding="utf-8")
            result = readiness.summarize_signature(root)
            self.assertEqual("PASS", result["status"])
            self.assertEqual("abcd" + "12" * 30, result["certificate_sha256"])
            digest_v2 = "0D:93:" + ":".join(["51"] * 30)
            report.write_text(f"Verifies\nNumber of signers: 1\nV2 Signer: certificate SHA-256 digest: {digest_v2}\n", encoding="utf-8")
            result = readiness.summarize_signature(root)
            self.assertEqual("PASS", result["status"])
            self.assertEqual("0d93" + "51" * 30, result["certificate_sha256"])
            report.write_text("Number of signers: 1\n", encoding="utf-8")
            self.assertEqual("FAIL", readiness.summarize_signature(root)["status"])

    def test_spatial_alignment_reads_only_same_capture_candidate_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            analysis = root / "research/analysis"
            analysis.mkdir(parents=True)
            (analysis / "2026-11-02-spatial-alignment.json").write_text(json.dumps({
                "overall_status": "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE",
                "captures": [
                    {
                        "archive": "many-moves.zip",
                        "pcap": "no-nodes.pcap",
                        "status": "NOT_VERIFIED",
                        "player_position_samples": 900,
                        "typed_resource_nodes": 0,
                    },
                    {
                        "archive": "capture.zip",
                        "pcap": "capture.pcap",
                        "status": "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE",
                        "player_position_samples": 300,
                        "typed_resource_nodes": 25,
                        "nearest_resource_to_player_path_distance_median": 12.5,
                        "warning": "candidate only",
                    }
                ]
            }), encoding="utf-8")
            result = readiness.summarize_spatial_alignment(root)
            self.assertEqual("PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE", result["status"])
            self.assertEqual("capture.zip / capture.pcap", result["source"])
            self.assertEqual(300, result["player_position_samples"])

    def test_exit_code_fails_only_on_explicit_fail_not_unverified_gate(self):
        self.assertEqual(0, readiness.report_exit_code({
            "overall_status": "NOT_READY_FOR_FIELD_TEST",
            "checks": [{"name": "Live capture", "status": "NOT_VERIFIED"}],
        }))
        self.assertEqual(1, readiness.report_exit_code({
            "overall_status": "BLOCKED",
            "checks": [{"name": "Gradle tests", "status": "FAIL"}],
        }))
        self.assertEqual(0, readiness.report_exit_code({
            "overall_status": "READY_FOR_CONTROLLED_FIELD_TEST",
            "checks": [{"name": "APK", "status": "PASS"}],
        }))
        self.assertEqual(0, readiness.report_exit_code({
            "overall_status": "READY_FOR_CONTROLLED_FIELD_TEST",
            "checks": [{"name": "APK", "status": "PASS"}],
        }, require_ready=True))
        self.assertEqual(2, readiness.report_exit_code({
            "overall_status": "NOT_READY_FOR_FIELD_TEST",
            "checks": [{"name": "Live capture", "status": "NOT_VERIFIED"}],
        }, require_ready=True))
        self.assertEqual(1, readiness.report_exit_code({
            "overall_status": "BLOCKED",
            "checks": [{"name": "APK", "status": "FAIL"}],
        }, require_ready=True))

    def test_not_verified_does_not_fail_ci_but_explicit_fail_does(self):
        report = {
            "overall_status": "NOT_READY_FOR_FIELD_TEST",
            "commit_sha": "test",
            "workflow_run_id": "0",
            "checks": [{"name": "Live capture", "status": "NOT_VERIFIED", "evidence": "Device test pending"}],
        }
        with mock.patch.object(readiness, "build_report", return_value=report), mock.patch.object(readiness, "write_report", return_value=(Path("report.md"), Path("report.json"))):
            self.assertEqual(0, readiness.main([]))

        failed_report = {
            **report,
            "overall_status": "BLOCKED",
            "checks": [{"name": "APK build", "status": "FAIL", "evidence": "APK missing"}],
        }
        with mock.patch.object(readiness, "build_report", return_value=failed_report), mock.patch.object(readiness, "write_report", return_value=(Path("report.md"), Path("report.json"))):
            self.assertEqual(1, readiness.main([]))

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
