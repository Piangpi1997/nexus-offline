# Public repository scope

This repository publishes the NEXUS OFFLINE application source, tests, Gradle wrapper/configuration, documentation, device-test helpers, and only the UI screenshots linked by the validation report.

Intentionally excluded: APK/AAB files, model weights, generated source ZIPs, build outputs and caches, local test/audit logs, signing keys/keystores, secrets, local credentials, and unrelated historical audit inputs. The working project directory and its generated artifacts are not mirrored wholesale.

Build the Android app locally using the Gradle wrapper instructions in `README.md`. Physical-device procedures and the current unexecuted test gates are documented in `PHYSICAL_DEVICE_TEST_MM.md` and `PHYSICAL_DEVICE_TEST_CHECKLIST_MM.md`.
