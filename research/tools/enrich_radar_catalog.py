#!/usr/bin/env python3
"""Attach same-PCAP player attack targets to the static Albion radar catalog."""
import argparse,csv,json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
DB=ROOT/"research/database"
ANALYSIS=ROOT/"research/analysis"
ASSET=ROOT/"V2rayNG/app/src/main/assets/albion-static-catalog.json"
DATASET="albion-1793682590317"
ARCHIVE="AlbionRadar.zip"
PCAP="albion-1793682590317.pcap"

def read_csv(path):
 if not path.is_file():return []
 with path.open(encoding="utf-8-sig",newline="") as f:return list(csv.DictReader(f))
def num(row,key,default=None):
 try:
  v=float(row.get(key,""))
  return int(v) if v.is_integer() else v
 except (TypeError,ValueError):return default
def yes(v):return str(v).lower() in {"1","true","yes"}
def tier(v):
 s=str(v or "").upper().replace("T","")
 return int(s) if s.isdigit() else None
def ench(v):
 s=str(v or "").upper().replace("E","")
 return int(s) if s.isdigit() else 0

def enrich(dataset=DATASET,archive=ARCHIVE,pcap=PCAP):
 path=DB/(dataset+"-catalog.json")
 catalog=json.loads(path.read_text(encoding="utf-8"))
 type_dictionary=json.loads((DB/"albion-mobile-type-dictionary.json").read_text(encoding="utf-8")).get("entries",{})
 region_rows=read_csv(DB/"2026-11-02-region-transition-evidence.csv")
 region_timelines={}
 for transition in region_rows:
  key=(str(transition.get("archive") or ""),str(transition.get("pcap") or ""))
  timestamp=num(transition,"timestamp_ms")
  region_id=(transition.get("region_id") or "").strip()
  if timestamp is not None and region_id:
   region_timelines.setdefault(key,[]).append((timestamp,region_id))
 for timeline in region_timelines.values():timeline.sort()
 def region_at(source_archive,source_pcap,timestamp):
  if timestamp is None:return None
  found=None
  for transition_ms,region_id in region_timelines.get((str(source_archive or ""),str(source_pcap or "")),[]):
   if transition_ms>timestamp:break
   found=region_id
  return found
 attacks=[r for r in read_csv(DB/"2026-11-02-pcap-attack-target-tracks.csv")
          if (archive is None or r.get("archive")==archive) and r.get("pcap")==pcap and not yes(r.get("known_player_spawn"))]
 attack_archives=sorted({str(r.get("archive") or "") for r in attacks if r.get("archive")})
 catalog_archive=archive if archive is not None else (attack_archives[0] if len(attack_archives)==1 else None)
 mob_rows={}
 for row in read_csv(DB/"2026-11-02-pcap-mob-lifecycle.csv"):
  if (archive is not None and row.get("archive")!=archive) or row.get("pcap")!=pcap:continue
  row_region=(row.get("region_id") or region_at(row.get("archive"),pcap,num(row,"first_seen_ms")))
  mob_rows[(str(row.get("archive") or ""),row_region,str(row.get("entity_id")))] = row
 targets=[]
 for row in attacks:
  eid=str(row.get("target_entity_id") or "")
  if not eid:continue
  target_region=(row.get("region_id") or region_at(row.get("archive"),pcap,num(row,"first_attack_ms")))
  mob=mob_rows.get((str(row.get("archive") or ""),target_region,eid),{})
  mobile=num(row,"event123_mobile_type",num(mob,"mobile_type_id"))
  type_info=type_dictionary.get(str(mobile),{})
  name=(row.get("event123_name") or mob.get("name") or type_info.get("display_name") or "").strip()
  x=num(row,"last_position_x")
  y=num(row,"last_position_y")
  position_source="Event3 target movement"
  if x is None or y is None:
   x,y=num(mob,"x"),num(mob,"y")
   position_source="Event123 last observed mob position"
  targets.append({
   "entity_id":eid,"source_archive":row.get("archive"),"region_id":target_region,
   "mobile_type_id":mobile,"name":name,"x":x,"y":y,
   "tier":type_info.get("tier"),"mob_category":type_info.get("category"),
   "type_unique_name":type_info.get("unique_name"),"localization_tag":type_info.get("localization_tag"),
   "mob_danger":type_info.get("danger"),"max_health":type_info.get("max_health"),
   "loot_type":type_info.get("loot_type"),"is_harvestable_mob":type_info.get("is_harvestable",False),
   "first_seen_ms":num(mob,"first_seen_ms",num(row,"first_attack_ms")),
   "last_seen_ms":num(mob,"last_seen_ms",num(row,"last_attack_ms")),
   "position_source":position_source,"position_last_seen_ms":num(mob,"last_seen_ms"),
   "first_attack_ms":num(row,"first_attack_ms"),"last_attack_ms":num(row,"last_attack_ms"),
   "attack_action_count":num(row,"attack_action_count",0),"attack_flag_values":row.get("attack_flag_values",""),
   "event3_position_samples":num(row,"event3_position_samples",0),
   "health_observations":num(row,"health_observations",0),
   "min_current_health":num(row,"min_current_health"),"last_current_health":num(row,"last_current_health"),
   "health_source_entity_ids":row.get("health_source_entity_ids",""),
   "leave_observed":yes(row.get("leave_observed")) or yes(mob.get("left_after_seen")),
   "leave_after_last_attack":yes(row.get("leave_after_last_attack")),
   "interpretation":row.get("interpretation",""),"targeted_by_player":True,
   "target_source":"outbound Photon operation 23 target request","last_kind":"mob",
   "classification":"player_attack_target","target_entity_id_proven":True,"marker_matches":[]
  })
 cycles=[c for c in catalog.get("markers",[]) if c.get("activity") in {"Mob","Mobs","Troll"}]
 for cycle in cycles:
  start=num(cycle,"found_ms")
  if start is None:continue
  cycle_region=region_at(catalog_archive,pcap,start) if catalog_archive else None
  cycle["region_id"]=cycle_region
  if catalog_archive is None:
   cycle["region_scope_status"]="ambiguous_source_archive" if len(attack_archives)>1 else "region_unverified"
  if cycle.get("status")=="TARGET_FOUND_SUPERSEDED_NOT_KILLED":
   cycle["attack_target_correlation"]={"confidence":"SUPERSEDED","candidate_count":0,"identity_proven":False}
   continue
  finish=num(cycle,"mob_killed_ms",start+15000)
  candidates=[]
  for target in targets:
   if catalog_archive is None or target.get("source_archive")!=catalog_archive:continue
   if target.get("region_id")!=cycle_region:continue
   first=target.get("first_attack_ms");last=target.get("last_attack_ms")
   if first is None or last is None:continue
   if last < start-1500 or first > finish+2500:continue
   if target["health_observations"]<=0 and not target["leave_after_last_attack"]:continue
   # An attack interval spanning the marker is a stronger temporal match than
   # requiring the first attack packet to occur after the user pressed START.
   interval_gap=0 if first<=start<=last else min(abs(first-start),abs(last-start))
   candidates.append((interval_gap,target))
  candidates.sort(key=lambda p:(p[0],-p[1]["health_observations"],-p[1]["attack_action_count"]))
  if not candidates:
   cycle["attack_target_correlation"]={"confidence":"NONE","candidate_count":0,"identity_proven":False}
   continue
  gap,best=candidates[0]
  nearby=[t for d,t in candidates if d<=gap+1500]
  confidence=("AMBIGUOUS" if len(nearby)>1 else
              "HIGH" if gap<=3000 and best["health_observations"]>0 and best["leave_after_last_attack"] else
              "MEDIUM" if gap<=8000 and best["health_observations"]>0 else "LOW")
  cycle["attack_target_correlation"]={
   "target_entity_id":best["entity_id"],"mobile_type_id":best["mobile_type_id"],
   "first_attack_ms":best["first_attack_ms"],"last_attack_ms":best["last_attack_ms"],
   "start_to_attack_delta_ms":best["first_attack_ms"]-start,"confidence":confidence,
   "candidate_count":len(candidates),"identity_proven":False,
   "evidence":["outbound operation 23 target ID","same-PCAP timestamps","Event 123 type","health/leave evidence"]
  }
  best["marker_matches"].append({"activity":cycle["activity"],"found_ms":start,
   "mob_killed_ms":cycle.get("mob_killed_ms"),"confidence":confidence,
   "tier":cycle.get("tier"),"enchantment":cycle.get("enchantment"),"identity_proven":False})
 entities=catalog.setdefault("entities",[])
 for entity in entities:
  if entity.get("last_kind")!="mob":continue
  info=type_dictionary.get(str(entity.get("mobile_type_id")), {})
  if not info:continue
  if not entity.get("name"):entity["name"]=info.get("display_name")
  for key in ("tier","category","unique_name","localization_tag","danger","max_health","loot_type","is_harvestable"):
   value=info.get(key)
   if value is not None:entity[{"category":"mob_category","unique_name":"type_unique_name","danger":"mob_danger","is_harvestable":"is_harvestable_mob"}.get(key,key)]=value
 for entity in entities:
  if not entity.get("region_id"):
   entity["region_id"]=region_at(catalog_archive,pcap,num(entity,"first_seen_ms")) if catalog_archive else None
 by_id={(str(e.get("region_id") or ""),str(e.get("source_entity_id"))):e for e in entities
        if e.get("last_kind")=="mob" and e.get("source_entity_id") is not None}
 for target in targets:
  entity_key=(str(target.get("region_id") or ""),target["entity_id"])
  item=by_id.get(entity_key)
  if item is None:
   item={"entity_id":"catalog:attack:"+str(target.get("region_id") or "unknown")+":"+target["entity_id"],
         "source_entity_id":target["entity_id"],"region_id":target.get("region_id"),"last_kind":"mob"}
   entities.append(item);by_id[entity_key]=item
  for key,value in target.items():
   if value is not None:item[key]=value
  matches=target["marker_matches"]
  if matches:
   best=next((m for m in matches if m["confidence"]=="HIGH"),
             next((m for m in matches if m["confidence"]=="MEDIUM"),matches[0]))
   conf=best["confidence"];act=best["activity"]
   item["marker_activity"]=act if conf in {"HIGH","MEDIUM"} else None
   item["marker_activity_candidate"]=act if conf in {"LOW","AMBIGUOUS"} else None
   item["marker_match_confidence"]=conf
   item["marker_tier"]=tier(best.get("tier"))
   item["marker_enchant"]=ench(best.get("enchantment"))
   if not target["name"]:item["name"]=("Troll" if act=="Troll" else "Mob")+" #"+str(target["mobile_type_id"] or "unknown")
  elif not target["name"]:item["name"]="Mob #"+str(target["mobile_type_id"] or "unknown")
  if target.get("last_current_health") is not None:item["health_value"]=target["last_current_health"]
  if target.get("min_current_health") is not None:item["health_min"]=target["min_current_health"]
  item["left"]=bool(item.get("left") or target["leave_observed"])
 catalog["attack_targets"]=targets
 catalog["attack_target_count"]=len(targets)
 catalog["correlated_attack_target_markers"]=sum(bool(c.get("attack_target_correlation",{}).get("target_entity_id")) for c in cycles)
 catalog["troll_target_count"]=sum(any(m["activity"]=="Troll" for m in t["marker_matches"]) for t in targets)
 catalog["mob_count"]=len(by_id)
 text=json.dumps(catalog,ensure_ascii=False,indent=2)
 path.write_text(text,encoding="utf-8")
 (ANALYSIS/(dataset+"-overlay-catalog.json")).write_text(text,encoding="utf-8")
 if dataset==DATASET:
  ASSET.parent.mkdir(parents=True,exist_ok=True);ASSET.write_text(text,encoding="utf-8")
 fields=["entity_id","mobile_type_id","name","x","y","first_attack_ms","last_attack_ms",
         "attack_action_count","health_observations","last_current_health","leave_after_last_attack","interpretation"]
 for dest in (DB/(dataset+"-attack-targets.csv"),ANALYSIS/(dataset+"-attack-targets.csv")):
  with dest.open("w",encoding="utf-8",newline="") as f:
   w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
   for target in targets:w.writerow({k:target.get(k) for k in fields})
 return {"nodes":catalog.get("node_count",0),"attack_targets":len(targets),"mob_count":catalog["mob_count"],
         "correlated_markers":catalog["correlated_attack_target_markers"],"troll_targets":catalog["troll_target_count"]}

