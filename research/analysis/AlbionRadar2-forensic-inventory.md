# ZIP forensic inventory: AlbionRadar2.zip

- ZIP SHA-256: 3fc1413d80c973cb26a2d6501320794ba5e2f3aeb7754f87ff9fe90a3689a0af

## Members
- albion-1793699283778.report.txt (3839579 bytes)
- albion-1793699283778.jsonl (19688976 bytes)
- albion-1793699283778.pcap (9661933 bytes)
- albion-1793699145258.pcap (698976 bytes)
- albion-1793699145258.report.txt (3371 bytes)
- albion-1793699145258.jsonl (608931 bytes)
- albion-1793699111286.report.txt (3258 bytes)
- albion-1793699111286.jsonl (177643 bytes)
- albion-1793699111286.pcap (192843 bytes)

## PCAPs
- albion-1793699283778.pcap: pcap, 9661933 bytes, linktype=101
- albion-1793699145258.pcap: pcap, 698976 bytes, linktype=101
- albion-1793699111286.pcap: pcap, 192843 bytes, linktype=101

## JSONL
- albion-1793699283778.jsonl: 43024 lines; malformed=0; events={"96": 46, "90": 2, "604": 10, "544": 5, "392": 3, "123": 737, "324": 39, "45": 39, "326": 1, "30": 38, "32": 31, "35": 19, "503": 1, "652": 1, "365": 3, "366": 7, "256": 1, "259": 1, "276": 68, "641": 1, "150": 1, "153": 1, "155": 1, "257": 1, "222": 1, "11": 203, "19": 107, "210": 23, "313": 2, "249": 1, "351": 1, "518": 1, "550": 1, "468": 1, "481": 1, "284": 1, "539": 1, "499": 1, "670": 1, "649": 1, "369": 1, "2": 1, "39": 456, "209": 39, "201": 1, "294": 2, "295": 1, "403": 1, "202": 1, "104": 2, "103": 2, "349": 1, "469": 1, "330": 1, "3": 11260, "600": 1, "159": 67, "1": 1797, "40": 83, "13": 62, "277": 50, "312": 59, "20": 8, "21": 32, "95": 140, "97": 5, "297": 1, "14": 33, "46": 45, "8": 49, "18": 36, "113": 25, "22": 8, "162": 24, "308": 4, "540": 3, "542": 3, "311": 6, "91": 25, "59": 15, "304": 15, "376": 12, "152": 24, "82": 12, "61": 14, "541": 1, "29": 12, "211": 2, "16": 3, "24": 19, "558": 3, "603": 1, "47": 2, "6": 26, "140": 4, "457": 4, "7": 2, "17": 9, "81": 1, "270": 1, "93": 1, "543": 1}
- albion-1793699145258.jsonl: 1821 lines; malformed=0; events={}
- albion-1793699111286.jsonl: 532 lines; malformed=0; events={}

## Test hints (text-only; not proof)
- straight_then_stop: 4
- ninety_degree_turns: 1
- resource_nodes: 18
- troll_golem_mob: 0

## Note: albion-1793699145258.report.txt

Albion / Photon capture report
IP packets in file: 1821
UDP packets involving port 5056: 0
Valid Photon envelopes: 0
Inbound to local device: 0
Outbound from local device: 0
Photon command types (transport commands, not Albion events):
Protocol18 Albion event codes:
Operation requests:
Operation responses:
Mapped entity observations:
Typed Photon event observations:
Malformed / unrecognized Photon envelopes: 0
Encrypted Photon packets: 0
Encrypted Protocol18 messages: 0
Malformed Protocol18 messages: 0
Pending fragmented messages: 0
Manual checkpoints: 2
  t=1793699145279 kind=manual_marker activity=Capture event=capture_started
  t=1793699282439 kind=manual_marker activity=Capture event=capture_stopping
Capture file quality: FAIR (80/100)
PCAP records: 1821
Captured bytes: 669816
Duration (ms): 137166
Timestamp regressions: 0
Invalid timestamp fractions: 0
Malformed record headers: 0
Truncated records: 0
Repeated packet payloads: 7
Gaps over 1 second: 18
Maximum observed gap (ms): 9997
Native tap dropped copies: 7
Native tap drop rate (%): 0.38
Limitation: Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.
Limitation: A long timestamp gap is a continuity warning, not proof that packets were dropped.
Limitation: Native tap drop rate measures local tap-queue drops, not network packet loss.

Detailed entity/player observations: 2
  t=1793699145279 kind=manual_marker activity=Capture event=capture_started
  t=1793699282439 kind=manual_marker activity=Capture event=capture_stopping
Warning: No UDP datagrams involving port 5056 were found.

