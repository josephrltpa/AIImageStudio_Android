# Build Instructions

## Don't have Android Studio? Two options that don't require it

### Option A — Build in the cloud with GitHub Actions (recommended, zero local install)

This repo includes `.github/workflows/build.yml`. Push this project to a
GitHub repository and it builds automatically:

1. Create a new (can be private) GitHub repo and push this project to it:
   ```bash
   cd AIImageStudio
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/AIImageStudio.git
   git push -u origin main
   ```
2. Go to your repo on GitHub → the **Actions** tab. You'll see a "Build
   APK" run in progress (it starts automatically on push).
3. When it finishes (a few minutes), click into the run → under
   **Artifacts**, download `AIImageStudio-debug-apk`. That's a zip
   containing `app-debug.apk`.
4. Transfer that APK to your phone (e.g. email it to yourself, or use
   Google Drive) and install it — you'll need to enable "Install from
   unknown sources" for whichever app you use to open it, since it isn't
   from the Play Store.

This builds the app but doesn't let you interactively debug on-device;
for that you'd want Option B or Android Studio eventually.

### Option B — Command-line build on your own machine (no Android Studio, just SDK tools)

You only need the Android **command-line tools**, not the full IDE:

1. Install JDK 17 (e.g. `sudo apt install openjdk-17-jdk` on Linux, or
   download Temurin 17 for macOS/Windows).
2. Download the "Command line tools only" package from
   https://developer.android.com/studio#command-tools and unzip it.
3. Use `sdkmanager` (inside the unzipped `cmdline-tools/bin/`) to install
   what's needed:
   ```bash
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
4. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to point at the SDK
   directory you installed into, and add `platform-tools` to your `PATH`.
5. From the project root:
   ```bash
   gradle wrapper          # generates gradlew/gradlew.bat + wrapper jar (one-time, needs a local Gradle install or Android Studio once)
   ./gradlew assembleDebug
   ```
   The resulting APK lands at `app/build/outputs/apk/debug/app-debug.apk`.
   Install it on a connected device with:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

If step 5's `gradle wrapper` command is inconvenient (it needs *some*
Gradle binary to bootstrap from — a system package manager install like
`brew install gradle` / `sdk install gradle` via SDKMAN works fine), Option
A avoids this entirely since GitHub's runner provisions Gradle for you.

---

## Full Android Studio path (if you get access to it later)

## Requirements

- Android Studio Koala (2024.1) or newer
- JDK 17 (bundled with recent Android Studio)
- Android SDK Platform 34, NDK (side-by-side) — installed automatically on
  first sync if missing
- A physical **arm64-v8a** Android 10+ device with at least 6–8GB free
  storage for models is strongly recommended for real testing. The x86_64
  emulator can build and run the UI/download-manager flow, but ONNX
  Runtime's NNAPI acceleration and realistic performance can only be judged
  on real ARM hardware.

## Steps

1. `git clone`/copy this project, then **File → Open** it in Android Studio.
2. Wait for Gradle sync. First sync pulls the ONNX Runtime Android AAR
   (~30–50MB) plus the usual AndroidX/Compose/Hilt dependencies.
3. If Hilt/KSP annotation processing errors appear on first build, do
   **Build → Clean Project** then **Build → Rebuild Project** — this is a
   normal one-time cache-priming step for Hilt.
4. Select a **64-bit** run target (the app's `abiFilters` is restricted to
   `arm64-v8a` per the target-device spec in `app/build.gradle.kts` —
   widen this if you also need to support 32-bit or x86 devices/emulators).
5. Run. The app launches to the Model Manager-less main screen with no
   models installed yet — go to the toolbar's storage icon to open
   **Model Manager** and download components (see `MODEL_SETUP.md` first,
   since the catalog's URLs are placeholders until you host real weight
   files).
6. Once at least the InstructPix2Pix bundle (`tokenizer`, `text encoder`,
   `ip2p unet`, `vae encoder`, `vae decoder`) shows installed, return to the
   main screen, upload a photo, type an instruction, and tap Generate.

## Signing a release build

```bash
./gradlew assembleRelease
```

Add your own signing config (`signingConfigs { }` block) in
`app/build.gradle.kts` before shipping — the project currently only
defines `debug`/`release` build types without a signing config, which is
intentional since signing keys shouldn't be checked into source control.

## Known first-build caveats

- **Hilt + KSP + kapt together**: this project uses `kapt` for Hilt/Room
  (broadest compatibility) and `ksp` is registered at the root level for
  future migration; if you prefer, migrate Room/Hilt to their KSP compiler
  artifacts for faster builds.
- **Large APK/AAB size**: because ONNX Runtime ships native `.so` binaries
  and models are downloaded post-install (not bundled), the APK itself
  stays small (~30–50MB); model storage happens in app-private internal
  storage after first launch.
- **`onnxruntime-android` version drift**: pin the AAR version
  (`1.18.0` as of writing) — check
  `https://mvnrepository.com/artifact/com.microsoft.onnxruntime/onnxruntime-android`
  for newer releases if you hit compatibility issues with a specific ONNX
  opset used during export.
