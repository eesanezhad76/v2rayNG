#!/usr/bin/env python3
"""Offline Albion capture ZIP/JSONL ingest. Preserves unknowns and exact runtime IDs."""
import argparse,csv,json,pathlib,zipfile,collections,hashlib
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
     cycle=queue["pending"].popleft();compare_metadata(cycle,marker,event);cycle["completed_ms"]=timestamp;cycle["status"]="COMPLETE" if not cycle["metadata_conflicts"] else "COMPLETE_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
    else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"completion_without_target_found"})
   elif event=="node_depleted":
    if queue["completed"]:
     cycle=queue["completed"].popleft();compare_metadata(cycle,marker,event);cycle["depleted_ms"]=timestamp;cycle["status"]="COMPLETE" if not cycle["metadata_conflicts"] else "COMPLETE_WITH_METADATA_CONFLICT";lifecycles.append(cycle)
    else: unmatched.append({"activity":activity,"event":event,"timestamp_ms":timestamp,"reason":"depletion_without_completion"})
  elif activity=="Mobs" and event=="target_found":
   mob_pending[activity].append({"activity":activity,"tier":marker.get("tier"),"enchantment":marker.get("enchantment"),"found_ms":timestamp,"started_ms":None,"completed_ms":None,"depleted_ms":None,"metadata_conflicts":[]})
  elif activity=="Mobs" and event=="mob_killed":
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
  w=csv.DictWriter(f,fieldnames=marker_fields);w.writeheader()
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
def analyze(path):
 nodes={}; states=collections.defaultdict(list); markers=[]; rows=bad=event123=0; packet5056=0
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
    if ident is None: continue
    key=str(ident)
    if k=="harvestable":
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
 resource_cycles=[x for x in marker_lifecycles if x["activity"] in RESOURCE_ACTIVITIES]
 stem=path.stem; ordered=sorted(nodes.values(),key=lambda n:(n["type_number"] or -1,n["tier"] or -1,n["enchant"] or 0,n["entity_id"]))
 doc={"dataset_id":stem,"source_file":path.name,"source_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"counts":{"jsonl_rows":rows,"malformed_lines":bad,"udp_5056_packets":packet5056,"event40_observations":sum(n["observations"] for n in ordered),"unique_event40_nodes":len(ordered),"event46_updates":sum(map(len,states.values())),"event46_state_ids":len(states),"event46_exact_joins":sum(bool(n["state_timeline"]) for n in ordered),"state_only_ids":len(state_only),"event123_living_entity_observations":event123,"manual_markers":len(markers),"resource_marker_cycles":len(resource_cycles),"complete_resource_marker_cycles":sum(x["status"] in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),"clean_complete_resource_marker_cycles":sum(x["status"]=="COMPLETE" for x in resource_cycles),"completed_with_metadata_conflict_resource_cycles":sum(x["status"]=="COMPLETE_WITH_METADATA_CONFLICT" for x in resource_cycles),"incomplete_resource_marker_cycles":sum(x["status"] not in {"COMPLETE","COMPLETE_WITH_METADATA_CONFLICT"} for x in resource_cycles),"mob_marker_pairs":sum(x["status"]=="TARGET_FOUND_TO_KILLED" for x in marker_lifecycles),"unmatched_marker_events":len(unmatched_markers),"marker_metadata_conflicts":sum(bool(x.get("metadata_conflicts")) for x in marker_lifecycles)},"nodes":ordered,"state_only_entities":state_only,"manual_markers":markers,"marker_lifecycles":marker_lifecycles,"unmatched_marker_events":unmatched_markers}
 DB.mkdir(parents=True,exist_ok=True);ANALYSIS.mkdir(parents=True,exist_ok=True)
 (DB/(stem+".json")).write_text(json.dumps(doc,indent=2),encoding="utf-8")
 fields=["entity_id","mobile_type_id","type_number","tier","enchant","x","y","first_seen_ms","last_seen_ms","observations","sizes_seen","state_timeline"]
 with (DB/(stem+".csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
  for n in ordered:
   x=n.copy();x["sizes_seen"]=";".join(map(str,x["sizes_seen"]));x["state_timeline"]=json.dumps(x["state_timeline"],separators=(",",":"));w.writerow(x)
 write_marker_lifecycle_csv(stem,marker_lifecycles)
 summary=doc["counts"]
 (ANALYSIS/(stem+".md")).write_text("# Capture ingest: "+stem+"\n\n"+"\n".join("- "+k+": "+str(v) for k,v in summary.items())+"\n\nState updates join only by exact runtime entity ID. Marker lifecycles are FIFO-paired but remain manual annotations without runtime entity identity. State-only IDs remain separate.\n",encoding="utf-8")
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
