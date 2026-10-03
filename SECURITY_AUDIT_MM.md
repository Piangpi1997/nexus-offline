# NEXUS OFFLINE — Security Audit (2026-10-03)

**Result:** Static source/APK and JVM review completed; **Android runtime and production verification remain pending**. The emulator install gate did not pass, so no Android app was installed or used for runtime security checks.

## Offline boundary and platform privacy

The fresh Debug APK has no `android.permission.INTERNET` permission. It does include `ACCESS_NETWORK_STATE`, which reports connectivity state but does not grant socket access. App HTML, scripts, styles, images, and fonts are bundled locally for the Android WebView. No app-authored provider API client, embedded credential, or cloud fallback was found in the audited core source.

Nearby Connections relies on Google Play services, user-granted Bluetooth/BLE/Wi-Fi permissions, and functioning radios. Google's Nearby documentation describes SDK-level device/performance metrics. This third-party platform behavior must be represented in privacy documentation; this audit does not claim zero third-party telemetry. See Google's [Nearby Connections overview](https://developers.google.com/nearby/connections/overview) and [Android setup guide](https://developers.google.com/nearby/connections/android/get-started).

## Manifest and exported components

The fresh APK manifest confirms `allowBackup=false` and `usesCleartextTraffic=false`. The app launcher activity is exported as required. The QR `CaptureActivity` and app `FileProvider` are non-exported; the provider grants URI access only for its configured narrow attachment-share cache path. The app's optional camera, Bluetooth, and nearby-Wi-Fi hardware declarations allow installation without those features.

The merged manifest also contains library-added components. The inspected Google Play services `WakeUpService` is exported behind the `com.google.android.gms.nearby.exposurenotification.EXPOSURE_CALLBACK` permission; AndroidX `ProfileInstallReceiver` is exported behind `android.permission.DUMP`. Recheck library-injected components on dependency upgrades. There is no `INTERNET` permission in the merged APK.

## Secrets, logs, crypto, and files

Targeted source scans found no embedded common provider/API-key patterns, private-key blocks, or app-authored Android/JavaScript logging calls. The fresh APK archive has no model-weight, keystore, or private-key file entries. These are bounded source/archive scans, not a guarantee against every possible secret format. Runtime logcat was not available because no app process was launched.

Source uses Android Keystore-backed state encryption and authenticated `AES/GCM/NoPadding` for attachment data. Attachment transfer checks SHA-256 integrity; metadata and file paths are constrained with canonical-path checks; protocol code includes duplicate/replay guards; and temporary plaintext exports are deleted on success/failure paths. Imported Local AI weights are a separate store and are **not encrypted at rest**; see the verified LiteRT-LM input API finding below and `LOCAL_AI_ARCHITECTURE_MM.md`. JVM tests cover cipher integrity/rejection, metadata and path bounds, safe legacy migration behavior, temporary-file cleanup, protocol replay/tamper cases, QR parsing/expiry, and Local AI import/inspection preflights. These tests do not verify Android Keystore, Android filesystem permissions, SAF/FileProvider behavior, system clear-data, process-death recovery, or real radio replay behavior.

## R8 compatibility finding

The older build emitted 88 Kotlin-metadata parsing warnings. The app uses Kotlin Gradle Plugin 2.4.0, and the resolved LiteRT-LM/Kotlin artifacts contain Kotlin 2.4 metadata; AGP 8.6.1 embeds R8 8.6.27. Google's [Kotlin/R8 compatibility table](https://developer.android.com/build/kotlin-support) specifies R8 9.1.29 or newer for Kotlin 2.4, and the [R8 project instructions](https://r8.googlesource.com/r8/+/refs/heads/main/README.md) document overriding AGP's embedded R8 through `pluginManagement`.

The project now pins published stable R8 9.1.31 through that documented mechanism. This preserves AGP 8.6.1 and the checksum-pinned Gradle 8.7 wrapper. A fresh full build has zero Kotlin-metadata warnings; two unrelated warnings remain because AGP's class-file resource provider does not support async parsing. They did not fail R8 or Release assembly and were not suppressed. Raw local build logs are intentionally excluded from the public source repository.

## Release and remaining runtime risks

Debug is signed with the Android debug certificate. Release is intentionally unsigned; no release keystore was available. Do not generate or adopt a production signing key without the owner's secure backup and ownership strategy. Default Release identity/version are `com.nexusoffline` and `0.1.0-alpha`; production ownership/version approval remains unverified.

The host lacks `/dev/kvm`, exposes no `vmx`/`svm` CPU flags, reports unavailable VT/KVM acceleration, has no boot-ready emulator, and has no attached device in ADB. No APK was installed. Keystore runtime, attachment encryption on-device, QR camera, Android permission flows, crash/logcat review, and Nearby radio behavior remain blocked or not executed. Two-phone and ten-phone acceptance are not demonstrated. Local AI is not verified because no model is installed and no inference was run.

Raw build/artifact audit files are kept outside the public repository. Continue device acceptance with `PHYSICAL_DEVICE_TEST_MM.md`; preserve the status **PRODUCTION VERIFICATION PENDING** until the actual physical-device, real Nearby, Local AI, and production signing requirements are completed.
