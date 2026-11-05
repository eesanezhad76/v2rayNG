import json,pathlib,tempfile,unittest,zipfile,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
SCRIPT=ROOT/"research/tools/ingest_capture.py"
class IngestTests(unittest.TestCase):
 def run_ingest(self,rows,d):
  src=d/"sample.jsonl";src.write_text("\n".join(json.dumps(x) for x in rows),encoding="utf-8")
  script=SCRIPT.read_text(encoding="utf-8")
  temp=d/"ingest_capture.py";temp.write_text(script.replace('ROOT=pathlib.Path(__file__).resolve().parents[2]',f'ROOT=pathlib.Path(r"{d}")'),encoding="utf-8")
  subprocess.run([sys.executable,str(temp),str(src)],check=True,capture_output=True,text=True)
  return json.loads((d/"research/database/sample.json").read_text(encoding="utf-8"))
 def test_safe_extract_rejects_zip_slip(self):
  sys.path.insert(0,str(SCRIPT.parent));import ingest_capture
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td);zpath=d/"bad.zip"
   with zipfile.ZipFile(zpath,"w") as z:z.writestr("../escape.txt","bad")
   with zipfile.ZipFile(zpath) as z:
    with self.assertRaises(ValueError):ingest_capture.safe_extract(z,d/"out")
 def test_actual_entities_schema_deduplicates_and_joins_exact_ids(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   rows=[
    {"time_ms":1,"entities":[{"kind":"harvestable","event_code":40,"entity_id":77,"mobile_type_id":574,"type_number":29,"tier":5,"enchant":0,"x":1,"y":2,"size":30}]},
    {"time_ms":2,"entities":[{"kind":"harvestable","event_code":40,"entity_id":77,"mobile_type_id":574,"type_number":29,"tier":5,"enchant":0,"x":1,"y":2,"size":30}]},
    {"time_ms":3,"entities":[{"kind":"resource_state","event_code":46,"entity_id":77,"size":4,"removed":False}]},
    {"time_ms":4,"entities":[{"kind":"resource_state","event_code":46,"entity_id":88,"size":0,"removed":False}]}
   ]
   out=self.run_ingest(rows,d)
   self.assertEqual(out["counts"]["unique_event40_nodes"],1)
   self.assertEqual(out["counts"]["event46_exact_joins"],1)
   self.assertEqual(out["counts"]["state_only_ids"],1)
   self.assertEqual(out["nodes"][0]["entity_id"],77)
   self.assertEqual(out["nodes"][0]["state_timeline"][0]["size"],4)
   self.assertEqual(out["nodes"][0]["sizes_seen"],[30])
   catalog=json.loads((d/"research/database/sample-catalog.json").read_text(encoding="utf-8"))
   catalog_node=next(x for x in catalog["entities"] if x.get("source_entity_id")==77)
   self.assertEqual([30],catalog_node["event40_amount_values"])
   self.assertEqual(30,catalog_node["event40_first_observed_amount"])
   self.assertEqual(out["state_only_entities"][0]["entity_id"],88)
 def test_manual_marker_fifo_pairs_multiple_found_targets_without_crossing_metadata(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":10,"activity":"Ore","event":"target_found","tier":"T4","enchantment":"Normal"},
    {"timestamp_ms":20,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":30,"activity":"Ore","event":"gathering_started"},
    {"timestamp_ms":40,"activity":"Ore","event":"gathering_started"},
    {"timestamp_ms":50,"activity":"Ore","event":"gathering_completed"},
    {"timestamp_ms":60,"activity":"Ore","event":"gathering_completed"},
    {"timestamp_ms":70,"activity":"Ore","event":"node_depleted"},
    {"timestamp_ms":80,"activity":"Ore","event":"node_depleted"}
   ]
   rows=[{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers]
   out=self.run_ingest(rows,d)
   cycles=out["marker_lifecycles"]
   self.assertEqual(2,out["counts"]["complete_resource_marker_cycles"])
   self.assertEqual([10,20],[x["found_ms"] for x in cycles])
   self.assertEqual(["T4","T5"],[x["tier"] for x in cycles])
   self.assertEqual([30,40],[x["started_ms"] for x in cycles])
   self.assertTrue(all(x["identity_scope"]=="manual_marker_only_no_runtime_entity_id" for x in cycles))

 def test_manual_marker_orphans_are_preserved_not_force_paired(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":10,"activity":"Ore","event":"target_found","tier":"T5"},
    {"timestamp_ms":20,"activity":"Ore","event":"gathering_started"},
    {"timestamp_ms":30,"activity":"Ore","event":"gathering_started"},
    {"timestamp_ms":40,"activity":"Ore","event":"gathering_completed"},
    {"timestamp_ms":50,"activity":"Ore","event":"node_depleted"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   self.assertEqual(1,out["counts"]["complete_resource_marker_cycles"])
   self.assertEqual(1,out["counts"]["unmatched_marker_events"])
   self.assertEqual("start_without_found",out["unmatched_marker_events"][0]["reason"])

 def test_marker_metadata_changes_are_preserved_as_ambiguous_not_silently_paired(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":10,"activity":"Ore","event":"target_found","tier":"T4","enchantment":"E1"},
    {"timestamp_ms":20,"activity":"Ore","event":"gathering_started","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":30,"activity":"Ore","event":"gathering_completed","tier":"T4","enchantment":"E1"},
    {"timestamp_ms":40,"activity":"Ore","event":"node_depleted","tier":"T4","enchantment":"E1"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   cycle=out["marker_lifecycles"][0]
   self.assertEqual("COMPLETE_WITH_METADATA_CONFLICT",cycle["status"])
   self.assertIn("tier:T4->T5@gathering_started",cycle["metadata_conflicts"])
   self.assertEqual(1,out["counts"]["complete_resource_marker_cycles"])
   self.assertEqual(0,out["counts"]["incomplete_resource_marker_cycles"])
   self.assertEqual(0,out["counts"]["clean_complete_resource_marker_cycles"])
   self.assertEqual(1,out["counts"]["completed_with_metadata_conflict_resource_cycles"])
   self.assertEqual(1,out["counts"]["marker_metadata_conflicts"])

 def test_manual_mob_markers_pair_found_to_killed_without_claiming_entity_id(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":100,"activity":"Mobs","event":"target_found","tier":"T4"},
    {"timestamp_ms":200,"activity":"Mobs","event":"mob_killed"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   self.assertEqual(1,out["counts"]["mob_marker_pairs"])
   self.assertEqual("TARGET_FOUND_TO_KILLED",out["marker_lifecycles"][0]["status"])
   self.assertEqual("manual_marker_only_no_runtime_entity_id",out["marker_lifecycles"][0]["identity_scope"])

 def test_troll_start_to_killed_markers_are_paired(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":100,"activity":"Troll","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":200,"activity":"Troll","event":"mob_killed","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":300,"activity":"Troll","event":"target_found","tier":"T4","enchantment":"Normal"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   self.assertEqual(1,out["counts"]["mob_marker_pairs"])
   troll=[x for x in out["marker_lifecycles"] if x["activity"]=="Troll"]
   self.assertEqual("TARGET_FOUND_TO_KILLED",troll[0]["status"])
   self.assertEqual("TARGET_FOUND_NOT_KILLED",troll[1]["status"])
   self.assertEqual(1,out["counts"]["incomplete_mob_marker_cycles"])

 def test_refresh_existing_database_rebuilds_marker_lifecycle_metadata(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td);db=d/"research/database";db.mkdir(parents=True)
   markers=[
    {"timestamp_ms":10,"activity":"Ore","event":"target_found","tier":"T4","enchantment":"E1"},
    {"timestamp_ms":20,"activity":"Ore","event":"gathering_started","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":30,"activity":"Ore","event":"gathering_completed","tier":"T4","enchantment":"E1"},
    {"timestamp_ms":40,"activity":"Ore","event":"node_depleted","tier":"T4","enchantment":"E1"}
   ]
   stale={"dataset_id":"legacy","source_file":"legacy.jsonl","counts":{"manual_markers":4},"manual_markers":markers,"marker_lifecycles":[]}
   (db/"legacy.json").write_text(json.dumps(stale),encoding="utf-8")
   script=SCRIPT.read_text(encoding="utf-8")
   temp=d/"ingest_capture.py"
   temp.write_text(script.replace('ROOT=pathlib.Path(__file__).resolve().parents[2]',f'ROOT=pathlib.Path(r"{d}")'),encoding="utf-8")
   subprocess.run([sys.executable,str(temp),"--refresh-existing"],check=True,capture_output=True,text=True)
   refreshed=json.loads((db/"legacy.json").read_text(encoding="utf-8"))
   self.assertEqual("COMPLETE_WITH_METADATA_CONFLICT",refreshed["marker_lifecycles"][0]["status"])
   self.assertEqual(1,refreshed["counts"]["marker_metadata_conflicts"])
   csv_text=(db/"legacy-marker-lifecycles.csv").read_text(encoding="utf-8")
   self.assertIn("metadata_conflicts",csv_text.splitlines()[0])

 def test_current_overlay_start_end_pair_is_a_complete_resource_cycle(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":100,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E3"},
    {"timestamp_ms":25000,"activity":"Ore","event":"gathering_completed","tier":"T5","enchantment":"E3"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   self.assertEqual(1,out["counts"]["complete_resource_marker_cycles"])
   self.assertEqual(0,out["counts"]["incomplete_resource_marker_cycles"])
   cycle=out["marker_lifecycles"][0]
   self.assertEqual("COMPLETE",cycle["status"])
   self.assertEqual(100,cycle["found_ms"])
   self.assertEqual(100,cycle["started_ms"])
   self.assertEqual(25000,cycle["completed_ms"])
   self.assertEqual("T5",cycle["tier"])
   self.assertEqual("E3",cycle["enchantment"])

 def test_current_overlay_supersedes_old_pending_start_and_preserves_duplicate_end(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":1098339,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":1105571,"activity":"Ore","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":1111961,"activity":"Ore","event":"gathering_completed","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":1315035,"activity":"Ore","event":"target_found","tier":"T4","enchantment":"E2"},
    {"timestamp_ms":1319942,"activity":"Ore","event":"gathering_completed","tier":"T4","enchantment":"E2"},
    {"timestamp_ms":1320625,"activity":"Ore","event":"gathering_completed","tier":"T4","enchantment":"E2"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   cycles=out["marker_lifecycles"]
   by_start={x["found_ms"]:x for x in cycles}
   self.assertEqual("SUPERSEDED_NOT_COMPLETED",by_start[1098339]["status"])
   self.assertEqual(1105571,by_start[1098339]["superseded_by_ms"])
   self.assertEqual(1111961,by_start[1105571]["completed_ms"])
   self.assertEqual(1319942,by_start[1315035]["completed_ms"])
   self.assertEqual(2,out["counts"]["complete_resource_marker_cycles"])
   self.assertEqual(1,out["counts"]["incomplete_resource_marker_cycles"])
   self.assertEqual(2,out["counts"]["unmatched_marker_events"])
   self.assertEqual("completion_without_target_found",out["unmatched_marker_events"][-1]["reason"])

 def test_legacy_event_analysis_schema_still_supported(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   rows=[{"time_ms":1,"photon_event_analysis":[{"event_code":40,"resources":[{"id":7,"type_id":29,"tier":5,"x":2,"y":3,"amount":5,"enchantment_raw":0}]}]},{"time_ms":2,"photon_event_analysis":[{"event_code":46,"state_update":{"id":7,"amount_or_state":4,"enchantment_raw":0}}]}]
   out=self.run_ingest(rows,d)
   self.assertEqual(out["counts"]["unique_event40_nodes"],1)
   self.assertEqual(out["nodes"][0]["state_timeline"][0]["size"],4)

 def test_troll_marker_correlates_to_named_mob_using_local_player_health_source(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   rows=[
    {"time_ms":900,"entities":[{"kind":"own_player_move","event_code":22,"entity_id":-1,"x":0.0,"y":0.0}]},
    {"time_ms":1000,"entities":[
     {"kind":"own_player_move","event_code":22,"entity_id":-1,"x":0.5,"y":0.5},
     {"kind":"player_move","event_code":3,"entity_id":77,"x":0.5,"y":0.5},
     {"kind":"mob","event_code":123,"entity_id":88,"mobile_type_id":300,"enchant":1,"x":5.0,"y":5.0,"name":"Forest Troll"}
    ]},
    {"time_ms":1500,"entities":[{"kind":"health","event_code":7,"entity_id":88,"source_entity_id":77,"value":-20.0,"secondary_value":80.0}]},
    {"time_ms":2100,"entities":[{"kind":"leave","event_code":1,"entity_id":88,"removed":True}]},
    {"time_ms":1000,"manual_marker":{"timestamp_ms":1000,"activity":"Troll","event":"target_found","tier":"T5","enchantment":"E1"}},
    {"time_ms":2000,"manual_marker":{"timestamp_ms":2000,"activity":"Troll","event":"mob_killed","tier":"T5","enchantment":"E1"}}
   ]
   out=self.run_ingest(rows,d)
   cycle=[x for x in out["marker_lifecycles"] if x["activity"]=="Troll"][0]
   corr=cycle["spatial_correlation"]
   self.assertEqual("HIGH",corr["confidence"],corr)
   self.assertEqual("88",corr["best_candidate"]["entity_id"],corr)
   self.assertFalse(corr["identity_proven"])
   mob=out["mobs"][0]
   self.assertEqual("Forest Troll",mob["name"])
   self.assertEqual(1,mob["player_damage_events"])
   self.assertTrue(mob["left"])
   catalog=json.loads((d/"research/database/sample-catalog.json").read_text(encoding="utf-8"))
   self.assertEqual(1,catalog["mob_count"])
   self.assertEqual("Troll",catalog["entities"][0]["marker_activity"])

 def test_troll_repeated_start_and_kill_markers_are_not_cross_paired(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   markers=[
    {"timestamp_ms":2028951,"activity":"Troll","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":2030445,"activity":"Troll","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":2043268,"activity":"Troll","event":"mob_killed","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":2501147,"activity":"Troll","event":"target_found","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":2513812,"activity":"Troll","event":"mob_killed","tier":"T5","enchantment":"E1"},
    {"timestamp_ms":2515233,"activity":"Troll","event":"mob_killed","tier":"T5","enchantment":"E1"}
   ]
   out=self.run_ingest([{"time_ms":m["timestamp_ms"],"manual_marker":m} for m in markers],d)
   troll=[x for x in out["marker_lifecycles"] if x["activity"]=="Troll"]
   by_start={x["found_ms"]:x for x in troll if x.get("found_ms")}
   self.assertEqual("TARGET_FOUND_SUPERSEDED_NOT_KILLED",by_start[2028951]["status"])
   self.assertEqual(2030445,by_start[2028951]["superseded_by_ms"])
   self.assertEqual(2043268,by_start[2030445]["mob_killed_ms"])
   self.assertEqual(2513812,by_start[2501147]["mob_killed_ms"])
   self.assertEqual(2,out["counts"]["mob_marker_pairs"])
   self.assertEqual(1,out["counts"]["incomplete_mob_marker_cycles"])
   self.assertEqual(2,out["counts"]["unmatched_marker_events"])
   self.assertEqual("mob_kill_without_target_found",out["unmatched_marker_events"][-1]["reason"])

if __name__=="__main__":unittest.main()
