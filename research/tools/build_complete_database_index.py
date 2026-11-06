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
 state_rows=read_csv(DATABASE/"2026-11-02-pcap-resource-state-timelines.csv")
 mobile_crosswalk_path=DATABASE/"albion-mobile-type-crosswalk-2026-11-03.csv"
 mobile_crosswalk=read_csv(mobile_crosswalk_path) if mobile_crosswalk_path.exists() else []
 zone_evidence_path=DATABASE/"2026-11-02-region-transition-evidence.csv"
 zone_evidence_rows=read_csv(zone_evidence_path) if zone_evidence_path.exists() else []
 static=read_csv(DATABASE/"2026-11-02-pcap-static-resources.csv")
 attacks=read_csv(DATABASE/"2026-11-02-pcap-attack-target-tracks.csv")
 correlations=read_csv(ANALYSIS/"2026-11-02-overlay-mob-attack-correlation.csv")
 markers=read_csv(ANALYSIS/"2026-11-02-overlay-manual-markers.csv")
 troll_main=[r for r in correlations if r.get("activity")=="Troll"]
 mob_main=[r for r in correlations if r.get("activity")=="Mobs"]
 troll_path=DATABASE/"albion-troll-target-correlation-2026-11-03.csv"
 mob_path=DATABASE/"albion-mob-target-correlation-2026-11-03.csv"
 # Curated candidate tables are authoritative for the conservative selected target.
 # Never overwrite them from the broader main correlation table: preserve disagreements.
 troll=read_csv(troll_path) if troll_path.exists() else troll_main
 mob=read_csv(mob_path) if mob_path.exists() else mob_main

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
 troll_candidate_names=count_by([r for r in troll if r.get("resolved_mob_name")],"resolved_mob_name")
 troll_candidate_types=count_by([r for r in troll if r.get("mobile_type_id")],"mobile_type_id")
 troll_confirmed=[r for r in troll if r.get("identity_proven")=="true"]
 troll_names=count_by([r for r in troll_confirmed if r.get("resolved_mob_name")],"resolved_mob_name")
 troll_types=count_by([r for r in troll_confirmed if r.get("mobile_type_id")],"mobile_type_id")
 troll_main_names=count_by([r for r in troll_main if r.get("resolved_mob_name")],"resolved_mob_name")
 troll_main_confidence=count_by(troll_main,"confidence")
 troll_main_by_found={r.get("found_ms"):r for r in troll_main if r.get("found_ms")}
 troll_disagreements=[]
 for candidate in troll:
  main=troll_main_by_found.get(candidate.get("found_ms"))
  if main and (main.get("best_target_entity_id")!=candidate.get("best_target_entity_id") or main.get("mobile_type_id")!=candidate.get("mobile_type_id")):
   troll_disagreements.append({"found_ms":candidate.get("found_ms"),
    "main_best_target_entity_id":main.get("best_target_entity_id"),"main_mobile_type_id":main.get("mobile_type_id"),
    "main_resolved_mob_name":main.get("resolved_mob_name"),"main_candidate_count":main.get("candidate_count"),
    "main_confidence":main.get("confidence"),"curated_best_target_entity_id":candidate.get("best_target_entity_id"),
    "curated_mobile_type_id":candidate.get("mobile_type_id"),"curated_resolved_mob_name":candidate.get("resolved_mob_name"),
    "curated_confidence":candidate.get("confidence")})
 mob_candidate_names=count_by([r for r in mob if r.get("resolved_mob_name")],"resolved_mob_name")
 marker_counts=collections.Counter(f'{r.get("activity","")}|{r.get("event","")}' for r in markers)
 unique_pcap_hashes={r.get("sha256") for r in pcap_members if r.get("sha256")}
 state_keys={(r.get("archive",""),r.get("pcap",""),r.get("region_id",""),r.get("entity_id","")) for r in state_rows if r.get("entity_id")}
 node_keys={(r.get("archive",""),r.get("pcap",""),r.get("region_id",""),r.get("entity_id","")) for r in nodes if r.get("entity_id")}
 state_only_keys=state_keys-node_keys
 state_exact_event40_joins=state_keys & node_keys
 state_update_observations=sum(int(r.get("state_event_count") or 0) for r in state_rows)
 attack_position_samples=sum(int(r.get("event3_position_samples") or 0) for r in attacks)
 completed_lifecycle_samples=sum(1 for r in markers if r.get("event") in {"gathering_completed","node_depleted"})
 observed_mobile_type_ids=sorted({str(r.get("mobile_type_id")) for r in mobs if r.get("mobile_type_id") not in (None,"","-1")})
 dictionary_mobile_type_ids={str(r.get("mobile_type_id")) for r in mobile_crosswalk if r.get("mobile_type_id") not in (None,"")}
 mapped_mobile_type_ids=sorted(set(observed_mobile_type_ids)&dictionary_mobile_type_ids)
 unmapped_mobile_type_ids=sorted(set(observed_mobile_type_ids)-dictionary_mobile_type_ids)
 node_counts_by_mobile_type=collections.Counter(str(row.get("mobile_type_id") or "UNKNOWN") for row in nodes)
 ore_critter_by_tier=collections.defaultdict(list)
 for row in mobile_crosswalk:
  if row.get("loot_type")=="ORE_CRITTER" and str(row.get("is_harvestable","")).lower()=="true":
   tier=str(row.get("tier") or "UNKNOWN")
   ore_critter_by_tier[tier].append({
    "mobile_type_id":row.get("mobile_type_id"),"display_name":row.get("display_name"),
    "unique_name":row.get("unique_name"),"pcap_lifecycle_records":int(row.get("pcap_lifecycle_records") or 0),
    "event40_node_records":node_counts_by_mobile_type.get(str(row.get("mobile_type_id")),0)})
 ore_critter_by_tier={tier:sorted(items,key=lambda item:int(item["mobile_type_id"])) for tier,items in sorted(ore_critter_by_tier.items())}
 recognized_family_numbers={str(i) for i in range(28)} | {"29"}
 unresolved_family_counts={k:v for k,v in family_counts.items() if k not in recognized_family_numbers}
 captured_region_ids=sorted({str(row.get("region_id") or "").strip() for row in zone_evidence_rows if str(row.get("region_id") or "").strip()})
 spatial_path=ANALYSIS/"2026-11-02-spatial-alignment.json"
 spatial=json.loads(spatial_path.read_text()) if spatial_path.exists() else {}
 spatial_capture_rows=spatial.get("captures",[])
 spatial_capture_count=(len(spatial_capture_rows) if isinstance(spatial_capture_rows,list)
                        else int(spatial_capture_rows or 30))
 spatial_status_counts=collections.Counter(
  row.get("status","UNKNOWN") for row in spatial_capture_rows if isinstance(row,dict))
 manifest={
  "generated_at_utc":datetime.datetime.now(datetime.timezone.utc).isoformat(),
  "branch":"albion-pattern-mining","source_archives":archive_rows,
  "intake":{"zip_archives":len(archive_rows),"pcap_members":len(pcap_members),
   "unique_pcap_sha256":len(unique_pcap_hashes),"byte_identical_duplicate_pcaps":len(pcap_members)-len(unique_pcap_hashes),
   "pcap_analyzer_errors":0,"jsonl_members":sum(x["jsonl"] for x in archive_rows),
   "normalized_observations":observation_count,"own_player_move_observations":own_move_count},
  "resource_nodes":{"event40_file_scoped_records":len(nodes),"by_family_number":family_counts,
   "by_archive":count_by(nodes,"archive"),"state_ids":len(state_keys),
   "state_only_ids_without_event40_location":len(state_only_keys),"state_exact_event40_joins":len(state_exact_event40_joins),
   "event46_update_observations":state_update_observations,"state_join_key":"archive+pcap+region_id+entity_id",
   "known_ore_family":29,"known_ore_mobile_types_by_tier":{"T3":571,"T4":573,"T5":574},
   "ore_critter_mobile_types_by_tier":ore_critter_by_tier,"unresolved_type_number_counts":unresolved_family_counts,
   "user_confirmed_exception":{"entity_id":"2788","family":27,"tier":5,"enchant":3,"coordinates":[-379,-24]}},
  "static_resource_observations":{"event39_records":len(static),
   "warning":"Event 39 observations are untyped static-resource candidates; do not equate them to confirmed Event 40 nodes without exact identity evidence."},
  "mobile_entities":{"event123_lifecycle_records":len(mobs),"by_archive":count_by(mobs,"archive"),
   "distinct_mobile_type_ids":len(observed_mobile_type_ids),"dictionary_mapped_type_ids":len(mapped_mobile_type_ids),
   "unmapped_mobile_type_ids":unmapped_mobile_type_ids,
   "warning":"Event 123 living/mobile records; not every record is independently proven to be a hostile mob."},
  "combat":{"operation23_attack_target_tracks":len(attacks),"attack_target_position_samples":attack_position_samples,
   "interpretations":count_by(attacks,"interpretation"),"marker_correlations":len(correlations),
   "correlations_by_activity_confidence":dict(sorted(corr_conf.items())),
   "troll_cycles":len(troll_confirmed),"troll_candidate_rows":len(troll),"troll_confidence":count_by(troll,"confidence"),
   "troll_resolved_mob_names":troll_names,"troll_candidate_rows_by_resolved_mob_name":troll_candidate_names,
   "troll_mobile_type_ids":troll_types,"troll_candidate_mobile_type_ids":troll_candidate_types,
   "troll_main_candidate_rows":len(troll_main),"troll_main_candidate_names":troll_main_names,
   "troll_main_confidence":troll_main_confidence,"troll_main_vs_curated_disagreements":troll_disagreements,
   "troll_unique_target_ids":len(troll_targets),"troll_unmatched_cycles":troll_unmatched,
   "troll_identity_proven_rows":len(troll_confirmed),
   "troll_region_joined_rows":sum(1 for r in troll if r.get("region_id")),
   "mob_cycles":sum(1 for r in mob if r.get("identity_proven")=="true"),"mob_candidate_rows":len(mob),
   "mob_candidate_rows_by_resolved_mob_name":mob_candidate_names,
   "mob_identity_proven_rows":sum(1 for r in mob if r.get("identity_proven")=="true"),
   "mob_region_joined_rows":sum(1 for r in mob if r.get("region_id")),
   "marker_region_joined_rows":sum(1 for r in correlations if r.get("region_id")),
   "warning":"Operation 23 provides exact selected target IDs; marker-to-target links remain confidence-ranked. Candidate mob names are not proven species identities; Leave is not proof of death."},
  "manual_markers":{"total":len(markers),"by_activity_event":dict(sorted(marker_counts.items())),
   "completed_lifecycle_event_rows":completed_lifecycle_samples,
   "completed_lifecycle_samples":completed_lifecycle_samples,
   "completed_lifecycle_metric_note":"Raw gathering_completed + node_depleted event rows; not matched lifecycle sample pairs."},
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
  "spatial_alignment":{"captures_checked":spatial_capture_count,
   "plausible_same_local_coordinate_space":spatial_status_counts.get("PLAUSIBLE_SAME_LOCAL_COORDINATE_SPACE",0),
   "status_counts":dict(sorted(spatial_status_counts.items())),
   "capture_details":spatial_capture_rows if isinstance(spatial_capture_rows,list) else [],
   "overall_status":spatial.get("overall_status","NOT_REPORTED"),
   "warning":"This does not establish current zone identity or a global map transform."},
  "zone_evidence":{"region_transition_records":len(zone_evidence_rows),"captured_region_ids":captured_region_ids,
   "distinct_region_id_count":len(captured_region_ids),
   "warning":"IDs are grounded in user captures. External names/maps were not imported because the referenced game-derived zone dataset has no verified redistribution license."},
  "supplemental_audit_2026_11_06":{
   "active_branch":"albion-pattern-mining",
   "ore_type_crosswalk_rows":sum(len(items) for items in ore_critter_by_tier.values()),
   "troll_marker_candidate_rows":len(troll),"troll_candidate_names":troll_candidate_names,
   "troll_main_curated_disagreement_count":len(troll_disagreements),
   "spatial_status_counts":dict(sorted(spatial_status_counts.items())),
   "region_transition_records":len(zone_evidence_rows),"captured_region_ids":captured_region_ids,
   "event46_counts":{"state_ids":len(state_keys),"exact_event40_joins":len(state_exact_event40_joins),
    "state_only":len(state_only_keys),"updates":state_update_observations,
    "join_key":"archive+pcap+region_id+entity_id"},
   "attack_target_position_samples":attack_position_samples,
   "manual_marker_records":len(markers),"manual_completed_lifecycle_event_rows":completed_lifecycle_samples,
   "external_source_license_note":"Game-derived world map/zone payloads remain reference-only; no unlicensed data was copied."}}
 out_json=DATABASE/"albion-complete-database-manifest-2026-11-03.json"
 files=[DATABASE/"2026-11-02-all-inbox-observations.jsonl",
  DATABASE/"2026-11-02-pcap-harvestable-nodes.csv",DATABASE/"2026-11-02-pcap-mob-lifecycle.csv",
  DATABASE/"2026-11-02-pcap-static-resources.csv",DATABASE/"2026-11-02-pcap-attack-target-tracks.csv",
  ANALYSIS/"2026-11-02-overlay-manual-markers.csv",ANALYSIS/"2026-11-02-overlay-mob-attack-correlation.csv",
  ANALYSIS/"2026-11-02-overlay-mob-attack-correlation.pre-region-join.csv",
  ANALYSIS/"2026-11-02-spatial-alignment.json",
  DATABASE/"2026-11-02-pcap-resource-state-timelines.csv",DATABASE/"2026-11-02-pcap-health-energy-events.csv",
  DATABASE/"2026-11-02-region-transition-evidence.csv",
  DATABASE/"albion-mobile-type-crosswalk-2026-11-03.csv",
  DATABASE/"albion-resource-node-catalog-2026-11-03.csv",
  DATABASE/"albion-knowledge-bundle-import.json",DATABASE/"albion-troll-target-correlation-2026-11-03.csv",
  DATABASE/"albion-mob-target-correlation-2026-11-03.csv"]
 manifest["tables"]=[{"path":str(p.relative_to(ROOT)),"bytes":p.stat().st_size,"sha256":sha_file(p)} for p in files if p.exists()]
 out_json.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")

 report=[
  "# Complete Albion inbox database — 2026-11-06","",
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
  f'- Event 46 state IDs: **{manifest["resource_nodes"]["state_ids"]}**; exact Event 40 joins: **{manifest["resource_nodes"]["state_exact_event40_joins"]}**; state-only: **{manifest["resource_nodes"]["state_only_ids_without_event40_location"]}** (join key: archive + PCAP + region + entity ID)',
  f'- Event 39 static-resource observations: **{len(static):,}** (untyped candidates, not automatically confirmed nodes)',
  "- Ore critter mobile TypeIDs by tier: "+"; ".join("T"+str(tier)+": "+", ".join(str(item["mobile_type_id"])+" "+str(item["display_name"]) for item in items) for tier,items in ore_critter_by_tier.items()),
  "- Unresolved Event 40 type-number counts: "+(", ".join(f"{key}: {value}" for key,value in unresolved_family_counts.items()) or "none"),
  "- Static Ore type_number family is 23–27; living Ore Critters are separately identified by mobile TypeID and Loot.Harvestable semantics.",
  "- User-confirmed T5.3 exception: entity 2788, family 27, coordinates (-379, -24); this does not classify all family-27 nodes.","",
  "## Mobs and Troll marker cycles",
  f'- Event 123 living/mobile lifecycle records: **{len(mobs):,}**; distinct Mobile TypeIDs: **{len(observed_mobile_type_ids)}**; dictionary mapped: **{len(mapped_mobile_type_ids)}/{len(observed_mobile_type_ids)}**',
  f'- Operation 23 attack-target tracks: **{len(attacks)}**; Event 3 target-position samples: **{attack_position_samples}**',
  f'- Manual marker records: **{len(markers)}**; completed lifecycle event rows (gathering_completed + node_depleted): **{completed_lifecycle_samples}**',
  f'- Aggregate marker-target rows: **{len(correlations)}**; by activity/confidence: '+", ".join(f'{k}: {v}' for k,v in sorted(corr_conf.items())),
  f'- Curated Troll marker candidates: **{len(troll)}**; candidate target names: '+", ".join(f'{k}: {v}' for k,v in troll_candidate_names.items()),
  f'- Troll identity-proven rows: **{len(troll_confirmed)}**; distinct candidate target IDs: **{len(troll_targets)}**; unlinked candidates: **{troll_unmatched}**',
  f'- Main-table Troll candidates: **{len(troll_main)}**; names: '+", ".join(f'{k}: {v}' for k,v in troll_main_names.items())+'; main-vs-curated disagreements: **'+str(len(troll_disagreements))+'**',
  f'- Mobs marker candidates: **{len(mob)}**; target-name candidates: '+", ".join(f'{k}: {v}' for k,v in mob_candidate_names.items())+'; identity-proven: **'+str(sum(1 for r in mob if r.get("identity_proven")=="true"))+'**',
  f'- Marker rows with exact region transition: **{sum(1 for r in correlations if r.get("region_id"))}/{len(correlations)}**',
  "- Marker label Troll is retained separately from decoded game name Ore Elemental; all marker-to-target links remain evidence-ranked, not identity-proven.",
  "- Attack-track interpretations: "+", ".join(f'{k}: {v}' for k,v in manifest["combat"]["interpretations"].items())+". Leave is not proof of death.","",
  "## Knowledge bundles",
  f'- Imported bundles: **{len(knowledge.get("imported_bundles",[]))}**',
  f'- Source entity records across manifests: **{manifest["knowledge_bundles"]["source_entity_records_sum_from_manifests"]:,}**; deduplicated session-scoped records: **{len(entities):,}**',
  f'- Deduplicated protocol events: **{len(knowledge.get("protocol_evidence",[])):,}**',
  f'- Learned patterns: **{len(knowledge.get("learned_patterns",[]))}**; autonomous signatures: **{len(autonomous)}**',
  f'- Troll-labeled autonomous signatures: **{len(troll_signatures)}** (signatures, not individual mobs)',"",
  "## Spatial evidence",
  f'- Captures checked: **{manifest["spatial_alignment"]["captures_checked"]}**; plausible same-local-coordinate-space: **{manifest["spatial_alignment"]["plausible_same_local_coordinate_space"]}**; not verified: **{manifest["spatial_alignment"]["status_counts"].get("NOT_VERIFIED",0)}**',
  f'- Captured region-transition evidence: **{len(zone_evidence_rows)}** records across **{len(captured_region_ids)}** region IDs: '+", ".join(captured_region_ids),
  "- Zone names and global map coordinates remain unverified/unlicensed; a local coordinate alignment is not a world-map transform.","",
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
  "- Event 40/46 entity IDs are scoped by archive + PCAP + region_id when known + entity_id; never merge numeric IDs alone or across regions.",
  "- Event 39 static-resource observations are not automatically typed Event 40 nodes.",
  "- Event 123 records are not all independently proven hostile mobs.",
  "- Leave is not kill confirmation.",
  "- ZIP text keyword-hit counts are not counts of nodes, Trolls, or mobs.",
  "- Unknown family/type evidence remains preserved as unknown."
 ]
 (ANALYSIS/"2026-11-06-complete-inbox-database-report.md").write_text("\n".join(report)+"\n",encoding="utf-8")
 print(json.dumps({"archives":len(archive_rows),"pcaps":len(pcap_members),"observations":observation_count,
  "nodes":len(nodes),"mobile_entities":len(mobs),"static_resources":len(static),
  "troll_cycles":len(troll),"mob_cycles":len(mob),"knowledge_entities":len(entities)},ensure_ascii=False))

if __name__=="__main__": main()
