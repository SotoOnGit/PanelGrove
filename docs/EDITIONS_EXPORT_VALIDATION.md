# Language editions and page export — development validation

Updated **2026-10-28**. These features are implemented in the working tree for a future release. Public **0.1.6 / version code 7** does not include them.

## Implemented behavior

- Existing series/volume metadata groups separately imported documents. Book details offers an existing-series chooser, a language chooser/code field, and a way to resume following document language. Explicitly clearing the language means unspecified. ISO 639 two/three-letter codes, case and underscores are normalized for comparison; script and region remain distinct.
- Language labels appear on shelf cards/list rows, Continue reading, History and the finish screen. The library offers a language filter. Book details and Reader actions offer edition selection for a shared nonempty series and volume/chapter number, respecting known chapter/volume kinds.
- Editions keep separate IDs, sources, positions, bookmarks, history, favorites and book settings. Switching resumes the selected edition. Next-unread suggestions stay in the current normalized language, with unspecified continuing only to unspecified. Existing series settings remain shared across the series.
- A nullable language override is included in existing serialized book payloads and JSON backups. Older records follow their document language. No Room schema change or destructive migration is needed. Rescans/relinks preserve overrides; metadata remains available separately.
- Reader actions → Export page selects a single page by number, including either member of a spread. Save uses Android `ACTION_CREATE_DOCUMENT`; share uses the Android chooser and `ACTION_SEND` with a content URI, ClipData and a temporary read-only grant.
- Archive/folder exports copy original PNG/JPEG/WebP bytes. PDF exports render a whole PNG at 2× point dimensions, proportionally bounded to 2048 × 3072. Export excludes reading adjustments and viewport transforms. Preparing an export uses its own document session and never opens a history visit or changes reading state.
- Export snapshots live only in the dedicated app-cache exports directory. The non-exported FileProvider exposes only that directory. Save cancellation/completion removes its snapshot; shared snapshots expire at a later startup after 24 hours. Pending picker data uses SavedStateHandle and refers to a fixed staged image rather than the current reader page/book.

## Automated checks

Build command:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

- Core JVM: **60 passed, 1 optional private-sample skip, 0 failures**. Six added cases cover language normalization/override precedence, grouping, next-volume rules, backup/merge identity and ComicInfo fallback when a manifest omits language.
- App JVM: **2 passed, 0 failures**.
- Debug APK/test APK builds passed for the configured native ABIs.
- Debug lint: **0 errors, 49 warnings**. Existing library/platform/deprecation suggestions remain; none block compilation.
- API 32 focused acceptance: **10 passed, 0 failures** before the final JPEG/WebP/PNG encoding case and screenshot captures were added. The first attempt had two test setup errors (a non-tree folder URI and comparison of a refreshed copy revision); both were corrected, and the original log remains preserved.

Final Android checks used the existing Pixel 5 **Android 12L / API 32 x86_64** emulator:

| Check | Result |
| --- | --- |
| Full regression suite | **90 passed, 5 expected skips, 0 failures**; 95 discovered cases in **92.963 seconds**, including the 11 new edition/export cases, existing reader/cover/display/storage/backup tests and the resource stress case |
| Final edition/export UI checks | **5 passed, 0 failures** in **16.612 seconds**. Added actual Android Documents picker cancellation with activity recreation, alongside selected-page validation/save/cancel, restored export state, manual language filtering, edition switching and bookmarks |
| Separate external process termination | Repository `test-process-restart.ps1 -Serial emulator-5580` passed its prepare/force-stop/new-process verification |
| Visual review | Settled screenshots of the language-edition picker and export dialog reviewed; language, filename, independent page progress and export controls are readable, including the export dialog with the numeric keyboard visible |

The extra real-picker test initially failed because `ActivityScenario.recreate()` requires the app to reach RESUMED while Android's picker keeps it stopped. The test now requests Android activity recreation directly, waits for the original activity to be destroyed, and cancels the picker; pending export data survives and its snapshot is cleaned afterward. No product-code workaround was needed. The earlier test log remains preserved.

