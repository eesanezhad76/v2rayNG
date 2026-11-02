# Albion research tests and validation

This folder contains offline capture-ingest tests, fixtures, and validation reports. Android/JUnit source tests remain under `V2rayNG/app/src/test` because Gradle discovers them there; moving those source files would silently remove them from the app test task. This folder is the central index for research/data validation.

Required checks for every ingest:
1. ZIP traversal/path safety and successful extraction.
2. JSONL parse count and malformed-line count.
3. Event 40 unique node identity by `entity_id`.
4. Event 46 timelines linked only by exact runtime ID.
5. Preserve unmatched state IDs and unmatched manual markers.
6. Keep unknown type/mobile codes null; never infer from proximity alone.
7. Deterministic JSON/CSV output and summary counts.
