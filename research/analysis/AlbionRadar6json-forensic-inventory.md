# ZIP forensic inventory: AlbionRadar6json.zip

- ZIP SHA-256: c9e1a8ddca784e232f5464148a5958a0596752dcb752d0ac0c7fc959a060326c

## Members
- albion-1793718384341.report.txt (9704989 bytes)
- albion-1793718384341.jsonl (32088054 bytes)
- albion-1793718384341.pcap (9283681 bytes)
- albion-1793718370435.report.txt (3473 bytes)
- albion-1793718370435.jsonl (174554 bytes)
- albion-1793718370435.pcap (113931 bytes)
- albion-1793718347142.report.txt (3372 bytes)
- albion-1793718347142.jsonl (1822382 bytes)
- albion-1793718347142.pcap (2378757 bytes)
- albion-1793718210678.report.txt (3265 bytes)
- albion-1793718210678.jsonl (1219764 bytes)
- albion-1793718210678.pcap (1316681 bytes)

## PCAPs
- albion-1793718384341.pcap: pcap, 9283681 bytes, linktype=101
- albion-1793718370435.pcap: pcap, 113931 bytes, linktype=101
- albion-1793718347142.pcap: pcap, 2378757 bytes, linktype=101
- albion-1793718210678.pcap: pcap, 1316681 bytes, linktype=101

## JSONL
- albion-1793718384341.jsonl: 56067 lines; malformed=0; events={"96": 61, "90": 4, "123": 1562, "324": 114, "45": 62, "326": 2, "30": 110, "32": 94, "35": 58, "503": 2, "652": 1, "365": 6, "366": 13, "256": 1, "259": 1, "276": 167, "641": 2, "150": 2, "153": 2, "155": 2, "257": 2, "222": 2, "11": 487, "19": 534, "210": 75, "313": 2, "249": 2, "351": 1, "518": 1, "550": 1, "468": 4, "481": 1, "284": 5, "499": 2, "670": 10, "369": 2, "2": 2, "39": 1187, "201": 2, "209": 62, "294": 2, "295": 1, "403": 1, "202": 1, "104": 39, "103": 56, "349": 1, "469": 1, "3": 28111, "330": 1, "600": 3, "1": 4364, "604": 15, "544": 9, "159": 165, "40": 157, "392": 12, "29": 43, "21": 62, "24": 100, "14": 118, "8": 168, "18": 127, "13": 80, "277": 75, "312": 50, "20": 16, "95": 157, "311": 25, "91": 101, "59": 56, "46": 112, "304": 58, "376": 45, "152": 90, "82": 45, "61": 55, "558": 17, "97": 7, "113": 27, "6": 90, "140": 4, "16": 5, "22": 12, "44": 1, "211": 4, "673": 1, "674": 1, "694": 6, "47": 15, "457": 10, "93": 2, "81": 2, "60": 1, "540": 6, "308": 10, "297": 2, "542": 2, "541": 1, "139": 35, "588": 6, "28": 1, "587": 19, "215": 4, "486": 1, "487": 247, "488": 1, "360": 32, "462": 1, "203": 4, "576": 2, "362": 1, "601": 1, "393": 2, "394": 2, "99": 2, "26": 8, "27": 5, "100": 2, "309": 1, "291": 1, "7": 3, "17": 10, "543": 4, "699": 1, "328": 22, "603": 2, "650": 2}
- albion-1793718370435.jsonl: 523 lines; malformed=0; events={}
- albion-1793718347142.jsonl: 5447 lines; malformed=0; events={}
- albion-1793718210678.jsonl: 3647 lines; malformed=0; events={}

## Test hints (text-only; not proof)
- straight_then_stop: 6
- ninety_degree_turns: 2
- resource_nodes: 27
- troll_golem_mob: 0

## Note: albion-1793718370435.report.txt

Albion / Photon capture report
IP packets in file: 523
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
  t=1793718370462 kind=manual_marker activity=Capture event=capture_started
  t=1793718382809 kind=manual_marker activity=Capture event=capture_stopping
Capture file quality: GOOD (100/100)
PCAP records: 523
Captured bytes: 105539
Duration (ms): 12347
Timestamp regressions: 0
Invalid timestamp fractions: 0
Malformed record headers: 0
Truncated records: 0
Repeated packet payloads: 1
Gaps over 1 second: 0
Maximum observed gap (ms): 580
Native tap dropped copies: 0
Native tap drop rate (%): 0.00
Limitation: Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.
Limitation: A long timestamp gap is a continuity warning, not proof that packets were dropped.
Limitation: Native tap drop rate measures local tap-queue drops, not network packet loss.

