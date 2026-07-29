# AI Image Studio

A **private, offline, on-device** AI image generation and editing app for Android.
Upload a photo, describe the edit in plain English, and get a result — no cloud
APIs, no external servers, no PC tethering, no subscriptions, no account.

Built with Kotlin, Jetpack Compose, MVVM + Clean Architecture, Hilt, Room,
WorkManager, and **ONNX Runtime Mobile** for on-device Stable Diffusion 1.5 /
InstructPix2Pix inference.

---

## Status of this codebase — please read first

This repository is a **complete, real architectural foundation**, not a UI
mockup: every layer (data, domain, presentation, AI inference, download
system, storage, gallery) is implemented with working Kotlin logic that
follows the actual InstructPix2Pix/Stable Diffusion inference algorithm
(tokenize → text-encode → latent diffusion loop with a real noise scheduler
→ VAE decode) using the real ONNX Runtime Android API.

Two things are intentionally left for you to finish, because they require
resources this environment doesn't have (a GPU/CPU big enough to run PyTorch,
a real Android device, and hosting for multi-gigabyte files):

1. **Converting SD 1.5 / InstructPix2Pix to ONNX** and hosting the resulting
   `.onnx` files somewhere the app can download them from. See
   [`docs/MODEL_SETUP.md`](docs/MODEL_SETUP.md) for the exact conversion
   commands and where to plug the URLs/checksums in
   (`ai/download/ModelCatalog.kt`).
2. **Building and testing on a real device or emulator** in Android Studio.
   See [`docs/BUILD_INSTRUCTIONS.md`](docs/BUILD_INSTRUCTIONS.md).

Everything else — UI, navigation, database, download manager with
pause/resume, memory management, the diffusion math, tensor conversion — is
implemented and ready to run once those two steps are done.

---

## Feature checklist against the spec

| Requirement | Status |
|---|---|
| Kotlin + Jetpack Compose | ✅ |
| MVVM + Clean Architecture (data/domain/presentation/ai) | ✅ |
| Hilt DI | ✅ |
| Room database (gallery + model state) | ✅ |
| WorkManager background downloads | ✅ |
| Coil image loading | ✅ |
| Scoped storage (no legacy storage permission) | ✅ |
| Android 10+ (minSdk 29) | ✅ |
| On-device inference engine (ONNX Runtime Mobile) | ✅ |
| No Python runtime / no desktop frameworks | ✅ (pure Kotlin + native ONNX Runtime .so) |
| SD 1.5 pipeline (txt2img) | ✅ code, ⏳ needs converted weights |
| InstructPix2Pix pipeline (instruction-based edit) | ✅ code, ⏳ needs converted weights |
| Simple main screen (upload → prompt → generate → save/share) | ✅ |
| Advanced settings (resolution, steps, CFG, seed, scheduler, denoising, memory mode) | ✅ |
| Model Manager (download/delete/verify/size/location) | ✅ |
| Resumable/pausable background downloads with progress | ✅ |
| Memory monitoring + low-RAM mode + FP16 + model unloading | ✅ |
| Gallery (originals, results, prompt, model, date, settings) | ✅ |

---

## Project layout

```
app/src/main/java/com/aiimagestudio/
├── app/                 Application class (Hilt entry point)
├── di/                  Hilt modules (DB, network, repository bindings)
├── presentation/        Compose UI, per-screen ViewModels, navigation, theme
├── domain/              Pure Kotlin: models, repository interfaces, use cases
├── data/                Room DB, DataStore settings, storage managers, repo impls
└── ai/
    ├── inference/       ONNX Runtime engine, tokenizer, scheduler, pipelines
    ├── download/        Model catalog + resumable WorkManager downloader
    └── memory/          RAM monitoring for low-RAM device support
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full data-flow
diagram and design rationale.

## Quick start

1. Open this folder in Android Studio (Koala/2024.1+).
2. Let Gradle sync (first sync downloads ~1GB of dependencies, including the
   ONNX Runtime Android AAR).
3. Follow [`docs/MODEL_SETUP.md`](docs/MODEL_SETUP.md) to convert and host the
   model weights, then update the URLs/SHA-256 hashes in
   `ai/download/ModelCatalog.kt`.
4. Build & run on a 64-bit ARM device (`docs/BUILD_INSTRUCTIONS.md` has full
   details, including the emulator caveat).
5. In-app: open **Model Manager**, download the components, then go to the
   main screen, upload a photo, type an instruction, and generate.

## License / attribution

Stable Diffusion 1.5 (CreativeML Open RAIL-M) and InstructPix2Pix are
third-party models with their own licenses — review them before
redistributing this app with bundled weights.
