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

The `Albion capture ingest` GitHub Actions workflow runs when capture ZIPs or relevant decoder/analyzer code change on `albion-pattern-mining`.

**Important: you do not need to upload PCAPs to ChatGPT.** The workflow runs on GitHub's runner, so ZIPs larger than the chat upload limit can still be analyzed there. Every `.pcap` inside every ZIP in `research/inbox/` is extracted temporarily and passed to the standalone Kotlin/JVM analyzer. It uses the same Kotlin Photon packet and Protocol18 decoder classes used by the Android app; it is not a generic filename-only scanner and it does not depend on JSONL being present.

The workflow now:
1. Checks the archive inventory and extracts available README/note text.
2. Safely extracts each PCAP from every ZIP while leaving the original ZIP unchanged.
3. Decodes the PCAP link/IP/UDP/Photon envelope and Protocol18 messages with the project's Kotlin decoder.
4. Records operation requests, including Move (22) and AttackStart (23).
5. Exports the local player's outbound movement coordinate timeline.
6. Extracts Event 39 static resources, Event 40 harvestable nodes, Event 46 state changes and joins them only by exact runtime ID inside the same capture.
7. Preserves Event 123 mobile/living entities, health/energy events (6/7/8/91/92), attacker/source IDs, and Leave events for combat correlation.
8. Writes normalized CSV/JSONL databases under `research/database/`.
9. Writes a full-inbox report and individual per-PCAP forensic reports under `research/analysis/`.
10. Runs Python validation tests and the standalone Kotlin analyzer, then commits derived evidence to `albion-pattern-mining`.

The primary normalized files include:
- `2026-11-02-own-player-movement-timeline.csv`
- `2026-11-02-pcap-harvestable-nodes.csv`
- `2026-11-02-pcap-resource-state-timelines.csv`
- `2026-11-02-pcap-static-resources.csv`
- `2026-11-02-pcap-mob-lifecycle.csv`
- `2026-11-02-pcap-health-energy-events.csv`
- `2026-11-02-pcap-attack-target-tracks.csv`
- `2026-11-02-all-inbox-observations.jsonl`

Check workflow runs here:
https://github.com/eesanezhad76/v2rayNG/actions/workflows/albion-capture-ingest.yml

A successful upload commit does not itself mean analysis succeeded. Wait for the workflow conclusion to be **success**. If it fails, send me the Actions run URL or a screenshot/log; do not keep uploading duplicates. The report distinguishes decoded facts from uncertain interpretations: for example, Leave means the server removed an entity from local visibility and is not by itself proof of death.

## Where test material belongs

- **Capture data / test recordings:** ZIP in `research/inbox/`.
- **New Python offline test code:** `research/tests/`.
- **Android/JUnit tests:** keep them in their existing Gradle source-set folders, such as `V2rayNG/app/src/test/`; do not move them into research/tests or Gradle may stop running them.
- **Analysis notes / observed results:** `research/analysis/`.
- **Approved production mappings:** `research/decisions/`, then selectively copied into app code after regression tests.

You do not need to edit code or analyze the ZIP yourself. Upload it to the inbox and send me the filename or Actions run link in ChatGPT. I will inspect the workflow, analyze the evidence, update the database/reports/tests, and only then decide whether any verified finding belongs in the APK.

## Important limitations

The current ingest automation is an initial pipeline, not a guarantee that every PCAP protocol field is decoded. JSONL schemas and new event types may require analyzer updates. Workflow success means the implemented parsing/tests completed; it does not prove every interpretation is correct or that the APK was tested on-device.
