#!/usr/bin/env python3
"""Safe offline Albion JSONL/ZIP ingest. No network calls; unknowns remain unknown."""
import argparse, csv, json, pathlib, zipfile, collections, hashlib, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_DB = ROOT / "research/database"
OUT_ANALYSIS = ROOT / "research/analysis"
def safe_extract(zf, dest):
    base = dest.resolve()
    for info in zf.infolist():
        target = (dest / info.filename).resolve()
        if target != base and base not in target.parents:
            raise ValueError("Unsafe ZIP member path: " + info.filename)
    zf.extractall(dest)
def analyze_jsonl(path):
    resources, states, markers = [], [], []
    packet_rows = malformed = event123 = 0
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            try: row = json.loads(line)
            except Exception:
                malformed += 1; continue
            packet_rows += 1
            if isinstance(row.get("manual_marker"), dict):
                markers.append(row["manual_marker"])
            for event in row.get("photon_event_analysis", []):
                code = event.get("event_code")
                if code == 40:
                    for node in event.get("resources", []):
                        resources.append({"time_ms":row.get("time_ms"), **node})
                elif code == 46 and isinstance(event.get("state_update"), dict):
                    states.append({"time_ms":row.get("time_ms"), **event["state_update"]})
                elif code == 123:
                    event123 += len(event.get("living_entities", []))
    by_id = collections.defaultdict(list)
    state_by_id = collections.defaultdict(list)
    for item in resources: by_id[item["id"]].append(item)
    for item in states: state_by_id[item["id"]].append(item)
    nodes=[]
    for entity_id, observations in by_id.items():
        observations.sort(key=lambda x:x["time_ms"])
        first=observations[0]
        timeline=sorted(state_by_id.get(entity_id,[]),key=lambda x:x["time_ms"])
        family,tier=first.get("type_id"),first.get("tier")
        mobile={(29,3):571,(29,4):573,(29,5):574}.get((family,tier))
        nodes.append({
          "entity_id":entity_id,"type_number":family,"tier":tier,
          "enchantment_raw":first.get("enchantment_raw",0),
          "mobile_type_id":mobile,
          "mobile_type_confidence":"confirmed_cross_capture" if mobile else "unknown",
          "x":first.get("x"),"y":first.get("y"),
          "first_seen_ms":observations[0]["time_ms"],
          "last_seen_ms":observations[-1]["time_ms"],
          "observations":len(observations),
          "state_timeline":[{"time_ms":s["time_ms"],"amount_or_state":s.get("amount_or_state"),
                             "enchantment_raw":s.get("enchantment_raw")} for s in timeline]
        })
    state_only=[]
    for entity_id, timeline in state_by_id.items():
        if entity_id not in by_id:
            state_only.append({"entity_id":entity_id,"location_status":"no_event40_snapshot_in_capture",
              "state_timeline":[{"time_ms":s["time_ms"],"amount_or_state":s.get("amount_or_state"),
                                 "enchantment_raw":s.get("enchantment_raw")} for s in sorted(timeline,key=lambda x:x["time_ms"])]})
    stem=path.stem
    dataset={"dataset_id":stem,"source_file":path.name,
      "source_sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
      "counts":{"jsonl_rows":packet_rows,"malformed_lines":malformed,
       "event40_observations":len(resources),"unique_event40_nodes":len(nodes),
       "event46_updates":len(states),"event123_living_entity_observations":event123,
       "manual_markers":len(markers),"state_only_ids":len(state_only)},
      "nodes":sorted(nodes,key=lambda n:(n["type_number"] or -1,n["tier"] or -1,n["enchantment_raw"] or 0,n["entity_id"])),
      "state_only_entities":sorted(state_only,key=lambda x:x["entity_id"]),
      "manual_markers":markers}
    OUT_DB.mkdir(parents=True,exist_ok=True); OUT_ANALYSIS.mkdir(parents=True,exist_ok=True)
    (OUT_DB/(stem+".json")).write_text(json.dumps(dataset,indent=2),encoding="utf-8")
    fields=["entity_id","type_number","mobile_type_id","tier","enchantment_raw","x","y","first_seen_ms","last_seen_ms","observations","state_count","state_values"]
    with (OUT_DB/(stem+".csv")).open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader()
        for n in dataset["nodes"]:
            vals=[s["amount_or_state"] for s in n["state_timeline"]]
            w.writerow({"entity_id":n["entity_id"],"type_number":n["type_number"],"mobile_type_id":n["mobile_type_id"],
              "tier":n["tier"],"enchantment_raw":n["enchantment_raw"],"x":n["x"],"y":n["y"],
              "first_seen_ms":n["first_seen_ms"],"last_seen_ms":n["last_seen_ms"],"observations":n["observations"],
              "state_count":len(vals),"state_values":">".join(map(str,vals))})
    summary=dataset["counts"]
    report="# Ingest report: "+stem+"\n\n"+"\n".join("- "+k+": "+str(v) for k,v in summary.items())+"\n\n"
    report+="Event 46 timelines are joined only by exact runtime entity ID. State-only IDs remain separate. Unknown type/mobile codes are null.\n"
    (OUT_ANALYSIS/(stem+".md")).write_text(report,encoding="utf-8")
    return dataset
def main():
    ap=argparse.ArgumentParser(); ap.add_argument("inputs",nargs="+"); args=ap.parse_args()
    for item in args.inputs:
        p=pathlib.Path(item)
        if p.suffix.lower()==".zip":
            dest=pathlib.Path("/tmp/albion-ingest")/p.stem
            dest.mkdir(parents=True,exist_ok=True)
            with zipfile.ZipFile(p) as zf: safe_extract(zf,dest)
            jsonls=list(dest.rglob("*.jsonl"))
        elif p.suffix.lower()==".jsonl": jsonls=[p]
        else: print("skip unsupported",p); continue
        for jf in jsonls:
            data=analyze_jsonl(jf)
            print(jf.name, json.dumps(data["counts"],sort_keys=True))
if __name__=="__main__": main()
