import json,pathlib,tempfile,unittest,zipfile,subprocess,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
SCRIPT=ROOT/"research/tools/ingest_capture.py"
class IngestTests(unittest.TestCase):
 def run_ingest(self,rows,d):
  src=d/"sample.jsonl";src.write_text("\n".join(json.dumps(x) for x in rows),encoding="utf-8")
  script=SCRIPT.read_text(encoding="utf-8")
  temp=d/"ingest_capture.py";temp.write_text(script.replace('ROOT=pathlib.Path(__file__).resolve().parents[2]',f'ROOT=pathlib.Path(r"{d}")'),encoding="utf-8")
  subprocess.run([sys.executable,str(temp),str(src)],check=True,capture_output=True,text=True)
  return json.loads((d/"research/database/sample.json").read_text(encoding="utf-8"))
 def test_safe_extract_rejects_zip_slip(self):
  sys.path.insert(0,str(SCRIPT.parent));import ingest_capture
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td);zpath=d/"bad.zip"
   with zipfile.ZipFile(zpath,"w") as z:z.writestr("../escape.txt","bad")
   with zipfile.ZipFile(zpath) as z:
    with self.assertRaises(ValueError):ingest_capture.safe_extract(z,d/"out")
 def test_actual_entities_schema_deduplicates_and_joins_exact_ids(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   rows=[
    {"time_ms":1,"entities":[{"kind":"harvestable","event_code":40,"entity_id":77,"mobile_type_id":574,"type_number":29,"tier":5,"enchant":0,"x":1,"y":2,"size":5}]},
    {"time_ms":2,"entities":[{"kind":"harvestable","event_code":40,"entity_id":77,"mobile_type_id":574,"type_number":29,"tier":5,"enchant":0,"x":1,"y":2,"size":5}]},
    {"time_ms":3,"entities":[{"kind":"resource_state","event_code":46,"entity_id":77,"size":4,"removed":False}]},
    {"time_ms":4,"entities":[{"kind":"resource_state","event_code":46,"entity_id":88,"size":0,"removed":False}]}
   ]
   out=self.run_ingest(rows,d)
   self.assertEqual(out["counts"]["unique_event40_nodes"],1)
   self.assertEqual(out["counts"]["event46_exact_joins"],1)
   self.assertEqual(out["counts"]["state_only_ids"],1)
   self.assertEqual(out["nodes"][0]["entity_id"],77)
   self.assertEqual(out["nodes"][0]["state_timeline"][0]["size"],4)
   self.assertEqual(out["state_only_entities"][0]["entity_id"],88)
 def test_legacy_event_analysis_schema_still_supported(self):
  with tempfile.TemporaryDirectory() as td:
   d=pathlib.Path(td)
   rows=[{"time_ms":1,"photon_event_analysis":[{"event_code":40,"resources":[{"id":7,"type_id":29,"tier":5,"x":2,"y":3,"amount":5,"enchantment_raw":0}]}]},{"time_ms":2,"photon_event_analysis":[{"event_code":46,"state_update":{"id":7,"amount_or_state":4,"enchantment_raw":0}}]}]
   out=self.run_ingest(rows,d)
   self.assertEqual(out["counts"]["unique_event40_nodes"],1)
   self.assertEqual(out["nodes"][0]["state_timeline"][0]["size"],4)
if __name__=="__main__":unittest.main()