=== Marker-correlated pattern mining ===
Overlay markers read: 4
Complete Found -> Done sample windows: 0
Unclosed sample starts: 0
Capture files analyzed: 2
Byte-identical capture files skipped: 0
Source capture: albion-1793699145258.jsonl:02f69cae7816d4f44c189bf040ac6f60c7127bc33fe225645228e70ddc828aa0
Source capture: albion-1793699111286.jsonl:1b4e9524f7e816dcfc1877675dfb3cb70dc7a131cbc6041281495431664a458b
Confidence is a heuristic evidence score (0-100), NOT a probability.
No repeatable labeled pattern found yet.
Warning: No complete Found -> Done marker pairs across the selected captures.
Warning: No signature has repeated across at least 3 independent labeled samples yet.
Warning: No decoded entity observations were available in labeled windows.
Warning: Entity IDs are scoped per capture; cross-capture pattern aggregation does not merge runtime entity identities.

Live knowledge cache
Unique tracked entities: 0
Current-session map entities: 0
Typed resource observations: 0
Static resource candidates (not confirmed Ore): 0
Entities marked depleted: 0
Corroborated by same-ID resource state: 0
Repeated typed resource observations: 0
Ambiguous entities due to conflicting typed evidence: 0
Average evidence score (0-100 heuristic): 0
Evidence score is an ordinal heuristic, not a calibrated probability.
Persistent file: albion-live-knowledge.json
Classification is conservative; no family-to-Ore guesses are made.

Autonomous signatures: 0 | repeated: 0 | marker-correlated: 0
Unknown signatures remain candidates; no semantic names are guessed.
Capture integrity
PCAP records (TUN packets + checkpoints): 1821
Native tap dropped copies: 7
Manual checkpoints: 2
Non-Albion / unclassified packets: 1819
Downloads mirror error: none


## Note: albion-1793699111286.report.txt

Albion / Photon capture report
IP packets in file: 532
UDP packets involving port 5056: 0
Valid Photon envelopes: 0
Inbound to local device: 0
Outbound from local device: 0
Photon command types (transport commands, not Albion events):
Protocol18 Albion event codes:
Operation requests:
Operation responses:
Mapped entity observations:
Typed Photon event observations:
Malformed / unrecognized Photon envelopes: 0
Encrypted Photon packets: 0
Encrypted Protocol18 messages: 0
Malformed Protocol18 messages: 0
Pending fragmented messages: 0
Manual checkpoints: 2
  t=1793699111317 kind=manual_marker activity=Capture event=capture_started
  t=1793699143756 kind=manual_marker activity=Capture event=capture_stopping
Capture file quality: GOOD (90/100)
PCAP records: 532
Captured bytes: 184307
Duration (ms): 32439
Timestamp regressions: 0
Invalid timestamp fractions: 0
Malformed record headers: 0
Truncated records: 0
Repeated packet payloads: 3
Gaps over 1 second: 5
Maximum observed gap (ms): 10477
Native tap dropped copies: 2
Native tap drop rate (%): 0.37
Limitation: Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.
Limitation: A long timestamp gap is a continuity warning, not proof that packets were dropped.
Limitation: Native tap drop rate measures local tap-queue drops, not network packet loss.

Detailed entity/player observations: 2
  t=1793699111317 kind=manual_marker activity=Capture event=capture_started
  t=1793699143756 kind=manual_marker activity=Capture event=capture_stopping
Warning: No UDP datagrams involving port 5056 were found.

=== Marker-correlated pattern mining ===
Overlay markers read: 2
Complete Found -> Done sample windows: 0
Unclosed sample starts: 0
Capture files analyzed: 1
Byte-identical capture files skipped: 0
Source capture: albion-1793699111286.jsonl:1b4e9524f7e816dcfc1877675dfb3cb70dc7a131cbc6041281495431664a458b
Confidence is a heuristic evidence score (0-100), NOT a probability.
No repeatable labeled pattern found yet.
Warning: No complete Found -> Done marker pairs across the selected captures.
Warning: No signature has repeated across at least 3 independent labeled samples yet.
Warning: No decoded entity observations were available in labeled windows.
Warning: Entity IDs are scoped per capture; cross-capture pattern aggregation does not merge runtime entity identities.

Live knowledge cache
Unique tracked entities: 0
Current-session map entities: 0
Typed resource observations: 0
Static resource candidates (not confirmed Ore): 0
Entities marked depleted: 0
Corroborated by same-ID resource state: 0
Repeated typed resource observations: 0
Ambiguous entities due to conflicting typed evidence: 0
Average evidence score (0-100 heuristic): 0
Evidence score is an ordinal heuristic, not a calibrated probability.
Persistent file: albion-live-knowledge.json
Classification is conservative; no family-to-Ore guesses are made.

Autonomous signatures: 0 | repeated: 0 | marker-correlated: 0
Unknown signatures remain candidates; no semantic names are guessed.
Capture integrity
PCAP records (TUN packets + checkpoints): 532
Native tap dropped copies: 2
Manual checkpoints: 2
Non-Albion / unclassified packets: 530
Downloads mirror error: none
