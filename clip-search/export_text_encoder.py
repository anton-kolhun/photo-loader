"""Export the CLIP text encoder to an fp32 ONNX model for the Lambda.

Why fp32 and not int8: a dynamically quantised int8 model is 4x smaller and matched PyTorch on a Mac,
but on Lambda's Graviton3 CPUs onnxruntime's int8 kernels drifted badly (cosine ~0.94 vs PyTorch,
only ~6 of the top-10 search results agreeing). fp32 is exact there, at ~240 MB.

Writes into lambda/model/:
  clip_text_fp32.onnx  - text encoder, input_ids/attention_mask [1, 77] -> embedding [1, D]
  tokenizer.json       - matching tokenizer for the `tokenizers` library (no torch needed)

Then checks that the ONNX model + standalone tokenizer reproduce the PyTorch embeddings.
"""
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from tokenizers import Tokenizer
from transformers import CLIPModel, CLIPTokenizerFast

from common import CONTEXT_LENGTH, MODEL_ID, text_features

OUT_DIR = Path(__file__).parent / "lambda" / "model"


class TextEncoder(torch.nn.Module):
    def __init__(self, model: CLIPModel):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        return text_features(self.model, input_ids, attention_mask)


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    model = CLIPModel.from_pretrained(MODEL_ID).eval()
    hf_tokenizer = CLIPTokenizerFast.from_pretrained(MODEL_ID)

    # standalone tokenizer.json, padded/truncated to CLIP's fixed context length
    tok = hf_tokenizer.backend_tokenizer
    pad_id = hf_tokenizer.pad_token_id
    tok.enable_truncation(CONTEXT_LENGTH)
    tok.enable_padding(length=CONTEXT_LENGTH, pad_id=pad_id, pad_token=hf_tokenizer.pad_token)
    tok.save(str(OUT_DIR / "tokenizer.json"))

    dummy = hf_tokenizer(["a photo of a dog"], padding="max_length", max_length=CONTEXT_LENGTH,
                         truncation=True, return_tensors="pt")
    model_path = OUT_DIR / "clip_text_fp32.onnx"
    torch.onnx.export(TextEncoder(model), (dummy["input_ids"], dummy["attention_mask"]), str(model_path),
                      input_names=["input_ids", "attention_mask"], output_names=["embedding"],
                      opset_version=17, dynamo=False)
    onnx.save(onnx.load(str(model_path)), str(model_path))  # fold any external-data files into one file

    verify(model, hf_tokenizer)


def verify(model, hf_tokenizer):
    queries = ["a dog on the beach", "Snow-covered mountains at sunset", "birthday cake with candles",
               "two kids playing football", "old black and white family photo", "дерево"]
    standalone = Tokenizer.from_file(str(OUT_DIR / "tokenizer.json"))
    session = ort.InferenceSession(str(OUT_DIR / "clip_text_fp32.onnx"))
    worst = 1.0
    for q in queries:
        ref = hf_tokenizer([q], padding="max_length", max_length=CONTEXT_LENGTH, truncation=True,
                           return_tensors="pt")
        enc = standalone.encode(q)
        assert enc.ids == ref["input_ids"][0].tolist(), f"tokenizer mismatch for {q!r}"
        with torch.no_grad():
            expected = text_features(model, ref["input_ids"], ref["attention_mask"])[0].numpy()
        got = session.run(None, {"input_ids": np.array([enc.ids], dtype=np.int64),
                                 "attention_mask": np.array([enc.attention_mask], dtype=np.int64)})[0][0]
        cos = float(np.dot(expected, got) / (np.linalg.norm(expected) * np.linalg.norm(got)))
        worst = min(worst, cos)
        print(f"  cos(torch, onnx) = {cos:.5f}  {q!r}")
    size_mb = (OUT_DIR / "clip_text_fp32.onnx").stat().st_size / 2**20
    print(f"model: {size_mb:.0f} MB, worst cosine similarity {worst:.4f}")
    assert worst > 0.9999, "ONNX model does not reproduce the PyTorch embeddings"


if __name__ == "__main__":
    main()
