# NEXUS OFFLINE — Private AI, Nearby Chat, no Internet required

NEXUS OFFLINE is an offline-first Android app and static web client. Its core goal remains private, on-device AI; nearby peer-to-peer chat; encrypted local state and attachments; QR pairing; diagnostics; and settings. It does not require FastAPI, PostgreSQL, Alembic, OAuth, or a cloud account to run its offline core.

## What is in the current source

The Android app is a local WebView wrapper around bundled assets. It has routes for Home, Chats, AI, Nearby/Connections, Diagnostics, and Settings. Attachment and QR actions are part of the Nearby flow. Android state and received attachments have Keystore-backed AES-GCM storage code. The Local AI screen now includes SAF model import and a LiteRT-LM CPU model manager. **No weights are bundled or installed, and actual Android inference is `NOT EXECUTED`.** When no local model is present, the status is exactly `LOCAL AI — MODEL NOT INSTALLED`; no fake answer or cloud fallback is used. Runtime/model choice, format checks and verified limits are in [LOCAL_AI_ARCHITECTURE_MM.md](LOCAL_AI_ARCHITECTURE_MM.md) and [LOCAL_AI_FEASIBILITY_MM.md](LOCAL_AI_FEASIBILITY_MM.md).

The app supports **မြန်မာ and English** from **Settings → Language**. The selected language updates the current screen, navigation, accessibility names, and dynamic status/error messages immediately. Android stores the preference in the existing Keystore-protected app state; the browser preview stores only the language choice locally, while profile/chat data remains session-memory-only there. The localization bundle is bundled and cached for offline use, and unknown strings safely fall back to their source text.

The Android app declares no `INTERNET` permission. Its HTML, JavaScript, styles, images, and fonts are local; the Android WebView asset loader serves the bundled app origin and rejects off-origin WebView requests. The browser service worker uses the local app origin and may fetch a same-origin cache miss as a fallback, so the static browser build should still be tested with its network disabled when deployed outside Android.

Nearby Connections is designed for offline peer-to-peer exchange, but it is not independent of platform services: it requires a compatible Google Play Services runtime, user-granted Android permissions, and working Bluetooth/BLE/Wi-Fi radios. Google also documents that its Nearby SDK may collect performance and device metrics through Google Play services. This SDK-level behavior is separate from the app's own source-level network access; review it when making privacy statements or choosing a non-GMS distribution target. See Google's [Nearby Connections overview](https://developers.google.com/nearby/connections/overview) and [Android setup guide](https://developers.google.com/nearby/connections/android/get-started).

## Optional / Future Online Integrations

OpenAI, Todoist, Google Calendar, and Notion are **optional / future online integrations**. Their clients, OAuth flows, credentials, and server-side token handling are not part of the current core source. Their absence is not a blocker for the offline app, and no provider credential is required to build or use its core. Any future online integration should be separately designed and must not replace local inference or nearby/offline behavior.

## Current validation

The final clean build passes **64/64 JVM tests** and both lint variants with zero errors. Four browser/host/mock suites pass, but no Android phone was attached and actual Local AI inference, camera, Keystore and physical Nearby tests remain **NOT EXECUTED**. See [FINAL_VALIDATION_MM.md](FINAL_VALIDATION_MM.md), [TEST_REPORT.md](TEST_REPORT.md), and [PHYSICAL_DEVICE_TEST_MM.md](PHYSICAL_DEVICE_TEST_MM.md) for test boundaries and the source-only publication scope. The 10-phone template is [TEN_PHONE_ACCEPTANCE_MM.md](TEN_PHONE_ACCEPTANCE_MM.md); it requires a physical two-phone PASS first.

Run the locale regression with its local HTTP server on port 4174 as described in `tests/i18n-navigation.py`. The mock Nearby integration script expects port 4173; start it with `python3 -m http.server 4173 --bind 127.0.0.1` and then run `python3 tests/mock-nearby-integration.py`. These are browser/mocked tests, not device acceptance.

For a physical run, use `tools/android-device-smoke-test.sh android/app/build/outputs/apk/debug/app-debug.apk` after exactly one authorized phone is visible to ADB; the script checks API ≥24, reports APK identity and captures launch evidence but does not approve permissions or simulate Nearby/AI. `tools/two-phone-test-preflight.sh` is diagnostics-only and requires two authorized devices. Review `PHYSICAL_DEVICE_TEST_MM.md` before running either helper.

The Android project uses the official checksum-pinned Gradle 8.7 wrapper and AGP 8.6.1. A documented plugin-management override pins published R8 9.1.31 to read Kotlin 2.4 metadata; the wrapper and app toolchain versions remain unchanged. From the `android/` directory, run:

```sh
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

The app's current Gradle configuration sets `minSdk 24`, `targetSdk 35`, and release minification with R8. Debug builds use only the Android debug certificate. Release builds remain unsigned unless the owner supplies protected signing inputs; neither variant is production-signed by default. This public repository is source-only: APKs, model weights, generated ZIPs, build outputs/caches, signing keys, secrets, and raw local audit logs are intentionally excluded. Build artifacts locally with the Gradle command above.
