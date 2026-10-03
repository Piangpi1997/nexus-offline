# NEXUS OFFLINE — Test Report (2026-10-03)

## Android clean build and tests

From the repository `android/` directory, the requested command completed with exit code 0:

```sh
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

**BUILD SUCCESSFUL.** The final JUnit XML contains **64 tests, 0 failures, 0 errors, 0 skipped** across 9 suites. All 53 methods in the archived baseline remain present; 11 tests were added. The added checks cover malformed/empty/truncated/oversized imports, size mismatch and fatal stream cleanup, unsupported architecture status through import, corrupted display metadata, available-RAM preflight, and a fake-runtime lifecycle sequence for cancellation, stale callbacks, overlapping generation, reload, unload, import/delete while loaded, and adapter recreation.

The structural `.litertlm` fixtures and injected fake runtime do **not** contain a real model and never call the LiteRT-LM native engine. They verify deterministic state and cleanup behavior only; no inference result is claimed.

## Build diagnostics

`lintDebug` and `lintRelease` each report **0 errors and 1 warning**: the `GradleDependency` advisory that `androidx.core:core-ktx` 1.19.1 is available while this project remains on 1.16.0. The advisory was not suppressed and the dependency was not upgraded just to silence it.

The pinned Gradle 8.7 wrapper and R8 9.1.31 override were retained. Kotlin-metadata/R8 warnings remain at **0**. Two non-blocking R8 notices say that AGP's `ClassFileProviderFactory$OrderedClassFileResourceProvider` does not support async parsing; the messages concern the Android Gradle Plugin's provider capability, not an app source defect. Release minification and assembly still pass, and neither notice was suppressed. Gradle also prints a forward-looking minimum-version advisory and an SDK XML v4 tooling notice; existing Android API deprecation warnings remain.

## Local AI security and lifecycle findings

LiteRT-LM 0.17.1's Android Kotlin text-generation API accepts a plaintext `modelPath`. A lower-level C API accepts a raw file descriptor, but the active Kotlin integration does not expose it and neither API provides an authenticated decrypt-on-read filesystem. The project therefore does **not** claim AES-GCM model-weight encryption or a model-specific Android Keystore key: **MODEL AT-REST ENCRYPTION — NOT IMPLEMENTED**. Imported weights remain in app-private files, copied in bounded 64 KiB chunks. The unauthenticated `.name` file is only a sanitized display label; the content-derived SHA-256 identifier is not a publisher signature. See `LOCAL_AI_ARCHITECTURE_MM.md` for the API constraints and storage decision; raw host-specific audit files are not included in the public repository.

The adapter now exposes an injectable runtime boundary to test cancellation, stale-callback fencing, active-generation unload/close, reload, loaded-model delete protection, and recreation without invoking native inference. `MainActivity` closes the runtime on destruction and responds to current Android UI-hidden/background trim callbacks off the UI thread. These are host/source-level checks; Activity callbacks, native mapping release, Android Keystore, SAF, clear-data, and real generation have not been exercised on a device.

## Host and device results

The host browser and mock checks test web assets and simulated Nearby behavior, not Android WebView or radio operation. Raw local test logs are intentionally excluded from the public repository. The canonical `app.js`, `i18n.js`, `index.html`, and `styles.css` are byte-identical to the Android WebView assets.

ADB reports no attached authorized devices and `/dev/kvm` is absent. Both device helpers were run; each stopped at its device-count gate before installation or pairing. Android install/launch/UI, QR camera, Keystore runtime, Native Nearby, two-phone and ten-phone tests are **NOT EXECUTED**. The current Local AI UI status remains **`LOCAL AI — MODEL NOT INSTALLED`**, and **LOCAL AI REAL INFERENCE = NOT EXECUTED**.

## Public source-only distribution

APK files are not included in this repository. Build locally using the Gradle wrapper and instructions in `README.md`. No model weights, signing keys, generated build outputs, or raw local test logs are published. The fresh Debug APK identity used for the UI validation is documented in `UI_VALIDATION_REPORT_MM.md` only as a SHA-256 reference; it is not distributed here.
