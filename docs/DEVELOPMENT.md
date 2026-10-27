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

### Testing one physical device

When a phone and an emulator are both connected, select the phone explicitly. The following PowerShell commands build the test packages and run only on the chosen serial:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb devices -l
$deviceSerial = '<phone serial>'
adb -s $deviceSerial install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $deviceSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $deviceSerial shell am instrument -w -r -e timeout_msec 90000 dev.soto.panelgrove.test/androidx.test.runner.AndroidJUnitRunner
.\scripts\test-process-restart.ps1 -Serial $deviceSerial
```

Keep the phone unlocked and approve any phone-side USB installation prompts. Some MIUI devices also restrict background activity launches; if the runner stays on the launcher, inspect the device log and Panelgrove's background pop-up permission before treating the attempt as an app test failure. Preserve interrupted runs and timeout results alongside any retries, including a changed timeout.

These tests create generated library entries and exercise settings, history, storage and recovery. Use a development installation. Debug and public APKs have different signing identities; back up existing reading data before replacing an installation. Test the published signed APK separately through Android's actual file picker, since passing debug instrumentation does not establish release behavior or production-provider access.

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

The first signed public prerelease, [v0.1.6](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.1.6), was published on **2026-10-27** from commit `8d28a4716ca1de8be15cb10027b8021251aa6745`. Its [release workflow](https://github.com/SotoOnGit/PanelGrove/actions/runs/40491207093) completed successfully. Keep using this release's signing identity for future public updates; the generated local-preview key is separate.

Set the following environment variables before running `./gradlew :app:assembleRelease`:

| Variable | Value |
| --- | --- |
| `PANELGROVE_KEYSTORE` | Absolute path to the release keystore |
| `PANELGROVE_STORE_PASSWORD` | Keystore password |
| `PANELGROVE_KEY_ALIAS` | Signing-key alias |
| `PANELGROVE_KEY_PASSWORD` | Signing-key password |

Without signing configuration, release APKs are unsigned. Keep the release key and a secure backup outside source control. Use the same signing identity for future public updates.

The GitHub release workflow uses repository Actions secrets named `PANELGROVE_KEYSTORE_BASE64`, `PANELGROVE_STORE_PASSWORD`, `PANELGROVE_KEY_ALIAS`, and `PANELGROVE_KEY_PASSWORD`. A pushed `v*` tag triggers core tests, release lint, a signed APK build, signature verification, ELF/ZIP alignment checks, and checksum generation. It creates a **draft** release with the APK, `SHA256SUMS`, and `SIGNING-CERTIFICATE.txt`.

### Publishing an update

1. Update `versionName` and increase `versionCode` in `app/build.gradle.kts`. The initial public release uses `0.1.6` / `7`.
2. Commit and push the changes, then check the ordinary CI results.
3. Create and push a new `v<versionName>` tag at the intended commit. Use a new tag for each version.
4. Wait for the signed release workflow and review its draft, APK, checksum, and signing-certificate information. Confirm the tag matches the app's built version and the certificate matches the previous public release.
5. Test the draft APK on a physical device, including installation over the previous public version and retention of reading data. Keep a backup before testing.
6. Describe the changes and known limits, mark preview builds as **prereleases**, and publish the reviewed draft.

The ordinary CI workflow builds and checks debug APKs and runs Android tests on API 26 and 35. The [CI run verified on 2026-10-27](https://github.com/SotoOnGit/PanelGrove/actions/runs/40413500147) completed successfully on its second attempt, with 66 passing Android cases and five deliberate skips on each API. Remote results, published-package details, and the separate local-preview checks are recorded in the [validation summary](VALIDATION.md).
