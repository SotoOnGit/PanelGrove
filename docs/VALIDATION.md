# Panelgrove 0.1.6 validation summary

This summary covers **0.1.6**, version code **7**, package `dev.soto.panelgrove`. The [first public prerelease](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.1.6) was published on **2026-10-27**. Physical-phone checks, earlier local-preview emulator results, and remote CI/release results are recorded separately below.

Development work on **2026-10-28** adds [language editions and page export](EDITIONS_EXPORT_VALIDATION.md). That record covers the new source/build/emulator checks; these features are not part of the public 0.1.6 validation below.

## Physical phone — 2026-10-27

**Xiaomi M2012K11AG (alioth), Android 13 / API 33, ARM64, 4096-byte runtime memory pages.** Panelgrove was absent before testing. Debug/test APKs were built from commit `60f6391b96a8b8f648c3ac8c510484a20c10c3e2`; app/core sources match the public release tag. The debug installation was then replaced with the unchanged published signed APK, whose hash and certificate are recorded below.

| Check | Recorded result |
| --- | --- |
| Standard debug instrumentation | **All 66 applicable cases passed across separate runs**: archive engines/compatibility, generated provider storage and copies, backup recovery, database migration, lifecycle, failure recovery, UI and resource assertions |
| Resource stress case | Timed out at **90 seconds**, then **240 seconds**; a final run on a fresh debug installation with the app foregrounded **passed in 86.922 seconds**. Initial failures remain recorded; foreground state and cache population both changed, so the cause is not isolated |
| Automated UI cases | **12 passed in 40.404 seconds** after the owner enabled MIUI background pop-ups. The earlier suite and focused attempt were interrupted when MIUI denied the runner's launch of `MainActivity` |
| External process termination | Separate prepare/force-stop/new-process verification passed; page ID, vertical offset, bookmark, completion, interrupted-restore recovery and temporary-output cleanup were retained/recovered |
| Optional private 157-page CBZ | Passed separately: expected stable page IDs and chapter groups, five representative renders and twenty cached renders |
| Published release installation and reading | Fresh installation and cold launch passed; CBZ, native RAR5, PDF and image-folder pages visibly rendered through Android's actual Documents picker |
| Published release backup recovery | Exported through the UI, uninstalled, freshly installed the same public APK, restored through the picker and reacquired the library-folder grant using **Settings → Relink**; all four formats reopened at **2 / 4** after a cold launch |

The phone has passing coverage for all 66 applicable standard cases: 24 before the initial UI interruption, 29 in the remaining non-UI run, 12 in the successful UI retry and one in the final resource retry. Two opt-in compatibility cases were deliberately skipped before the interruption. Process restart and the private-volume check ran separately. This is **complete case coverage across runs, not an uninterrupted passing full suite**; the stress timing variability remains open. The earlier Gradle JVM results (49 passes and one optional skip) were up to date and reused; the APKs were rebuilt.

The final resource workload completed in **84.888 seconds** within the 86.922-second JUnit run. It traversed all 1,000 pages, issued/cancelled 50 rapid region requests and rendered the 20,000×20,000 image. Sampled app-process peaks were **31.2 MiB Java heap** and **413.6 MiB PSS**; bitmap-cache usage peaked at **42.5 MiB** against its heap-derived **42.7 MiB** budget, and page-cache usage at **19.9 MiB** against **512 MiB**. The largest bitmap was **6.0 MiB** and staging usage was zero. These are sampled observations from this run, not guaranteed total-memory ceilings or an explanation of the earlier timeouts.

The release backup comparison retained **four book IDs**, all saved page IDs/indices/offsets and book metadata/settings, **three bookmarks**, **four history entries**, the source ID and global preferences. All seven generated source files retained their SHA-256 hashes. Rescanning changed cache-generation IDs and restore added provenance keys, as expected. These were linked books in the phone's internal shared storage through Android Documents; removable media, other providers and physical managed-copy transfer were not exercised.

The optional local-volume check observed **820 ms** for import/index/cover, **1,830 ms total** for five representative page renders, and **29 ms total** for twenty cached renders. The archive was already in app storage; USB transfer time is excluded. These are single-session debug observations, not release performance guarantees or sustained-reading measurements.

The published APK remains installed with the four generated sample books and their backup files. Device logs, private samples, screenshots, APK hashes and comparison reports remain in ignored local validation storage.

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

The CI Android suites used debug APKs. The separate published-APK phone checks are recorded above.

The published APK's SHA-256, verified against the downloaded file and release checksum on **2026-10-27**, is:

```text
a78d0da9d2da733657db63b0c92a8be997359a7b24aaae1ca9046de11c6132a7
```

The public signing certificate's SHA-256 fingerprint is recorded in the release's [SIGNING-CERTIFICATE.txt](https://github.com/SotoOnGit/PanelGrove/releases/download/v0.1.6/SIGNING-CERTIFICATE.txt):

```text
bdcf7e47ee8e19c06ee2c8f17f1e43a3390c8254d83ed9a369a3713685f2c955
```

## Local-preview checks — 2026-10-24

These earlier emulator checks used the local-preview signing identity. The published-APK phone checks above are separate; this session did not rerun the emulator matrix.

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

- The tested emulators and Xiaomi phone use **4 KB runtime memory pages**. Passing native ELF and APK alignment checks does not establish actual 16 KB runtime compatibility.
- Investigate the phone's initial stress timeouts and reproduce the result with controlled foreground/cache conditions; a single uninterrupted full phone suite is not yet recorded. All 12 previously blocked UI cases and the final resource retry passed.
- The published APK's four-format reading and fresh-install linked-folder restore pass on one phone/provider. Tablets, removable SD cards, physical managed-copy transfer and additional production providers remain open.
- Human accessibility review, broader light-theme review, sustained physical-device reading and broader resource measurements remain open.
- Installation of a future public update over 0.1.6, using the same signing identity and preserving reading data, remains to be verified.

Panelgrove remains a preview. Test sources and original generated fixtures are included so contributors can repeat the automated checks; see the [development guide](DEVELOPMENT.md).
