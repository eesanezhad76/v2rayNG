#!/usr/bin/env python3
"""Offline Albion capture ZIP/JSONL ingest. Preserves unknowns and exact runtime IDs."""
import argparse,csv,json,pathlib,zipfile,collections,hashlib,math
ROOT=pathlib.Path(__file__).resolve().parents[2]
DB=ROOT/"research/database"; ANALYSIS=ROOT/"research/analysis"
def safe_extract(zf,dest):
 base=dest.resolve()
 for info in zf.infolist():
  target=(dest/info.filename).resolve()
  if target!=base and base not in target.parents: raise ValueError("Unsafe ZIP member: "+info.filename)
 zf.extractall(dest)
def parse_row(row):
 entities=list(row.get("entities") or [])
 for event in row.get("photon_event_analysis",[]):
  code=event.get("event_code")
  if code==40:
   for n in event.get("resources",[]):
    entities.append({"kind":"harvestable","event_code":40,"entity_id":n.get("id"),"mobile_type_id":n.get("mobile_type_id",-1),"type_number":n.get("type_id"),"tier":n.get("tier"),"enchant":n.get("enchantment_raw",0),"x":n.get("x"),"y":n.get("y"),"size":n.get("amount",n.get("size"))})
  elif code==46 and isinstance(event.get("state_update"),dict):
   s=event["state_update"]; entities.append({"kind":"resource_state","event_code":46,"entity_id":s.get("id"),"size":s.get("amount_or_state",s.get("size")),"removed":s.get("removed")})
 return entities
RESOURCE_ACTIVITIES={"Ore","Wood","Stone","Fiber","Hide"}
RESOURCE_EVENTS={"target_found","gathering_started","gathering_completed","node_depleted"}

def pair_manual_markers(markers):
 """FIFO-pair manual resource and mob markers without inventing runtime entity IDs."""
 def compare_metadata(cycle,marker,event):
  conflicts=cycle.setdefault("metadata_conflicts",[])
  for field in ("tier","enchantment"):
   before=cycle.get(field);after=marker.get(field)
   if before not in (None,"") and after not in (None,"") and str(before)!=str(after):
    item=f"{field}:{before}->{after}@{event}"
    if item not in conflicts:conflicts.append(item)
 states=collections.defaultdict(lambda:{"pending":collections.deque(),"active":collections.deque(),"completed":collections.deque()})
 mob_pending=collections.defaultdict(collections.deque)
 lifecycles=[]; unmatched=[]
 ordered=[]
 for index,marker in enumerate(markers):
  try: timestamp=int(marker.get("timestamp_ms"))
  except (TypeError,ValueError):
   unmatched.append({"activity":marker.get("activity"),"event":marker.get("event"),"timestamp_ms":None,"reason":"missing_or_invalid_timestamp"})
   continue
  ordered.append((timestamp,index,marker))
 for timestamp,index,marker in sorted(ordered,key=lambda x:(x[0],x[1])):
  activity=str(marker.get("activity") or "Unknown")
  event=str(marker.get("event") or "")
  if activity in RESOURCE_ACTIVITIES and event in RESOURCE_EVENTS:
   queue=states[activity]
   if event=="target_found":
    queue["pending"].append({"activity":activity,"tier":marker.get("tier"),"enchantment":marker.get("enchantment"),"found_ms":timestamp,"started_ms":None,"completed_ms":None,"depleted_ms":None,"metadata_conflicts":[]})
   elif event=="gathering_started":
    if queue["pending"]:
     cycle=queue["pending"].popleft();compare_metadata(cycle,marker,event);cycle["started_ms"]=timestamp;queue["active"].append(cycle)
    else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"start_without_found"})
   elif event=="gathering_completed":
    # Current Android overlay has exactly two resource actions:
    # Start emits target_found; End emits gathering_completed.
    # Legacy captures may additionally contain gathering_started and node_depleted.
    if queue["active"]:
     cycle=queue["active"].popleft();compare_metadata(cycle,marker,event);cycle["completed_ms"]=timestamp;queue["completed"].append(cycle)
    elif queue["pending"]:
     cycle=queue["pending"].pop();compare_metadata(cycle,marker,event);cycle["completed_ms"]=timestamp;cycle["status"]="COMPLETE" if not cycle["metadata_conflicts"] else "COMPLETE_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
    else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"completion_without_target_found"})
   elif event=="node_depleted":
    if queue["completed"]:
     cycle=queue["completed"].popleft();compare_metadata(cycle,marker,event);cycle["depleted_ms"]=timestamp;cycle["status"]="COMPLETE" if not cycle["metadata_conflicts"] else "COMPLETE_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
    else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"depletion_without_completion"})
  elif activity in {"Mob","Mobs","Troll"} and event=="target_found":
   mob_pending[activity].append({"activity":activity,"tier":marker.get("tier"),"enchantment":marker.get("enchantment"),"found_ms":timestamp,"started_ms":None,"completed_ms":None,"depleted_ms":None,"metadata_conflicts":[]})
  elif activity in {"Mob","Mobs","Troll"} and event=="mob_killed":
   if mob_pending[activity]:
    cycle=mob_pending[activity].popleft();cycle["mob_killed_ms"]=timestamp;cycle["status"]="TARGET_FOUND_TO_KILLED";lifecycles.append(cycle)
   else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"mob_kill_without_target_found"})
 for activity,queue in states.items():
  for cycle in queue["pending"]:
   cycle["status"]="FOUND_NOT_STARTED" if not cycle["metadata_conflicts"] else "FOUND_NOT_STARTED_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
  for cycle in queue["active"]:
   cycle["status"]="STARTED_NOT_COMPLETED" if not cycle["metadata_conflicts"] else "STARTED_NOT_COMPLETED_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
  for cycle in queue["completed"]:
   cycle["status"]="COMPLETED_NOT_DEPLETED" if not cycle["metadata_conflicts"] else "COMPLETED_NOT_DEPLETED_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
 for queue in mob_pending.values():
  for cycle in queue:
   cycle["status"]="TARGET_FOUND_NOT_KILLED";lifecycles.append(cycle)
 for cycle in lifecycles:
  cycle["identity_scope"]="manual_marker_only_no_runtime_entity_id"
  cycle.setdefault("mob_killed_ms",None)
 lifecycles.sort(key=lambda x:(x.get("found_ms") or 0,x.get("activity") or ""))
 return lifecycles,unmatched
