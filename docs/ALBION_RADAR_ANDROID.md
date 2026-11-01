# Albion Android radar integration

This branch is a feasibility checkpoint, not a working radar APK.

## Confirmed architecture

The normal Android path establishes one VpnService TUN descriptor in CoreVpnService, passes it through CoreServiceManager to CoreController.startLoop(), and starts Xray from the AndroidLibXrayLite Go wrapper. The optional HEV mode uses a separate native hev-socks5-tunnel path.

A second Android VpnService cannot safely mirror the same traffic. Duplicating a TUN descriptor is not a safe passive tap because both descriptors refer to the same queue and a monitor could consume packets intended for the proxy.

## Native boundary

AndroidLibXrayLite and hev-socks5-tunnel are pinned git submodules. The Xray AAR is built separately with gomobile. A real packet-copy feature therefore requires a native-source change and a rebuilt AAR; Kotlin traffic counters do not expose raw Photon payloads.

## Protocol scope

OpenRadar documents harvestable events 39, 40, 46 and the living-resource path 123. Passive resource parsing is a separate task from player live-position recovery. The latter is protected by additional Photon and Albion protocol layers and is not promised by this project.

## Safe implementation plan

1. Add an opt-in, non-blocking packet-copy hook at the native TUN ingress. It must never consume, alter, delay or drop the original packet; queue overflow drops only monitor copies.
2. Filter candidate Albion UDP flows.
3. Port and test the passive Photon/ENet resource-event parser.
4. Resolve zone identity and resource coordinates from validated events.
5. Add the Android radar UI only after real events are decoded.
6. Keep capture disabled by default and provide an immediate stop control.
7. Build all supported ABIs and test on a physical device with V2RayNG connected.

No native hook, APK, or physical-device validation has been completed in this checkpoint.


## Offline mining-session discovery (first implementation)

The radar activity now includes an **Analyze mining session** action after capture stops.
It reads the raw-IP PCAP locally and:
- parses IPv4/IPv6 UDP payloads (and Ethernet PCAP when present);
- ranks repeated UDP endpoint ports to select the dominant service port;
- groups payloads by service port, length bucket and leading bytes;
- reports stable and variable byte offsets for repeated payload-length groups;
- reports byte offsets that change between adjacent same-length packets;
- preserves the original PCAP as the authoritative raw evidence.

This is deliberately a discovery report, not a claim that the app already decodes Albion's Photon/Protocol18 event semantics. It does not yet identify mining actions, entity IDs, enchantment, remaining resource count or map coordinates, and it does not yet merge learned fields into a persistent semantic database. Those require a validated Photon/Protocol18 parser and controlled comparisons. The report labels patterns as candidates rather than assigning unsupported meanings.

### First field trial

For the first trial, run one continuous capture while doing mining only for about one hour. Avoid combat, gathering living mobs, and zone changes if possible. Stop capture and tap **Analyze mining session**. Export the PCAP as well; the PCAP is needed to independently validate any candidate. The resulting report should be retained with that PCAP.
