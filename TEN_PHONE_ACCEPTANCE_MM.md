# NEXUS OFFLINE — 10-Phone Acceptance Evidence Template

**Status: NOT EXECUTED.** Begin only after the complete two-phone offline acceptance is **PASS** and its evidence has been reviewed. This template does not certify a device, group, or scale test.

## Test gate and setup

- Two-phone acceptance report/reference: `________________________________`
- Two-phone overall result: `NOT EXECUTED` / `FAIL` / `PASS` (10-phone testing is blocked unless `PASS`)
- Test lead / date / timezone: `________________________________`
- Network isolation method and evidence: `________________________________`
- APK filename / version / package / SHA-256: `________________________________`
- App signing state: `________________________________`
- Group/test ID and protocol version: `________________________________`
- Human fingerprint comparison completed by both participants: `NOT EXECUTED`
- Use disposable accounts/data only. Keep Internet disconnected while enabling the local radios required by Nearby.

## Per-device acceptance records

Record `PASS`, `FAIL`, or `NOT EXECUTED` only from direct physical-device evidence. Do not use mock/browser/JVM results as phone results. Use stable test IDs rather than personal identifiers.

| # | Device ID / Test ID | Model | Android version / API | App version | Join result | Send result | Receive result | Reconnect result | Attachment result | Failures / notes | Evidence path(s) |
|---:|---|---|---|---|---|---|---|---|---|---|---|
| 1 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 2 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 3 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 4 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 5 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 6 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 7 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 8 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 9 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |
| 10 |  |  |  |  | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED | NOT EXECUTED |  |  |

## Group-level scenarios

| Scenario | Result | Test IDs / evidence path | Failures / recovery notes |
|---|---|---|---|
| Ten devices join the intended group under offline conditions | NOT EXECUTED |  |  |
| Human-verified fingerprints / identity confirmation | NOT EXECUTED |  |  |
| Burmese and English send/receive across devices | NOT EXECUTED |  |  |
| Concurrent sends and receive ordering | NOT EXECUTED |  |  |
| Duplicate/replay/tampered message rejection | NOT EXECUTED |  |  |
| Disconnect, reconnect, and queued-delivery behavior | NOT EXECUTED |  |  |
| Attachment consent, transfer, integrity/hash, and encrypted storage | NOT EXECUTED |  |  |
| Background/foreground, permissions, radio toggles, and restart | NOT EXECUTED |  |  |
| Long-run stability, resource use, and observed failures | NOT EXECUTED |  |  |

## Sign-off

- Device records complete and references retained: `NOT EXECUTED`
- All scenarios passed with no unresolved critical failures: `NOT EXECUTED`
- Reviewer / date: `________________________________`
- Overall 10-phone result: `NOT EXECUTED`

A 10-phone result is not **PASS** unless the two-phone gate was already **PASS** and every required group-level scenario has direct physical-device evidence. Preserve **PRODUCTION VERIFICATION PENDING** until production signing, supported-device verification, and the required acceptance evidence are complete.
