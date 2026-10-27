# Custom covers and display adjustments

Implemented after public 0.1.6, pending release. Validation date: **2026-10-27**.

## Behavior

- Book details uses Android's image picker to choose a cover and offers removal. The selected image becomes an app-owned PNG thumbnail, with camera orientation applied and bounded input/decode sizes. Generated document covers remain separate. Rescans, reopening and relinking retain the custom selection; removal reveals the generated cover. Deleting a library entry deletes its owned cover and leaves the external book and selected image intact.
- Contrast and gamma range from 0.50 to 2.00 with a neutral value of 1.00. Contrast is applied around mid-gray, followed by gamma (`channel^(1/gamma)`). Gamma above 1 brightens midtones. Alpha is retained. Preview, reader pages and zoom tiles share this curve. Work runs outside the UI thread; cached decoder bitmaps and source files are unchanged.
- Reader settings offers a current-page preview, reset, Save, Cancel and Use defaults. Drafts survive activity recreation. Dismissing or cancelling discards the draft. Book settings override series settings, then defaults. Existing JSON payloads default to neutral, and backups validate and retain the new display settings without a Room schema migration.
- Cover images are local to the installation and excluded from JSON backups. Same-install restores retain a matching book's local cover; foreign cover-file references are ignored. Fresh installations require choosing covers again. Startup recovery removes interrupted staging and uncommitted cover files while retaining referenced covers.

Camera orientation uses [AndroidX ExifInterface 1.4.2](https://developer.android.com/jetpack/androidx/releases/exifinterface), with the existing AndroidX Apache-2.0 notice. No new Android permission is required.

## Reproduce

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
python scripts/check_apk.py app/build/outputs/apk/debug/app-debug.apk
.\scripts\test-process-restart.ps1 -Serial <device serial>
```

The feature cases are `CustomCoverInstrumentedTest`, `CustomCoverUiTest` and `DisplayControlsUiTest`. JVM cases are in `DisplayAdjustmentsTest`. The selected phone regression adds reader UI/modes/viewport/lifecycle, storage, backup recovery, migration, redesign, CB7 and copy-folder UI cases.

## Measured results

The physical device was a **Xiaomi M2012K11AG, Android 13 / API 33, ARM64**. Testing used a development installation of `dev.soto.panelgrove`. The existing separate CB7 QA installation was retained.

- Core: **54 passed**, one optional private-sample case skipped. App JVM: **two passed**.
- Debug app and Android test APKs built successfully for the configured ARM64, ARMv7 and x86-64 ABIs. Debug lint completed with **zero errors, 49 warnings**, including two KTX style suggestions in the new bitmap code.
- All **38 selected phone cases** have passing results across the regression run and a storage retry, including all six feature cases and 32 existing cases. The 38-case run passed 37 cases and found one fixture-isolation failure: the separate process-restart test had left an image in the provider root, so the storage scan correctly found a third book. Removing that generated image and its generated library entry restored the expected fixture state; all four storage cases then passed. The restart test now removes its source image after verification. The temporary cleanup harness was removed.
- APK inspection passed: all packaged native LOAD segments have at least 16 KiB alignment and no document archives are bundled. This is a packaging check, not a 16 KiB runtime result.
- An external force-stop test passed recovery of the committed cover, book/default contrast and gamma, reading position, bookmarks and completion. It also verified cleanup of interrupted cover staging and uncommitted output.
- Android's real Documents picker selected a generated PNG from device storage and returned to Book details with the new cover. The selection survived force-stop and reopening. The chosen source image's SHA-256 remained unchanged. Phone screenshots of the cover, adjusted preview, saved reader and reset preview were visually inspected.

Local evidence is retained under ignored `local-validation/covers-display/`, including build logs, instrumentation output, original screenshots and picker hierarchy captures. Initial attempts retain the test-installation rejection, a corrected JUnit signature error and interrupted UI runs caused by MIUI's background-launch restriction. Reinstalling this app resets that restriction; successful UI runs require enabling it after installation. The original restriction is restored after testing.

These are development-build checks on one physical phone. They do not establish a signed public update, runtime acceptance on every supported API, or human accessibility acceptance. A public release remains separate work.
