# Where and how to upload Albion captures and tests

## The one upload location for all capture ZIPs

**Repository:** `eesanezhad76/v2rayNG`  
**Branch:** `albion-pattern-mining`  
**Folder:** `research/inbox/`

Direct link:
https://github.com/eesanezhad76/v2rayNG/tree/albion-pattern-mining/research/inbox

Upload every capture ZIP here, including:
- player movement / position experiments
- idle / baseline traffic
- resource gathering and enchanted nodes
- mobs, golems, health and kills
- map / zone / transition experiments
- any new controlled test

Keep the original ZIP intact. Use descriptive names, e.g.:
- `movement-test-2026-11-03.zip`
- `ore-enchant-t5-test-2026-11-03.zip`
- `t4-golem-combat-test-2026-11-03.zip`

Do not extract or rename files inside the archive before uploading. If you have several separate experiments, separate ZIPs are preferable. Add a short `README.txt` inside the ZIP describing what you did, which character was used, and the order of actions/markers. Do not include account passwords, VPN credentials, or private keys.

## Upload from GitHub on Android

1. Open the direct link above and sign in to GitHub.
2. Confirm the branch selector says `albion-pattern-mining`.
3. Open `research/inbox/`.
4. Tap **Add file** → **Upload files** (if the mobile layout hides it, open GitHub in the browser and enable Desktop site).
5. Select the ZIP from your phone.
6. Commit directly to `albion-pattern-mining`. Do not create a new branch or pull request.
7. Wait for the commit to finish.

## What happens after upload

The `Albion capture ingest` GitHub Actions workflow is configured to run when files under `research/inbox/` change on `albion-pattern-mining`.

It should:
1. Download the repository.
2. Safely extract the ZIP.
3. Parse JSONL records and preserve source archives.
4. Extract/deduplicate Event 40 node IDs.
5. Link Event 46 updates only by exact runtime entity ID.
6. Keep state-only IDs and ambiguous markers separate.
7. Write normalized JSON/CSV under `research/database/`.
8. Write a capture report under `research/analysis/`.
9. Run tests under `research/tests/`.
10. Commit derived outputs back to `albion-pattern-mining`.

Check workflow runs here:
https://github.com/eesanezhad76/v2rayNG/actions/workflows/albion-capture-ingest.yml

A successful upload commit does not itself mean analysis succeeded. Wait for the workflow conclusion to be **success**. If it fails, send me the Actions run URL or a screenshot/log; do not keep uploading duplicates.

## Where test material belongs

- **Capture data / test recordings:** ZIP in `research/inbox/`.
- **New Python offline test code:** `research/tests/`.
- **Android/JUnit tests:** keep them in their existing Gradle source-set folders, such as `V2rayNG/app/src/test/`; do not move them into research/tests or Gradle may stop running them.
- **Analysis notes / observed results:** `research/analysis/`.
- **Approved production mappings:** `research/decisions/`, then selectively copied into app code after regression tests.

You do not need to edit code or analyze the ZIP yourself. Upload it to the inbox and send me the filename or Actions run link in ChatGPT. I will inspect the workflow, analyze the evidence, update the database/reports/tests, and only then decide whether any verified finding belongs in the APK.

## Important limitations

The current ingest automation is an initial pipeline, not a guarantee that every PCAP protocol field is decoded. JSONL schemas and new event types may require analyzer updates. Workflow success means the implemented parsing/tests completed; it does not prove every interpretation is correct or that the APK was tested on-device.
