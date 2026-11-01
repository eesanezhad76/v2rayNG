# Albion Android capture and radar status

This branch is an integrated capture/decoder feasibility build, not yet a validated live radar APK.

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

This does not yet establish a complete player-position decoder, zone-coordinate transform, or live map UI. Event interpretation still requires validation against real PCAPs captured during gameplay.

## Validation required before calling it reliable

1. Run the race-enabled Go tests for the native TUN tap.
2. Run Kotlin unit tests, including encrypted-packet classification and Protocol18 fragment reassembly.
3. Build the Android AAR and debug APK for all configured ABIs.
4. Install the arm64 APK on a physical Android device and verify that normal VPN traffic remains unaffected.
5. Capture while idle, moving, gathering, fighting mobs and changing zones; inspect PCAP, JSONL, drop counters and decoder statistics.
6. Force activity recreation, network handover, VPN stop/start and process restart; verify capture resumes only when the persisted opt-in is enabled and files remain readable.

No physical-device validation has been completed yet.
