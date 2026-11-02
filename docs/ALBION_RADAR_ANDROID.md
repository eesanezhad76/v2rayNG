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
- A learned-database ZIP export contains normalized live entity records, protocol evidence, a versioned manifest and SHA-256 digests; it excludes VPN profiles, credentials and raw PCAP payloads.

Important limits: no zone identifier or global coordinate transform has been validated, so the map is a coordinate plane for the currently observed game coordinate space, not a complete Albion world map with terrain/roads. Arbitrary Event 3 movement payloads remain untrusted as player coordinates. Resource/mobile mappings remain limited to evidence-backed catalog entries. Event interpretation and actual overlay delivery still require physical-device validation.

## Validation required before calling it reliable

1. Run the race-enabled Go tests for the native TUN tap.
2. Run Kotlin unit tests, including encrypted-packet classification and Protocol18 fragment reassembly.
3. Build the Android AAR and debug APK for all configured ABIs.
4. Install the arm64 APK on a physical Android device and verify that normal VPN traffic remains unaffected.
5. Capture while idle, moving, gathering, fighting mobs and changing zones; inspect PCAP, JSONL, drop counters and decoder statistics.
6. Force activity recreation, network handover, VPN stop/start and process restart; verify capture resumes only when the persisted opt-in is enabled and files remain readable.
7. Export the learned-database ZIP and verify its manifest, entity/protocol counts and SHA-256 values; compare it with the compact replay diagnostics.
8. Verify the local-coordinate map on-device while moving, gathering and changing zones; record whether the coordinate system resets and whether a zone identifier can be decoded.

No physical-device validation has been completed yet.
