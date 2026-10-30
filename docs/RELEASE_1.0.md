# Panelgrove 1.0 launch preparation

Prepared **2026-10-30** for the first stable GitHub release. Version **1.0**, version code **9**, package `dev.soto.panelgrove`, Android **8.0+**. Public 0.9 remains the published prerelease until this candidate is signed, reviewed and published.

## Prepared scope

- The planned 1.0 features are implemented, including local reading statistics and backup retention. See the [release notes](releases/1.0.md), [user guide](USAGE.md#reading-statistics) and [roadmap](ROADMAP.md).
- The vertical-reader layout/restoration fix and UI-thread test corrections are committed in `eccd35b080d60486424f80741074502108093974`. Its [Build and test run](https://github.com/SotoOnGit/PanelGrove/actions/runs/41120333678) passed build, JVM/lint/package checks and both API 26/35 jobs.
- Version metadata is 1.0/code 9. The release workflow checks that the APK version matches the tag, requires versioned notes, and creates a draft using those notes.
- Local previews use the existing preview signing key. They are review artifacts and cannot update a public installation.

## Candidate validation

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

## Publish sequence

1. Review and commit the prepared files, push the release commit to `main`, and wait for **Build and test**, including API 26 and 35, at that exact commit.
2. Tag that passing commit **`v1.0`** and push the tag. **Signed draft release** uses the existing public signing secrets and produces `panelgrove-v1.0.apk`, `SHA256SUMS` and `SIGNING-CERTIFICATE.txt` in a GitHub draft. It does not publish automatically.
3. Verify the draft's APK package/version (1.0/code 9), checksum and public certificate SHA-256:

   ```text
   bdcf7e47ee8e19c06ee2c8f17f1e43a3390c8254d83ed9a369a3713685f2c955
   ```

4. Install that **publicly signed** candidate over public **0.9** on a development device with generated books. Confirm library/source access, progress, bookmarks, history, reader preferences, custom covers and new statistics. Also retain a 0.1.6 → 1.0 check for users skipping 0.9. Export a backup first; updating must not require uninstalling the public app.
5. Exercise save/share with an actual receiving app and confirm the reader and statistics after a cold start. Review the final APK, notes and screenshots.
6. Publish the reviewed draft as a **stable release**, with the prerelease checkbox clear. Update README/user-guide/roadmap status and download links to the now-published 1.0 assets; retain the measured validation limits.

No tag, push, GitHub draft, production-key signing operation or publication is performed by local preparation.

## Remaining coverage

Existing physical-device, broader provider/tablet, actual 16 KB runtime, accessibility and sustained-reading limits remain recorded in [VALIDATION.md](VALIDATION.md#remaining-limits). Local preview-key update rehearsals can validate migration and data retention, but cannot establish public signing continuity or acceptance of the final GitHub-signed APK.
