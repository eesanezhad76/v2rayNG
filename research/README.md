# Albion Radar research vault

This is the permanent evidence area for every capture, finding, dataset, test, and decision. It is separate from Android production source code.

- `inbox/`: upload original ZIP capture archives here. Do not rename internal files.
- `database/`: normalized node/entity databases and cross-capture dictionaries.
- `analysis/`: per-capture reports, timelines, hypotheses, confidence and unresolved questions.
- `tests/`: offline ingest/decoder tests, fixtures, validation notes and regression evidence.
- `decisions/`: promotion log explaining which findings are approved for production and why.

A GitHub Actions workflow watches ZIP uploads to `research/inbox/`, extracts them, runs the offline analyzer and tests, and commits derived evidence back to this branch. Raw ZIPs are preserved in the inbox. Production app code must consume only reviewed/approved data from the database; raw captures and experimental hypotheses must never be compiled into the APK.

## Importing the Android learned-database ZIP

Use the app's **Export learned database (ZIP)** action after stopping a capture. Put that ZIP in `research/inbox/` exactly like a raw capture archive. The ingest workflow recognizes its `albion-radar-knowledge` manifest, validates the schema, byte counts and SHA-256 values, then updates `database/albion-knowledge-bundle-import.json`.

The central importer:
- preserves entity records under their capture-session-scoped `record_key`; the same runtime ID in different sessions remains separate;
- deduplicates cumulative exports instead of adding the same sightings or pattern sample counts repeatedly;
- keeps raw protocol parameter evidence and pattern signatures with source bundle hashes;
- ignores ordinary raw-capture ZIPs (those continue through the PCAP/JSONL ingest path);
- never promotes an unverified item ID, stack delta, Ore name, zone ID or world-map coordinate transform to a confirmed fact.

After upload, check the **Albion capture ingest** workflow and its `albion-knowledge-bundle-import.md` report. The workflow commits derived database files automatically when there are changes.

Branch policy: `albion-pattern-mining` is the single working branch for this workflow. Do not merge experimental findings into app code without validation and regression tests.
