# Albion marker-correlated pattern collection

## How to collect a labeled Ore sample

1. Start capture and wait until the capture status says it is running.
2. In the overlay select **Ore**, then select the node's tier and enchantment.
3. Tap **Found** immediately before interacting with the node.
4. Mine the node normally.
5. Tap **Done** immediately after the node is depleted / the gather interaction finishes.
6. Repeat for each node. Keep the activity, tier and enchantment selections accurate.

Found opens a labeled sample window. Done closes it. The miner also includes a 10-second packet-observation margin around the window to catch spawn/state events that arrive just before or after the manual action. Samples are matched by event kind, event code, entity type, tier and enchantment; entity IDs are counted separately because they normally change from node to node.

## What the report means

The offline report groups observations that recur across separate Found -> Done samples. It includes:
- complete and incomplete marker pairs;
- repeated signatures and number of independent samples;
- distinct entity IDs and observation counts;
- observed Photon event codes;
- a 0-100 heuristic evidence score.

The score is a ranking aid, not a calibrated probability, protocol guarantee, or proof that an event field has the claimed game meaning. A high score means the same decoded signature recurred consistently in labeled windows; it does not prove the decoder's interpretation is correct. Unknown or encrypted payloads cannot be semantically classified by this miner.

## Interpreting a 50-node run

For 50 Ore nodes, aim for 50 complete Found -> Done pairs. Keep different resource activities separate. If you change tier/enchantment, the miner will create distinct signatures. A useful candidate should recur across multiple independent samples and have stable type/event fields while entity IDs vary. Inspect the underlying PCAP/JSONL before promoting a candidate into a production decoder rule.

## Integrity limits

- Markers are local PCAP/JSONL annotations; they are not sent to Albion or the VPN.
- The miner reads the JSONL file after capture stops and creates an offline report; it does not modify or inject game traffic.
- A sample with no decoded entity observations is retained as a complete labeled window but cannot produce a useful signature.
- Native tap queue loss, encrypted traffic, process survival, and Downloads mirroring still require device-level validation on the target phone.
