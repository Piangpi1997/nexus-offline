# NEXUS OFFLINE — Final Validation (2026-10-03)

**PRODUCTION VERIFICATION PENDING. MODEL AT-REST ENCRYPTION — NOT IMPLEMENTED.** LiteRT-LM 0.17.1 requires a plaintext model path through its active Android Kotlin interface. No Android Keystore AES-GCM key, encrypted model container, pretend encryption, or plaintext staging workaround was introduced; imported model weights remain in app-private storage and are not encrypted by this model manager.

## Model persistence and Qwen candidate

The resolved Android `EngineConfig` accepts `modelPath: String`; `Engine.initialize()` forwards that path to native engine creation, which opens the model through file-based `ModelAssets`. The Android Kotlin text-generation API has no stream, raw-FD, or virtual encrypted filesystem input. Although a lower-level C API has a raw file-descriptor entry point, it does not provide authenticated decrypt-on-read, and the Kotlin API in use does not expose it. Decrypting to a staged file would leave plaintext at rest; decrypting a model-sized file wholly into memory would violate the bounded-memory requirement. The supported conclusion is **app-private plaintext fallback only**.

The store copies in bounded 64 KiB chunks to a same-directory `.part` file, syncs the completed file, inspects it, then renames it to a content-derived model path. Display-label metadata uses a separate temporary file and rename. Failed imports remove partial data and a just-installed model if metadata installation fails; startup recovery removes incomplete `.part` files and orphaned label metadata. JVM tests cover those failure paths. The display label is not authenticated, the SHA-256 ID is not a publisher signature, and there is no decrypt path or decrypt-failure cleanup. Model deletion uses filesystem deletion, not forensic secure erase. `allowBackup=false` and backup/device-transfer exclusions are configured; system clear-data/uninstall should remove private model files, but this has not been exercised on a phone.

**Qwen3-0.6B is a candidate recommendation only, not a verified compatible model.** The published `Qwen3-0.6B.litertlm` reference is 614,236,160 bytes with SHA-256 `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4`, dynamic INT8 weights, floating-point KV cache, and a 4,096-token context. The [architecture note](LOCAL_AI_ARCHITECTURE_MM.md) gives the exact file-acquisition and checksum procedure and distinguishes it from a new `litert-torch export_hf` conversion. No model file was downloaded, converted, imported on-device, or loaded for inference.

## Test and build results

The final command completed with **BUILD SUCCESSFUL** using the checksum-pinned Gradle 8.7 wrapper and the retained R8 9.1.31 pin:

```sh
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

JUnit reports **64 tests, 0 failures, 0 errors, 0 skipped** across 9 suites. All **53 archived baseline methods remain present**; 11 tests were added for model import/inspection and fake-runtime lifecycle behavior. The fake runtime tests cover cancellation, stale callbacks, overlapping generation, reload/unload, delete/import while loaded, active-generation close races, and adapter recreation. They verify manager behavior without calling LiteRT-LM JNI or claiming real inference.

Both lint variants have zero errors and one `GradleDependency` advisory: `androidx.core:core-ktx` 1.19.1 is available while the project remains on 1.16.0. Kotlin metadata/R8 warnings are zero. Two non-blocking R8 notices say AGP's class-file resource provider does not support async parsing; release minification still succeeds. The build also emits the retained Gradle 8.7 forward-compatibility advisory, an SDK XML v4 tooling notice, and existing Android API deprecation warnings. None were suppressed. The four canonical WebView localization assets remain byte-identical to their Android copies.

## Final status by gate

| Gate | Status | Evidence / boundary |
|---|---|---|
| Encrypted attachment import | **HOST TESTS PASS** | Existing attachment encryption/import tests remain in the 64-test suite; Android Keystore/runtime validation is separate and not executed. |
| Local AI manager | **HOST TESTS PASS** | State/lifecycle and fake-runtime paths tested; no native inference claim. |
| Model import | **HOST STORE TESTS PASS; ANDROID PICKER NOT EXECUTED** | Valid, rejected, size/storage, interrupted/fatal, cancellation, metadata, and unknown-architecture import cases are covered on host. |
| Model at-rest protection | **NOT IMPLEMENTED** | App-private plaintext fallback only; no Keystore model key or authenticated encryption metadata. |
| Local AI real inference | **NOT EXECUTED** | No model was installed or loaded on an Android device; Qwen remains a candidate only. |
| Android install / UI | **NOT EXECUTED** | `adb devices -l` returned no attached devices; smoke helper stopped before installation. |
| Android Keystore runtime | **NOT EXECUTED** | No physical device; the model store intentionally has no Keystore encryption implementation. |
| QR camera | **NOT EXECUTED** | No physical Android camera test. |
| Native Nearby | **NOT EXECUTED** | Host mocks do not prove radio behavior. |
| Two-phone acceptance | **NOT EXECUTED** | No paired physical devices. |
| Ten-phone acceptance | **NOT EXECUTED** | No multi-device run. |
| Release signing | **UNSIGNED / NOT READY FOR DISTRIBUTION** | No production keystore or signing secrets were supplied; release APK assembly passed, but signature verification correctly fails. |

`/dev/kvm` is absent as well as attached ADB devices, so no emulator-based Android acceptance was possible. Keep **PRODUCTION VERIFICATION PENDING** until Android install/UI, Keystore behavior, a real offline Local AI run on a target ARM64 phone, QR and Nearby device flows, and required multi-phone tests have direct evidence.

## Public repository scope

The public repository contains the application source, tests, Gradle wrapper/configuration, documentation, device-test helpers, and the UI screenshots linked above. It intentionally excludes APKs (including the locally verified Debug APK), model weights, generated source ZIPs, build outputs/caches, signing material, secrets, and raw local audit/build logs. The latest locally verified Debug APK SHA-256 is recorded in `UI_VALIDATION_REPORT_MM.md`; it is not a downloadable artifact in this repository.

Full test details are in [TEST_REPORT.md](TEST_REPORT.md); lifecycle, conversion and model-storage decisions are summarized in [LOCAL_AI_ARCHITECTURE_MM.md](LOCAL_AI_ARCHITECTURE_MM.md). Raw host-specific logs and generated artifact files are intentionally not published.
