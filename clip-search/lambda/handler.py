"""AWS Lambda: turn a search phrase into a CLIP text embedding.

Request:  {"text": "dog on the beach"}   or   {"warmup": true}
Response: {"embedding": [D floats, L2-normalised], "model": "ViT-B-32/openai"}

The model is loaded once per execution environment (module import = cold start)
and reused by every warm invocation.
"""
import os
import time
from pathlib import Path

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

MODEL_NAME = "ViT-B-32/openai"
MAX_TEXT_LENGTH = 300
MODEL_DIR = Path(os.environ.get("MODEL_DIR", Path(__file__).parent / "model"))

_started = time.perf_counter()
_tokenizer = Tokenizer.from_file(str(MODEL_DIR / "tokenizer.json"))
_options = ort.SessionOptions()
_options.intra_op_num_threads = int(os.environ.get("ORT_THREADS", "0"))  # 0 = one per vCPU
_session = ort.InferenceSession(str(MODEL_DIR / "clip_text_fp32.onnx"), _options,
                                providers=["CPUExecutionProvider"])
print(f"model loaded in {(time.perf_counter() - _started) * 1000:.0f} ms")


def encode(text: str) -> list:
    enc = _tokenizer.encode(text)
    vec = _session.run(None, {"input_ids": np.array([enc.ids], dtype=np.int64),
                              "attention_mask": np.array([enc.attention_mask], dtype=np.int64)})[0][0]
    return (vec / np.linalg.norm(vec)).astype(np.float32).tolist()


def handler(event, context=None):
    if event.get("warmup"):
        return {"warm": True, "model": MODEL_NAME}
    text = (event.get("text") or "").strip()
    if not text:
        return {"error": "missing 'text'"}
    return {"embedding": encode(text[:MAX_TEXT_LENGTH]), "model": MODEL_NAME}
