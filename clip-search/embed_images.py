"""Embed every photo in the bucket with CLIP's image encoder and write a search index.

Output (index dir, default ./index):
  embeddings.f32  N x D float32, little-endian, row-major, each row L2-normalised
  index.json      {"model", "dim", "count", "keys": [...]}  - row i of embeddings belongs to keys[i]
  thumbnails/     <key>.jpg, 360px on the shorter side - served by the search page, so it never waits on S3

Incremental: keys already in an existing index are reused, so re-runs only embed new photos.
Downloads are cached as 360px JPEGs in ./.image-cache, so re-runs never hit S3 twice.

  .venv/bin/python embed_images.py --bucket ak-photo --region eu-central-1 [--limit 100]
"""
import argparse
import io
import json
import shutil
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import boto3
import numpy as np
import torch
from PIL import Image, ImageOps
from transformers import CLIPImageProcessor, CLIPModel

from common import MODEL_ID, MODEL_NAME, image_features, l2_normalize

IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png")
THUMB_SIDE = 360  # CLIP looks at 224x224; 360 also gives sharp 180px grid tiles on 2x screens
CACHE_DIR = Path(__file__).parent / ".image-cache" / str(THUMB_SIDE)
BATCH_SIZE = 32


def list_image_keys(s3, bucket):
    keys = []
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket):
        keys += [o["Key"] for o in page.get("Contents", []) if o["Key"].lower().endswith(IMAGE_EXTENSIONS)]
    return sorted(keys)


def cached_thumbnail(s3, bucket, key):
    path = CACHE_DIR / (key + ".jpg")
    if not path.exists():
        body = s3.get_object(Bucket=bucket, Key=key)["Body"].read()
        img = ImageOps.exif_transpose(Image.open(io.BytesIO(body))).convert("RGB")
        scale = THUMB_SIDE / min(img.size)
        if scale < 1:
            img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path, "JPEG", quality=80, optimize=True, progressive=True)
    return path


def load_thumbnail(s3, bucket, key):
    return Image.open(cached_thumbnail(s3, bucket, key)).convert("RGB")


def export_thumbnails(s3, bucket, keys, index_dir, workers):
    """Copy the cached thumbnail of every indexed photo into the index (downloads any that are missing)."""
    out = index_dir / "thumbnails"
    missing = [k for k in keys if not (out / (k + ".jpg")).exists()]
    if not missing:
        return []
    print(f"writing {len(missing)} thumbnails")

    def export(key):
        src = cached_thumbnail(s3, bucket, key)
        dst = out / (key + ".jpg")
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)

    with ThreadPoolExecutor(workers) as pool:
        results = list(pool.map(lambda k: _safe(export, k), missing))
    return [(k, r) for k, r in zip(missing, results) if isinstance(r, Exception)]


def load_index(index_dir):
    meta_path = index_dir / "index.json"
    if not meta_path.exists():
        return {}
    meta = json.loads(meta_path.read_text())
    if meta["model"] != MODEL_NAME:
        print(f"existing index uses {meta['model']}, rebuilding for {MODEL_NAME}")
        return {}
    vectors = np.fromfile(index_dir / "embeddings.f32", dtype="<f4").reshape(meta["count"], meta["dim"])
    return dict(zip(meta["keys"], vectors))


def save_index(index_dir, vectors_by_key):
    index_dir.mkdir(parents=True, exist_ok=True)
    keys = sorted(vectors_by_key)
    matrix = np.stack([vectors_by_key[k] for k in keys]).astype("<f4")
    matrix.tofile(index_dir / "embeddings.f32")
    (index_dir / "index.json").write_text(json.dumps(
        {"model": MODEL_NAME, "dim": int(matrix.shape[1]), "count": len(keys), "keys": keys}))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bucket", default="ak-photo")
    parser.add_argument("--region", default="eu-central-1")
    parser.add_argument("--index-dir", default=str(Path(__file__).parent / "index"))
    parser.add_argument("--limit", type=int, help="only embed the first N new photos (for a quick trial)")
    parser.add_argument("--workers", type=int, default=16, help="parallel S3 downloads")
    args = parser.parse_args()
    index_dir = Path(args.index_dir)

    s3 = boto3.client("s3", region_name=args.region)
    keys = list_image_keys(s3, args.bucket)
    vectors = {k: v for k, v in load_index(index_dir).items() if k in set(keys)}  # drop deleted photos
    todo = [k for k in keys if k not in vectors][: args.limit]
    print(f"{len(keys)} images in bucket, {len(vectors)} already indexed, embedding {len(todo)}")
    if not todo:
        save_index(index_dir, vectors)
        report(export_thumbnails(s3, args.bucket, sorted(vectors), index_dir, args.workers))
        return

    device = "mps" if torch.backends.mps.is_available() else "cpu"
    model = CLIPModel.from_pretrained(MODEL_ID).eval().to(device)
    processor = CLIPImageProcessor.from_pretrained(MODEL_ID)

    started, failed = time.time(), []
    with ThreadPoolExecutor(args.workers) as pool:
        for start in range(0, len(todo), BATCH_SIZE):
            batch_keys = todo[start:start + BATCH_SIZE]
            images, ok_keys = [], []
            for key, result in zip(batch_keys, pool.map(lambda k: _safe(load_thumbnail, s3, args.bucket, k),
                                                        batch_keys)):
                if isinstance(result, Exception):
                    failed.append((key, result))
                else:
                    images.append(result)
                    ok_keys.append(key)
            if images:
                pixels = processor(images=images, return_tensors="pt")["pixel_values"].to(device)
                with torch.no_grad():
                    embeds = l2_normalize(image_features(model, pixels)).cpu().numpy()
                vectors.update(zip(ok_keys, embeds))
            done = start + len(batch_keys)
            print(f"\r  {done}/{len(todo)}  ({done / (time.time() - started):.1f} img/s)", end="", flush=True)
            if done % (BATCH_SIZE * 10) == 0:
                save_index(index_dir, vectors)  # checkpoint, so an interrupted run can resume
    print()
    save_index(index_dir, vectors)
    print(f"index: {len(vectors)} photos -> {index_dir}")
    failed += export_thumbnails(s3, args.bucket, sorted(vectors), index_dir, args.workers)
    report(failed)


def report(failed):
    for key, err in failed:
        print(f"  skipped {key}: {err}", file=sys.stderr)


def _safe(fn, *args):
    try:
        return fn(*args)
    except Exception as e:  # corrupt or mislabelled files shouldn't stop the run
        return e


if __name__ == "__main__":
    main()
