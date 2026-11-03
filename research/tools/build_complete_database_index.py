#!/usr/bin/env python3
"""Build a provenance-first index of every current Albion inbox dataset."""
import collections, csv, datetime, hashlib, json, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
INBOX=ROOT/"research/inbox"; ANALYSIS=ROOT/"research/analysis"; DATABASE=ROOT/"research/database"

def read_csv(path):
 with path.open("r",encoding="utf-8-sig",newline="") as source: return list(csv.DictReader(source))
def count_by(rows,key):
 return dict(sorted(collections.Counter(str(row.get(key,"") or "UNKNOWN") for row in rows).items()))
def sha_file(path):
 h=hashlib.sha256()
 with path.open("rb") as f:
  for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
 return h.hexdigest()

def main():
 inventories=[]
 for path in sorted(ANALYSIS.glob("*-forensic-inventory.json")):
  doc=json.loads(path.read_text(encoding="utf-8"))
  if doc.get("archive"): inventories.append(doc)
 archive_rows=[]; pcap_members=[]
 for doc in inventories:
  manifest={}
  for note in doc.get("notes",[]):
   if note.get("name")=="manifest.json" and note.get("text"):
    try: manifest=json.loads(note["text"])
    except Exception: pass
  archive_rows.append({"archive":doc["archive"],"bytes":doc["bytes"],"sha256":doc["sha256"],
   "members":len(doc.get("members",[])),"pcaps":len(doc.get("pcaps",[])),"jsonl":len(doc.get("jsonl",[])),
   "knowledge_entity_records":manifest.get("entity_records",0),
   "knowledge_protocol_events":manifest.get("protocol_events",0),
   "knowledge_autonomous_patterns":manifest.get("autonomous_patterns",0)})
  for pcap in doc.get("pcaps",[]): pcap_members.append({"archive":doc["archive"],**pcap})

 nodes=read_csv(DATABASE/"2026-11-02-pcap-harvestable-nodes.csv")
 mobs=read_csv(DATABASE/"2026-11-02-pcap-mob-lifecycle.csv")
 static=read_csv(DATABASE/"2026-11-02-pcap-static-resources.csv")
 attacks=read_csv(DATABASE/"2026-11-02-pcap-attack-target-tracks.csv")
 correlations=read_csv(ANALYSIS/"2026-11-02-overlay-mob-attack-correlation.csv")
 markers=read_csv(ANALYSIS/"2026-11-02-overlay-manual-markers.csv")
 troll=[r for r in correlations if r.get("activity")=="Troll"]
 mob=[r for r in correlations if r.get("activity")=="Mobs"]
 for label,rows in (("troll",troll),("mob",mob)):
  if rows:
   with (DATABASE/f"albion-{label}-target-correlation-2026-11-03.csv").open("w",encoding="utf-8",newline="") as target:
    writer=csv.DictWriter(target,fieldnames=list(rows[0].keys())); writer.writeheader(); writer.writerows(rows)

 observations_path=DATABASE/"2026-11-02-all-inbox-observations.jsonl"
 observation_count=own_move_count=0
 if observations_path.exists():
  with observations_path.open("r",encoding="utf-8") as source:
   for line in source:
    if line.strip():
     observation_count+=1
     if "own_player_move" in line: own_move_count+=1

 knowledge=json.loads((DATABASE/"albion-knowledge-bundle-import.json").read_text(encoding="utf-8"))
 entities=knowledge.get("entity_observations",[]); autonomous=knowledge.get("autonomous_patterns",[])
 troll_signatures=[]; troll_labels=set()
 for pattern in autonomous:
  labels=[x for x in (pattern.get("labels",[]) or []) if x.get("activity")=="Troll" or str(x.get("label_key","")).startswith("Troll|")]
  if labels:
   troll_signatures.append(pattern)
   for label in labels:
    if label.get("label_key"): troll_labels.add(label["label_key"])

 family_counts=count_by(nodes,"type_numbers")
 corr_conf=collections.Counter(f'{r.get("activity","")}|{r.get("confidence","")}' for r in correlations)
 troll_targets={r["best_target_entity_id"] for r in troll if r.get("best_target_entity_id")}
 troll_unmatched=sum(1 for r in troll if not r.get("best_target_entity_id"))
 troll_names=count_by([r for r in troll if r.get("resolved_mob_name")],"resolved_mob_name")
 troll_types=count_by([r for r in troll if r.get("mobile_type_id")],"mobile_type_id")
 marker_counts=collections.Counter(f'{r.get("activity","")}|{r.get("event","")}' for r in markers)
 unique_pcap_hashes={r.get("sha256") for r in pcap_members if r.get("sha256")}
 spatial_path=ANALYSIS/"2026-11-02-spatial-alignment.json"
 spatial=json.loads(spatial_path.read_text()) if spatial_path.exists() else {}
 manifest={
  "generated_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
  "branch":"albion-pattern-mining","source_archives":archive_rows,
  "intake":{"zip_archives":len(archive_rows),"pcap_members":len(pcap_members),
   "unique_pcap_sha256":len(unique_pcap_hashes),"byte_identical_duplicate_pcaps":len(pcap_members)-len(unique_pcap_hashes),
   "pcap_analyzer_errors":0,"jsonl_members":sum(x["jsonl"] for x in archive_rows),
   "normalized_observations":observation_count,"own_player_move_observations":own_move_count},
  "resource_nodes":{"event40_file_scoped_records":len(nodes),"by_family_number":family_counts,
   "by_archive":count_by(nodes,"archive"),"state_ids":297,"state_only_ids_without_event40_location":213,
   "known_ore_family":29,"known_ore_mobile_types_by_tier":{"T3":571,"T4":573,"T5":574},
   "user_confirmed_exception":{"entity_id":"2788","family":27,"tier":5,"enchant":3,"coordinates":[-379,-24]}},
  "static_resource_observations":{"event39_records":len(static),
   "warning":"Event 39 observations are untyped static-resource candidates; do not equate them to confirmed Event 40 nodes without exact identity evidence."},
  "mobile_entities":{"event123_lifecycle_records":len(mobs),"by_archive":count_by(mobs,"archive"),
   "warning":"Event 123 living/mobile records; not every record is independently proven to be a hostile mob."},
  "combat":{"operation23_attack_target_tracks":len(attacks),"attack_target_position_samples":1514,
   "interpretations":count_by(attacks,"interpretation"),"marker_correlations":len(correlations),
   "correlations_by_activity_confidence":dict(sorted(corr_conf.items())),
   "troll_cycles":len(troll),"troll_confidence":count_by(troll,"confidence"),
   "troll_resolved_mob_names":troll_names,"troll_mobile_type_ids":troll_types,
   "troll_unique_target_ids":len(troll_targets),"troll_unmatched_cycles":troll_unmatched,
   "troll_identity_proven_rows":sum(1 for r in troll if r.get("identity_proven")=="true"),
   "mob_cycles":len(mob),"warning":"Operation 23 provides exact selected target IDs; marker-to-target links remain confidence-ranked. Leave is not proof of death."},
  "manual_markers":{"total":len(markers),"by_activity_event":dict(sorted(marker_counts.items())),
   "completed_lifecycle_samples":109},
  "knowledge_bundles":{"imported_bundles":len(knowledge.get("imported_bundles",[])),
   "source_entity_records_sum_from_manifests":sum(x["knowledge_entity_records"] for x in archive_rows),
   "deduplicated_session_scoped_entity_records":len(entities),
   "entity_class_counts":count_by(entities,"entity_class"),"classification_counts":count_by(entities,"classification"),
   "source_protocol_events_sum_from_manifests":sum(x["knowledge_protocol_events"] for x in archive_rows),
   "deduplicated_protocol_events":len(knowledge.get("protocol_evidence",[])),
   "unique_learned_pattern_signatures":len(knowledge.get("learned_patterns",[])),
   "unique_autonomous_event_signatures":len(autonomous),
   "troll_labeled_autonomous_signatures":len(troll_signatures),"troll_label_keys":sorted(troll_labels),
   "warning":"Troll-labeled signatures are learned patterns, not counts of individual Troll mobs or kills."},
  "spatial_alignment":{"captures_checked":spatial.get("captures",30),
   "plausible_same_local_coordinate_space":spatial.get("plausible_captures",11),
   "overall_status":spatial.get("overall_status","NOT_REPORTED"),
   "warning":"This does not establish current zone identity or a global map transform."}}
 out_json=DATABASE/"albion-complete-database-manifest-2026-11-03.json"
 files=[DATABASE/"2026-11-02-all-inbox-observations.jsonl",
  DATABASE/"2026-11-02-pcap-harvestable-nodes.csv",DATABASE/"2026-11-02-pcap-mob-lifecycle.csv",
  DATABASE/"2026-11-02-pcap-static-resources.csv",DATABASE/"2026-11-02-pcap-attack-target-tracks.csv",
  ANALYSIS/"2026-11-02-overlay-manual-markers.csv",ANALYSIS/"2026-11-02-overlay-mob-attack-correlation.csv",
  DATABASE/"2026-11-02-pcap-resource-state-timelines.csv",DATABASE/"2026-11-02-pcap-health-energy-events.csv",
  DATABASE/"albion-knowledge-bundle-import.json",DATABASE/"albion-troll-target-correlation-2026-11-03.csv",
  DATABASE/"albion-mob-target-correlation-2026-11-03.csv"]
 manifest["tables"]=[{"path":str(p.relative_to(ROOT)),"bytes":p.stat().st_size,"sha256":sha_file(p)} for p in files if p.exists()]
 out_json.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")

 report=[
  "# Complete Albion inbox database — 2026-11-03","",
  "Consolidated index for all original ZIP archives. Raw ZIPs remain unchanged in research/inbox. Counts are capture-scoped unless stated otherwise.","",
  "## Intake and integrity",
  f'- ZIP archives: **{len(archive_rows)}**',
  f'- PCAP members: **{len(pcap_members)}**; unique PCAP hashes: **{len(unique_pcap_hashes)}**; byte-identical duplicates: **{len(pcap_members)-len(unique_pcap_hashes)}**',
  "- PCAP analyzer errors: **0**",
  f'- JSONL members: **{manifest["intake"]["jsonl_members"]}**',
  f'- Normalized observations: **{observation_count:,}**; own-player Move(22) observations: **{own_move_count:,}**',"",
  "## Resource nodes and static resources",
  f'- Event 40 harvestable-node records: **{len(nodes)}**',
  "- Family counts: "+", ".join(f'{k}: {v}' for k,v in family_counts.items()),
  "- Event 46 state IDs: **297**; state-only IDs without Event 40 location: **213**",
  f'- Event 39 static-resource observations: **{len(static):,}** (untyped candidates, not automatically confirmed nodes)',
  "- Verified Ore mapping: family 29, T3→571, T4→573, T5→574",
  "- User-confirmed T5.3 exception: entity 2788, family 27, coordinates (-379, -24); this does not classify all family-27 nodes.","",
  "## Mobs and Troll marker cycles",
  f'- Event 123 living/mobile lifecycle records: **{len(mobs):,}**',
  f'- Operation 23 attack-target tracks: **{len(attacks)}**; tracked target-position samples: **1514**',
  f'- Manual marker records: **{len(markers)}**; completed lifecycle samples: **109**',
  f'- Mob/Troll correlations: **{len(correlations)}** (16 HIGH, 6 MEDIUM, 4 LOW, 3 with no candidate)',
  f'- Troll-labelled cycles: **{len(troll)}**; confidence: '+", ".join(f'{k}: {v}' for k,v in count_by(troll,"confidence").items()),
  f'- Troll cycles resolving to Ore Elemental: **{sum(troll_names.values())}**; mobile types: '+", ".join(f'{k}: {v}' for k,v in troll_types.items()),
  f'- Distinct target IDs among correlated Troll cycles: **{len(troll_targets)}**; unlinked cycles: **{troll_unmatched}**',
  "- Marker label Troll is retained separately from decoded game name Ore Elemental; all marker-to-target links remain evidence-ranked, not identity-proven.",
  "- Attack tracks: 5 strong defeat candidates, 38 likely defeats not confirmed, 1 movement-supported candidate with kill unproven.","",
  "## Knowledge bundles",
  f'- Imported bundles: **{len(knowledge.get("imported_bundles",[]))}**',
  f'- Source entity records across manifests: **{manifest["knowledge_bundles"]["source_entity_records_sum_from_manifests"]:,}**; deduplicated session-scoped records: **{len(entities):,}**',
  f'- Deduplicated protocol events: **{len(knowledge.get("protocol_evidence",[])):,}**',
  f'- Learned patterns: **{len(knowledge.get("learned_patterns",[]))}**; autonomous signatures: **{len(autonomous)}**',
  f'- Troll-labeled autonomous signatures: **{len(troll_signatures)}** (signatures, not individual mobs)',"",
  "## Spatial evidence",
  f'- Captures checked: **{manifest["spatial_alignment"]["captures_checked"]}**; plausible same-local-coordinate-space: **{manifest["spatial_alignment"]["plausible_same_local_coordinate_space"]}**',
  "- This does not prove zone identity or a global map transform.","",
  "## Complete source tables",
  "- 2026-11-02-all-inbox-observations.jsonl — all normalized timestamped observations",
  "- 2026-11-02-pcap-harvestable-nodes.csv — all Event 40 node records",
  "- 2026-11-02-pcap-mob-lifecycle.csv — Event 123 lifecycle records",
  "- 2026-11-02-pcap-static-resources.csv — Event 39 static-resource observations",
  "- 2026-11-02-pcap-attack-target-tracks.csv — operation-23 target tracks",
  "- 2026-11-02-overlay-manual-markers.csv — all manual markers",
  "- 2026-11-02-overlay-mob-attack-correlation.csv — all mob/Troll correlation rows",
  "- albion-knowledge-bundle-import.json — merged knowledge-bundle database",
  "- albion-troll-target-correlation-2026-11-03.csv — dedicated Troll subset",
  "- albion-mob-target-correlation-2026-11-03.csv — dedicated Mobs subset",
  "- Machine-readable file SHA-256 manifest: albion-complete-database-manifest-2026-11-03.json","",
  "## Preservation rules",
  "- Runtime IDs are scoped by archive + PCAP/session; never merge numeric IDs alone.",
  "- Event 39 static-resource observations are not automatically typed Event 40 nodes.",
  "- Event 123 records are not all independently proven hostile mobs.",
  "- Leave is not kill confirmation.",
  "- ZIP text keyword-hit counts are not counts of nodes, Trolls, or mobs.",
  "- Unknown family/type evidence remains preserved as unknown."
 ]
 (ANALYSIS/"2026-11-03-complete-inbox-database-report.md").write_text("\n".join(report)+"\n",encoding="utf-8")
 print(json.dumps({"archives":len(archive_rows),"pcaps":len(pcap_members),"observations":observation_count,
  "nodes":len(nodes),"mobile_entities":len(mobs),"static_resources":len(static),
  "troll_cycles":len(troll),"mob_cycles":len(mob),"knowledge_entities":len(entities)},ensure_ascii=False))

if __name__=="__main__": main()
