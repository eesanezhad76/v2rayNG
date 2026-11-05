#!/usr/bin/env python3
"""Losslessly shard oversized generated research outputs for Git storage."""
import argparse
import hashlib
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_OUTPUT = ROOT / "research/analysis/derived-shards"
DEFAULT_MANIFEST = ROOT / "research/analysis/derived-output-shards-manifest.json"


def digest_file(path, block_size=1024 * 1024):
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as stream:
        while True:
            block = stream.read(block_size)
            if not block:
                break
            size += len(block)
            digest.update(block)
    return size, digest.hexdigest()


def split_one(source, root, output_dir, chunk_size):
    source = pathlib.Path(source)
    relative = source.resolve().relative_to(root.resolve()).as_posix()
    size, sha256 = digest_file(source)
    shard_dir = output_dir / sha256
    shard_dir.mkdir(parents=True, exist_ok=True)
    parts = []
    with source.open("rb") as stream:
        index = 1
        while True:
            block = stream.read(chunk_size)
            if not block:
                break
            name = f"part-{index:04d}.bin"
            target = shard_dir / name
            if target.exists():
                existing_size, existing_hash = digest_file(target)
                if existing_size != len(block) or existing_hash != hashlib.sha256(block).hexdigest():
                    raise RuntimeError(f"Existing shard differs: {target}")
            else:
                temporary = target.with_suffix(target.suffix + ".tmp")
                with temporary.open("wb") as out:
                    out.write(block)
                temporary.replace(target)
            parts.append({
                "path": target.relative_to(root).as_posix(),
                "bytes": len(block),
                "sha256": hashlib.sha256(block).hexdigest(),
            })
            index += 1
    return {
        "source_path": relative,
        "bytes": size,
        "sha256": sha256,
        "shard_directory": shard_dir.relative_to(root).as_posix(),
        "parts": parts,
    }


def verify_sources(manifest, root):
    checked = 0
    for source in manifest.get("sources", []):
        digest = hashlib.sha256()
        size = 0
        for part in source.get("parts", []):
            path = root / part["path"]
            part_size, part_hash = digest_file(path)
            if part_size != part["bytes"] or part_hash != part["sha256"]:
                raise RuntimeError(f"Shard checksum mismatch: {path}")
            with path.open("rb") as stream:
                while True:
                    block = stream.read(1024 * 1024)
                    if not block:
                        break
                    digest.update(block)
                    size += len(block)
        if size != source["bytes"] or digest.hexdigest() != source["sha256"]:
            raise RuntimeError(f"Reassembled checksum mismatch: {source['source_path']}")
        checked += 1
    return checked


def restore_sources(manifest, root):
    restored = 0
    for source in manifest.get("sources", []):
        target = root / source["source_path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("wb") as output:
            for part in source.get("parts", []):
                with (root / part["path"]).open("rb") as stream:
                    while True:
                        block = stream.read(1024 * 1024)
                        if not block:
                            break
                        output.write(block)
        size, sha256 = digest_file(target)
        if size != source["bytes"] or sha256 != source["sha256"]:
            raise RuntimeError(f"Restored file checksum mismatch: {source['source_path']}")
        restored += 1
    return restored


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=ROOT)
    parser.add_argument("--max-mib", type=int, default=45)
    parser.add_argument("--threshold-mib", type=int, default=50)
    parser.add_argument("--output-dir", type=pathlib.Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--manifest", type=pathlib.Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--restore", action="store_true", help="reassemble original files from the manifest")
    args = parser.parse_args()
    root = args.root.resolve()
    manifest_path = args.manifest.resolve()
    if args.restore:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        restored = restore_sources(manifest, root)
        print(json.dumps({"restored_sources": restored, "manifest": manifest_path.relative_to(root).as_posix()}, sort_keys=True))
        return
    output_dir = args.output_dir.resolve()
    chunk_size = args.max_mib * 1024 * 1024
    threshold = args.threshold_mib * 1024 * 1024
    if chunk_size <= 0 or threshold <= 0:
        parser.error("chunk and threshold sizes must be positive")

    candidates = []
    for base in (root / "research/analysis", root / "research/database"):
        if not base.exists():
            continue
        for path in sorted(base.rglob("*")):
            if not path.is_file() or output_dir in path.resolve().parents:
                continue
            if path.stat().st_size > threshold:
                candidates.append(path)

    by_hash = {}
    sources = []
    for path in candidates:
        size, sha256 = digest_file(path)
        if sha256 not in by_hash:
            by_hash[sha256] = split_one(path, root, output_dir, chunk_size)
        record = dict(by_hash[sha256])
        record["source_path"] = path.resolve().relative_to(root).as_posix()
        record["bytes"] = size
        sources.append(record)

    manifest = {
        "schema_version": 1,
        "algorithm": "sha256",
        "chunk_size_bytes": chunk_size,
        "threshold_bytes": threshold,
        "sources": sources,
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    checked = verify_sources(manifest, root)
    print(json.dumps({
        "oversized_sources": len(sources),
        "unique_shard_sets": len(by_hash),
        "shard_parts": sum(len(x["parts"]) for x in by_hash.values()),
        "verified_sources": checked,
        "manifest": manifest_path.relative_to(root).as_posix(),
    }, sort_keys=True))


if __name__ == "__main__":
    main()
