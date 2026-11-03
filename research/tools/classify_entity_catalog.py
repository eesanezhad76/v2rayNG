#!/usr/bin/env python3
"""Evidence-ranked crosswalk for Albion resource families and mobile type IDs."""
import collections, csv, json, pathlib, re
ROOT=pathlib.Path(__file__).resolve().parents[2]
DB=ROOT/"research/database"; ANALYSIS=ROOT/"research/analysis"

def read_csv(path):
    with path.open("r",encoding="utf-8-sig",newline="") as f: return list(csv.DictReader(f))
def counter(rows,key):
    c=collections.Counter(str(r.get(key,"") or "UNKNOWN") for r in rows)
    return dict(sorted(c.items(),key=lambda x:(-x[1],x[0])))
def field_inventory(rows,limit=20):
    keys=collections.Counter(); examples=collections.defaultdict(collections.Counter)
    for row in rows:
        if not isinstance(row,dict): continue
        for key,value in row.items():
            keys[key]+=1
            if re.search(r"type|family|tier|enchant|class|categor|resource|name|label|rarity|level|node|mob",key,re.I):
                if value is not None and not isinstance(value,(dict,list)): examples[key][str(value)[:100]]+=1
    return {k:{"present_in_records":v,"top_values":examples[k].most_common(limit)}
            for k,v in sorted(keys.items()) if re.search(r"type|family|tier|enchant|class|categor|resource|name|label|rarity|level|node|mob",k,re.I)}

