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
        number=int(family) if family.lstrip("-").isdigit() else None
        resource_type=("WOOD" if 0<=number<=5 else "ROCK" if 6<=number<=10 else "FIBER" if 11<=number<=15 else "HIDE" if 16<=number<=22 else "ORE" if 23<=number<=27 else None) if number is not None else None
        p={"type_number":family,"event40_records":len(rows),"mobile_type_ids":types,"resource_type_from_type_number":resource_type,
           "tiers":counter(rows,"tiers"),"enchantment_raw_values":counter(rows,"enchantment_raw_values"),
           "archives":counter(rows,"archive"),"distinct_pcaps":len({(r.get("archive"),r.get("pcap")) for r in rows}),
           "interpretation":"unclassified",
           "evidence_note":"Capture-scoped Event 40 observations; no name inferred from family number alone."}
        if resource_type:
            p["interpretation"]="type_number_resource_mapping_candidate"
            p["evidence_note"]="Type number falls in the public HarvestablesDatabase range mapping; retain as evidence-ranked until marker/state validation."
        if family=="29":
            p["resource_type_from_type_number"]="ORE_RESOURCE_CREATURE"
            p["interpretation"]="confirmed_ore_resource_creature"
            p["evidence_note"]="TypeIDs 571/573/574 resolve to harvestable Ore Elemental T3/T4/T5; loot_type ORE_CRITTER. Living Ore Elemental is distinct from static Ore type numbers 23-27."
        elif family=="27":
            p["interpretation"]="ore_type_number_candidate"
            p["evidence_note"]="Type number 27 maps to ORE in HarvestablesDatabase; entity 2788 remains a separately user-confirmed individual target."
        elif family=="20" and set(types)=={"482"}:
            p["interpretation"]="hide_type_number_with_nonharvestable_mobile_fallback"
            p["evidence_note"]="Type number 20 maps to HIDE. TypeID 482 is Mountain Rabbit with is_harvestable=false, so OpenRadar semantics fall back to type_number rather than overriding it."
        elif family=="28":
            p["interpretation"]="unresolved_type_number_outside_static_range"
            p["evidence_note"]="Type number 28 is outside the public static-resource mapping 0-27 and is not covered by the confirmed living-resource mapping; retain unresolved."
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
    legacy_path=DB/"albion-1793682590317.csv"
    legacy_rows=read_csv(legacy_path) if legacy_path.exists() else []
    current_capture=[r for r in nodes if r.get("archive")=="AlbionRadar.zip" and r.get("pcap")=="albion-1793682590317.pcap"]
    legacy_by_id={str(r.get("entity_id")):str(r.get("type_number")) for r in legacy_rows}
    validation_pairs=[r for r in current_capture if str(r.get("entity_id")) in legacy_by_id]
    validation_mismatches=[{"entity_id":r["entity_id"],"pcap_type_number":r.get("type_numbers"),"legacy_type_number":legacy_by_id[str(r["entity_id"])]} for r in validation_pairs if str(r.get("type_numbers"))!=legacy_by_id[str(r["entity_id"])]]
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
                filename_hints.append({"type_number":family,"archive":archive,"pcap":pcap,"type_number_records_in_capture":count,"hint":"filename_only_unverified"})
    def split_values(value):
        return [x.strip() for x in str(value or "").split("|") if x.strip()]
    static_type_map={**{i:"Wood" for i in range(0,6)},**{i:"Rock" for i in range(6,11)},
                     **{i:"Fiber" for i in range(11,16)},**{i:"Hide" for i in range(16,23)},
                     **{i:"Ore" for i in range(23,28)}}
    enriched_nodes=[]; resource_type_counts=collections.Counter(); unresolved_node_count=0
    for row in nodes:
        nums=split_values(row.get("type_numbers"))
        number=int(nums[0]) if len(nums)==1 and nums[0].lstrip("-").isdigit() else None
        tiers=split_values(row.get("tiers")); mobile_ids=split_values(row.get("mobile_type_ids"))
        positive_ids=[int(x) for x in mobile_ids if x.lstrip("-").isdigit() and int(x)>0]
        mobile_id=positive_ids[0] if len(set(positive_ids))==1 and positive_ids else None
        mobile_entry=dict_entries.get(str(mobile_id)) if mobile_id is not None else None
        mobile_harvestable=bool(mobile_entry and mobile_entry.get("is_harvestable") is True)
        static_type=static_type_map.get(number)
        if mobile_harvestable:
            resource_type="Ore" if str(mobile_entry.get("loot_type","")).startswith("ORE") else str(mobile_entry.get("loot_type","")).title()
            display_name=mobile_entry.get("display_name") or resource_type
            mapping_status="PACKET_CONFIRMED_LIVING_RESOURCE"
            evidence_note="Direct Event 40 mobile TypeID is marked harvestable in the game mob dictionary; it overrides the static type number."
            effective_tier=mobile_entry.get("tier")
        elif static_type is not None:
            resource_type=static_type
            display_name=static_type
            mapping_status="REFERENCE_STATIC_TYPE_NUMBER_PROVISIONAL"
            evidence_note="Static Event 40 type number maps through the reference HarvestablesDatabase ranges."
            effective_tier="|".join(tiers)
            if mobile_entry:
                evidence_note+=" Mobile TypeID "+str(mobile_id)+" is not harvestable; static type-number fallback retained."
        elif number==29:
            resource_type="Ore"
            display_name="Ore Elemental"
            mapping_status="UNVERIFIED_LIVING_RESOURCE_TYPE"
            evidence_note="Type number 29 is a capture-correlated Ore Elemental family; this row lacks a verified harvestable mobile TypeID."
            effective_tier="|".join(tiers)
        else:
            resource_type=""
            display_name=""
            mapping_status="UNRESOLVED"
            evidence_note="No verified static type-number or harvestable mobile-TypeID mapping."
            effective_tier="|".join(tiers)
        if not resource_type: unresolved_node_count+=1
        else: resource_type_counts[resource_type]+=1
        tier_conflict=bool(mobile_harvestable and tiers and str(mobile_entry.get("tier")) not in tiers)
        user_confirmed=(row.get("archive")=="AlbionRadar.zip" and row.get("pcap")=="albion-1793682590317.pcap" and str(row.get("entity_id"))=="2788")
        coords=[x.strip() for x in str(row.get("coordinates","")).split(",") if x.strip()]
        enriched_nodes.append({
          "archive":row.get("archive",""),"pcap":row.get("pcap",""),"entity_id":row.get("entity_id",""),
          "type_number":number if number is not None else "|".join(nums),"resource_type":resource_type,
          "resource_display_name":display_name,"mapping_status":mapping_status,"evidence_note":evidence_note,
          "tier": "|".join(tiers),"effective_tier":effective_tier,
          "enchantment_raw_values":row.get("enchantment_raw_values",""),
          "mobile_type_id":mobile_id if mobile_id is not None else "|".join(mobile_ids),
          "mobile_type_name":(mobile_entry or {}).get("display_name",""),
          "mobile_type_unique_name":(mobile_entry or {}).get("unique_name",""),
          "mobile_type_category":(mobile_entry or {}).get("category",""),
          "mobile_type_loot_type":(mobile_entry or {}).get("loot_type",""),
          "mobile_type_is_harvestable":mobile_harvestable if mobile_entry else "",
          "tier_conflict":tier_conflict,"x":coords[0] if len(coords)>0 else "",
          "y":coords[1] if len(coords)>1 else "","first_seen_ms":row.get("first_seen_ms",""),
          "last_seen_ms":row.get("last_seen_ms",""),"observation_count":row.get("observation_count",""),
          "state_event_count":row.get("state_event_count",""),"state_values":row.get("state_values",""),
          "user_confirmed_target":user_confirmed
        })
    node_catalog_path=DB/"albion-resource-node-catalog-2026-11-03.csv"
    with node_catalog_path.open("w",encoding="utf-8",newline="") as f:
        fields=["archive","pcap","entity_id","type_number","resource_type","resource_display_name","mapping_status",
          "evidence_note","tier","effective_tier","enchantment_raw_values","mobile_type_id","mobile_type_name",
          "mobile_type_unique_name","mobile_type_category","mobile_type_loot_type","mobile_type_is_harvestable",
          "tier_conflict","x","y","first_seen_ms","last_seen_ms","observation_count","state_event_count",
          "state_values","user_confirmed_target"]
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(enriched_nodes)
    mapped=sum(1 for p in crosswalk if p["dictionary_match"])
    resource_display_counts=collections.Counter(r["resource_display_name"] for r in enriched_nodes if r["resource_display_name"])
    mapping_status_counts=collections.Counter(r["mapping_status"] for r in enriched_nodes)
    report={"schema_version":2,"purpose":"Evidence-ranked Event 40 Type Number and Mobile TypeID discovery; no unproven semantic labels.",
      "counts":{"event40_node_records":len(nodes),"event123_lifecycle_records":len(mobs),
        "knowledge_entity_observations":len(entities),"learned_pattern_signatures":len(learned),
        "autonomous_pattern_signatures":len(autonomous),"protocol_evidence_records":len(protocol),
        "distinct_event40_type_numbers":len(families),"distinct_event123_mobile_type_ids":len(mob_groups)},
      "mobile_type_dictionary_source":dictionary.get("source",{}),
      "type_number_mapping_source":{"repository":"Nouuu/Albion-Online-OpenRadar","files":["web/scripts/data/HarvestablesDatabase.js","web/scripts/handlers/HarvestablesHandler.js","web/scripts/data/MobsDatabase.js"],"mapping":"0-5 WOOD; 6-10 ROCK; 11-15 FIBER; 16-22 HIDE; 23-27 ORE; living resources override only when MobsDatabase.isHarvestable(TypeID) is true."},
      "type_number_cross_validation":{"capture":"AlbionRadar.zip/albion-1793682590317.pcap","matched_records":len(validation_pairs),"mismatches":validation_mismatches},
      "mobile_type_dictionary_coverage":{"pcap_distinct_type_ids":len(mob_groups),"mapped_type_ids":mapped,
        "unmapped_type_ids":[p["mobile_type_id"] for p in crosswalk if not p["dictionary_match"]]},
      "type_number_profiles":family_profiles,"mobile_type_profiles":mob_profiles,"mobile_type_crosswalk":crosswalk,
      "resource_node_catalog":{"rows":len(enriched_nodes),"mapped_rows":len(enriched_nodes)-unresolved_node_count,"unresolved_rows":unresolved_node_count,"resource_type_counts":dict(sorted(resource_type_counts.items())),"resource_display_counts":dict(sorted(resource_display_counts.items())),"mapping_status_counts":dict(sorted(mapping_status_counts.items())),"file":"albion-resource-node-catalog-2026-11-03.csv"},
      "knowledge_schema_inventory":schema,"knowledge_type_and_family_field_values":type_fields,
      "weak_filename_context_hints":filename_hints,
      "guardrails":["Runtime IDs are scoped by archive and PCAP/session.",
        "Event 39 static observations are not Event 40 harvestable nodes.",
        "Event 123 lifecycle rows are not all proven hostile mobs.",
        "Do not confuse Event 40 Type Number with Mobile Type ID; unknown numbers remain unresolved.",
        "Do not join by numeric ID alone; require same capture/session and time/coordinate evidence.",
        "Do not convert filename hints into confirmed labels.","Unknown values are retained rather than dropped."]}
    (DB/"albion-entity-discovery-report-2026-11-03.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    with (DB/"albion-mobile-type-crosswalk-2026-11-03.csv").open("w",encoding="utf-8",newline="") as f:
        fields=["mobile_type_id","pcap_lifecycle_records","display_name","unique_name","tier","category","danger","loot_type","loot_tier","is_harvestable","dictionary_match"]
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader(); w.writerows(crosswalk)
    lines=["# Albion entity discovery — evidence-ranked reconnaissance","",
      "Profiles every Event 40 Type Number, Event 123 mobile type, and merged Knowledge schema. Numeric IDs are not assigned names without independent evidence.","",
      "## Coverage",f"- Event 40 node records: **{len(nodes)}**",f"- Event 123 lifecycle records: **{len(mobs)}**",
      f"- Knowledge entity records: **{len(entities)}**",f"- Learned patterns: **{len(learned)}**; autonomous patterns: **{len(autonomous)}**; protocol evidence: **{len(protocol)}**",
      f"- Mobile TypeID dictionary coverage: **{mapped}/{len(mob_groups)}** PCAP types mapped",
      f"- Dictionary source: {dictionary.get('source',{}).get('repository','unknown')} at {dictionary.get('source',{}).get('source_commit','unknown')}","",
      "## Event 40 type-number profiles","| Type number | Records | Resource mapping | Mobile Type IDs | Tiers | Interpretation |","|---:|---:|---|---|---|---|"]
    for p in family_profiles:
        lines.append("| "+p["type_number"]+" | "+str(p["event40_records"])+" | "+str(p.get("resource_type_from_type_number") or "UNRESOLVED")+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["mobile_type_ids"].items())+" | "+
          ", ".join(f"{k} ({v})" for k,v in p["tiers"].items())+" | "+p["interpretation"]+" |")
    lines+=["","## Event 123 mobile TypeID crosswalk","| Type ID | Name | Tier | Category | Loot type | Harvestable | PCAP records |","|---:|---|---:|---|---|---|---:|"]
    for p in mob_profiles:
        lines.append(f"| {p['mobile_type_id']} | {p.get('display_name','UNKNOWN')} | {p.get('tier','')} | {p.get('category','')} | {p.get('loot_type','')} | {p.get('is_harvestable','')} | {p['lifecycle_records']} |")
    lines+=["","## Enriched Event 40 node catalog",
      f"- Rows: {len(enriched_nodes)}; mapped: {len(enriched_nodes)-unresolved_node_count}; unresolved: {unresolved_node_count}",
      "- File: albion-resource-node-catalog-2026-11-03.csv",
      "- Resource display counts: "+", ".join(f"{k}={v}" for k,v in sorted(resource_display_counts.items())),
      "- Mapping status counts: "+", ".join(f"{k}={v}" for k,v in sorted(mapping_status_counts.items())),
      "","## Mapping caveats and unresolved Type Number",
      "- Type number 20 maps to HIDE. Its mobile TypeID 482 is Mountain Rabbit and is not harvestable, so the reference OpenRadar handler falls back to type number 20 rather than overriding it.",
      "- Type number 29 is a separate living Ore Elemental resource mapping: TypeIDs 571/573/574, loot ORE_CRITTER, harvestable=true.",
      "- Type numbers 7/14/20/27 map to ROCK/FIBER/HIDE/ORE through the public HarvestablesDatabase mapping; 28 remains unresolved.",
      "- Type-number cross-validation against the independent per-capture ingest for AlbionRadar.zip/albion-1793682590317.pcap: "+str(len(validation_pairs))+" matched rows; "+str(len(validation_mismatches))+" mismatches.",
      "- Entity 2788, T5.3 at (-379,-24), remains a user-confirmed individual target; do not generalize its confirmation.",
      "","## Knowledge schema reconnaissance",
      "The JSON report records exact fields and common values for type, family, tier, enchantment, classification, resource, name, labels and IDs. Field-name similarity alone is not proof.",
      "","## Weak filename hints"]
    lines += [f"- Type number {r['type_number']} — {r['archive']} / {r['pcap']} ({r['type_number_records_in_capture']} rows; filename hint only)" for r in filename_hints]
    lines += ["","## Safety rules"]+[f"- {x}" for x in report["guardrails"]]
    (ANALYSIS/"albion-entity-discovery-report-2026-11-03.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
    print(json.dumps({"counts":report["counts"],"mobile_type_dictionary_coverage":report["mobile_type_dictionary_coverage"]},ensure_ascii=False,sort_keys=True))
if __name__=="__main__": main()