Detailed entity/player observations: 2
  t=1793718370462 kind=manual_marker activity=Capture event=capture_started
  t=1793718382809 kind=manual_marker activity=Capture event=capture_stopping
Warning: No UDP datagrams involving port 5056 were found.

=== Marker-correlated pattern mining ===
Overlay markers read: 6
Complete Found -> Done sample windows: 0
Unclosed sample starts: 0
Capture files analyzed: 3
Byte-identical capture files skipped: 0
Source capture: albion-1793718370435.jsonl:cb7ae217d70f2b2f3ccb498c16d7b6ac0e13eb13b4c9bd36c63a81169463ffae
Source capture: albion-1793718347142.jsonl:813ded01eaff825eb567a73aa1d02b0fdf920bc93fb7909712c4088536fdc836
Source capture: albion-1793718210678.jsonl:8841a8051fb8ac6e8ce783ba33dd2dd0f2fd4574fec45571a16af211bdec932d
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
PCAP records (TUN packets + checkpoints): 523
Native tap dropped copies: 0
Manual checkpoints: 2
Non-Albion / unclassified packets: 521
Downloads mirror error: none


## Note: albion-1793718347142.report.txt

Albion / Photon capture report
IP packets in file: 5447
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
  t=1793718347169 kind=manual_marker activity=Capture event=capture_started
  t=1793718365950 kind=manual_marker activity=Capture event=capture_stopping
Capture file quality: GOOD (100/100)
PCAP records: 5447
Captured bytes: 2291581
Duration (ms): 18781
Timestamp regressions: 1
Invalid timestamp fractions: 0
Malformed record headers: 0
Truncated records: 0
Repeated packet payloads: 9
Gaps over 1 second: 0
Maximum observed gap (ms): 335
Native tap dropped copies: 15
Native tap drop rate (%): 0.27
Limitation: Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.
Limitation: A long timestamp gap is a continuity warning, not proof that packets were dropped.
Limitation: Native tap drop rate measures local tap-queue drops, not network packet loss.

Detailed entity/player observations: 2
  t=1793718347169 kind=manual_marker activity=Capture event=capture_started
  t=1793718365950 kind=manual_marker activity=Capture event=capture_stopping
Warning: No UDP datagrams involving port 5056 were found.

=== Marker-correlated pattern mining ===
Overlay markers read: 4
Complete Found -> Done sample windows: 0
Unclosed sample starts: 0
Capture files analyzed: 2
Byte-identical capture files skipped: 0
Source capture: albion-1793718347142.jsonl:813ded01eaff825eb567a73aa1d02b0fdf920bc93fb7909712c4088536fdc836
Source capture: albion-1793718210678.jsonl:8841a8051fb8ac6e8ce783ba33dd2dd0f2fd4574fec45571a16af211bdec932d
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
PCAP records (TUN packets + checkpoints): 5447
Native tap dropped copies: 15
Manual checkpoints: 2
Non-Albion / unclassified packets: 5445
Downloads mirror error: none


## Note: albion-1793718210678.report.txt

Albion / Photon capture report
IP packets in file: 3647
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
  t=1793718210736 kind=manual_marker activity=Capture event=capture_started
  t=1793718344297 kind=manual_marker activity=Capture event=capture_stopping
Capture file quality: FAIR (80/100)
PCAP records: 3647
Captured bytes: 1258305
Duration (ms): 133561
Timestamp regressions: 0
Invalid timestamp fractions: 0
Malformed record headers: 0
Truncated records: 0
Repeated packet payloads: 10
Gaps over 1 second: 18
Maximum observed gap (ms): 9759
Native tap dropped copies: 6
Native tap drop rate (%): 0.16
Limitation: Packet duplicates are counted, not assumed to be packet loss; retransmission/repetition may be legitimate.
Limitation: A long timestamp gap is a continuity warning, not proof that packets were dropped.
Limitation: Native tap drop rate measures local tap-queue drops, not network packet loss.

Detailed entity/player observations: 2
  t=1793718210736 kind=manual_marker activity=Capture event=capture_started
  t=1793718344297 kind=manual_marker activity=Capture event=capture_stopping
Warning: No UDP datagrams involving port 5056 were found.

=== Marker-correlated pattern mining ===
Overlay markers read: 2
Complete Found -> Done sample windows: 0
Unclosed sample starts: 0
Capture files analyzed: 1
Byte-identical capture files skipped: 0
Source capture: albion-1793718210678.jsonl:8841a8051fb8ac6e8ce783ba33dd2dd0f2fd4574fec45571a16af211bdec932d
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
PCAP records (TUN packets + checkpoints): 3647
Native tap dropped copies: 6
Manual checkpoints: 2
Non-Albion / unclassified packets: 3645
Downloads mirror error: none
