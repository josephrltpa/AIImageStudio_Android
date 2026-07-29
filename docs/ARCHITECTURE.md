# Architecture

AI Image Studio follows **MVVM + Clean Architecture** with four layers,
matching the structure requested in the spec (`data/ domain/ presentation/ ai/`).

```
presentation/  →  domain/  ←  data/
                     ↑
                    ai/
```

- **`presentation`** (Compose UI + ViewModels) depends only on `domain`.
  It never imports Room, ONNX Runtime, or OkHttp directly.
- **`domain`** is pure Kotlin: data classes, repository *interfaces*, and
  use cases. It has zero Android or third-party dependencies, so it's
  trivially unit-testable.
- **`data`** implements the domain repository interfaces using Room
  (gallery + model metadata), DataStore (settings), and the storage
  managers (scoped-storage file I/O).
- **`ai`** is where the actual on-device intelligence lives: the ONNX
  Runtime session manager, the CLIP tokenizer, the diffusion noise
  scheduler, the two generation pipelines, and the resumable model
  downloader. `data/repository/InferenceRepositoryImpl` is the only bridge
  between `ai` and the rest of the app, so the inference engine could be
  swapped (e.g. ONNX Runtime → MNN) without touching UI or domain code.

## Generation data flow (InstructPix2Pix edit)

```
HomeScreen (Compose)
  → HomeViewModel.generate()
    → GenerateImageUseCase (domain)
      → InferenceRepositoryImpl (data)
        → InstructPix2PixPipeline (ai/inference)
          1. ImageTensorConverter: Bitmap -> NCHW float tensor
          2. OnnxInferenceEngine.runFloatOutput(SD15_VAE_ENCODER)  → image latents
          3. TextTokenizer.encode(instruction)                     → token ids
          4. OnnxInferenceEngine.runTextEncoder(...)                → text embedding
          5. DiffusionScheduler.initialNoise(...)                   → starting latents
          6. loop steps: OnnxInferenceEngine.runFloatOutput(INSTRUCT_PIX2PIX_UNET)
                          DiffusionScheduler.step(...)               → denoised latents
          7. OnnxInferenceEngine.runFloatOutput(SD15_VAE_DECODER)   → output tensor
          8. ImageTensorConverter: tensor -> Bitmap
        ← emits GenerationJob.{Preprocessing,Denoising,Decoding,Success}
      ← SaveGeneratedImageUseCase persists to Room + scoped storage
    ← HomeUiState updates (progress bar, result preview)
```

Every stage emits a `GenerationJob` sealed-class event so the UI can show
real step-by-step progress ("Generating (12/25)") instead of a spinner.

## Why ONNX Runtime Mobile

The spec allowed MNN, ONNX Runtime Mobile, or ncnn. ONNX Runtime Mobile was
chosen because:

- It has an official, maintained Android AAR
  (`com.microsoft.onnxruntime:onnxruntime-android`) with NNAPI execution
  provider support for GPU/NPU acceleration, falling back to optimized CPU
  (XNNPACK) automatically.
- The Hugging Face `diffusers` ecosystem has first-party ONNX export
  tooling (`optimum-cli export onnx`) for both SD 1.5 and InstructPix2Pix,
  making the PyTorch → on-device path well-trodden (see `MODEL_SETUP.md`).
- No Python runtime ships in the APK — conversion happens once, offline, on
  a dev machine; the app only ever loads static `.onnx` graphs.

## Memory management

`ai/memory/MemoryMonitor` reads `ActivityManager.MemoryInfo` and resolves
the user's `MemoryMode` preference (`AUTO`/`LOW_RAM`/`PERFORMANCE`) against
live conditions. `OnnxInferenceEngine.maybeUnloadForLowRam()` is called
after every pipeline stage; in `LOW_RAM` mode each `OrtSession` (text
encoder, UNet, VAE) is closed as soon as its stage finishes, trading speed
for a much lower peak memory footprint — important because the UNet alone
is ~3.4GB in FP32 (roughly half that in FP16).

## Download system

`ai/download/ModelDownloadWorker` is a Hilt-injected `CoroutineWorker` that
performs **HTTP Range-based resumable downloads**: progress is checkpointed
to Room after every 1% change, and a `.part` file on disk preserves
progress across pause/app-kill/reboot. `ai/download/DownloadManager` gives
the rest of the app simple start/pause/resume/cancel calls backed by
WorkManager's unique-work queue, so re-enqueuing a "resume" safely continues
rather than duplicating a job. SHA-256 verification runs before a `.part`
file is promoted to a usable model file.
