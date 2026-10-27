# Panelgrove 0.1.6 validation summary

This summary covers **0.1.6**, version code **7**, package `dev.soto.panelgrove`. The [first public prerelease](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.1.6) was published on **2026-10-27**. Local-preview results from **2026-10-24** and remote CI/release results verified on **2026-10-27** are recorded separately below.

## GitHub CI and public release — verified 2026-10-27

Both workflows ran at commit `8d28a4716ca1de8be15cb10027b8021251aa6745`.

| Area | Recorded result |
| --- | --- |
| [Build and test](https://github.com/SotoOnGit/PanelGrove/actions/runs/40413500147) | Completed successfully on attempt 2; core tests, debug build, debug lint, and package checks passed |
| Android API 26 emulator | 66 passed, five deliberate skips, zero failures |
| Android API 35 emulator | 66 passed, five deliberate skips, zero failures |
| [Signed draft release](https://github.com/SotoOnGit/PanelGrove/actions/runs/40491207093) | Core tests, release lint, minified signed APK build, signature verification, ELF/ZIP alignment checks, and checksum generation passed |
| Public distribution | `v0.1.6` published as a prerelease with `panelgrove-v0.1.6.apk`, `SHA256SUMS`, and `SIGNING-CERTIFICATE.txt` |

Each remote Android suite discovered 71 cases. The five skips cover private-sample tests and separately invoked process-restart phases. The first CI attempt failed while downloading/unpacking the Android emulator before the Android tests started; rerunning the failed jobs completed both suites successfully without source changes.

The Android suites used debug APKs. Physical-device acceptance of the published signed APK remains open.

The published APK's SHA-256, verified against the downloaded file and release checksum on **2026-10-27**, is:

```text
a78d0da9d2da733657db63b0c92a8be997359a7b24aaae1ca9046de11c6132a7
```

The public signing certificate's SHA-256 fingerprint is recorded in the release's [SIGNING-CERTIFICATE.txt](https://github.com/SotoOnGit/PanelGrove/releases/download/v0.1.6/SIGNING-CERTIFICATE.txt):

```text
bdcf7e47ee8e19c06ee2c8f17f1e43a3390c8254d83ed9a369a3713685f2c955
```

## Local-preview checks — 2026-10-24

These earlier checks used the local-preview signing identity. They have not been rerun against the published APK as part of this documentation update.

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

## Local-preview storage and recovery

Actual Android Documents-picker checks on API 32 and API 36 exercised export/restore between separate installations and recovery from a moved folder. Three generated CBZ/RAR/PDF books retained identifiers, reading positions, bookmarks, history, metadata, settings, and file hashes. Repeating the restore retained repaired locations without duplicating reading data.

Picker process death, deferred access, picker cancellation followed by a Settings retry, restart/reboot persistence, and signed-release reading were checked. Separately invoked external force-stop/restart checks passed on API 32 and API 36, retaining saved reading data and recovering interrupted work.

## Public screenshots

On **2026-10-26**, the signed 0.1.6 local preview was installed on the owner-opened API 32 emulator for fresh screenshots. The library, reader, reading settings, bookmarks, book details, history, Settings, and light/dark library appearances were visibly inspected using original geometric demo books. The [eight screen captures](screenshots/README.md) retain the original Android screenshot pixels.

This was a documentation capture session, not a rerun of the automated suites above or a physical-device acceptance run.

## Remaining limits

- The tested emulators use **4 KB runtime memory pages**. Passing native ELF and APK alignment checks does not establish actual 16 KB runtime compatibility.
- Acceptance of the published APK on physical phones/tablets, fresh-install transfer on physical devices, removable SD cards, and additional production document providers remains open.
- Human accessibility review, broader light-theme review, and sustained physical-device reading and resource measurements remain open.
- Installation of a future public update over 0.1.6, using the same signing identity and preserving reading data, remains to be verified.

Panelgrove remains a preview. Test sources and original generated fixtures are included so contributors can repeat the automated checks; see the [development guide](DEVELOPMENT.md).
