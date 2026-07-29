# Model Setup: converting SD 1.5 / InstructPix2Pix to ONNX

The app's inference engine (`ai/inference/OnnxInferenceEngine.kt`) loads
`.onnx` graphs via ONNX Runtime Mobile. Stable Diffusion 1.5 and
InstructPix2Pix are distributed by their authors as PyTorch / SafeTensors
checkpoints, so they need a **one-time conversion** on a development
machine (not on the phone) before the app can use them.

This step needs a machine with Python, PyTorch, and ideally a GPU — it
can't be done inside the Android app itself, and it can't be done in this
sandbox either (no GPU, no PyTorch, and downloading multi-GB checkpoints is
outside what's reachable from here). Run the following on your own machine.

## 1. Install the conversion toolchain

```bash
python -m venv sd-onnx-env
source sd-onnx-env/bin/activate
pip install "optimum[exporters]" diffusers transformers onnx onnxruntime torch --upgrade
```

## 2. Export Stable Diffusion 1.5 to ONNX

```bash
optimum-cli export onnx \
  --model runwayml/stable-diffusion-v1-5 \
  --task stable-diffusion \
  sd15-onnx/
```

This produces a directory containing separate graphs for the text encoder,
UNet, VAE encoder, and VAE decoder — matching the component split this app
expects (`SD15_TEXT_ENCODER`, `SD15_UNET`, `SD15_VAE_ENCODER`,
`SD15_VAE_DECODER` in `domain/model/AIModelType.kt`).

## 3. Export InstructPix2Pix to ONNX

```bash
optimum-cli export onnx \
  --model timbrooks/instruct-pix2pix \
  --task stable-diffusion \
  ip2p-onnx/
```

Only the UNet from this export is used (`ip2p-onnx/unet/model.onnx`) — it
takes an 8-channel input (4 latent + 4 image-conditioning channels), which
is why `InstructPix2PixPipeline.kt` concatenates `latents` and
`imageLatents` before calling the UNet.

## 4. (Recommended) Convert to FP16 for mobile

FP16 roughly halves file size and RAM/VRAM usage with minimal quality loss,
matching the app's default `Precision.FP16` setting:

```bash
python -m onnxruntime.transformers.optimizer \
  --input sd15-onnx/unet/model.onnx \
  --output sd15-onnx/unet/model_fp16.onnx \
  --float16
# repeat for text_encoder, vae_encoder, vae_decoder, and the ip2p unet
```

## 5. Export the tokenizer

The CLIP tokenizer used by `ai/inference/TextTokenizer.kt` needs two plain
files placed alongside the models: `tokenizer_vocab.json` (a `{token:
id}` JSON map) and `tokenizer_merges.txt` (BPE merge ranks, one pair per
line). Both ship inside any standard SD 1.5 checkpoint's `tokenizer/`
folder (`vocab.json` and `merges.txt`) — just rename them to match.

## 6. Host the files and wire up the catalog

Upload the resulting `.onnx` (and tokenizer) files to HTTPS storage you
control (a private S3/GCS bucket, or a GitHub Release for smaller files —
note GitHub caps release assets at 2GB, so the ~1.7GB FP16 UNet fits but
plan accordingly).

Then, for each entry in `ai/download/ModelCatalog.kt`, update:

```kotlin
downloadUrl = "https://your-host.example.com/sd15/unet_fp16.onnx",
sha256 = "<run `shasum -a 256 unet_fp16.onnx` and paste the result>",
sizeBytes = <file size in bytes>,
```

The app verifies this checksum after every download
(`ModelDownloadWorker` / `ModelRepository.verifyModel`), so an incorrect
hash will cause downloads to be rejected — always paste the real one.

## Expected sizes (FP16)

| Component | Approx. size |
|---|---|
| Tokenizer | ~2 MB |
| Text encoder | ~250 MB |
| SD 1.5 UNet | ~1.7 GB |
| VAE encoder | ~70 MB |
| VAE decoder | ~100 MB |
| InstructPix2Pix UNet | ~1.7 GB |

Total install footprint: roughly **3.8 GB** for both pipelines combined,
which is why the target device spec (12GB RAM, presumably 128GB+ storage)
matters, and why the Model Manager lets users install only what they need
(e.g. skip the plain SD 1.5 UNet if you only care about photo editing).
