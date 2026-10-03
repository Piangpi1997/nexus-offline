#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT_DIR/android/app/build/outputs/apk/debug/app-debug.apk}"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${ADB:-$(command -v adb || true)}"
if [[ -z "$ADB" && -x "$SDK_ROOT/platform-tools/adb" ]]; then ADB="$SDK_ROOT/platform-tools/adb"; fi
AAPT="${AAPT:-}"
if [[ -z "$AAPT" ]]; then
  for candidate in "$SDK_ROOT/build-tools/35.0.0/aapt"; do
    [[ -x "$candidate" ]] && AAPT="$candidate" && break
  done
fi
EXPECTED_APK_SHA256="${EXPECTED_APK_SHA256:-}"
LOGCAT_SECONDS="${LOGCAT_SECONDS:-15}"
SERIAL="${ANDROID_SERIAL:-}"

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ -n "$ADB" && -x "$ADB" ]] || fail 'adb is unavailable; add Android platform-tools to PATH or set ADB.'
[[ -f "$APK" ]] || fail "APK not found: $APK"
[[ -n "$AAPT" && -x "$AAPT" ]] || fail 'aapt is unavailable; set AAPT to the Android build-tools executable.'
[[ "$LOGCAT_SECONDS" =~ ^[0-9]+$ ]] || fail 'LOGCAT_SECONDS must be a non-negative integer.'

