# NEXUS OFFLINE — Local AI Feasibility (2026-10-03)

**Status: LOCAL AI MANAGEMENT IMPLEMENTED; ACTUAL ON-DEVICE INFERENCE = NOT EXECUTED.** The Android app now has a LiteRT-LM 0.17.1 CPU adapter, SAF import, bounded container inspection, private model store, compatibility checks, load/unload/delete, and a real-runtime-only Test Inference action. No weights are bundled, no model is installed on an Android device, and there is no fake response or cloud fallback. Keep the no-model screen state exactly **`LOCAL AI — MODEL NOT INSTALLED`**. See [LOCAL_AI_ARCHITECTURE_MM.md](LOCAL_AI_ARCHITECTURE_MM.md) for the implementation and comparison.

## Runtime and device fit

The resolved LiteRT-LM Android 0.17.1 AAR declares `minSdk 24` and includes `arm64-v8a` and `x86_64` native libraries; both APKs package those libraries. This verifies the artifact metadata and build packaging, not native operation across Android API levels or device vendors. The candidate ARM64 path uses `Backend.CPU()`; GPU/NPU setup is not required. Runtime package presence, ABI, structural file inspection, RAM estimates, and free storage are separate manager facts; only native loading and a real generation can verify inference compatibility.

Model loading runs on a background executor and output is forwarded from the runtime's actual streamed callback. Cancellation fences later callbacks and closes the active runtime/conversation in source, but neither actual generation nor cancellation has been exercised on a device. The host has no connected ADB phone and `/dev/kvm` is absent. No model weights are installed locally for this app.

## Candidate model and budget

The recommended first ARM64 trial artifact is **LiteRT Community `Qwen3-0.6B.litertlm`**, dynamically quantized INT8 weights with floating-point KV cache. The [converted model card](https://huggingface.co/litert-community/Qwen3-0.6B) specifies a 4,096-token context and a rounded size of 586 MB. The repository object is **614,236,160 bytes**, SHA-256 **`555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4`**. App-specific Qwen metadata is exposed only when that full object hash matches; a `.litertlm` filename alone does not identify architecture, context or license.

The model card reports CPU peak private footprints around 2,697–2,699 MB and example decode rates of 13.02 tokens/second and 9.32 tokens/second on two named Android phones. Those rows used LiteRT-LM v0.13.1 and the card's benchmark setup, not this app or runtime 0.17.1, so they are planning references rather than guarantees or hard minimums. For device trials, a **6 GB ARM64 phone and about 1.5 GB free storage** are conservative starting recommendations, not vendor requirements. The app compares an approximately 2.7 GB estimate for this exact hash against live available RAM; other models use a conservative file-size estimate. KV-cache/context, operating-system pressure, backend and device affect actual memory.

The importer accepts user-selected files up to 8 GiB, checks private free storage before/during copy, hashes the full contents, writes a content-addressed copy to app-private storage and removes incomplete imports. Model weights in that private directory are **not encrypted at rest by this model manager**; the existing encrypted chat/attachment storage is unchanged. The Android builds contain no model weights and declare no `INTERNET` permission.

## Licensing and distribution

The converted model card, upstream [Qwen3-0.6B card](https://huggingface.co/Qwen/Qwen3-0.6B), and LiteRT-LM upstream repository identify Apache-2.0. Keep weights separate from the APK. Before distributing a converted file, confirm its exact provenance and notices and review tokenizer, chat template, conversion scripts and all accompanying assets; preserve required license/attribution text. The app's hash-gated facts do not prove provenance for other imported files.

## Verification gate

All file/parser/store/runtime-contract tests pass on the host. The parser fixture is a synthetic structural test file and **is not an inference-ready model**. No Android device was found by `adb devices`; `/dev/kvm` is absent. Therefore model import on-device, native load, English/Burmese prompts, streamed output, cancellation, unload/reload, process restart, offline operation, RAM, speed, thermals and corrupted-model recovery all remain **NOT EXECUTED**. Do not claim Local AI inference is available until a compatible ARM64 phone loads the exact model and returns real runtime output with networking disabled.
