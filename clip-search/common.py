"""Shared settings for the offline scripts. The Lambda must use the same MODEL_ID."""
import torch

MODEL_ID = "openai/clip-vit-base-patch32"
MODEL_NAME = "ViT-B-32/openai"  # written into the index and returned by the Lambda; the app checks they match
CONTEXT_LENGTH = 77


def text_features(model, input_ids, attention_mask):
    """Projected text embedding; spelled out so it does not depend on the transformers version."""
    pooled = model.text_model(input_ids=input_ids, attention_mask=attention_mask).pooler_output
    return model.text_projection(pooled)


def image_features(model, pixel_values):
    pooled = model.vision_model(pixel_values=pixel_values).pooler_output
    return model.visual_projection(pooled)


def l2_normalize(x: torch.Tensor) -> torch.Tensor:
    return x / x.norm(dim=-1, keepdim=True)
