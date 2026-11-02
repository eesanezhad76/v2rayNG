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
def analyze(path):
 nodes={}; states=collections.defaultdict(list); markers=[]; rows=bad=event123=0; packet5056=0
 with path.open(encoding="utf-8",errors="replace") as f:
  for line in f:
   try:r=json.loads(line)
   except Exception:bad+=1;continue
   rows+=1
   if r.get("src_port")==5056 or r.get("dst_port")==5056: packet5056+=1
   if isinstance(r.get("manual_marker"),dict):markers.append(r["manual_marker"])
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
 stem=path.stem; ordered=sorted(nodes.values(),key=lambda n:(n["type_number"] or -1,n["tier"] or -1,n["enchant"] or 0,n["entity_id"]))
 doc={"dataset_id":stem,"source_file":path.name,"source_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"counts":{"jsonl_rows":rows,"malformed_lines":bad,"udp_5056_packets":packet5056,"event40_observations":sum(n["observations"] for n in ordered),"unique_event40_nodes":len(ordered),"event46_updates":sum(map(len,states.values())),"event46_state_ids":len(states),"event46_exact_joins":sum(bool(n["state_timeline"]) for n in ordered),"state_only_ids":len(state_only),"event123_living_entity_observations":event123,"manual_markers":len(markers)},"nodes":ordered,"state_only_entities":state_only,"manual_markers":markers}
 DB.mkdir(parents=True,exist_ok=True);ANALYSIS.mkdir(parents=True,exist_ok=True)
 (DB/(stem+".json")).write_text(json.dumps(doc,indent=2),encoding="utf-8")
 fields=["entity_id","mobile_type_id","type_number","tier","enchant","x","y","first_seen_ms","last_seen_ms","observations","sizes_seen","state_timeline"]
 with (DB/(stem+".csv")).open("w",newline="",encoding="utf-8") as f:
  w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
  for n in ordered:
   x=n.copy();x["sizes_seen"]=";".join(map(str,x["sizes_seen"]));x["state_timeline"]=json.dumps(x["state_timeline"],separators=(",",":"));w.writerow(x)
 summary=doc["counts"]
 (ANALYSIS/(stem+".md")).write_text("# Capture ingest: "+stem+"\n\n"+"\n".join("- "+k+": "+str(v) for k,v in summary.items())+"\n\nState updates join only by exact runtime entity ID. State-only IDs remain separate.\n",encoding="utf-8")
 return doc
def main():
 ap=argparse.ArgumentParser();ap.add_argument("inputs",nargs="+");args=ap.parse_args()
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
