# Albion Android capture and radar status

This branch is an integrated capture/decoder and local-coordinate radar build. CI validates offline integration; physical-device behavior is still unverified.

## Integration path

- The normal Android `VpnService` creates one TUN descriptor and passes it to `CoreServiceManager` and Xray.
- The passive packet-copy hook is implemented in the `eesanezhad76/Xray-core` fork under `proxy/tun/packet_tap.go`.
- It observes both packets read from the Android TUN and packets written back to it. It copies packet bytes into a bounded queue; it does not consume or rewrite the original traffic.
- `AndroidLibXrayLite` exposes enable/read/drop-count/disable methods to Kotlin.
- `CoreServiceManager` owns the capture worker in the same `:daemon` process as the VPN service and radar activity. Closing the activity does not stop the worker.
- The capture opt-in is persisted. After an Android service/process restart, the worker is started again when the core comes up.
- Core shutdown waits for capture finalization before stopping Xray and releasing the TUN descriptor.

## Reliability boundaries

The native observation queue is bounded to at most 4096 packets. The TUN forwarding path uses a non-blocking lock attempt; when the queue is full, contended, or a packet is too large, only the observer copy is dropped and the drop counter increments. This protects VPN forwarding latency, but it means absolute zero-loss capture cannot be promised. A zero drop counter is useful evidence, not proof that the network or upstream game server lost no packets.

The Kotlin worker writes PCAP and JSONL files under the app-specific `AlbionCaptures` directory and flushes its buffers periodically. On an explicit stop, it disables observation, drains queued copies, flushes and closes both files. The activity can publish completed files to `Downloads/AlbionCaptures`. If Android kills the process abruptly or storage fills, the last unflushed data or the remainder of the queue may still be lost.

## Decoder scope

- IPv4/IPv6 UDP candidate packets involving Albion Photon port 5056 are identified.
- Photon command envelopes and Protocol18 event, request, response and fragment paths are parsed.
- Fragment reassembly is bounded, duplicate-aware and expires stale partial messages.
- Protocol18 event code 252 and operation code 253 follow the OpenRadar parameter contract.
- Encrypted Photon and Protocol18 payloads are counted separately from malformed packets; they are not decrypted.
- Known entity events are mapped to typed observations for resources, harvestables, resource state, mobs, player lifecycle, health and dungeon exits.
- Unknown events are logged/inventoried rather than guessed.
- Outbound Photon operation 22 parameter 1 is corroborated by two independent movement captures as the local player's current [x, y] coordinate pair; parameters 2, 3 and 4 are retained as heading, target position and speed.
- The mini radar and a pan/zoom local-coordinate map consume the daemon live-knowledge snapshot. A bounded 240-point own-player trail is persisted and rendered.
- A learned-database ZIP export contains normalized live entity records, protocol evidence, the structured learned-pattern database, the latest pattern-learning report, a versioned manifest and SHA-256 digests; it excludes VPN profiles, credentials and raw PCAP payloads.
- The live entity store is persistent, capture-session scoped and bounded at 25,000 records. The pattern miner scans up to 32 recent independent captures, skips byte-identical copies by SHA-256, aggregates pattern signatures and retains source capture hashes.
- Unvalidated Event 3 other-player coordinates are not rendered on either map; they remain evidence-only until decoded and corroborated.

Important limits: no zone identifier or global coordinate transform has been validated, so the map is a coordinate plane for the currently observed game coordinate space, not a complete Albion world map with terrain/roads. Player/resource coordinate alignment is explicitly provisional until the same-capture PCAP spatial audit and device test pass. Arbitrary Event 3 movement payloads remain untrusted as other-player coordinates and are withheld from rendering. Resource/mobile mappings remain limited to evidence-backed catalog entries. Event interpretation and actual overlay delivery still require physical-device validation.

## Controlled first field test

For a Poco F5 / Android 15, use the `arm64-v8a` APK (or the universal APK). The debug package ID is `com.v2ray.ang.radar`, so it installs separately from the regular V2RayNG app.

1. Install the APK, open V2RayNG Radar, connect the VPN, then open the app drawer and select **Albion Radar**.
2. Grant the requested display-over-other-apps permission. Start **Full capture** before starting the overlay; keep the VPN connected.
3. In one known zone, move around for at least 30 seconds. Open **Live coordinate map** and check whether the green YOU marker follows movement. The mini-radar should also show YOU when movement packets are decoded.
4. Expand the overlay, select Ore, and use Found → Start → Done → Empty for at least three separate nodes. Set the tier/enchantment before each sample. These manual labels train pattern correlation; they do not independently prove a node's catalog identity.
5. Select Mobs and use Seen then Killed for at least two mobs. Do not mix a mob label with an Ore activity.
6. Stop capture from the Albion Radar screen. Check packet count, tap drops, writer errors, marker count, and the generated offline report. A nonzero tap-drop count must be retained in the report, not hidden.
7. Export **Learned database (ZIP)**. This compact ZIP contains the persistent entity cache, raw protocol evidence, learned pattern signatures, manifest and SHA-256 digests; it excludes VPN profiles and raw PCAP payloads. Send this ZIP back for central database ingestion. Do not rename its internal files.
8. Repeat in a second capture session in the same zone, then in a different zone. The database must preserve each session's observations without joining runtime entity IDs across zones. A world-map/zone transform is not claimed until independently decoded.

## Validation required before calling it reliable

1. Run the race-enabled Go tests for the native TUN tap.
2. Run Kotlin unit tests, including encrypted-packet classification and Protocol18 fragment reassembly.
3. Build the Android AAR and debug APK for all four ABI splits plus universal; verify every APK signature, package ID and SHA-256 checksum.
4. Install the arm64 APK on a physical Android device and verify that normal VPN traffic remains unaffected.
5. Complete the controlled field test above; inspect PCAP, JSONL, drop counters, decoder statistics, live map and overlay delivery.
6. Force activity recreation, network handover, VPN stop/start and process restart; verify capture resumes only when the persisted opt-in is enabled and files remain readable.
7. Export the learned-database ZIP, import it into the research vault, and verify source hashes, session-scoped entity records, protocol evidence and deduplicated pattern signatures.
8. Verify whether coordinates reset on zone changes and whether a zone identifier/global map transform can be decoded.

No physical-device validation has been completed yet. A CI build and offline replay are not substitutes for the first on-device test.
