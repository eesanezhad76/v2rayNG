# Albion Radar research vault

This is the permanent evidence area for every capture, finding, dataset, test, and decision. It is separate from Android production source code.

- `inbox/`: upload original ZIP capture archives here. Do not rename internal files.
- `database/`: normalized node/entity databases and cross-capture dictionaries.
- `analysis/`: per-capture reports, timelines, hypotheses, confidence and unresolved questions.
- `tests/`: offline ingest/decoder tests, fixtures, validation notes and regression evidence.
- `decisions/`: promotion log explaining which findings are approved for production and why.

A GitHub Actions workflow watches ZIP uploads to `research/inbox/`, extracts them, runs the offline analyzer and tests, and commits derived evidence back to this branch. Raw ZIPs are preserved in the inbox. Production app code must consume only reviewed/approved data from the database; raw captures and experimental hypotheses must never be compiled into the APK.

Branch policy: `albion-pattern-mining` is the single working branch for this workflow. Do not merge experimental findings into app code without validation and regression tests.