def write_marker_lifecycle_csv(stem,marker_lifecycles):
 marker_fields=["activity","tier","enchantment","found_ms","started_ms","completed_ms","depleted_ms","mob_killed_ms","status","metadata_conflicts","identity_scope"]
 with (DB/(stem+"-marker-lifecycles.csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=marker_fields,extrasaction="ignore");w.writeheader()
  for cycle in marker_lifecycles:
   row=cycle.copy();row["metadata_conflicts"]=";".join(row.get("metadata_conflicts",[]));w.writerow(row)
def refresh_existing_marker_lifecycles():
 refreshed=0
 DB.mkdir(parents=True,exist_ok=True);ANALYSIS.mkdir(parents=True,exist_ok=True)
 for path in sorted(DB.glob("*.json")):
  try:doc=json.loads(path.read_text(encoding="utf-8"))
  except (OSError,json.JSONDecodeError):continue
  markers=doc.get("manual_markers")
  if not isinstance(markers,list):continue
  lifecycles,unmatched=pair_manual_markers(markers)
  resource_cycles=[x for x in lifecycles if x.get("activity") in RESOURCE_ACTIVITIES]
  counts=doc.setdefault("counts",{})
  counts.update({
   "manual_markers":len(markers),
   "resource_marker_cycles":len(resource_cycles),
   "complete_resource_marker_cycles":sum(x.get("status") in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),
   "clean_complete_resource_marker_cycles":sum(x.get("status")=="COMPLETE" for x in resource_cycles),
   "completed_with_metadata_conflict_resource_cycles":sum(x.get("status")=="COMPLETE_WITH_METADATA_CONFLICT" for x in resource_cycles),
   "incomplete_resource_marker_cycles":sum(x.get("status") not in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),
   "mob_marker_pairs":sum(x.get("status")=="TARGET_FOUND_TO_KILLED" for x in lifecycles),
   "incomplete_mob_marker_cycles":sum(x.get("status")=="TARGET_FOUND_NOT_KILLED" for x in lifecycles),
   "unmatched_marker_events":len(unmatched),
   "marker_metadata_conflicts":sum(bool(x.get("metadata_conflicts")) for x in lifecycles)
  })
  doc["marker_lifecycles"]=lifecycles;doc["unmatched_marker_events"]=unmatched
  path.write_text(json.dumps(doc,indent=2),encoding="utf-8")
  write_marker_lifecycle_csv(path.stem,lifecycles)
  (ANALYSIS/(path.stem+".md")).write_text(
   "# Capture ingest: "+path.stem+"\n\n"+
   "\n".join("- "+k+": "+str(v) for k,v in counts.items())+
   "\n\nState updates join only by exact runtime entity ID. Marker lifecycles are FIFO-paired, metadata conflicts are retained, and manual annotations do not establish runtime entity identity. State-only IDs remain separate.\n",
   encoding="utf-8")
  refreshed+=1
 return refreshed

def finite_number(value):
 try:
  number=float(value)
  return number if math.isfinite(number) and abs(number)<=10000 else None
 except (TypeError,ValueError):
  return None

def correlate_mob_markers(lifecycles,mobs,player_positions,player_moves,health_events):
 """Attach evidence-ranked mob candidates to manual Mob/Troll markers without claiming packet identity."""
 local_player_ids=set()
 for move in player_moves:
  if move.get("entity_id") is None or move.get("x") is None or move.get("y") is None:continue
  nearby=[p for p in player_positions if abs(p["time_ms"]-move["time_ms"])<=1500]
  if not nearby:continue
  nearest=min(nearby,key=lambda p:abs(p["time_ms"]-move["time_ms"]))
  if math.hypot(move["x"]-nearest["x"],move["y"]-nearest["y"])<=3.0:
   local_player_ids.add(str(move["entity_id"]))
 for health in health_events:
  health["source_is_local_player"]=str(health.get("source_entity_id")) in local_player_ids
  mob=mobs.get(str(health.get("entity_id")))
  if mob is not None:
   mob["health_timeline"].append(health.copy())
   mob["health_event_count"]=len(mob["health_timeline"])
   if health.get("source_is_local_player"):mob["player_damage_events"]=mob.get("player_damage_events",0)+1
 for mob in mobs.values():
  positions=mob.get("positions",[])
  if positions:
   latest=max(positions,key=lambda p:p["time_ms"])
   mob["x"]=latest["x"];mob["y"]=latest["y"];mob["last_position_ms"]=latest["time_ms"]
  mob["last_kind"]="mob"
  mob["classification"]="observed_mob"
  mob["manual_marker_matches"]=[]
  mob["positions"]=sorted(positions,key=lambda p:p["time_ms"])[-120:]
  mob["health_timeline"]=sorted(mob.get("health_timeline",[]),key=lambda h:h["time_ms"])[-160:]
 for cycle in lifecycles:
  if cycle.get("activity") not in {"Mob","Mobs","Troll"}:continue
  start=cycle.get("found_ms")
  if not isinstance(start,int):continue
  finish=cycle.get("mob_killed_ms") or start+12000
  nearby_player=min(player_positions,key=lambda p:abs(p["time_ms"]-start)) if player_positions else None
  player_gap=abs(nearby_player["time_ms"]-start) if nearby_player else None
  if player_gap is not None and player_gap>2000:nearby_player=None
  candidates=[]
  for mob in mobs.values():
   positions=mob.get("positions",[])
   if not positions:continue
   position=min(positions,key=lambda p:abs(p["time_ms"]-start))
   position_gap=abs(position["time_ms"]-start)
   if position_gap>5000:continue
   health=[h for h in mob.get("health_timeline",[]) if start-500<=h["time_ms"]<=finish+500]
   local_hits=[h for h in health if h.get("source_is_local_player")]
   name=str(mob.get("name") or "")
   troll_name="troll" in name.lower()
   distance=math.hypot(position["x"]-nearby_player["x"],position["y"]-nearby_player["y"]) if nearby_player else None
   if distance is not None and distance>55 and not local_hits:continue
   if not health and distance is None:continue
   candidates.append({
    "entity_id":str(mob["entity_id"]),"name":name or ("Troll candidate" if cycle["activity"]=="Troll" else "Mob candidate"),
    "mobile_type_id":mob.get("mobile_type_id"),"enchant":mob.get("enchant"),
    "x":position["x"],"y":position["y"],"position_ms":position["time_ms"],
    "position_gap_ms":position_gap,"distance_from_player":round(distance,2) if distance is not None else None,
    "health_events_in_window":len(health),"player_damage_events":len(local_hits),
    "name_matches_troll":troll_name
   })
  candidates.sort(key=lambda c:(c["player_damage_events"]>0,c["name_matches_troll"],c["distance_from_player"] is not None,-(c["distance_from_player"] if c["distance_from_player"] is not None else 9999),-c["position_gap_ms"]),reverse=True)
  correlation={
   "player_position":({"x":nearby_player["x"],"y":nearby_player["y"],"time_ms":nearby_player["time_ms"],"gap_ms":player_gap} if nearby_player else None),
   "local_player_entity_ids_inferred":sorted(local_player_ids),
   "candidate_count":len(candidates),"candidates":candidates[:5],
   "identity_proven":False,
   "method":"same-capture marker time + Event22 own-player position + Event123 mob position + Event6/7 health source correlation"
  }
  if candidates:
   best=candidates[0]
   local_damage=best["player_damage_events"]>0
   named_troll=best["name_matches_troll"]
   distance=best["distance_from_player"]
   if local_damage and (cycle["activity"]!="Troll" or named_troll) and (distance is None or distance<=45):
    confidence="HIGH"
   elif local_damage or (cycle["activity"]=="Troll" and named_troll and distance is not None and distance<=25):
    confidence="MEDIUM"
   else:
    confidence="LOW"
   if len(candidates)>1 and not local_damage and candidates[1]["name_matches_troll"]==named_troll:
    d1=candidates[1]["distance_from_player"];d0=distance
    if d0 is not None and d1 is not None and abs(d1-d0)<3:confidence="AMBIGUOUS"
   correlation.update({"best_candidate":best,"confidence":confidence,"status":"CANDIDATE_"+confidence})
   mob=mobs.get(str(best["entity_id"]))
   if mob is not None:
    match={"activity":cycle["activity"],"found_ms":start,"mob_killed_ms":cycle.get("mob_killed_ms"),
           "confidence":confidence,"tier":cycle.get("tier"),"enchantment":cycle.get("enchantment"),
           "identity_proven":False}
    mob["manual_marker_matches"].append(match)
    if confidence in {"HIGH","MEDIUM"}:
     mob["marker_activity"]=cycle["activity"]
     mob["marker_match_confidence"]=confidence
     mob["marker_tier"]=cycle.get("tier")
     mob["marker_enchantment"]=cycle.get("enchantment")
     mob["marker_enchant"]=int(str(cycle.get("enchantment") or "E0").replace("E","")) if str(cycle.get("enchantment") or "E0").replace("E","").isdigit() else 0
     if not mob.get("name") and cycle["activity"]=="Troll":mob["name"]="Troll candidate"
    else:
     mob["marker_activity_candidate"]=cycle["activity"]
     mob["marker_match_confidence"]=confidence
  else:
   correlation.update({"confidence":"NONE","status":"NO_SPATIAL_CANDIDATE"})
  cycle["spatial_correlation"]=correlation
 return local_player_ids

def analyze(path):
 nodes={}; states=collections.defaultdict(list); markers=[]; rows=bad=event123=0; packet5056=0
 mobs={}; player_positions=[]; player_moves=[]; health_events=[]
 with path.open(encoding="utf-8",errors="replace") as f:
  for line in f:
   try:r=json.loads(line)
   except Exception:bad+=1;continue
   rows+=1
   if r.get("src_port")==5056 or r.get("dst_port")==5056: packet5056+=1
   if isinstance(r.get("manual_marker"),dict):
    marker=dict(r["manual_marker"])
    marker.setdefault("timestamp_ms",r.get("time_ms"))
    markers.append(marker)
   for e in parse_row(r):
    k=e.get("kind"); ident=e.get("entity_id"); t=r.get("time_ms")
    if k=="mob": event123+=1
    if k=="own_player_move" and finite_number(e.get("x")) is not None and finite_number(e.get("y")) is not None:
     player_positions.append({"time_ms":int(t or 0),"x":finite_number(e.get("x")),"y":finite_number(e.get("y"))})
    elif k=="player_move" and ident is not None and finite_number(e.get("x")) is not None and finite_number(e.get("y")) is not None:
     player_moves.append({"time_ms":int(t or 0),"entity_id":str(ident),"x":finite_number(e.get("x")),"y":finite_number(e.get("y"))})
    elif k=="health" and ident is not None:
     health_events.append({"time_ms":int(t or 0),"entity_id":str(ident),"source_entity_id":str(e.get("source_entity_id")) if e.get("source_entity_id") is not None else None,"value":e.get("value"),"secondary_value":e.get("secondary_value")})
    if ident is None: continue
    key=str(ident)
    if k=="mob":
     mob=mobs.setdefault(key,{"entity_id":ident,"mobile_type_id":e.get("mobile_type_id"),"enchant":e.get("enchant"),"name":e.get("name"),"first_seen_ms":t,"last_seen_ms":t,"positions":[],"health_timeline":[],"health_event_count":0,"player_damage_events":0,"left":False})
     mob["first_seen_ms"]=min(mob["first_seen_ms"] or t,t or mob["first_seen_ms"])
     mob["last_seen_ms"]=max(mob["last_seen_ms"] or t,t or mob["last_seen_ms"])
     if e.get("name"):mob["name"]=e.get("name")
     if e.get("mobile_type_id") is not None:mob["mobile_type_id"]=e.get("mobile_type_id")
     if e.get("enchant") is not None:mob["enchant"]=e.get("enchant")
     x=finite_number(e.get("x"));y=finite_number(e.get("y"))
     if x is not None and y is not None:mob["positions"].append({"time_ms":int(t or 0),"x":x,"y":y})
    elif k=="leave" and key in mobs:
     mobs[key]["left"]=True;mobs[key]["left_ms"]=t
    elif k=="harvestable":
     n=nodes.setdefault(key,{"entity_id":ident,"mobile_type_id":e.get("mobile_type_id"),"type_number":e.get("type_number"),"tier":e.get("tier"),"enchant":e.get("enchant",0),"x":e.get("x"),"y":e.get("y"),"first_seen_ms":t,"last_seen_ms":t,"observations":0,"sizes_seen":[],"state_timeline":[]})
     n["first_seen_ms"]=t if n["first_seen_ms"] is None else min(n["first_seen_ms"],t)
     n["last_seen_ms"]=t if n["last_seen_ms"] is None else max(n["last_seen_ms"],t);n["observations"]+=1
     if e.get("size") not in n["sizes_seen"]:n["sizes_seen"].append(e.get("size"))
    elif k=="resource_state":
     states[key].append({"time_ms":t,"size":e.get("size"),"removed":e.get("removed")})
    elif k=="mob": event123+=0
   # Legacy event-123 decoder results
   for ev in r.get("photon_event_analysis",[]):
    if ev.get("event_code")==123:event123+=len(ev.get("living_entities",[]))
 for key,seq in states.items():
  if key in nodes:nodes[key]["state_timeline"]=sorted(seq,key=lambda x:x["time_ms"] or 0)
 state_only=[{"entity_id":int(k) if k.isdigit() else k,"state_timeline":sorted(v,key=lambda x:x["time_ms"] or 0)} for k,v in states.items() if k not in nodes]
 marker_lifecycles,unmatched_markers=pair_manual_markers(markers)
 local_player_ids=correlate_mob_markers(marker_lifecycles,mobs,player_positions,player_moves,health_events)
 resource_cycles=[x for x in marker_lifecycles if x["activity"] in RESOURCE_ACTIVITIES]
 mob_marker_cycles=[x for x in marker_lifecycles if x.get("activity") in {"Mob","Mobs","Troll"}]
 correlated_mob_markers=[x for x in mob_marker_cycles if x.get("spatial_correlation",{}).get("best_candidate")]

 stem=path.stem; ordered=sorted(nodes.values(),key=lambda n:(n["type_number"] or -1,n["tier"] or -1,n["enchant"] or 0,n["entity_id"]))
 doc={"dataset_id":stem,"source_file":path.name,"source_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"counts":{"jsonl_rows":rows,"malformed_lines":bad,"udp_5056_packets":packet5056,"event40_observations":sum(n["observations"] for n in ordered),"unique_event40_nodes":len(ordered),"event46_updates":sum(map(len,states.values())),"event46_state_ids":len(states),"event46_exact_joins":sum(bool(n["state_timeline"]) for n in ordered),"state_only_ids":len(state_only),"event123_living_entity_observations":event123,"unique_mob_entities":len(mobs),"own_player_position_samples":len(player_positions),"inferred_local_player_entity_ids":len(local_player_ids),"mob_markers_with_spatial_candidates":len(correlated_mob_markers),"mob_markers_high_confidence":sum(x.get("spatial_correlation",{}).get("confidence")=="HIGH" for x in correlated_mob_markers),"manual_markers":len(markers),"resource_marker_cycles":len(resource_cycles),"complete_resource_marker_cycles":sum(x["status"] in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),"clean_complete_resource_marker_cycles":sum(x["status"]=="COMPLETE" for x in resource_cycles),"completed_with_metadata_conflict_resource_cycles":sum(x["status"]=="COMPLETE_WITH_METADATA_CONFLICT" for x in resource_cycles),"incomplete_resource_marker_cycles":sum(x["status"] not in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),"mob_marker_pairs":sum(x["status"]=="TARGET_FOUND_TO_KILLED" for x in marker_lifecycles),"incomplete_mob_marker_cycles":sum(x["status"]=="TARGET_FOUND_NOT_KILLED" for x in marker_lifecycles),"unmatched_marker_events":len(unmatched_markers),"marker_metadata_conflicts":sum(bool(x.get("metadata_conflicts")) for x in marker_lifecycles)},"nodes":ordered,"mobs":sorted(mobs.values(),key=lambda m:(m.get("first_seen_ms") or 0,str(m.get("entity_id")))),"player_marker_positions":[{"time_ms":p["time_ms"],"x":p["x"],"y":p["y"]} for p in player_positions if any(abs(p["time_ms"]-cycle.get("found_ms",0))<=2500 for cycle in mob_marker_cycles)],"state_only_entities":state_only,"manual_markers":markers,"marker_lifecycles":marker_lifecycles,"unmatched_marker_events":unmatched_markers}
 DB.mkdir(parents=True,exist_ok=True);ANALYSIS.mkdir(parents=True,exist_ok=True)
 (DB/(stem+".json")).write_text(json.dumps(doc,indent=2),encoding="utf-8")
 fields=["entity_id","mobile_type_id","type_number","tier","enchant","x","y","first_seen_ms","last_seen_ms","observations","sizes_seen","state_timeline"]
 with (DB/(stem+".csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
  for n in ordered:
   x=n.copy();x["sizes_seen"]=";".join(map(str,x["sizes_seen"]));x["state_timeline"]=json.dumps(x["state_timeline"],separators=(",",":"));w.writerow(x)
 write_marker_lifecycle_csv(stem,marker_lifecycles)
 # A single catalog feeds the Android radar and remains inspectable beside the uploaded ZIP.
 catalog_entities=[]
 for node in ordered:
  timeline=node.get("state_timeline") or []
  latest_size=timeline[-1].get("size") if timeline else None
  catalog_entities.append({
   "entity_id":"catalog:node:"+str(node["entity_id"]),"source_entity_id":node["entity_id"],
   "last_kind":"static_resource","family":node.get("type_number"),"mobile_type_id":node.get("mobile_type_id"),
   "tier":node.get("tier"),"enchant":node.get("enchant",0),"x":node.get("x"),"y":node.get("y"),
   "first_seen_ms":node.get("first_seen_ms"),"last_seen_ms":node.get("last_seen_ms"),
   "observations":node.get("observations"),"state_timeline":timeline,"depleted":latest_size==0,
   "classification":"unclassified_resource_candidate",
   "evidence_grade":"family_29_ore_candidate" if node.get("type_number")==29 else "typed_resource_family_unresolved",
   "user_confirmed_target":str(node["entity_id"])=="2788",
   "user_confirmation":"First T5.3 target confirmed by user; temporal/state correlation" if str(node["entity_id"])=="2788" else None
  })
 for mob in mobs.values():
  if finite_number(mob.get("x")) is None or finite_number(mob.get("y")) is None:continue
  item={k:v for k,v in mob.items() if k not in {"positions","health_timeline"}}
  item["entity_id"]="catalog:mob:"+str(mob["entity_id"])
  item["source_entity_id"]=str(mob["entity_id"])
  item["last_kind"]="mob"
  item["classification"]="observed_mob"
  if mob.get("health_timeline"):
   latest_health=mob["health_timeline"][-1]
   if latest_health.get("secondary_value") is not None:item["health_value"]=latest_health.get("secondary_value")
   if latest_health.get("value") is not None:item["health_delta"]=latest_health.get("value")
  catalog_entities.append(item)
 catalog={"schema_version":1,"dataset":stem,"source_archive":pathlib.Path(path).name,
  "coordinate_semantics":"Capture-local game coordinates. Zone identity is not decoded; do not combine across zones.",
  "node_count":len(ordered),"mob_count":sum(1 for x in catalog_entities if x.get("last_kind")=="mob"),
  "marker_count":len(marker_lifecycles),"correlated_mob_marker_count":len(correlated_mob_markers),
  "confirmed_user_target":{"entity_id":"2788","tier":5,"enchant":3,"x":-379,"y":-24,"confidence":"user-confirmed temporal/state correlation"},
  "entities":catalog_entities,"markers":marker_lifecycles}
 catalog_text=json.dumps(catalog,indent=2)
 (DB/(stem+"-catalog.json")).write_text(catalog_text,encoding="utf-8")
 (ANALYSIS/(stem+"-overlay-catalog.json")).write_text(catalog_text,encoding="utf-8")
 if stem=="albion-1793682590317":
  asset=ROOT/"V2rayNG/app/src/main/assets/albion-static-catalog.json"
  asset.parent.mkdir(parents=True,exist_ok=True)
  asset.write_text(catalog_text,encoding="utf-8")
 mob_fields=["entity_id","mobile_type_id","name","enchant","x","y","first_seen_ms","last_seen_ms","health_event_count","player_damage_events","left","marker_activity","marker_match_confidence"]
 with (DB/(stem+"-mob-entities.csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=mob_fields);w.writeheader()
  for mob in sorted(mobs.values(),key=lambda m:str(m.get("entity_id"))):w.writerow({k:mob.get(k) for k in mob_fields})
 correlation_fields=["activity","found_ms","mob_killed_ms","status","best_entity_id","best_name","best_x","best_y","confidence","candidate_count","identity_proven"]
 with (DB/(stem+"-mob-marker-spatial-correlation.csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=correlation_fields);w.writeheader()
  for cycle in mob_marker_cycles:
   corr=cycle.get("spatial_correlation",{});best=corr.get("best_candidate") or {}
   w.writerow({"activity":cycle.get("activity"),"found_ms":cycle.get("found_ms"),"mob_killed_ms":cycle.get("mob_killed_ms"),
    "status":corr.get("status"),"best_entity_id":best.get("entity_id"),"best_name":best.get("name"),
    "best_x":best.get("x"),"best_y":best.get("y"),"confidence":corr.get("confidence"),
    "candidate_count":corr.get("candidate_count"),"identity_proven":corr.get("identity_proven",False)})
 summary=doc["counts"]
 (ANALYSIS/(stem+".md")).write_text("# Capture ingest: "+stem+"\n\n"+"\n".join("- "+k+": "+str(v) for k,v in summary.items())+"\n\nEvent 123 mob entities, Event 6/7 health observations, Event 22 player positions and manual Mob/Troll markers are correlated within this PCAP only. Candidate matches are evidence-ranked and never asserted as exact runtime identity. State updates join only by exact runtime entity ID; state-only IDs remain separate.\n",encoding="utf-8")
 return doc
def main():
 ap=argparse.ArgumentParser()
 ap.add_argument("--refresh-existing",action="store_true")
 ap.add_argument("inputs",nargs="*")
 args=ap.parse_args()
 if args.refresh_existing:
  print("Refreshed existing marker datasets:",refresh_existing_marker_lifecycles())
 for item in args.inputs:
  p=pathlib.Path(item)
  if p.suffix.lower()==".zip":
   dest=pathlib.Path("/tmp/albion-ingest")/p.stem;dest.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(p) as z:safe_extract(z,dest)
   files=list(dest.rglob("*.jsonl"))
  elif p.suffix.lower()==".jsonl":files=[p]
  else:continue
  for jf in files:
   d=analyze(jf);print(jf.name,json.dumps(d["counts"],sort_keys=True))
if __name__=="__main__":main()
