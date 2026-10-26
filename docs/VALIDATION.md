# Panelgrove 0.1.6 validation summary

This summary describes the current preview, version code **7**, package `dev.soto.panelgrove`. The results below were recorded locally on **2026-10-24**. They have not been rerun as part of preparing the public documentation and do not represent remote GitHub Actions results or validation of a future production signing key.

## Executed checks

| Area | Recorded result |
| --- | --- |
| Core Kotlin tests | 49 passed; one optional private-sample test skipped |
| Android 8 / API 26 emulator | 66 applicable cases passed; five deliberate skips |
| Android 12L / API 32 emulator | 66 applicable cases passed; five deliberate skips |
| Android 16 / API 36 emulator | All 66 applicable cases passed across two runs; five deliberate skips |
| Builds and lint | Debug/test APKs and minified release built; release lint had 0 errors and 47 warnings |
| Package verification | Local-preview signature, native 16 KB ELF alignment, and APK ZIP alignment checks passed |
| Release smoke checks | Signed minified release cold-launched on API 26, 32, and 36; CBZ, native RAR, and PDF rendering checked on API 32 and 36 |
| Launcher artwork | Normal adaptive and monochrome themed icons visually checked on an API 36 launcher |

The Android suites exercise ZIP/RAR/PDF engines, Unicode names, image folders, a 1,000-page archive, a large generated image, storage-provider behavior, database migration, backup recovery, reading gestures, settings, and activity recreation. The five skips comprise an optional private sample, two separately invoked process phases, and two opt-in private compatibility tests.

API 26 and API 32 completed full suites. The first API 36 run completed 60 passing cases before the emulator stopped during a resource test; no app failure code was emitted, and the cause was not established. A subsequent run passed the remaining six cases. A case-by-case comparison confirmed complete coverage. This is not an uninterrupted API 36 suite result.

## Storage and recovery

Actual Android Documents-picker checks on API 32 and API 36 exercised export/restore between separate installations and recovery from a moved folder. Three generated CBZ/RAR/PDF books retained identifiers, reading positions, bookmarks, history, metadata, settings, and file hashes. Repeating the restore retained repaired locations without duplicating reading data.

Picker process death, deferred access, picker cancellation followed by a Settings retry, restart/reboot persistence, and signed-release reading were checked. Separately invoked external force-stop/restart checks passed on API 32 and API 36, retaining saved reading data and recovering interrupted work.

## Public screenshots

On **2026-10-26**, the signed 0.1.6 local preview was installed on the owner-opened API 32 emulator for fresh screenshots. The library, reader, reading settings, bookmarks, book details, history, Settings, and light/dark library appearances were visibly inspected using original geometric demo books. The [eight screen captures](screenshots/README.md) retain the original Android screenshot pixels.

This was a documentation capture session, not a rerun of the automated suites above or a physical-device acceptance run.

## Remaining limits

- The tested emulators use **4 KB runtime memory pages**. Passing native ELF and APK alignment checks does not establish actual 16 KB runtime compatibility.
- Current-preview coverage on physical phones/tablets, fresh-install transfer on physical devices, removable SD cards, and additional production document providers remains open.
- Human accessibility review, broader light-theme review, and sustained physical-device reading and resource measurements remain open.
- A production signing identity, public APK distribution, and successful remote CI/release runs remain initial-release work.

Panelgrove remains a preview. Test sources and original generated fixtures are included so contributors can repeat the automated checks; see the [development guide](DEVELOPMENT.md).