The full-suite run preceded that one additional real-picker case and a removal of an obsolete string resource; the final UI pass used the rebuilt current APK. The total Android suite now contains 96 cases. Local logs and screenshots are kept in ignored `local-validation/editions-export/`.

## Grouped shelf refinement — 2026-10-28

The requested collection appearance is implemented in both library layouts: one stacked cover and a version-count badge per series/volume-or-chapter group. The most recently opened edition supplies the cover; before any edition is opened, a deterministic representative is used. Group identity remains stable when the representative changes. Unnumbered/unsorted books stay separate. Known volume/chapter kinds cannot be bridged through an untyped PDF or folder when both sequences share a number.

Tapping a stack opens **Choose version**, with per-edition covers, language, status, percentage/page progress, a progress bar and a details action. Selection resumes that edition's saved place. The picker survives activity recreation. Filters restrict eligible editions; one remaining match is shown as an individual book, and clearing filters restores the stack. Continue reading remains a direct resume action for its displayed edition.

Three additional core checks cover stable identity/representative selection, separate number/kind groups, and untyped-edition attribution. The Android UI flow checks grouped grid/list presentation, cover updates after reading another edition, saved progress, picker recreation and per-edition details access. The existing language-filter test now verifies that clearing a filter restores the group.

Final checks after this refinement: **63 core JVM passes and 1 optional skip**, **2 app JVM passes**, debug/test APK builds successful, and **0 lint errors / 49 warnings**. The final full **API 32** suite discovered **97 cases: 92 passed, 5 expected skips, 0 failures**, in **135.318 seconds**. Grid, list and version-picker screenshots were reviewed and saved in ignored local validation storage.

The initial existing-emulator attempt rejected the debug APK because the installed app had a different signing certificate. The original app/data were preserved; subsequent checks used a fresh `-datadir`/`-data` inside ignored local validation storage. In that fresh setup, an existing save-picker test needed to inspect all interactive accessibility windows because the filename keyboard could be the active window. The updated test dismisses that keyboard before cancelling the picker. Logs are retained under `local-validation/editions-export/grouped-*`.

An intermediate full run had one remaining test failure when checking the reader immediately after picker cancellation. The test now stops sending Back once the result arrives and waits for the reader to resume. Its isolated retry and the final full suite above passed; the earlier logs remain preserved. No export implementation change was required.

## API 26 picker recreation compatibility — 2026-10-28

CI reported a 10-second timeout in `androidDocumentPickerCancellationAfterRecreationKeepsTheSelectedSnapshot`. That wait checks destruction of the original reader activity while Android's save picker is in front. The test now calls `ActivityCompat.recreate` instead of calling the platform method directly. AndroidX supplies the older-platform recreation path, including API 26/27 relaunch/stop-order handling; see the [AndroidX implementation](https://raw.githubusercontent.com/androidx/androidx/androidx-main/core/core/src/main/java/androidx/core/app/ActivityCompat.java).

The 10-second wait and pending-export/snapshot/page checks are retained. An additional assertion verifies that the save picker remains open after recreation. The change is limited to the test and this validation record.

Local API 26 baseline runs of the unchanged isolated test and all six edition/export UI cases passed, including with animations disabled to match CI. The reported CI timeout was therefore not reproduced deterministically. After the compatibility change:

- The affected case passed **three consecutive API 26 runs**.
- The full **API 26 / Android 8.0** suite passed: **92 passed, 5 expected skips, 0 failures**, 97 discovered cases, **105.468 seconds**, with animations disabled.
- The **API 32** edition/export UI class passed: **6 passed, 0 failures**, **36.101 seconds**, with animations disabled.
- Test APK compilation and debug lint passed: **0 errors, 49 warnings**.

Both emulators used fresh app/data directories. Logs are retained in ignored `local-validation/api26-export-recreation/`. Remote CI confirmation remains pending.

## Remaining acceptance

- Physical-device testing with real document providers, removable storage, and external share receivers remains open. No physical phone was connected in this session.
- The export chooser/URI contract and document-provider saves are covered separately from actual receiver-app behavior. Cache eviction can require retrying an unfinished export.
- Broader device/API coverage and signed public-release acceptance remain separate from these local emulator checks. No release version was bumped or published.
