# Building and developing Panelgrove

## Requirements

- JDK **21**
- Android SDK platform **36.1** and build tools **36.0.0**
- Android NDK **28.2.13676358**
- CMake **3.22.1**

The Gradle wrapper and dependency versions are pinned. The app requires Android 8.0 (API 26), targets API 36, and packages `arm64-v8a`, `armeabi-v7a`, and `x86_64` native libraries. The x86_64 build is also used for emulator testing.

Open the project in Android Studio, set `ANDROID_HOME`, or create an ignored `local.properties` with your SDK's `sdk.dir`. Native builds use the checksum-pinned `gradle/libarchive-3.8.10.tar.xz` source archive; CMake does not fetch libarchive from the network. Dependency resolution and the first Gradle-wrapper run require network access.

## Build and test

From the project root:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

On Windows, use `./gradlew.bat` for the same tasks. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With a connected, unlocked Android device or emulator:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Run this Windows check separately from connected tests to verify externally terminated process recovery:

```powershell
.\scripts\test-process-restart.ps1 -Serial emulator-5554
```

The restart script installs the built debug and test APKs, prepares generated reading data, stops the running app externally, and verifies recovery in a new process. Use a development device or emulator.

## Project layout

| Path | Purpose |
| --- | --- |
| `app/` | Compose UI, Android storage, Room database, background work, and isolated document decoding |
| `core/` | Kotlin models, metadata, ordering, backups, and reading rules |
| `app/schemas/` | Room schemas required by migration tests |
| `app/src/androidTest/assets/fixtures/` | Original generated test documents |
| `gradle/` | Wrapper and pinned native source archive |
| `LICENSES/` | Third-party licenses and notices |
| `branding/` | Supplied icon source and palette |
| `scripts/` | Fixture generation, package verification, and local validation helpers |

Public test fixtures contain original geometric artwork. `scripts/prepare_fixtures.py` regenerates the ordinary fixtures with Pillow; regenerating RAR fixtures additionally needs a separately installed RAR CLI at the path configured in that script. The CLI is neither distributed nor a build/runtime dependency. `scripts/prepare_hardening_fixture.py` generates the large resource-test page with streaming PNG compression.

An optional local compatibility test accepts a private sample directory through the Gradle property `panelgroveSamples`. Samples are not needed for a normal checkout, build, or test run and must stay outside the published source. Use `local-validation/` for local samples, reports, and device-specific evidence.

## Interface text and diagnostics

English interface text, accessibility labels, plurals, and formatted messages are in `app/src/main/res/values/strings.xml`. Add translations in `values-<language>/strings.xml`. `StringDictionary` resolves Android resources in composition and background work. Protocol keys, filenames, metadata, and parser diagnostics remain separate from interface translations.

Debug builds show diagnostic details. Release builds log technical failures, keep optional metadata failures quiet, and show actionable messages for failed imports or blocked reading and saving actions. No analytics or network service is included.

## Local previews

On Windows, `scripts/build-preview.ps1` builds and checks a signed, minified local preview in ignored `dist/`. It keeps its generated validation signing files in ignored `local-validation/`; these are not public-release credentials.

Use `-RunAndroidTests` to test all connected, unlocked devices. `-FullKotlinRebuild` disables incremental Kotlin compilation when local compiler state needs rebuilding. Neither option is required for a normal source build.

## Release signing

Set the following environment variables before running `./gradlew :app:assembleRelease`:

| Variable | Value |
| --- | --- |
| `PANELGROVE_KEYSTORE` | Absolute path to the release keystore |
| `PANELGROVE_STORE_PASSWORD` | Keystore password |
| `PANELGROVE_KEY_ALIAS` | Signing-key alias |
| `PANELGROVE_KEY_PASSWORD` | Signing-key password |

Without signing configuration, release APKs are unsigned. Keep the release key and a secure backup outside source control. Use the same signing identity for future public updates.

The GitHub release workflow uses repository Actions secrets named `PANELGROVE_KEYSTORE_BASE64`, `PANELGROVE_STORE_PASSWORD`, `PANELGROVE_KEY_ALIAS`, and `PANELGROVE_KEY_PASSWORD`. A pushed `v*` tag triggers core tests, release lint, a signed APK build, signature verification, ELF/ZIP alignment checks, and checksum generation. It creates a **draft** release with the APK, `SHA256SUMS`, and `SIGNING-CERTIFICATE.txt`.

Before publishing a draft, check the tag against the built version, review the results, describe the current app's features and known limits, and mark preview builds as prereleases. Permanent release signing and remote workflow execution are still pending for the initial public release.

The ordinary CI workflow builds and checks debug APKs and runs Android tests on API 26 and 35. These workflow definitions are included in source; their presence does not establish that a remote run has passed. Recorded local results are in the [validation summary](VALIDATION.md).
