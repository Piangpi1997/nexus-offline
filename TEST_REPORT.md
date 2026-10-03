# NEXUS OFFLINE — Test Report (2026-10-04)

## Android host build and JVM tests

The latest clean Android build completed with **BUILD SUCCESSFUL** using the checksum-pinned Gradle 8.7 wrapper and the current Android Gradle configuration:

```sh
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
./gradlew clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

The JUnit results contain **81 tests, 0 failures, 0 errors, and 0 skipped tests across 10 suites**. `PairingQrTest` passes **6/6** tests, including valid and malformed payloads, endpoint validation, expiry/replay rejection, cancellation, and safe scanner-result handling. The Activity-result path converts unexpected processing exceptions into a generic error state rather than allowing them to escape as an Activity crash.

These are host JVM tests. They do not run Android Keystore, camera scanning, Nearby radios, the LiteRT-LM JNI runtime, or real model inference. Software test key providers and fake runtimes are not evidence of Android-device behavior.

## Browser and UI regression

All six Chromium/browser-host suites pass (**6/6**): mobile navigation, localization/navigation, mock Nearby integration, attachment UI security, responsive UI regression, and QR modal visual regression. The QR-specific fixture passes **8/8 viewport scenarios**. It checks layout bounds, square placeholder geometry, clipping/overflow, minimum button dimensions, Escape dismissal, and focus return.

The QR fixture uses a deliberately non-scannable mock pattern. Its screenshots demonstrate layout and localization only; they do not show a generated pairing QR, a camera scan, fingerprint verification, or a successful pairing. Other browser/mock results likewise do not prove Android WebView, SAF, Keystore, filesystem, or radio behavior.

## Lint and build notes

Both `lintDebug` and `lintRelease` report **0 errors** and one unsuppressed dependency advisory: `androidx.core:core-ktx` 1.19.1 is available while the project remains on 1.16.0. The build also reports the Gradle 8.7 forward-compatibility advisory, an Android SDK XML tooling notice, existing Android API deprecations, and a non-blocking R8 class-file provider notice. No warnings were suppressed to make the build pass.

## Device and production status

No physical-device QR scan or Nearby pairing was performed. Android install/UI, Android Keystore runtime, camera permission and scanning, two-phone radio acceptance, and real Local AI inference remain **NOT TESTED**. No model weights are included. Local AI model protection remains **PARTIAL** because a private temporary plaintext copy is needed by the active LiteRT-LM file-path API during import and runtime load.

**PRODUCTION VERIFICATION PENDING.** Browser mocks, host JVM tests, source inspection, and a successful build must not be represented as physical-device acceptance or production readiness.