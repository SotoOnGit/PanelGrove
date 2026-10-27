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

## API 26 dialog screenshot compatibility

Follow-up validation: **2026-10-28**.

CI exposed a Compose test limitation: `captureToImage()` cannot capture dialog windows before API 28. The cover and display preview assertions now use a shared test-only capture helper. On API 26–27 it crops a `UiAutomation` screen capture using the node's screen position, including the dialog window offset; API 28+ retains Compose capture. Reader-page assertions keep their existing activity capture so closing-sheet animations do not affect the comparison. Both tests retain their pixel assertions, with no SDK skips or application-code changes.

Both previously failing tests passed on existing API 26 and API 32 / x86-64 emulators, exercising both capture paths. The emulators ran one at a time in read-only mode and were stopped afterward. Debug app/test builds and lint passed with zero errors. Local build and instrumentation logs, including the initial closing-animation comparison failure before restricting the fallback to dialog captures, are under `local-validation/dialog-capture-fix/`. These local runs do not constitute a rerun of remote CI or its full suite.

## API 35 viewport and font-scale synchronization

Follow-up validation: **2026-10-28**.

The display-test timeout was reproduced on Android 15 / API 35. Its initial page capture was 1080×1920; after activity recreation, it was 1080×1794. The comparison returned `false` whenever capture dimensions differed, so it could never observe the saved appearance change. It now samples the generated fixture's solid color panel at normalized positions, avoiding movable image borders and letterboxing. Preview, Cancel, Save, recreation, reset and default-setting checks remain enabled.

The large-text test changed system font scale while its activity was running, then immediately called `recreate()`. Android's configuration recreation could destroy that instance first. A JUnit rule now sets font scale and waits for the configuration before launching the Compose activity, then restores and waits for the original scale after the activity closes. The test still exercises large-text commands and background/resume behavior.

The focused display and reader UI classes passed all three cases on API 35, and font scale was restored. Debug app/test builds and lint passed with zero errors. The official Google APIs x86-64 API 35 revision 9 image was checksum-verified and run using the existing Pixel 2 configuration with separate workspace data and read-only mode. The saved Android 36 data and PIN were not used or reset. Logs and baseline failure evidence are under ignored `local-validation/api35-fix/`.

The complete API 35 instrumentation suite then passed **79 cases, with five expected skips and zero failures**, in **118.385 seconds**, using the existing 90-second per-test timeout. The runner reports `OK (84 tests)` including those five skips. The skipped cases are two optional private-archive checks, one optional local translation volume and the two separately invoked process-restart phases. These are local API 35 results; a new GitHub Actions result requires pushing the changes.

The same three display/reader UI cases also passed on the existing API 26 / x86-64 emulator after these fixes. Font scale was restored on both versions, and each emulator was stopped before starting another. Application code and the CI workflow were unchanged.

## UI synchronization audit

Follow-up validation: **2026-10-28**.

The subsequent API 26 CI failure occurred after cover removal: Room already held `customCover = null`, but the flow update had not yet reached Compose, so the removal button still existed. The test now waits for both the button to disappear and the original cover's sampled color to return, then separately checks the persisted null value. It also waits for the initial cover to load before exercising cancellation and selection.

All seven Compose UI test classes were reviewed, along with shared preference and fixture setup/cleanup in the backend tests. The same premature UI assertions were addressed in copy-folder restore dialogs and switches, bookmark removal, history rows, and appearance controls. The history fixture derives its date from one captured timestamp and keeps all three generated visits on that date. Preference cleanup in the design-flow test still runs if diagnostic screenshot/semantics capture fails. Assertions and the existing test timeouts remain in place; no additional SDK skips or application-code changes were introduced.

Reader navigation checks now wait for the displayed page counter before issuing the opposite command, and the reading-mode test verifies the saved selection in the settings UI before performing the next gesture. A retained-state API 35 run also exposed an ambiguous `Cancel` selector when the library scan and a dialog both offered that action. The copy-folder and design-flow dialog actions now select the button within the dialog.

A fresh API 26 run then caught a clipped Book details action: with the keyboard open, the favorite button was below the sheet's visible scroll area. The test now scrolls that button into view and asserts it is displayed before injecting the click. Its original database assertion remains enabled. The failure screenshot and runner output are retained with the audit evidence.

That retained-state run exposed a second issue in the resource harness: every traversal added another set of cached pages, and its shared-cache directory had grown past 2,300 files. The 1,000-page case exceeded the existing 90-second limit. Each resource case now uses its own temporary source/cache directories and a real `SourceAccess`/`DocumentEngine`, opens fixture books directly, and removes its generated files afterward. This establishes a cold-cache baseline and avoids adding synthetic stress books to the shared library. Library imports remain covered by the storage and document tests. The traversal still renders all 1,000 pages, submits 50 rapid requests, renders a 20,000 by 20,000 image and its region, and checks the original memory/disk budgets; the timeout remains 90 seconds. Progress logging identifies the stage if a future run times out.

Build, JVM tests and debug lint passed; lint reported zero errors. Evidence for full-suite and repeated-cover checks is retained under ignored `local-validation/ui-sync-audit-2026-10-28/`. Emulator sessions use one existing AVD configuration at a time in read-only mode; the app and test helper are reinstalled for the initial full run, with later runs retaining test state to exercise cleanup and ordering.
