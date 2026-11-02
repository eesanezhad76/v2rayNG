import json, pathlib, tempfile, unittest, zipfile, subprocess, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
SCRIPT=ROOT/"research/tools/ingest_capture.py"
class IngestSmokeTest(unittest.TestCase):
    def test_safe_extract_rejects_zip_slip(self):
        sys.path.insert(0,str(SCRIPT.parent))
        import ingest_capture
        with tempfile.TemporaryDirectory() as d:
            zpath=pathlib.Path(d)/"bad.zip"
            with zipfile.ZipFile(zpath,"w") as z: z.writestr("../escape.txt","bad")
            with zipfile.ZipFile(zpath) as z:
                with self.assertRaises(ValueError): ingest_capture.safe_extract(z,pathlib.Path(d)/"out")
    def test_event40_deduplicates_runtime_identity_and_exact_event46_join(self):
        with tempfile.TemporaryDirectory() as d:
            d=pathlib.Path(d); src=d/"sample.jsonl"
            rows=[
              {"time_ms":1,"photon_event_analysis":[{"event_code":40,"resources":[{"id":77,"type_id":29,"tier":5,"x":1,"y":2,"amount":0,"enchantment_raw":0}]}]},
              {"time_ms":2,"photon_event_analysis":[{"event_code":40,"resources":[{"id":77,"type_id":29,"tier":5,"x":1,"y":2,"amount":0,"enchantment_raw":0}]}]},
              {"time_ms":3,"photon_event_analysis":[{"event_code":46,"state_update":{"id":77,"amount_or_state":1,"enchantment_raw":0}}]},
              {"time_ms":4,"photon_event_analysis":[{"event_code":46,"state_update":{"id":88,"amount_or_state":0,"enchantment_raw":0}}]}
            ]
            src.write_text("\n".join(json.dumps(x) for x in rows),encoding="utf-8")
            # Exercise the same public CLI while redirecting the output root to a temporary tree.
            old=SCRIPT.read_text(encoding="utf-8")
            temp_script=d/"ingest_capture.py"
            temp_script.write_text(old.replace('ROOT = pathlib.Path(__file__).resolve().parents[2]',f'ROOT = pathlib.Path(r"{d}")'),encoding="utf-8")
            subprocess.run([sys.executable,str(temp_script),str(src)],check=True,capture_output=True,text=True)
            out=json.loads((d/"research/database/sample.json").read_text())
            self.assertEqual(out["counts"]["unique_event40_nodes"],1)
            self.assertEqual(out["nodes"][0]["entity_id"],77)
            self.assertEqual([s["amount_or_state"] for s in out["nodes"][0]["state_timeline"]],[1])
            self.assertEqual(out["state_only_entities"][0]["entity_id"],88)
if __name__=="__main__": unittest.main()
