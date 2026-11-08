# clip-search

Text-to-image search for photo-loader. The `/search` page in photo-webapp finds photos by a
description ("dog on the beach") using CLIP ViT-B/32 (English queries).

```
offline (your Mac)                       AWS                         EC2 (photo-webapp)
embed_images.py ── index/ ──── scp ─────────────────────────────▶ ~/clip-index/
  embeddings + thumbnails                                          loaded into memory (4 MB)
export_text_encoder.py ── lambda/model/ ─▶ clip-text-encoder ◀──── invoked per new query
  fp32 ONNX text encoder                    Lambda (arm64)          → exact cosine search
```

Everything in photo-webapp is behind `clip.search.enabled` (off by default).

## Setup

```bash
cd clip-search
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python torch transformers onnx onnxruntime onnxscript tokenizers numpy pillow boto3
```

## Build the index (re-run after adding photos; it is incremental)

```bash
.venv/bin/python embed_images.py --bucket ak-photo --region eu-central-1
```

Writes `index/` (`embeddings.f32`, `index.json`, `thumbnails/`, about 85 MB). Uses your local AWS
credentials and caches 360px copies in `.image-cache/`, so re-runs only download new photos.

Ship it to the server (it must **not** go into the photo bucket: the slideshow would show the files):

```bash
COPYFILE_DISABLE=1 tar czf clip-index.tgz --no-xattrs -C index .   # no macOS ._ files
scp -i photo.pem clip-index.tgz ec2-user@<host>:~
ssh -i photo.pem ec2-user@<host> 'mkdir -p ~/clip-index && tar xzf clip-index.tgz -C ~/clip-index'
# then restart photo-webapp so it reloads the index
```

## Text-encoder Lambda

```bash
.venv/bin/python export_text_encoder.py   # -> lambda/model/, verifies against PyTorch
sam build && sam deploy --guided           # first time; later just `sam build && sam deploy`
```

The image index and the Lambda must use the same model: the Lambda reports its model name and
photo-webapp refuses to search if it differs from `index.json`.

## Try it locally

```bash
.venv/bin/python local_lambda.py        # Lambda Invoke API stand-in on :3001
# from the repo root:
java -jar photo-webapp/target/photo-webapp-1.0-exec.jar --spring.profiles.active=local
# open http://localhost:8080/search  (admin / admin)
```

Test the real container image without AWS:

```bash
docker build --platform linux/arm64 -t clip-text-encoder:local lambda
docker run -p 9000:8080 clip-text-encoder:local
curl -XPOST localhost:9000/2015-03-31/functions/function/invocations -d '{"text":"dog"}'
```

## photo-webapp configuration

```properties
clip.search.enabled=true
clip.index.dir=/home/ec2-user/clip-index
clip.lambda.function-name=clip-text-encoder
clip.lambda.region=eu-west-1          # defaults to aws.region
#clip.lambda.endpoint=http://localhost:3001   # local testing only
```

photo-webapp's AWS identity needs `lambda:InvokeFunction` on the function.
