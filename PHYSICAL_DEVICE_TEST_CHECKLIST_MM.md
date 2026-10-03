# NEXUS OFFLINE — Physical Device Test Checklist

**Status:** All device-only checks below remain **NOT RUN** until an Android device/AVD passes the readiness gate and, for Nearby, multiple real devices are available. Browser simulations and JVM tests are not substitutes.

## Test equipment and release candidate

- [ ] Use a fresh, checksum-verified build; record APK SHA-256, package, version, signing certificate, device model, Android API, ABI, and Google Play services version.
- [ ] Test at least one current 64-bit ARM Android phone with Google Play services; include the oldest claimed Android API supported by the app and a current target API device. AVDs are suitable for UI/storage checks but not for a physical Nearby-radio PASS.
- [ ] For Nearby, prepare two physical Android phones for functional tests and ten phones for the requested scale test. Keep Internet/WAN disabled while keeping the Bluetooth/Wi-Fi radios needed for Nearby enabled. Nearby requires Google Play services and user-granted permissions; account for documented Google Play services metrics in privacy decisions.
- [ ] Use only disposable test profile/chat/attachment data. Never capture or publish raw private keys, authentication secrets, or real user messages in logs/screenshots.

## Install, launch, navigation, lifecycle, and Burmese

- [ ] Clean install and `adb install -r` succeeds only after `sys.boot_completed=1`, `service check package` reports found, PackageManager responds, and HOME launcher resolution succeeds.
- [ ] Launch the app, background/foreground it, press Android Back, and force-stop/relaunch. Confirm no crash, lost state, blank WebView, or duplicate background task.
- [ ] Visit Home, Chat, Devices/Connections/Nearby, Attachments, QR Pairing, Local AI, Settings, and Diagnostics; capture screenshots of each screen and any warning/permission state.
- [ ] Test mobile drawer open/close, scrim tap, all tap targets, brand/logo-to-Home, dashboard shortcuts, Android/system Back, and touch targets with both one-hand and keyboard accessibility navigation.
- [ ] Verify Myanmar/Burmese Unicode, punctuation, line wrapping, error strings, focus indication, and no replacement glyphs or clipped text at default and enlarged font scales.
- [ ] Rotate portrait/landscape; resize multi-window/foldable width if supported; open/close keyboard and verify focused controls and scroll position.

## Permissions and radios

- [ ] On supported Android API levels, verify Nearby runtime permission dialogs for Bluetooth scan/connect/advertise and Nearby Wi-Fi devices; verify QR camera permission separately.
- [ ] Test both grant and deny paths, then grant through Android Settings and retry. Confirm accurate UI status and no crash or permission loop.
- [ ] Disable Bluetooth and Wi-Fi in turn, try advertising/discovery, and confirm a clear request to enable a required radio; re-enable and retry.
- [ ] Verify that a device without camera/radio can still install and use offline local portions.

## Keystore-backed profile/chat state and clear-data

- [ ] Create a disposable profile change and local chat note; verify save feedback.
- [ ] Background/foreground and force-stop/restart; verify encrypted state reads back without duplication or plaintext local-storage fallback.
- [ ] Inspect app-private storage without exposing values; confirm ciphertext, not plaintext, is persisted and no secret content appears in logcat.
- [ ] Exercise the app's explicit clear-data flow. Verify state, model/cache, encrypted attachments, outgoing/incoming transfers, and export/receive temporary files are cleared and storage keys are handled as designed.
- [ ] Also test Android system “Clear storage/data” on a disposable test install and confirm the next launch is clean and stable.

## Attachments and legacy migration

- [ ] From a paired device, receive an attachment; verify encryption at rest, metadata/name/size preservation, list refresh, authenticated integrity verification, and no plaintext before verified open/share.
- [ ] Open and export/share a verified test attachment through the Android document/content-provider flow; verify the receiver gets exact bytes and the app removes temporary plaintext after success and after an induced export failure.
- [ ] Verify tampered ciphertext, wrong key, wrong metadata, and traversal names are rejected; ensure corrupt files remain deletable.
- [ ] On a disposable fixture, migrate a legacy plaintext attachment. Verify encrypted commit and metadata/hash preservation before deleting the old file; induce a failed commit and confirm the original remains.
- [ ] Test warning/retry UI, process termination during migration, and restart/recovery. Do not place failure-injection hooks in a production build.

## QR pairing

- [ ] Grant/deny camera permission, display and close a newly generated short-lived QR, and scan it with another device.
- [ ] Confirm valid payload parsing, endpoint matching, nearby-device selection, mutual human comparison of the authentication fingerprint/code, and explicit acceptance on both ends.
- [ ] Test expired, replayed, malformed, overlong, wrong-endpoint, and already-consumed QR payloads; verify clear errors and no unintended connection.
- [ ] Restart the app and confirm replay protection for recently consumed QR nonces remains effective.

## Nearby Chat — two-device and ten-device validation

- [ ] With two physical phones and no Internet, test advertising, discovery, device naming, request/accept/reject, authentication-code comparison, secure handshake, and connection teardown.
- [ ] Send/receive normal and Burmese messages; confirm encryption, local queue persistence while disconnected, retry after reconnect, and correct duplicate/tamper rejection.
- [ ] Test screen/background transitions, radio changes, process restart, disconnect/reconnect, permission revocation, and GMS-unavailable behavior.
- [ ] Transfer a test file with consent/ready ACK, progress, cancel, failure, retry-by-reselection, tamper detection, and cleanup. Record throughput and failure behavior.
- [ ] Repeat with ten real phones in a controlled offline environment. Record how many peers are discovered, connected, and able to exchange messages/files; do not extrapolate from one emulator or mock tabs.

## Local AI and diagnostics

- [ ] Without an imported model, keep the exact status `LOCAL AI — MODEL NOT INSTALLED`. Do not mark Test Inference successful until a compatible model is genuinely loaded and streamed output is returned on-device; otherwise keep inference `NOT EXECUTED`.
- [ ] On a supported ARM64 phone, turn Internet and mobile data off while leaving only the user-selected local file available. Import a real model from local storage, verify its provenance/hash/license, load it, generate output, cancel, unload, and reload; do not simulate any of these actions.
- [ ] Record phone model, Android/API, ABI, total RAM, available RAM before load, model filename/version and exact size, import duration, load duration, RAM after load/peak if available, first-token latency, generation speed if measurable, cancellation latency, crash/OOM observations, and the confirmed offline state. Mark any unavailable measurement `NOT MEASURED`, not PASS.
- [ ] Run English and Burmese prompts; verify meaningful output, Unicode rendering, cancellation, unload/reload, and process restart. Confirm no cloud/network fallback.
- [ ] Open Diagnostics and verify each result is labeled as a real device/runtime check, not a browser simulation.
- [ ] Capture filtered `adb logcat` across launch, permission, QR, storage, Nearby, attachment, AI, background/restart, and clear-data checks. Search for FATAL EXCEPTION, AndroidRuntime, WebView/JavaScript bridge errors, SecurityException, permission, FileProvider, Keystore, and Nearby errors; redact test content.

## Exit criteria

- [ ] Every check is recorded as PASS, FAIL, or NOT RUN with device/API/steps/evidence; host/JVM and Android-runtime results stay in separate categories.
- [ ] No production release claim until supported-device Local AI, physical Nearby, security/runtime behavior, and production release signing have been verified.