def enrich_all():
 """Enrich every per-capture catalog using attack tracks from its matching PCAP.

 The dataset name is derived from the catalog filename, while the source archive
 is resolved from the attack-track index. This avoids silently enriching only
 the historical default capture when newer captures contain the actual markers.
 """
 track_rows=read_csv(DB/"2026-11-02-pcap-attack-target-tracks.csv")
 summaries=[]
 for path in sorted(DB.glob("albion-*-catalog.json")):
  try:
   catalog=json.loads(path.read_text(encoding="utf-8"))
  except (OSError,ValueError):
   continue
  dataset=str(catalog.get("dataset") or path.name.removesuffix("-catalog.json"))
  pcap=dataset+".pcap"
  if not any(r.get("pcap")==pcap for r in track_rows):
   continue
  summaries.append(enrich(dataset, None, pcap))
 return summaries

def main():
 p=argparse.ArgumentParser()
 p.add_argument("--dataset",default=None)
 p.add_argument("--archive",default=ARCHIVE)
 p.add_argument("--pcap",default=None)
 p.add_argument("--all",action="store_true")
 a=p.parse_args()
 if a.all or a.dataset is None:
  print(json.dumps(enrich_all(),sort_keys=True))
 else:
  print(json.dumps(enrich(a.dataset,a.archive,a.pcap or (a.dataset+".pcap")),sort_keys=True))
if __name__=="__main__":main()
