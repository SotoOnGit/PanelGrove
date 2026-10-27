# CB7 / 7Z development validation

This feature is implemented after public 0.1.6 and is pending release. It adds `SEVEN_Z` to the existing book model and shares the isolated libarchive decoder used for RAR. Native builds statically link checksum-pinned XZ Utils 5.8.4/liblzma for LZMA and LZMA2; no codec download or new Android permission is needed at runtime.

Supported methods are LZMA, LZMA2, stored, Deflate, and PPMd. Solid archives are supported, with sequential preparation costs when jumping to uncached pages. Encrypted and multipart archives, BZip2, and other unlisted methods are unsupported. Existing path, link, entry-count, expanded-size, metadata-size, cache, and decoder timeout limits still apply.

## Verification

Validation date: **2026-10-27**.

Build and checks:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --no-daemon
python scripts/check_apk.py app/build/outputs/apk/debug/app-debug.apk
```

The focused `SevenZipInstrumentedTest` covers:

- Solid LZMA2, non-solid LZMA, stored, Deflate, and PPMd extraction in nonsequential page order, checked byte for byte against the original generated pages, plus rendering and zoom regions.
- Unicode filenames, natural page ordering, `ComicInfo.xml`, manifest ordering, stable page IDs, and malformed optional metadata fallback.
- Encrypted headers and data, corruption, unsafe paths, oversized entries, no image pages, CRC failures, unsupported BZip2, and successful reading after decoder errors.
- CB7/7Z folder scanning, rescanning, saved positions, bookmarks, favorites, manual titles, history, and detection by actual container even when extensions are wrong.
- Nonseekable document-provider staging, managed copies, filename retention, and Android file-opening MIME types.

Core tests cover signatures, extension matching, split-extension exclusion, backup serialization/merge, reading-data retention, and copy naming.

Results:

| Check | Result |
| --- | --- |
| Core JVM tests | 49 passed, one optional supplied-sample test skipped |
| App JVM tests | Two passed |
| Debug app and Android test APKs | Built successfully, including ARM64, ARMv7, and x86-64 native libraries |
| Debug lint | Zero errors, 40 warnings; no findings in the new test files |
| Native packaging | All ELF LOAD segments aligned to at least 16 KiB; APK passed `zipalign -c -P 16 4`; no document archives bundled |
| API 26 / x86-64 | All seven new tests and 38 existing regression cases passed; two optional private-sample checks skipped |
| API 32 / x86-64 | The same 45 cases passed; two optional private-sample checks skipped |
| Physical Android 13 / API 33 / ARM64 | All seven new tests and 38 regression cases passed; two optional private-sample checks skipped. Real Documents-picker reading checks also passed. |

The regression selection includes `EngineInstrumentedTest`, `ArchiveCompatibilityTest`, `StorageInstrumentedTest`, `CopyFolderInstrumentedTest`, `BackupRecoveryInstrumentedTest`, and `DatabaseMigrationTest`. These are scoped archive/storage checks, not a rerun of the complete UI and resource-stress suite.

The existing API 36 AVD booted with PIN-locked credential-encrypted storage. Its attempted run could not open the test provider or app library and produced setup/cleanup failures; it is not a valid compatibility result. The emulator was stopped without changing its PIN or saved data, and API 32 supplied the second completed run. All emulators were used one at a time in read-only mode and stopped afterward.

Local logs are retained under ignored `local-validation/cb7-*` paths. The emulator-tested debug APK had SHA-256 `8edd9a29639d367a087c8cf90e5078df572fcc83165851fd26b5a49cd0d8eea0`. API 36 acceptance, actual 16 KiB runtime execution, and a signed public release remain separate follow-up checks.

## Physical-device acceptance

The connected Xiaomi M2012K11AG ran Android 13 / API 33 on ARM64, with 4 KiB pages. To preserve the installed signed public release and its reading data, testing used a local-only side-by-side package, `dev.soto.panelgrove.cb7qa`, labeled **Panelgrove CB7 test**. The main sources were unchanged; a temporary Gradle init script changed the application identity/label and isolated the test-provider authority. Its native decoder bytes matched the normal development APK for all three ABIs. This confirms ARM64 functionality in a development build, not a signed public update.

The selected Android suite completed in **21.64 seconds** with **45 passed, zero failed, and two optional private-sample checks skipped**, including all seven CB7/7Z cases. An initial attempt used the original test-provider address and failed during harness setup; the local Kotlin test-source configuration was corrected before the successful run. Both logs are retained.

Additional manual checks used generated files under `Download/Panelgrove-CB7-QA-20261027`, selected through Android's actual Documents picker:

- A solid CB7 imported with its metadata and four pages, rendered correctly, and navigated to page 3.
- Its page-3 bookmark and reading position survived an external force-stop and reopening.
- A 7Z containing Japanese/emoji paths imported with `ComicInfo.xml` series metadata, rendered, and navigated through its three pages.
- A corrupt 7Z produced an actionable import-error dialog without crashing or creating a readable library entry.

The standard Downloads provider initially omitted the ADB-copied files; the device-storage provider exposed the same directory and completed the imports. The separate QA app and two working library entries remain available for manual use. The generated test harness was removed after testing. The original public APK's SHA-256 remained `a78d0da9d2da733657db63b0c92a8be997359a7b24aaae1ca9046de11c6132a7` before and after; its installation and data were not replaced or cleared.

Evidence, screenshots, package hashes, and the result summary are retained locally under `local-validation/cb7-physical/`. The normal development APK outputs were rebuilt after the isolated QA build. No feature-code changes were needed during physical acceptance.
