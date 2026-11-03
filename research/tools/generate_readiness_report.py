#!/usr/bin/env python3
"""Generate a conservative, evidence-backed Albion Radar readiness report."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


def summarize_junit(results_dir: Path) -> dict[str, Any]:
    files = sorted(results_dir.glob("TEST-*.xml")) if results_dir.is_dir() else []
    tests = failures = errors = skipped = 0
    suite_names: list[str] = []
    for file in files:
        try:
            root = ET.parse(file).getroot()
        except (ET.ParseError, OSError):
            errors += 1
            continue
        suites = [root] if root.tag == "testsuite" else list(root.findall(".//testsuite"))
        for suite in suites:
            tests += int(suite.attrib.get("tests", 0))
            failures += int(suite.attrib.get("failures", 0))
            errors += int(suite.attrib.get("errors", 0))
            skipped += int(suite.attrib.get("skipped", 0))
            suite_names.append(suite.attrib.get("name", file.stem))
    status = "PASS" if files and tests > 0 and failures == 0 and errors == 0 else "FAIL"
    decoder_suites = [name for name in suite_names if "decoder" in name.lower() or "mapper" in name.lower()]
    return {
        "status": status,
        "files": len(files),
        "tests": tests,
        "failures": failures,
        "errors": errors,
        "skipped": skipped,
        "suites": len(suite_names),
        "suite_names": suite_names,
        "decoder_suites": len(decoder_suites),
        "decoder_status": "PASS" if status == "PASS" and decoder_suites else "NOT_VERIFIED",
    }


def summarize_replay(analysis_dir: Path) -> dict[str, Any]:
    index_file = analysis_dir / "2026-11-02-pcap-replay-index.json"
    baseline_file = analysis_dir / "2026-11-02-pcap-replay-baseline.json"
    if not index_file.is_file():
        return {"status": "FAIL", "unique": 0, "duplicates": 0, "errors": 1, "stable": 0, "review": 0}
    try:
        index = json.loads(index_file.read_text(encoding="utf-8"))
        rows = index.get("captures", [])
    except (OSError, json.JSONDecodeError):
        return {"status": "FAIL", "unique": 0, "duplicates": 0, "errors": 1, "stable": 0, "review": 0}
    unique_rows = [row for row in rows if row.get("status") != "DUPLICATE_SKIPPED"]
    duplicates = len(rows) - len(unique_rows)
    errors = sum(row.get("status") == "ANALYSIS_ERROR" for row in unique_rows)
    review = sum(row.get("status") in {
        "OUTPUT_CHANGED_REVIEW_REQUIRED",
        "SOURCE_CHANGED_REVIEW_REQUIRED",
        "NEW_CAPTURE_REVIEW",
    } for row in unique_rows)
    stable = sum(row.get("status") in {"STABLE", "BASELINE_INITIALIZED"} for row in unique_rows)
    status = "PASS" if baseline_file.is_file() and unique_rows and errors == 0 and review == 0 and stable == len(unique_rows) else "NOT_VERIFIED"
    return {
        "status": status,
        "unique": len(unique_rows),
        "duplicates": duplicates,
        "errors": errors,
        "stable": stable,
        "review": review,
        "baseline_present": baseline_file.is_file(),
    }


def summarize_pcap_audit(analysis_dir: Path) -> dict[str, Any]:
    candidates = sorted(analysis_dir.glob("*all-inbox-pcap-analysis.md"))
    for file in candidates:
        text = file.read_text(encoding="utf-8", errors="replace")
        marker = re.search(r"PCAP_AUDIT_COMPLETE archives=(\d+) pcaps=(\d+)(?: unique=(\d+) duplicates=(\d+))? errors=(\d+)", text)
        summary = re.search(
            r"Archives:\s*(\d+).*?Discovered PCAP files:\s*(\d+).*?Unique PCAP files analyzed:\s*(\d+).*?Byte-identical duplicates skipped:\s*(\d+).*?Analysis errors:\s*(\d+)",
            text,
            re.S,
        )
        match = marker or summary
        if match:
            values = [int(value) if value is not None else 0 for value in match.groups()]
            if marker:
                archives, pcaps = values[0], values[1]
                unique, duplicates, errors = values[2] or pcaps, values[3], values[4]
            else:
                archives, pcaps, unique, duplicates, errors = values
            return {
                "status": "PASS" if archives > 0 and pcaps > 0 and unique > 0 and errors == 0 else "FAIL",
                "archives": archives, "pcaps": pcaps, "unique_pcaps": unique,
                "duplicates": duplicates, "errors": errors,
            }
    return {"status": "NOT_VERIFIED", "archives": 0, "pcaps": 0, "unique_pcaps": 0, "duplicates": 0, "errors": 0}


def summarize_spatial_alignment(repo_root: Path) -> dict[str, Any]:
    report_path = repo_root / "research/analysis/2026-11-02-spatial-alignment.json"
    if not report_path.is_file():
        return {"status": "NOT_VERIFIED", "source": None}
    try:
        document = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"status": "NOT_VERIFIED", "source": report_path.name}
    captures = document.get("captures") if isinstance(document, dict) else None
    if not isinstance(captures, list) or not captures:
        return {"status": "NOT_VERIFIED", "source": report_path.name}
    best = max(captures, key=lambda row: (row.get("status") == "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE", int(row.get("player_position_samples", 0)), int(row.get("typed_resource_nodes", 0))))
    return {
        "status": best.get("status", "NOT_VERIFIED"),
        "source": str(best.get("archive", "")) + " / " + str(best.get("pcap", "")),
        **best,
    }


def summarize_signature(repo_root: Path) -> dict[str, Any]:
    report = repo_root / "V2rayNG/app/build/reports/albion/apk-signature-verification.txt"
    if not report.is_file():
        return {"status": "NOT_VERIFIED", "signers": 0, "reason": "apksigner verification output is missing"}
    text = report.read_text(encoding="utf-8", errors="replace")
    signer_match = re.search(r"Number of signers:\s*(\d+)", text)
    signers = int(signer_match.group(1)) if signer_match else 0
    verified = bool(re.search(r"\bVerifies\b", text)) and signers > 0
    cert_match = re.search(
        r"(?:Signer #1:?\s*|V2 Signer:\s*)certificate SHA-256 digest:\s*([0-9a-fA-F:]+)",
        text,
    )
    certificate_sha256 = re.sub(r"[^0-9a-fA-F]", "", cert_match.group(1)).lower() if cert_match else None
    if certificate_sha256 and len(certificate_sha256) != 64:
        certificate_sha256 = None
    verified_certificate = verified and certificate_sha256 is not None
    return {"status": "PASS" if verified_certificate else "FAIL", "signers": signers,
            "certificate_sha256": certificate_sha256,
            "reason": None if verified_certificate else "apksigner verification or SHA-256 certificate digest is missing"}


def summarize_overlay_wiring(repo_root: Path) -> dict[str, Any]:
    manifest = repo_root / "V2rayNG/app/src/main/AndroidManifest.xml"
    try:
        root = ET.parse(manifest).getroot()
    except (OSError, ET.ParseError):
        return {"status": "FAIL", "activity_process": None, "service_process": None}
    android_ns = "{http://schemas.android.com/apk/res/android}"
    activity = next((node for node in root.iter() if node.tag.endswith("activity") and node.attrib.get(android_ns + "name") == ".ui.albion.AlbionRadarActivity"), None)
    service = next((node for node in root.iter() if node.tag.endswith("service") and node.attrib.get(android_ns + "name") == ".ui.albion.AlbionOverlayService"), None)
    map_activity = next((node for node in root.iter() if node.tag.endswith("activity") and node.attrib.get(android_ns + "name") == ".ui.albion.AlbionWorldMapActivity"), None)
    activity_process = activity.attrib.get(android_ns + "process") if activity is not None else None
    service_process = service.attrib.get(android_ns + "process") if service is not None else None
    map_activity_process = map_activity.attrib.get(android_ns + "process") if map_activity is not None else None
    map_view = repo_root / "V2rayNG/app/src/main/java/com/v2ray/ang/ui/albion/AlbionWorldMapView.kt"
    same_daemon = activity_process == ":daemon" and service_process == ":daemon"
    map_wired = map_activity_process == ":daemon" and map_view.is_file()
    return {"status": "PASS" if same_daemon else "FAIL",
            "activity_process": activity_process, "service_process": service_process,
            "map_activity_process": map_activity_process, "map_view_exists": map_view.is_file(),
            "map_status": "PASS" if map_wired else "FAIL"}


def inspect_apk(apk_root: Path) -> dict[str, Any]:
    candidates = sorted(apk_root.rglob("*.apk")) if apk_root.is_dir() else []
    universal = [path for path in candidates if "universal" in path.name.lower()]
    apk = universal[0] if universal else (candidates[0] if candidates else None)
    if apk is None:
        return {"status": "FAIL", "reason": "No APK output found"}
    try:
        with zipfile.ZipFile(apk) as archive:
            bad_member = archive.testzip()
            names = set(archive.namelist())
        apk_bytes = apk.read_bytes()
        abis = sorted({
            name.split("/")[1]
            for name in names
            if name.startswith("lib/") and name.count("/") >= 2
        })
        manifest = "AndroidManifest.xml" in names
        dex_count = sum(name.endswith(".dex") for name in names)
        signature_block = b"APK Sig Block 42" in apk_bytes
        ok = bad_member is None and manifest and dex_count > 0
        return {
            "status": "PASS" if ok else "FAIL",
            "file": str(apk.relative_to(apk_root.parents[2])) if len(apk_root.parents) > 2 else apk.name,
            "size_bytes": len(apk_bytes),
            "sha256": hashlib.sha256(apk_bytes).hexdigest(),
            "manifest_present": manifest,
            "dex_count": dex_count,
            "abis": abis,
            "zip_integrity": "PASS" if bad_member is None else f"FAIL:{bad_member}",
            "signing_block": "PRESENT_NOT_CRYPTOGRAPHICALLY_VERIFIED" if signature_block else "NOT_FOUND",
            "signing_status": "NOT_VERIFIED",
        }
    except (OSError, zipfile.BadZipFile) as exc:
        return {"status": "FAIL", "reason": str(exc)}


def build_report(repo_root: Path, env: dict[str, str] | None = None) -> dict[str, Any]:
    env = env or os.environ
    analysis = repo_root / "research" / "analysis"
    junit = summarize_junit(repo_root / "V2rayNG/app/build/test-results/testPlaystoreDebugUnitTest")
    replay = summarize_replay(analysis)
    pcap = summarize_pcap_audit(analysis)
    apk = inspect_apk(repo_root / "V2rayNG/app/build/outputs/apk/playstore/debug")
    signature = summarize_signature(repo_root)
    overlay_wiring = summarize_overlay_wiring(repo_root)
    spatial_alignment = summarize_spatial_alignment(repo_root)

    unique = replay.get("unique", 0)
    quality_rows = 0
    index_file = analysis / "2026-11-02-pcap-replay-index.json"
    if index_file.is_file():
        try:
            rows = json.loads(index_file.read_text(encoding="utf-8")).get("captures", [])
            quality_rows = sum(
                row.get("status") != "DUPLICATE_SKIPPED"
                and isinstance(row.get("quality_score"), (int, float))
                and bool(row.get("quality_grade"))
                for row in rows
            )
        except (OSError, json.JSONDecodeError):
            quality_rows = 0
    quality_status = "PASS" if unique > 0 and quality_rows == unique else "NOT_VERIFIED"

    python_tests_status = env.get("ALBION_PYTHON_TESTS_STATUS", "NOT_VERIFIED")
    if python_tests_status not in {"PASS", "FAIL"}:
        python_tests_status = "NOT_VERIFIED"

    checks = [
        {"name": "Python offline-analysis, FIFO marker, and readiness tests", "status": python_tests_status,
         "evidence": "The full research/tests suite covers ZIP safety, exact-ID joins, FIFO marker lifecycle pairing, metadata-conflict retention, and readiness-report exit policy"},
        {"name": "Android APK build and packaging", "status": apk.get("status", "FAIL"),
         "evidence": f"APK SHA-256: {apk.get('sha256', 'unavailable')}; size: {apk.get('size_bytes', 0)} bytes"},
        {"name": "Gradle unit tests", "status": junit["status"],
         "evidence": f'{junit["tests"]} tests, {junit["failures"]} failures, {junit["errors"]} errors, {junit["skipped"]} skipped'},
        {"name": "Decoder and event-mapper tests", "status": junit["decoder_status"],
         "evidence": f'{junit["decoder_suites"]} matching test suites'},
        {"name": "Offline PCAP audit", "status": pcap["status"],
         "evidence": f'{pcap["archives"]} archives, {pcap["pcaps"]} PCAPs, {pcap["errors"]} errors'},
        {"name": "Deterministic replay baseline", "status": replay["status"],
         "evidence": f'{replay["unique"]} unique captures, {replay["stable"]} stable, {replay["review"]} review-required, {replay["duplicates"]} duplicates'},
        {"name": "Isolated Knowledge/Evidence replay pipeline", "status": "PASS" if junit["status"] == "PASS" and any("AlbionCaptureReplayEngineTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Offline replay invokes the production event mapper and isolated Knowledge/Evidence stores; daemon live stores are not mutated and overlay delivery remains unverified"},
        {"name": "Overlay health monitor", "status": "PASS" if junit["status"] == "PASS" and any("AlbionCaptureHealthMonitorTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Automated stage-by-stage freshness, empty-overlay, stale-entity, and inactive-capture checks"},
        {"name": "Capture quality scoring", "status": quality_status,
         "evidence": f'{quality_rows}/{unique} unique captures have quality score and grade'},
        {"name": "Native tap-drop quality scoring", "status": "PASS" if junit["status"] == "PASS" and any("AlbionCaptureQualityAnalyzerTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Worker-supplied native tap queue drops contribute to quality score; this is explicitly not interpreted as network packet loss"},
        {"name": "APK signing verification", "status": signature["status"],
         "evidence": f'apksigner verification; signers={signature["signers"]}; certificate SHA-256={signature.get("certificate_sha256") or "unavailable"}'},
        {"name": "Live packet capture", "status": "NOT_VERIFIED",
         "evidence": "Requires an on-device live Albion capture"},
        {"name": "Inventory schema candidate analysis", "status": "PASS" if junit["status"] == "PASS" and any("AlbionInventoryEvidenceAnalyzerTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Committed PCAPs are replayed into isolated Inventory/Harvest evidence stores; repeated numeric paths and adjacent deltas remain unclassified candidates, not Item ID or stack mappings"},
        {"name": "Entity evidence ambiguity and conflict scoring", "status": "PASS" if junit["status"] == "PASS" and any("AlbionLiveKnowledgeStoreTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Conflicting type/family/tier/enchant evidence is downgraded to ambiguous; duplicate bursts are not counted as repeated independent sightings; scores are heuristics, not probabilities"},
        {"name": "Provenance-aware resource identity catalog", "status": "PASS" if junit["status"] == "PASS" and any("ResourcePatternLearnerTest" in name for name in junit.get("suite_names", [])) and any("AlbionRadarSnapshotParserTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Family 29 is labelled Ore from independent capture evidence; T3-T5 mobile types are bounded to observed mappings; unknown -1 mobile-type sentinel is not treated as a real ID; inferred names carry visible provenance markers"},
        {"name": "Supervised Gathering label learning", "status": "PASS" if junit["status"] == "PASS" and any("AlbionGatheringPatternLearnerTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Cross-session enchant promotion and rejection of out-of-order, expired, unverified-family, and tier-mismatched samples are unit-tested; exact target-entity binding still requires stronger marker/spatial evidence"},
        {"name": "Autonomous Photon event-shape learning", "status": "PASS" if junit["status"] == "PASS" and any("AlbionAutonomousPatternLearnerTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Unknown event signatures remain candidates; repeated marker-correlated shapes are labelled only after multi-sample, multi-session corroboration and are never assigned guessed semantic names"},
        {"name": "Inventory Item ID and Stack Delta mapping", "status": "NOT_VERIFIED",
         "evidence": "Candidate parameter paths are not semantic mappings; real item IDs and stack deltas still require independent live evidence"},
        {"name": "Same-capture player/resource coordinate alignment candidate",
         "status": "PASS" if spatial_alignment.get("status") == "PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE" else "NOT_VERIFIED",
         "evidence": f'source={spatial_alignment.get("source")}; player samples={spatial_alignment.get("player_position_samples", 0)}; typed resource nodes={spatial_alignment.get("typed_resource_nodes", 0)}; median nearest distance={spatial_alignment.get("nearest_resource_to_player_distance_median", "n/a")}; candidate-only heuristic, not zone/global-map proof'},
        {"name": "Live spatial correlation", "status": "NOT_VERIFIED",
         "evidence": "A same-capture coordinate-space heuristic does not replace live device verification of entity identity and zone continuity"},
        {"name": "Overlay Activity/Service process wiring", "status": overlay_wiring["status"],
         "evidence": f'Radar Activity process={overlay_wiring["activity_process"]}; Overlay Service process={overlay_wiring["service_process"]}'},
        {"name": "Live coordinate map screen wiring", "status": overlay_wiring.get("map_status", "NOT_VERIFIED"),
         "evidence": f'Map Activity process={overlay_wiring.get("map_activity_process")}; map view exists={overlay_wiring.get("map_view_exists")}; map consumes daemon snapshot in the same process'},
        {"name": "Learned database ZIP export", "status": "PASS" if junit["status"] == "PASS" and any("AlbionKnowledgeBundleTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "ZIP manifest, entity/protocol record counts, empty-database behavior, and exclusion of VPN profile secrets are covered by Android unit tests"},
        {"name": "Persistent own-player movement trail", "status": "PASS" if junit["status"] == "PASS" and any("AlbionLiveKnowledgeStoreTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Operation-22 own-player coordinates are stored as a bounded 240-point history and rendered on the mini radar and coordinate map"},
        {"name": "Overlay delivery and rendering", "status": "NOT_VERIFIED",
         "evidence": "Snapshot parsing is validated before acknowledgement; actual device-side rendering and live data delivery still require a device run"},
        {"name": "Fault-injection and malformed-input tests", "status": "PASS" if junit["status"] == "PASS" and any("AlbionPipelineSelfTest" in name for name in junit.get("suite_names", [])) and any("AlbionRadarSnapshotParserTest" in name for name in junit.get("suite_names", [])) else "NOT_VERIFIED",
         "evidence": "Automated malformed snapshot, corrupt evidence, coordinate guard, and replay determinism tests"},
    ]
    if any(check["status"] == "FAIL" for check in checks):
        overall = "BLOCKED"
    else:
        # Device-only checks are deliberately reported as pending, but they are the
        # purpose of the first controlled field test, not reasons to withhold the APK.
        device_only = {
            "Live packet capture",
            "Inventory Item ID and Stack Delta mapping",
            "Live spatial correlation",
            "Overlay delivery and rendering",
        }
        preflight_unverified = [
            check for check in checks
            if check["status"] == "NOT_VERIFIED" and check["name"] not in device_only
        ]
        overall = "NOT_READY_FOR_FIELD_TEST" if preflight_unverified else "READY_FOR_CONTROLLED_FIELD_TEST"
    return {
        "schema_version": 1,
        "overall_status": overall,
        "commit_sha": env.get("GITHUB_SHA", "local"),
        "workflow_run_id": env.get("GITHUB_RUN_ID", "local"),
        "checks": checks,
        "metrics": {"unit_tests": junit, "pcap_audit": pcap, "replay": replay, "quality_rows": quality_rows, "apk": apk, "signature": signature, "overlay_wiring": overlay_wiring, "spatial_alignment": spatial_alignment},
    }


def write_report(repo_root: Path, report: dict[str, Any]) -> tuple[Path, Path]:
    analysis = repo_root / "research" / "analysis"
    analysis.mkdir(parents=True, exist_ok=True)
    json_path = analysis / "albion-readiness-report.json"
    markdown_path = analysis / "albion-readiness-report.md"
    json_path.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    lines = [
        "# Albion Radar Readiness Report",
        "",
        f'**Overall status: {report["overall_status"]}**',
        "",
        f'- Commit: `{report["commit_sha"]}`',
        f'- Workflow run: `{report["workflow_run_id"]}`',
        "",
        "| Check | Status | Evidence |",
        "|---|---|---|",
    ]
    for check in report["checks"]:
        evidence = str(check["evidence"]).replace("|", "\\|")
        lines.append(f'| {check["name"]} | **{check["status"]}** | {evidence} |')
    lines += [
        "",
        "A NOT_VERIFIED result is not a pass. Offline simulation and replay do not replace a live device test.",
        "",
    ]
    markdown_path.write_text("\n".join(lines), encoding="utf-8")
    return markdown_path, json_path


def report_exit_code(report: dict[str, Any], require_ready: bool = False) -> int:
    """Fail broken preflight checks; optionally require readiness for a controlled device field test."""
    if any(check["status"] == "FAIL" for check in report["checks"]):
        return 1
    if require_ready and report["overall_status"] != "READY_FOR_CONTROLLED_FIELD_TEST":
        return 2
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate Albion Radar readiness report")
    parser.add_argument("--require-ready", action="store_true",
                        help="Exit 2 unless every readiness gate is PASS and the report is READY_FOR_FIELD_TEST")
    args = parser.parse_args(argv)
    repo_root = Path(__file__).resolve().parents[2]
    report = build_report(repo_root)
    markdown_path, json_path = write_report(repo_root, report)
    exit_code = report_exit_code(report, require_ready=args.require_ready)
    print(json.dumps({
        "overall_status": report["overall_status"],
        "markdown": str(markdown_path),
        "json": str(json_path),
        "require_ready": args.require_ready,
        "exit_code": exit_code,
        "exit_policy": "FAIL checks always exit 1; NOT_VERIFIED is informational by default and exits 2 with --require-ready",
    }, indent=2))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
