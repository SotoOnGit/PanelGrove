# Building and developing Panelgrove

## Requirements

- JDK **21**
- Android SDK platform **36.1** and build tools **36.0.0**
- Android NDK **28.2.13676358**
- CMake **3.22.1**

The Gradle wrapper and dependency versions are pinned. The app requires Android 8.0 (API 26), targets API 36, and packages `arm64-v8a`, `armeabi-v7a`, and `x86_64` native libraries. The x86_64 build is also used for emulator testing.

Open the project in Android Studio, set `ANDROID_HOME`, or create an ignored `local.properties` with your SDK's `sdk.dir`. Native builds use checksum-pinned source archives for libarchive 3.8.10 and XZ Utils 5.8.4 (`gradle/libarchive-3.8.10.tar.xz` and `gradle/xz-5.8.4.tar.xz`). CMake builds and links liblzma statically for 7Z decoding without downloading native dependencies or searching for a host codec. The XZ command-line tools are disabled. Dependency resolution and the first Gradle-wrapper run require network access.

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

`python scripts/prepare_7z_fixtures.py --seven-zip <path-to-7z>` regenerates the CB7/7Z fixtures from the original `sample.cbz` using a local 7-Zip CLI. The CLI is only a fixture-authoring tool. Checked-in fixtures cover solid LZMA2, non-solid LZMA, stored, Deflate, PPMd, Unicode names, metadata fallback, encrypted headers/data, invalid paths, oversized entries, corruption, CRC failure, and unsupported BZip2. `SevenZipInstrumentedTest` also checks provider staging, folder rescans, managed copies, reading-data retention, and Android MIME handling.

XZ Utils source: [upstream 5.8.4 release](https://github.com/tukaani-project/xz/releases/tag/v5.8.4), SHA-256 `4ce24038fd4221e0d13bc1a2de7a4db56e90b92b3bf75321f6c14be73f65de4b`. The compiled liblzma component uses the BSD Zero Clause license; see `LICENSES/xz.txt` and the source archive for full licensing details.

An optional local compatibility test accepts a private sample directory through the Gradle property `panelgroveSamples`. Samples are not needed for a normal checkout, build, or test run and must stay outside the published source. Use `local-validation/` for local samples, reports, and device-specific evidence.

## Public releases

The current public prerelease is [0.9](releases/0.9.md), with `versionName = "0.9"`, `versionCode = 8`, and package `dev.soto.panelgrove`. Its [build/test CI](https://github.com/SotoOnGit/PanelGrove/actions/runs/40780054553) and [signed-release workflow](https://github.com/SotoOnGit/PanelGrove/actions/runs/40780054592) passed. See the [validation summary](VALIDATION.md) for measured scope.

The `Build and test` workflow builds the debug APK, runs core tests, lint and package checks, and runs Android instrumentation on API 26 and 35. A pushed `v*` tag triggers `Signed draft release`, which runs core tests and release lint, builds the signed minified APK, verifies its signature and native/ZIP alignment, and creates a GitHub draft with the APK, `SHA256SUMS` and `SIGNING-CERTIFICATE.txt`. The draft needs release-note review and publication; tagging alone does not publish a release.

Signing uses the repository secrets `PANELGROVE_KEYSTORE_BASE64`, `PANELGROVE_STORE_PASSWORD`, `PANELGROVE_KEY_ALIAS` and `PANELGROVE_KEY_PASSWORD`. The workflow decodes the keystore to runner temporary storage and removes it afterward. Keep the production signing identity and its secure backup outside the repository; later public APKs must use that identity and a higher version code to update existing installations.

For a future release, update the version, release notes, README download links, user guide, roadmap and validation summary. Push the release commit and wait for CI before creating its version tag. Review the draft's APK, checksum and signing fingerprint, and test installation over the previous public APK with library/progress/bookmarks/settings retained. Keep failed attempts and remaining acceptance work explicit when publishing. Public 0.9 is already available; the recorded evidence still lacks the 0.1.6 → 0.9 update-install check.

## Interface text and diagnostics

English interface text, accessibility labels, plurals, and formatted messages are in `app/src/main/res/values/strings.xml`. Add translations in `values-<language>/strings.xml`. `StringDictionary` resolves Android resources in composition and background work. Protocol keys, filenames, metadata, and parser diagnostics remain separate from interface translations.

Debug builds show diagnostic details. Release builds log technical failures, keep optional metadata failures quiet, and show actionable messages for failed imports or blocked reading and saving actions. No analytics or network service is included.

## Local previews

On Windows, `scripts/build-preview.ps1` builds and checks a signed, minified local preview in ignored `dist/`. It keeps its generated validation signing files in ignored `local-validation/`; these are not public-release credentials.

Use `-RunAndroidTests` to test all connected, unlocked devices. `-FullKotlinRebuild` disables incremental Kotlin compilation when local compiler state needs rebuilding. Neither option is required for a normal source build.
