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
 attacks=[r for r in read_csv(DB/"2026-11-02-pcap-attack-target-tracks.csv")
          if r.get("archive")==archive and r.get("pcap")==pcap and not yes(r.get("known_player_spawn"))]
 mob_rows={str(r.get("entity_id")):r for r in read_csv(DB/"2026-11-02-pcap-mob-lifecycle.csv")
           if r.get("archive")==archive and r.get("pcap")==pcap}
 targets=[]
 for row in attacks:
  eid=str(row.get("target_entity_id") or "")
  if not eid:continue
  mob=mob_rows.get(eid,{})
  mobile=num(row,"event123_mobile_type",num(mob,"mobile_type_id"))
  name=(row.get("event123_name") or mob.get("name") or "").strip()
  x=num(row,"last_position_x")
  y=num(row,"last_position_y")
  if x is None or y is None:x,y=num(mob,"x"),num(mob,"y")
  targets.append({
   "entity_id":eid,"mobile_type_id":mobile,"name":name,"x":x,"y":y,
   "first_seen_ms":num(mob,"first_seen_ms",num(row,"first_attack_ms")),
   "last_seen_ms":num(mob,"last_seen_ms",num(row,"last_attack_ms")),
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
  finish=num(cycle,"mob_killed_ms",start+15000)
  candidates=[]
  for target in targets:
   first=target.get("first_attack_ms");last=target.get("last_attack_ms")
   if first is None or last is None:continue
   delta=first-start
   if delta < -2500 or delta > 20000 or last < start-1500 or first > finish+2500:continue
   if target["health_observations"]<=0 and not target["leave_after_last_attack"]:continue
   candidates.append((abs(delta),target))
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
 by_id={str(e.get("source_entity_id")):e for e in entities
        if e.get("last_kind")=="mob" and e.get("source_entity_id") is not None}
 for target in targets:
  item=by_id.get(target["entity_id"])
  if item is None:
   item={"entity_id":"catalog:attack:"+target["entity_id"],"source_entity_id":target["entity_id"],"last_kind":"mob"}
   entities.append(item);by_id[target["entity_id"]]=item
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

def main():
 p=argparse.ArgumentParser();p.add_argument("--dataset",default=DATASET);p.add_argument("--archive",default=ARCHIVE);p.add_argument("--pcap",default=PCAP)
 a=p.parse_args();print(json.dumps(enrich(a.dataset,a.archive,a.pcap),sort_keys=True))
if __name__=="__main__":main()
