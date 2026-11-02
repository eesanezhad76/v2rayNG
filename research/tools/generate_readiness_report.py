#!/usr/bin/env python3
"""Generate a conservative, evidence-backed Albion Radar readiness report."""
from __future__ import annotations

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
        match = re.search(r"PCAP_AUDIT_COMPLETE archives=(\d+) pcaps=(\d+) errors=(\d+)", text)
        if match:
            archives, pcaps, errors = map(int, match.groups())
            return {
                "status": "PASS" if pcaps > 0 and errors == 0 else "FAIL",
                "archives": archives,
                "pcaps": pcaps,
                "errors": errors,
            }
    return {"status": "NOT_VERIFIED", "archives": 0, "pcaps": 0, "errors": 0}


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

    checks = [
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
        {"name": "Capture quality scoring", "status": quality_status,
         "evidence": f'{quality_rows}/{unique} unique captures have quality score and grade'},
        {"name": "APK signing verification", "status": "NOT_VERIFIED",
         "evidence": f'APK signing block: {apk.get("signing_block", "not checked")}; debug build; cryptographic verification not performed'},
        {"name": "Live packet capture", "status": "NOT_VERIFIED",
         "evidence": "Requires an on-device live Albion capture"},
        {"name": "Inventory Item ID and Stack Delta mapping", "status": "NOT_VERIFIED",
         "evidence": "Parameter shapes are recorded; item identity and stack deltas are not confirmed"},
        {"name": "Live spatial correlation", "status": "NOT_VERIFIED",
         "evidence": "Offline candidates do not prove live entity identity"},
        {"name": "Overlay delivery and rendering", "status": "NOT_VERIFIED",
         "evidence": "Health instrumentation exists; actual device-side delivery and rendering require field verification"},
    ]
    if any(check["status"] == "FAIL" for check in checks):
        overall = "BLOCKED"
    elif any(check["status"] == "NOT_VERIFIED" for check in checks):
        overall = "NOT_READY_FOR_FIELD_TEST"
    else:
        overall = "READY_FOR_CONTROLLED_FIELD_TEST"
    return {
        "schema_version": 1,
        "overall_status": overall,
        "commit_sha": env.get("GITHUB_SHA", "local"),
        "workflow_run_id": env.get("GITHUB_RUN_ID", "local"),
        "checks": checks,
        "metrics": {"unit_tests": junit, "pcap_audit": pcap, "replay": replay, "quality_rows": quality_rows, "apk": apk},
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


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    report = build_report(repo_root)
    markdown_path, json_path = write_report(repo_root, report)
    print(json.dumps({"overall_status": report["overall_status"], "markdown": str(markdown_path), "json": str(json_path)}, indent=2))
    return 1 if any(check["status"] == "FAIL" for check in report["checks"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
