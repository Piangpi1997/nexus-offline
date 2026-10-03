# Release signing and identity template

This project intentionally contains **no release keystore or signing secrets**. The `release` variant is unsigned unless all four environment variables below are supplied at build time:

```sh
export NEXUS_RELEASE_STORE_FILE=/secure/path/to/release.jks
export NEXUS_RELEASE_STORE_PASSWORD='(provide through your protected shell environment)'
export NEXUS_RELEASE_KEY_ALIAS='release'
export NEXUS_RELEASE_KEY_PASSWORD='(provide through your protected shell environment)'
```

Do not commit the keystore, passwords, or a filled-in credential file. Back up the keystore and its passwords using an approved secret-management process. If one variable is absent, Gradle leaves the release APK unsigned; verify the output with `apksigner verify --print-certs` before distribution.

Optional non-secret Gradle properties let the release owner set production identity/version without editing source:

```properties
productionApplicationId=com.example.nexusoffline
releaseVersionCode=1
releaseVersionName=1.0.0
```

Set these in an untracked `android/gradle.properties` or pass them with `-P` on the build command. The default `com.nexusoffline` / `0.1.0-alpha` remains a development identity and is not production-approved. Changing the application ID after installation creates a different Android app identity and does not upgrade the existing package.