printf '%s\n' 'NEXUS OFFLINE — Android device smoke test'
printf 'APK: %s\n' "$APK"
DEVICE_LIST="$($ADB devices -l)"
printf '%s\n' "$DEVICE_LIST"
if grep -Eq '^[^[:space:]]+[[:space:]]+unauthorized([[:space:]]|$)' <<<"$DEVICE_LIST"; then fail 'An adb device is unauthorized. Confirm USB debugging on the phone, then rerun.'; fi
if grep -Eq '^[^[:space:]]+[[:space:]]+offline([[:space:]]|$)' <<<"$DEVICE_LIST"; then fail 'An adb device is offline. Reconnect it, then rerun.'; fi
mapfile -t READY_SERIALS < <(awk 'NR>1 && $2=="device" {print $1}' <<<"$DEVICE_LIST")
if [[ -z "$SERIAL" ]]; then
  [[ ${#READY_SERIALS[@]} -eq 1 ]] || fail "Expected exactly one authorized device; found ${#READY_SERIALS[@]}. Set ANDROID_SERIAL to select one device."
  SERIAL="${READY_SERIALS[0]}"
else
  printf '%s\n' "${READY_SERIALS[@]}" | grep -Fxq "$SERIAL" || fail "Selected serial is not an authorized adb device: $SERIAL"
fi

API="$($ADB -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI_LIST="$($ADB -s "$SERIAL" shell getprop ro.product.cpu.abilist | tr -d '\r')"
PRIMARY_ABI="$($ADB -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
DEVICE_MODEL="$($ADB -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
[[ "$API" =~ ^[0-9]+$ ]] || fail "Could not read Android API level for $SERIAL."
(( API >= 24 )) || fail "Android API $API is below this app's minSdk 24. No install attempted."
[[ ",$ABI_LIST," == *,arm64-v8a,* ]] || fail "This physical Local AI smoke test requires arm64-v8a. Device ABI list: $ABI_LIST. No install attempted."
AVAILABLE_STORAGE_BYTES="$($ADB -s "$SERIAL" shell df -k /data | tr -d '\r' | awk 'NR > 1 && $4 ~ /^[0-9]+$/ { bytes=$4*1024 } END { if (bytes > 0) printf "%.0f", bytes; else exit 1 }')" || fail 'Could not read available /data storage.'
MEM_TOTAL_BYTES="$($ADB -s "$SERIAL" shell cat /proc/meminfo | tr -d '\r' | awk '$1 == "MemTotal:" { print $2*1024; exit }')"
MEM_AVAILABLE_BYTES="$($ADB -s "$SERIAL" shell cat /proc/meminfo | tr -d '\r' | awk '$1 == "MemAvailable:" { print $2*1024; exit }')"
[[ "$MEM_TOTAL_BYTES" =~ ^[0-9]+$ && "$MEM_AVAILABLE_BYTES" =~ ^[0-9]+$ ]] || fail 'Could not read device total/available RAM from /proc/meminfo.'
printf 'Device serial: %s\nDevice model: %s\nAndroid API: %s\nPrimary ABI: %s\nSupported ABI list: %s\nAvailable /data storage bytes: %s\nTotal RAM bytes: %s\nAvailable RAM before load bytes: %s\n' \
  "$SERIAL" "$DEVICE_MODEL" "$API" "$PRIMARY_ABI" "$ABI_LIST" "$AVAILABLE_STORAGE_BYTES" "$MEM_TOTAL_BYTES" "$MEM_AVAILABLE_BYTES"

APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
printf 'APK bytes: %s\nAPK SHA-256: %s\n' "$(wc -c < "$APK" | tr -d ' ')" "$APK_SHA256"
if [[ -n "$EXPECTED_APK_SHA256" && "$APK_SHA256" != "$EXPECTED_APK_SHA256" ]]; then fail 'APK SHA-256 differs from EXPECTED_APK_SHA256. No install attempted.'; fi
BADGING="$($AAPT dump badging "$APK")"
PACKAGE_INFO="$(grep -m1 '^package:' <<<"$BADGING")"
PACKAGE_NAME="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$PACKAGE_INFO")"
VERSION_CODE="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$PACKAGE_INFO")"
VERSION_NAME="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$PACKAGE_INFO")"
[[ -n "$PACKAGE_NAME" ]] || fail 'Could not read APK package identity with aapt.'
printf 'Package: %s\nVersion code: %s\nVersion name: %s\n' "$PACKAGE_NAME" "$VERSION_CODE" "$VERSION_NAME"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="$ROOT_DIR/test-evidence/android/$SERIAL/$STAMP"
mkdir -p "$EVIDENCE_DIR/screenshots"
printf 'Evidence directory: %s\n' "$EVIDENCE_DIR"
printf '%s\n' 'WARNING: screenshots/logcat may contain personal content; review and redact before sharing.'
LOGCAT_PID=''
cleanup() {
  if [[ -n "$LOGCAT_PID" ]]; then kill "$LOGCAT_PID" 2>/dev/null || true; wait "$LOGCAT_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT INT TERM

"$ADB" -s "$SERIAL" logcat -v threadtime > "$EVIDENCE_DIR/logcat.txt" 2>&1 &
LOGCAT_PID=$!
if INSTALL_OUTPUT="$("$ADB" -s "$SERIAL" install -r "$APK" 2>&1)"; then
  printf '%s\n' "$INSTALL_OUTPUT"
  printf '%s\n' 'INSTALL_RESULT=SUCCESS'
else
  printf '%s\n' "$INSTALL_OUTPUT" >&2
  fail 'INSTALL_RESULT=FAILED; see command output and captured logcat.'
fi
if LAUNCH_OUTPUT="$("$ADB" -s "$SERIAL" shell am start -W -n "$PACKAGE_NAME/com.nexusoffline.MainActivity" 2>&1)"; then
  printf '%s\n' "$LAUNCH_OUTPUT"
  grep -Eq 'Status:[[:space:]]*ok' <<<"$LAUNCH_OUTPUT" || fail 'LAUNCH_RESULT=UNCONFIRMED; ActivityManager did not report Status: ok.'
  printf '%s\n' 'LAUNCH_RESULT=ACTIVITY_MANAGER_OK; visual/runtime behavior still requires human verification.'
else
  printf '%s\n' "$LAUNCH_OUTPUT" >&2
  fail 'LAUNCH_RESULT=FAILED; see command output and captured logcat.'
fi
sleep 3
"$ADB" -s "$SERIAL" exec-out screencap -p > "$EVIDENCE_DIR/screenshots/01-launch.png"
if (( LOGCAT_SECONDS > 0 )); then sleep "$LOGCAT_SECONDS"; fi
cleanup
LOGCAT_PID=''
printf 'Install/launch helper completed for %s.\n' "$SERIAL"
printf 'Screenshot: %s/screenshots/01-launch.png\nLogcat: %s/logcat.txt\n' "$EVIDENCE_DIR" "$EVIDENCE_DIR"
printf '%s\n' 'LOCAL_AI_IMPORT_OR_INFERENCE=NOT_AUTOMATED. Use a real user-selected model, disable Internet/mobile data, and record import/load/inference measurements manually.'
printf '%s\n' 'Human verification of permissions, QR, Nearby pairing/fingerprint, UI behavior, crash/OOM, and Local AI remains required; this helper does not simulate those actions.'
