# Panelgrove 1.0 release record

Panelgrove's first stable GitHub release was published on **2026-10-30**. Version **1.0**, version code **9**, package `dev.soto.panelgrove`, Android **8.0+**. Public release metadata, CI job results, checksum text and signing-certificate text were checked on **2026-11-02**. Dates use Europe/Bucharest; GitHub records publication as **2026-10-30 21:59:01 UTC**.

## Published release

The [v1.0 release](https://github.com/SotoOnGit/PanelGrove/releases/tag/v1.0) is public, marked stable, and reported by GitHub as the latest release. Tag `v1.0` points to commit `54b7affa6c5b250de4d9d625a9190f1f74b83bbd`.

| Check | Recorded result |
| --- | --- |
| [Build and test at the release tag](https://github.com/SotoOnGit/PanelGrove/actions/runs/41132967590) | Debug build, core/app JVM tests, lint, package checks and Android instrumentation on **API 26 and 35** passed |
| [Signed draft release](https://github.com/SotoOnGit/PanelGrove/actions/runs/41132967585) | Core/app JVM tests, release lint, minified signed build, tag/APK version gate, signature/native/ZIP alignment checks and checksum generation passed |
| Public assets | [panelgrove-v1.0.apk](https://github.com/SotoOnGit/PanelGrove/releases/download/v1.0/panelgrove-v1.0.apk) (**5,093,615 bytes**), [SHA256SUMS](https://github.com/SotoOnGit/PanelGrove/releases/download/v1.0/SHA256SUMS) and [SIGNING-CERTIFICATE.txt](https://github.com/SotoOnGit/PanelGrove/releases/download/v1.0/SIGNING-CERTIFICATE.txt) |
| Signing identity | Published certificate fingerprint matches public **0.9** and **0.1.6** |

The published checksum text and GitHub APK asset digest agree on SHA-256:

```text
e95da4143327e7f0811cbb6b381fb2fa6af9b1b538524bb3da243b14ba820c79
```

The published signing-certificate SHA-256 is:

```text
bdcf7e47ee8e19c06ee2c8f17f1e43a3390c8254d83ed9a369a3713685f2c955
```

These results establish publication, workflow success and the reported artifact identity. No new APK installation or device acceptance run was performed during this documentation update. Public-signed update retention and remaining physical-device coverage are listed below.

## Preparation baseline

- The planned 1.0 features are implemented, including local reading statistics and backup retention. See the [release notes](releases/1.0.md), [user guide](USAGE.md#reading-statistics) and [roadmap](ROADMAP.md).
- The vertical-reader layout/restoration fix and UI-thread test corrections are committed in `eccd35b080d60486424f80741074502108093974`. Its [Build and test run](https://github.com/SotoOnGit/PanelGrove/actions/runs/41120333678) passed build, JVM/lint/package checks and both API 26/35 jobs.
- Version metadata is 1.0/code 9. The release workflow checks that the APK version matches the tag, requires versioned notes, and creates a draft using those notes.
- Local previews use the existing preview signing key. They are review artifacts and cannot update a public installation.

## Historical local candidate validation

The following checks used **1.0 / code 9**, prepared from the passing `eccd35b` feature/fix baseline. Earlier full regression suites and device checks retain their original identities in the [validation summary](VALIDATION.md) and [statistics record](STATISTICS_VALIDATION.md).

| Check | Result |
| --- | --- |
| Local preview build | Minified, resource-shrunk release APK and debug/test APKs built successfully |
| JVM tests | **81 core passes / one optional skip**, **two app passes**, zero failures |
| Release lint | **Zero errors**, 50 existing/advisory warnings |
| APK identity | `dev.soto.panelgrove`, **1.0 / code 9**, minimum API 26, target API 36 |
| Package/signature | Preview signature verified; native ELF LOAD and APK ZIP alignment passed **16 KB** checks; no manga/document archives bundled |
| Permissions | No internet or all-files-access permission in the merged APK |
| Native packaging | ARM64, ARMv7 and x86-64 libraries present; this session's runtime checks used x86-64 emulators |
| Current UI/screenshots | **Six UI cases passed** on API 32; **11 unedited 1080 × 2340 captures** visually reviewed in the [gallery](screenshots/README.md) |
| Minified API 26 reading | **Four cases passed:** CBZ, RAR4, PDF and solid LZMA2 CB7 opened/rendered and recorded a viewed page plus reading time; screenshots visually reviewed |
| Minified update rehearsal | **0.9 → 1.0 on API 32 passed**, with schema 2 → 3 migration, the generated book/source, saved page, bookmark, history, reader overrides and byte-identical preference file retained; the retained source reopened and new statistics recorded |
| Release workflow/docs | YAML/Bash syntax passed; version gate accepted `v1.0` and rejected an APK/tag mismatch; local documentation/image links and anchors resolved |

The update rehearsal used a downloaded public 0.9 APK **re-signed locally with the preview key**, followed by the 1.0 minified preview. All 158 non-`META-INF` payload entries were byte-identical before and after re-signing. This checks actual old/new minified code and database migration while remaining separate from the final public-key update check.

The local review artifact is `dist/Panelgrove-1.0-preview.apk`, with matching `Panelgrove-1.0-preview-SHA256SUMS.txt` and `Panelgrove-1.0-preview-SIGNING-CERTIFICATE.txt`. It uses the local preview certificate and **cannot update a public installation**. Its SHA-256 is:

```text
0921d176966b1c9e964383a070e9c6ddad6988e637aa20a9f040023d60ce7d2d
```

Detailed build logs, CI metadata/logs, package/certificate checks, screenshots, database comparisons and smoke summaries remain in ignored `local-validation/release-1.0/`. The initial API 26 smoke setup launched as root and did not acquire a persistable source grant, correctly reaching the import-folder prompt. That attempt is retained; the passing attempts launched as Android's shell user, with root used only for emulator evidence collection. No app change was needed for that setup correction. The connected physical phone was not used or changed.

## Remaining coverage

- Public **0.9 → 1.0** and **0.1.6 → 1.0** update-install checks with retained library/source access, progress, bookmarks, history, reader preferences, custom covers and new statistics remain unrecorded. Matching public signing fingerprints establish identity continuity; the preview-key rehearsal above checks migration separately.
- Save/share with an actual receiving app and physical acceptance of the final signed 1.0 APK remain unrecorded.
- Broader provider/tablet, actual 16 KB runtime, accessibility and sustained-reading limits remain recorded in [VALIDATION.md](VALIDATION.md#remaining-limits).

Publication does not close these acceptance items. See the [development guide](DEVELOPMENT.md#public-releases) for the workflow used for future releases.
