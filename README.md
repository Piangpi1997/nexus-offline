# NEXUS OFFLINE — Private AI, Nearby Chat, no Internet required

NEXUS OFFLINE is an offline-first Android app and static web client for private, on-device AI, nearby peer-to-peer chat, encrypted local state and attachments, QR pairing, diagnostics, and settings. The offline core does not require a cloud account or backend. Android packages the web client inside a local WebView and does not request the `INTERNET` permission. Nearby Connections still depends on compatible Google Play Services, user-granted permissions, and functioning Bluetooth/BLE/Wi-Fi radios; platform-service behavior is separate from the app's source-level network access.

## Current source

The Android app bundles its HTML, JavaScript, styles, images, and localization assets. The Local AI manager supports user-selected model import and a LiteRT-LM CPU runtime, but no model weights are included or installed. Android model storage protection is **PARTIAL**: committed model containers are encrypted, while an app-private temporary plaintext file is still needed during import and while the native runtime loads the model. Actual on-device inference has **NOT BEEN EXECUTED**. See [LOCAL_AI_ARCHITECTURE_MM.md](LOCAL_AI_ARCHITECTURE_MM.md) and [LOCAL_AI_FEASIBILITY_MM.md](LOCAL_AI_FEASIBILITY_MM.md).

The UI supports Burmese and English from **Settings → Language**. Android stores app state through its Keystore-backed store; browser preview data remains session-memory-only except for the language preference. OpenAI, Todoist, Google Calendar, and Notion are optional/future integrations and are not part of the current offline core.

## Current validation (2026-10-04)

The latest clean Android build completed successfully. **All 81/81 Android JVM unit tests pass across 10 suites**; the tests run on the host and do not establish Android Keystore behavior, Android runtime behavior, or real model inference. The six Chromium/browser-host regression suites also pass (**6/6**). The QR modal visual fixture passes **8/8 viewport scenarios**, covering Burmese and English layouts, expired state, larger text, and a short viewport.

These are automated host/browser results only. The QR screenshots show a deliberately **non-scannable mock pattern**, not a QR payload or a physical Android screen. A real camera scan, two-device QR pairing, Nearby radio connection, Android Keystore runtime, and on-device Local AI inference have **NOT BEEN TESTED**. **PRODUCTION VERIFICATION PENDING.** See [TEST_REPORT.md](TEST_REPORT.md), [QR_MODAL_UI_VALIDATION_MM.md](QR_MODAL_UI_VALIDATION_MM.md), and [PHYSICAL_DEVICE_TEST_MM.md](PHYSICAL_DEVICE_TEST_MM.md).

## Build and tests

From `android/`, build and run the JVM tests and lint checks with the official Gradle wrapper:

```sh
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

For browser checks, serve the project root on port 4174 for the UI, localization, and attachment suites, and on port 4173 for the mock Nearby suite. Set `NEXUS_TEST_URL` to the corresponding local URL when running each test. These mock/browser checks are not Android-device acceptance tests.

## Source-only repository

This repository contains application source, Android and Gradle configuration, tests, documentation, device-test helpers, and safe browser UI screenshots. APK/AAB packages, model weights, signing keys, secrets, local machine configuration, build outputs/caches, generated archives, raw build logs, and private device logs are intentionally excluded. Build artifacts locally with the command above; this repository does not distribute APKs or source ZIPs.