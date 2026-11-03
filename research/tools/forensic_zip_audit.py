#!/usr/bin/env python3
"""Forensic ZIP inventory. Preserve complete text payloads and hash every member."""
import base64, collections, hashlib, json, pathlib, re, struct, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
INBOX=ROOT/"research/inbox"; OUT=ROOT/"research/analysis"
TEXT_EXT={".txt",".md",".log",".csv",".json"}
MAX_TEXT_MEMBER_BYTES=64*1024*1024

def inspect(zp):
 doc={"archive":zp.name,"bytes":zp.stat().st_size,"sha256":hashlib.sha256(zp.read_bytes()).hexdigest(),
      "members":[],"notes":[],"pcaps":[],"jsonl":[]}
 with zipfile.ZipFile(zp) as z:
  for info in z.infolist():
   name=info.filename
   member={"name":name,"bytes":info.file_size,"compressed_bytes":info.compress_size}
   doc["members"].append(member)
   if info.is_dir(): continue
   ext=pathlib.PurePosixPath(name.lower()).suffix
   if ext in TEXT_EXT or any(s in name.lower() for s in ("readme","note","report")):
    raw=z.read(info); digest=hashlib.sha256(raw).hexdigest(); member["sha256"]=digest
    note={"name":name,"bytes":len(raw),"sha256":digest}
    if len(raw)<=MAX_TEXT_MEMBER_BYTES:
     try:
      note["text"]=raw.decode("utf-8-sig"); note["encoding"]="utf-8"
     except UnicodeDecodeError:
      note["text"]=None; note["encoding"]="base64"; note["base64"]=base64.b64encode(raw).decode("ascii")
    else:
     note["text"]=None; note["encoding"]="over_inline_limit"
    doc["notes"].append(note)
   elif ext in (".pcap",".pcapng"):
    raw=z.read(info); digest=hashlib.sha256(raw).hexdigest(); member["sha256"]=digest
    p={"name":name,"bytes":len(raw),"magic":raw[:4].hex(),"sha256":digest}
    if raw[:4] in (b"\xd4\xc3\xb2\xa1",b"\xa1\xb2\xc3\xd4",b"\x4d\x3c\xb2\xa1",b"\xa1\xb2\x3c\x4d") and len(raw)>=24:
     endian="<" if raw[:4] in (b"\xd4\xc3\xb2\xa1",b"\x4d\x3c\xb2\xa1") else ">"
     major,minor,zone,sig,snap,link=struct.unpack(endian+"HHIIII",raw[4:24])
     p.update({"format":"pcap","version":str(major)+"."+str(minor),"snaplen":snap,"linktype":link})
    elif raw[:4]==b"\x0a\x0d\x0d\x0a": p["format"]="pcapng"
    else: p["format"]="unknown"
    doc["pcaps"].append(p)
   elif ext==".jsonl":
    raw=z.read(info); member["sha256"]=hashlib.sha256(raw).hexdigest()
    top=collections.Counter(); events=collections.Counter(); malformed=0; samples=[]
    for line in raw.splitlines():
     try: row=json.loads(line)
     except Exception: malformed+=1; continue
     if isinstance(row,dict):
      top.update(row.keys())
      for ev in row.get("photon_event_analysis",[]) or []:
       if isinstance(ev,dict): events[str(ev.get("event_code","?"))]+=1
      if len(samples)<2: samples.append({k:row.get(k) for k in list(row)[:12]})
    doc["jsonl"].append({"name":name,"bytes":len(raw),"lines":len(raw.splitlines()),
      "malformed":malformed,"keys":dict(top),"event_codes":dict(events),"samples":samples})
   else:
    member["sha256"]=hashlib.sha256(z.read(info)).hexdigest()
 note_text="\n".join(n.get("text") or "" for n in doc["notes"])
 hints={}
 for key,pattern in {"straight_then_stop":r"straight|straight.?line|مستقیم|وایست|توقف|stop",
  "ninety_degree_turns":r"90|۹۰|right.?angle|turn|چرخش|تغییر جهت",
  "resource_nodes":r"ore|resource|node|سنگ|معدن|نود",
  "troll_golem_mob":r"troll|golem|mob|ترول|گولم"}.items():
  hints[key]=len(re.findall(pattern,note_text,re.I))
 doc["text_hints"]=hints
 doc["text_hints_semantics"]="Regex keyword occurrence counts in text members only; not counts of unique nodes, mobs, Trolls, or game entities."
 OUT.mkdir(parents=True,exist_ok=True); stem=zp.stem
 (OUT/(stem+"-forensic-inventory.json")).write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding="utf-8")
 lines=["# ZIP forensic inventory: "+zp.name,"","- ZIP SHA-256: "+doc["sha256"],"","## Members"]
 lines += ["- "+x["name"]+" ("+str(x["bytes"])+" bytes; SHA-256 "+x.get("sha256","not-computed")+")" for x in doc["members"]]
 lines += ["","## PCAPs"]+["- "+x["name"]+": "+x.get("format","unknown")+", "+str(x["bytes"])+" bytes, linktype="+str(x.get("linktype","n/a")) for x in doc["pcaps"]]
 lines += ["","## JSONL"]+["- "+x["name"]+": "+str(x["lines"])+" lines; malformed="+str(x["malformed"])+"; events="+json.dumps(x["event_codes"],ensure_ascii=False) for x in doc["jsonl"]]
 lines += ["","## Text members","Full text is preserved without truncation in the matching JSON inventory file, under the notes array."]
 lines += ["- "+n["name"]+": "+str(n["bytes"])+" bytes; SHA-256 "+n["sha256"]+"; encoding="+n["encoding"] for n in doc["notes"]]
 lines += ["","## Test hints (text-only; not entity counts)"]+["- "+k+": "+str(v) for k,v in hints.items()]
 (OUT/(stem+"-forensic-inventory.md")).write_text("\n".join(lines)+"\n",encoding="utf-8")
 print(json.dumps({"archive":zp.name,"members":len(doc["members"]),"pcaps":[x["name"] for x in doc["pcaps"]],
  "jsonl":[x["name"] for x in doc["jsonl"]],"notes":[x["name"] for x in doc["notes"]],"hints":hints},ensure_ascii=False))

def main():
 zips=sorted(INBOX.rglob("*.zip")); print("FORENSIC_AUDIT zip_count="+str(len(zips)))
 if not zips: raise SystemExit("No ZIP files found")
 for zp in zips: inspect(zp)

if __name__=="__main__": main()
