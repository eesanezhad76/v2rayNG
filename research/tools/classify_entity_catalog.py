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
    dictionary=json.loads((ROOT/"V2rayNG/app/src/main/assets/albion-mobile-type-dictionary.json").read_text(encoding="utf-8"))
    dict_entries=dictionary.get("entries",{})
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
            p["evidence_note"]="Independent marker evidence and TypeID dictionary agree: T3/T4/T5 Ore Elemental IDs 571/573/574; loot_type ORE_CRITTER; harvestable=true."
        elif family=="27":
            p["interpretation"]="unknown_family_with_user_confirmed_exception"
            p["evidence_note"]="Only entity 2788, T5.3 at (-379,-24), is user-confirmed; do not generalize."
        elif family=="20" and set(types)=={"482"}:
            p["interpretation"]="event40_typeid_conflict_with_mobile_dictionary"
            p["evidence_note"]="TypeID 482 maps to T1 Mountain Rabbit (harvestable=false), yet these rows were parsed from Event 40 harvestable records. Re-decode raw Event 40 fields; do not classify as resource."
        family_profiles.append(p)
    mob_groups=collections.defaultdict(list)
    for r in mobs: mob_groups[str(r.get("mobile_type_id") or "UNKNOWN")].append(r)
    mob_profiles=[]; crosswalk=[]
    for type_id,rows in sorted(mob_groups.items(),key=lambda x:(-len(x[1]),x[0])):
        entry=dict_entries.get(str(type_id))
        p={"mobile_type_id":type_id,"lifecycle_records":len(rows),
           "enchantment_values":counter(rows,"enchantment"),"archives":counter(rows,"archive"),
           "distinct_pcaps":len({(r.get("archive"),r.get("pcap")) for r in rows}),
           "movement_observations":sum(int(r.get("movement_observations") or 0) for r in rows),
           "health_observation_records":sum(int(r.get("health_observations") or 0) for r in rows),
           "leave_observed_records":sum(1 for r in rows if r.get("left_after_seen")=="true"),
           "dictionary_match":bool(entry)}
        if entry:
            for key in ("unique_name","display_name","tier","category","localization_tag","max_health","avatar","danger","loot_type","loot_tier","is_harvestable"):
                p[key]=entry.get(key)
        else:
            p["warning"]="No dictionary entry; retain as unresolved."
        mob_profiles.append(p)
        crosswalk.append({"mobile_type_id":type_id,"pcap_lifecycle_records":len(rows),
          "display_name":(entry or {}).get("display_name",""),"unique_name":(entry or {}).get("unique_name",""),
          "tier":(entry or {}).get("tier",""),"category":(entry or {}).get("category",""),
          "danger":(entry or {}).get("danger",""),"loot_type":(entry or {}).get("loot_type",""),
          "loot_tier":(entry or {}).get("loot_tier",""),"is_harvestable":(entry or {}).get("is_harvestable",""),
          "dictionary_match":bool(entry)})
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
                filename_hints.append({"family":family,"archive":archive,"pcap":pcap,"family_records_in_capture":count,"hint":"filename_only_unverified"})
    mapped=sum(1 for p in crosswalk if p["dictionary_match"])
    report={"schema_version":2,"purpose":"Evidence-ranked family/type discovery; no unproven semantic labels.",
      "counts":{"event40_node_records":len(nodes),"event123_lifecycle_records":len(mobs),
        "knowledge_entity_observations":len(entities),"learned_pattern_signatures":len(learned),
        "autonomous_pattern_signatures":len(autonomous),"protocol_evidence_records":len(protocol),
        "distinct_event40_families":len(families),"distinct_event123_mobile_type_ids":len(mob_groups)},
      "mobile_type_dictionary_source":dictionary.get("source",{}),
      "mobile_type_dictionary_coverage":{"pcap_distinct_type_ids":len(mob_groups),"mapped_type_ids":mapped,
        "unmapped_type_ids":[p["mobile_type_id"] for p in crosswalk if not p["dictionary_match"]]},
      "family_profiles":family_profiles,"mobile_type_profiles":mob_profiles,"mobile_type_crosswalk":crosswalk,
      "knowledge_schema_inventory":schema,"knowledge_type_and_family_field_values":type_fields,
      "weak_filename_context_hints":filename_hints,
      "guardrails":["Runtime IDs are scoped by archive and PCAP/session.",
        "Event 39 static observations are not Event 40 harvestable nodes.",
        "Event 123 lifecycle rows are not all proven hostile mobs.",
        "Do not infer a family name from numeric family ID alone.",
        "Do not join by numeric ID alone; require same capture/session and time/coordinate evidence.",
        "Do not convert filename hints into confirmed labels.","Unknown values are retained rather than dropped."]}
    (DB/"albion-entity-discovery-report-2026-11-03.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    with (DB/"albion-mobile-type-crosswalk-2026-11-03.csv").open("w",encoding="utf-8",newline="") as f:
        fields=["mobile_type_id","pcap_lifecycle_records","display_name","unique_name","tier","category","danger","loot_type","loot_tier","is_harvestable","dictionary_match"]
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(crosswalk)
    lines=["# Albion entity discovery — evidence-ranked reconnaissance","",
      "Profiles every Event 40 family, Event 123 mobile type, and merged Knowledge schema. Numeric IDs are not assigned names without independent evidence.","",
      "## Coverage",f"- Event 40 node records: **{len(nodes)}**",f"- Event 123 lifecycle records: **{len(mobs)}**",
      f"- Knowledge entity records: **{len(entities)}**",f"- Learned patterns: **{len(learned)}**; autonomous patterns: **{len(autonomous)}**; protocol evidence: **{len(protocol)}**",
      f"- Mobile TypeID dictionary coverage: **{mapped}/{len(mob_groups)}** PCAP types mapped",
      f"- Dictionary source: {dictionary.get('source',{}).get('repository','unknown')} at {dictionary.get('source',{}).get('source_commit','unknown')}","",
      "## Event 40 family profiles","| Family | Records | Mobile Type IDs | Tiers | Enchantment values | Interpretation |","|---:|---:|---|---|---|---|"]
    for p in family_profiles:
        lines.append("| "+p["family"]+" | "+str(p["event40_records"])+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["mobile_type_ids"].items())+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["tiers"].items())+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["enchantment_raw_values"].items())+" | "+p["interpretation"]+" |")
    lines+=["","## Event 123 mobile TypeID crosswalk","| Type ID | Name | Tier | Category | Loot type | Harvestable | PCAP records |","|---:|---|---:|---|---|---|---:|"]
    for p in mob_profiles:
        lines.append(f"| {p['mobile_type_id']} | {p.get('display_name','UNKNOWN')} | {p.get('tier','')} | {p.get('category','')} | {p.get('loot_type','')} | {p.get('is_harvestable','')} | {p['lifecycle_records']} |")
    lines+=["","## Key conflict requiring raw-event re-decode",
      "- Family 20 has 7 Event 40 rows with TypeID 482, but the game TypeID dictionary maps 482 to T1 Mountain Rabbit, with is_harvestable=false. This is a protocol-field/semantic conflict, not a confirmed resource family.",
      "- Family 29 is consistent: TypeIDs 571/573/574 resolve to Ore Elemental T3/T4/T5, loot ORE_CRITTER, harvestable=true.",
      "- Families 7, 14, 27 and 28 have no decoded TypeID in Event 40; their names remain unresolved.",
      "- Family 27 has only one user-confirmed exception: entity 2788, T5.3 at (-379,-24).",
      "","## Knowledge schema reconnaissance",
      "The JSON report records exact fields and common values for type, family, tier, enchantment, classification, resource, name, labels and IDs. Field-name similarity alone is not proof.",
      "","## Weak filename hints"]
    lines += [f"- Family {r['family']} — {r['archive']} / {r['pcap']} ({r['family_records_in_capture']} rows; filename hint only)" for r in filename_hints]
    lines += ["","## Safety rules"]+[f"- {x}" for x in report["guardrails"]]
    (ANALYSIS/"albion-entity-discovery-report-2026-11-03.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print(json.dumps({"counts":report["counts"],"mobile_type_dictionary_coverage":report["mobile_type_dictionary_coverage"]},ensure_ascii=False,sort_keys=True))
if __name__=="__main__": main()