def main():
    nodes=read_csv(DB/"2026-11-02-pcap-harvestable-nodes.csv")
    mobs=read_csv(DB/"2026-11-02-pcap-mob-lifecycle.csv")
    knowledge=json.loads((DB/"albion-knowledge-bundle-import.json").read_text(encoding="utf-8"))
    entities=knowledge.get("entity_observations",[]); learned=knowledge.get("learned_patterns",[])
    autonomous=knowledge.get("autonomous_patterns",[]); protocol=knowledge.get("protocol_evidence",[])
    families=collections.defaultdict(list)
    for r in nodes: families[str(r.get("type_numbers") or "UNKNOWN")].append(r)
    family_profiles=[]
    for family,rows in sorted(families.items()):
        types=counter(rows,"mobile_type_ids")
        p={"family":family,"event40_records":len(rows),"mobile_type_ids":types,
           "tiers":counter(rows,"tiers"),"enchantment_raw_values":counter(rows,"enchantment_raw_values"),
           "archives":counter(rows,"archive"),"distinct_pcaps":len({(r.get("archive"),r.get("pcap")) for r in rows}),
           "interpretation":"unclassified",
           "evidence_note":"Capture-scoped Event 40 observations; no name inferred from family number alone."}
        if family=="29":
            p["interpretation"]="confirmed_ore_family"
            p["evidence_note"]="Independently verified Ore mapping: T3=571, T4=573, T5=574."
        elif family=="27":
            p["interpretation"]="unknown_family_with_user_confirmed_exception"
            p["evidence_note"]="Only entity 2788, T5.3 at (-379,-24), is user-confirmed; do not generalize."
        elif family=="20" and set(types)=={"482"}:
            p["interpretation"]="type_482_cross_domain_candidate"
            p["evidence_note"]="Type 482 also appears in Event 123; require same-session/time/coordinate evidence."
        family_profiles.append(p)
    mob_groups=collections.defaultdict(list)
    for r in mobs: mob_groups[str(r.get("mobile_type_id") or "UNKNOWN")].append(r)
    mob_profiles=[]
    for type_id,rows in sorted(mob_groups.items(),key=lambda x:(-len(x[1]),x[0])):
        names=[r.get("name","") for r in rows if r.get("name")]
        mob_profiles.append({"mobile_type_id":type_id,"lifecycle_records":len(rows),
          "nonempty_names":counter([{"name":n} for n in names],"name"),
          "enchantment_values":counter(rows,"enchantment"),"archives":counter(rows,"archive"),
          "distinct_pcaps":len({(r.get("archive"),r.get("pcap")) for r in rows}),
          "movement_observations":sum(int(r.get("movement_observations") or 0) for r in rows),
          "health_observation_records":sum(int(r.get("health_observations") or 0) for r in rows),
          "leave_observed_records":sum(1 for r in rows if r.get("left_after_seen")=="true"),
          "warning":"Event 123 lifecycle evidence; leave is not proof of death."})
    schema={"entity_observations":field_inventory(entities),"learned_patterns":field_inventory(learned),
            "autonomous_patterns":field_inventory(autonomous),"protocol_evidence":field_inventory(protocol)}
    type_fields={}
    for section,rows in (("entities",entities),("learned_patterns",learned),("autonomous_patterns",autonomous),("protocol",protocol)):
        keys=set().union(*(r.keys() for r in rows if isinstance(r,dict))) if rows else set()
        for key in keys:
            if re.search(r"(mobile.*type|type.*id|type_number|type_numbers|family)",key,re.I):
                values=collections.Counter()
                for row in rows:
                    if isinstance(row,dict):
                        value=row.get(key)
                        if value is not None and not isinstance(value,(dict,list)): values[str(value)[:100]]+=1
                type_fields[f"{section}.{key}"]=values.most_common(100)
    filename_hints=[]
    for family,rows in sorted(families.items()):
        by_pcap={}
        for r in rows: by_pcap.setdefault((r.get("archive"),r.get("pcap")),0); by_pcap[(r.get("archive"),r.get("pcap"))]+=1
        for (archive,pcap),count in sorted(by_pcap.items()):
            if re.search(r"golem|ore|troll|mob|elemental|stone|wood|fiber|hide",f"{archive} {pcap}",re.I):
                filename_hints.append({"family":family,"archive":archive,"pcap":pcap,
                  "family_records_in_capture":count,"hint":"filename_only_unverified"})
    report={"schema_version":1,"purpose":"Evidence-ranked family/type discovery; no unproven semantic labels.",
      "counts":{"event40_node_records":len(nodes),"event123_lifecycle_records":len(mobs),
        "knowledge_entity_observations":len(entities),"learned_pattern_signatures":len(learned),
        "autonomous_pattern_signatures":len(autonomous),"protocol_evidence_records":len(protocol),
        "distinct_event40_families":len(families),"distinct_event123_mobile_type_ids":len(mob_groups)},
      "family_profiles":family_profiles,"mobile_type_profiles":mob_profiles,
      "knowledge_schema_inventory":schema,"knowledge_type_and_family_field_values":type_fields,
      "weak_filename_context_hints":filename_hints,
      "guardrails":["Runtime IDs are scoped by archive and PCAP/session.",
        "Event 39 static observations are not Event 40 harvestable nodes.",
        "Event 123 lifecycle rows are not all proven hostile mobs.",
        "Do not infer a family name from numeric family ID alone.",
        "Do not join by numeric ID alone; require same capture/session and time/coordinate evidence.",
        "Do not convert filename hints into confirmed labels.","Unknown values are retained rather than dropped."]}
    out_json=DB/"albion-entity-discovery-report-2026-11-03.json"
    out_md=ANALYSIS/"albion-entity-discovery-report-2026-11-03.md"
    out_json.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    lines=["# Albion entity discovery — evidence-ranked reconnaissance","",
      "Profiles all Event 40 families, Event 123 mobile types, and the schema/value vocabulary of merged Knowledge records. Numeric IDs are not assigned names without independent evidence.","",
      "## Coverage",f"- Event 40 node records: **{len(nodes)}**",f"- Event 123 lifecycle records: **{len(mobs)}**",
      f"- Knowledge entity records: **{len(entities)}**",f"- Learned patterns: **{len(learned)}**; autonomous patterns: **{len(autonomous)}**; protocol evidence: **{len(protocol)}**","",
      "## Event 40 family profiles","| Family | Records | Mobile Type IDs | Tiers | Enchantment values | Interpretation |","|---:|---:|---|---|---|---|"]
    for p in family_profiles:
        lines.append("| "+p["family"]+" | "+str(p["event40_records"])+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["mobile_type_ids"].items())+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["tiers"].items())+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["enchantment_raw_values"].items())+" | "+p["interpretation"]+" |")
    lines+=["","## Event 123 mobile type profiles","| Mobile Type ID | Lifecycle records | Movement samples | Health-event records | Leave observed |","|---:|---:|---:|---:|---:|"]
    for p in mob_profiles:
        lines.append(f"| {p['mobile_type_id']} | {p['lifecycle_records']} | {p['movement_observations']} | {p['health_observation_records']} | {p['leave_observed_records']} |")
    lines+=["","## Knowledge schema reconnaissance",
      "The JSON report records exact fields and common values for type, family, tier, enchantment, classification, resource, name, labels and IDs. Field-name similarity alone is not proof.",
      "","## Weak filename hints"]
    lines += [f"- Family {r['family']} — {r['archive']} / {r['pcap']} ({r['family_records_in_capture']} rows; filename hint only)" for r in filename_hints]
    lines += ["","## Safety rules"]+[f"- {x}" for x in report["guardrails"]]
    out_md.write_text("\n".join(lines)+"\n",encoding="utf-8")
    print(json.dumps(report["counts"],ensure_ascii=False,sort_keys=True))
if __name__=="__main__": main()
