# Capture upload inbox

Upload original Albion capture ZIP files directly into this folder on branch `albion-pattern-mining`. Keep each ZIP intact and use a descriptive filename. A push automatically starts the Albion capture ingest workflow, safely extracts JSONL files, calculates node/entity inventories, preserves Event 46 timelines, runs regression tests, and commits derived JSON/CSV/reports to `research/database/` and `research/analysis/`.

Do not put APKs or unrelated archives here. Raw capture ZIPs are evidence and are never included in the APK.
