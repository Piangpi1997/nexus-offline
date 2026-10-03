#!/usr/bin/env bash
set -Eeuo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${ADB:-$(command -v adb || true)}"
if [[ -z "$ADB" && -x "$SDK_ROOT/platform-tools/adb" ]]; then ADB="$SDK_ROOT/platform-tools/adb"; fi
PACKAGE_NAME="${PACKAGE_NAME:-com.nexusoffline.debug}"
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
[[ -n "$ADB" && -x "$ADB" ]] || fail 'adb is unavailable; add platform-tools to PATH or set ADB.'

printf '%s\n' 'NEXUS OFFLINE — two-phone test preflight (diagnostics only)'
DEVICE_LIST="$($ADB devices -l)"
printf '%s\n' "$DEVICE_LIST"
if grep -Eq '^[^[:space:]]+[[:space:]]+unauthorized([[:space:]]|$)' <<<"$DEVICE_LIST"; then fail 'At least one connected device is unauthorized; authorize it on the phone before running.'; fi
if grep -Eq '^[^[:space:]]+[[:space:]]+offline([[:space:]]|$)' <<<"$DEVICE_LIST"; then fail 'At least one connected device is offline; reconnect it before running.'; fi
mapfile -t READY_SERIALS < <(awk 'NR>1 && $2=="device" {print $1}' <<<"$DEVICE_LIST")
if [[ -n "${DEVICE_SERIALS:-}" ]]; then
  IFS=',' read -r -a SERIALS <<< "$DEVICE_SERIALS"
  [[ ${#SERIALS[@]} -eq 2 ]] || fail 'DEVICE_SERIALS must contain exactly two comma-separated serials.'
  for serial in "${SERIALS[@]}"; do
    printf '%s\n' "${READY_SERIALS[@]}" | grep -Fxq "$serial" || fail "Selected serial is not an authorized adb device: $serial"
  done
else
  [[ ${#READY_SERIALS[@]} -eq 2 ]] || fail "Expected exactly two authorized devices; found ${#READY_SERIALS[@]}. Set DEVICE_SERIALS=serialA,serialB to select two from a larger set."
  SERIALS=("${READY_SERIALS[@]}")
fi
[[ "${SERIALS[0]}" != "${SERIALS[1]}" ]] || fail 'The two serials must be different.'
printf 'Authorized device count: 2\nApplication package checked: %s\n' "$PACKAGE_NAME"

for index in 0 1; do
  serial="${SERIALS[$index]}"
  printf '\n--- Phone %s ---\n' "$((index + 1))"
  printf 'Serial: %s\n' "$serial"
  model="$($ADB -s "$serial" shell getprop ro.product.model | tr -d '\r')"
  api="$($ADB -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
  release="$($ADB -s "$serial" shell getprop ro.build.version.release | tr -d '\r')"
  primary_abi="$($ADB -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
  abi_list="$($ADB -s "$serial" shell getprop ro.product.cpu.abilist | tr -d '\r')"
  available_storage="$($ADB -s "$serial" shell df -k /data 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $4 ~ /^[0-9]+$/ { bytes=$4*1024 } END { if (bytes > 0) printf "%.0f", bytes }' || true)"
  total_ram="$($ADB -s "$serial" shell cat /proc/meminfo 2>/dev/null | tr -d '\r' | awk '$1 == "MemTotal:" { print $2*1024; exit }' || true)"
  available_ram="$($ADB -s "$serial" shell cat /proc/meminfo 2>/dev/null | tr -d '\r' | awk '$1 == "MemAvailable:" { print $2*1024; exit }' || true)"
  printf 'Model: %s\nAndroid version/API: %s / %s\nPrimary ABI: %s\nSupported ABI list: %s\nAvailable /data storage bytes: %s\nTotal RAM bytes: %s\nAvailable RAM bytes: %s\n' \
    "$model" "$release" "$api" "$primary_abi" "$abi_list" "${available_storage:-unavailable}" "${total_ram:-unavailable}" "${available_ram:-unavailable}"
  if [[ ",$abi_list," == *,arm64-v8a,* || ",$abi_list," == *,x86_64,* ]]; then
    printf '%s\n' 'LiteRT-LM ABI metadata: supported ABI present; this is not a runtime/inference result.'
  else
    printf '%s\n' 'LiteRT-LM ABI metadata: no supported arm64-v8a/x86_64 ABI found; do not mark Local AI runtime compatible.'
  fi
  if [[ "$api" =~ ^[0-9]+$ ]] && (( api < 24 )); then printf 'APP COMPATIBILITY: API below minSdk 24\n'; else printf 'APP COMPATIBILITY: API gate passed or unreadable; verify before install\n'; fi
  if [[ -n "$($ADB -s "$serial" shell pm path "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')" ]]; then
    printf 'Package: installed\n'
    dump="$($ADB -s "$serial" shell dumpsys package "$PACKAGE_NAME" 2>/dev/null || true)"
    version_name="$(grep -m1 -o 'versionName=[^[:space:]]*' <<<"$dump" || true)"
    version_code="$(grep -m1 -o 'versionCode=[^[:space:]]*' <<<"$dump" || true)"
    printf 'App version: %s %s\n' "${version_name:-versionName unavailable}" "${version_code:-versionCode unavailable}"
    printf 'Declared runtime permission diagnostics (read-only):\n'
    permissions="$(grep -E 'android\.permission\.(BLUETOOTH_SCAN|BLUETOOTH_CONNECT|BLUETOOTH_ADVERTISE|NEARBY_WIFI_DEVICES)' <<<"$dump" | head -12 || true)"
    if [[ -n "$permissions" ]]; then printf '%s\n' "$permissions"; else printf 'No matching permission state exposed by dumpsys.\n'; fi
  else
    printf 'Package: not installed\n'
  fi
  bluetooth_setting="$($ADB -s "$serial" shell settings get global bluetooth_on 2>/dev/null | tr -d '\r' || true)"
  wifi_setting="$($ADB -s "$serial" shell settings get global wifi_on 2>/dev/null | tr -d '\r' || true)"
  printf 'Bluetooth global setting diagnostic: %s\nWi-Fi global setting diagnostic: %s\n' "${bluetooth_setting:-unavailable}" "${wifi_setting:-unavailable}"
  printf '%s\n' 'Radio settings are diagnostics only and may not reflect actual Nearby readiness.'
done
printf '\n%s\n' 'Preflight complete. No radio state was changed and no pairing/message/attachment action was attempted.'
printf '%s\n' 'Local AI model import/load/inference is not automated; use a real user-selected model and record offline measurements separately.'
printf '%s\n' 'Continue only with human confirmation of identity fingerprints, permissions, and actual on-device results; this report is not a physical-test PASS.'
