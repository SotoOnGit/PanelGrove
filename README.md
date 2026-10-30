# Panelgrove

<p align="center">
  <img src="branding/icon.svg" width="112" height="112" alt="Panelgrove icon">
</p>

A free, open source manga reader for Android. Read CBZ, CBR, CB7/7Z, PDF, and image folders offline, with no ads, accounts, or analytics.

**Android 8.0+ · Prerelease 0.9 · GPL-3.0-or-later**

[Download APK](https://github.com/SotoOnGit/PanelGrove/releases/download/v0.9/panelgrove-v0.9.apk) · [Release notes](docs/releases/0.9.md) · [Releases](https://github.com/SotoOnGit/PanelGrove/releases) · [User guide](docs/USAGE.md) · [Build from source](docs/DEVELOPMENT.md) · [Report an issue](https://github.com/SotoOnGit/PanelGrove/issues)

**New in 0.9:** language editions grouped by volume or chapter, page export, custom covers, contrast and gamma controls, and CB7/7Z support. Local reading statistics are planned for 1.0.

## Screenshots

Actual app screens using original demo books, captured from 0.1.6. The new 0.9 controls are not pictured. [View all screenshots](docs/screenshots/README.md), including the light theme and book details.

| Library | Reader | Bookmarks |
| --- | --- | --- |
| [<img src="docs/screenshots/library-dark.png" width="240" alt="Library in dark mode with Continue reading and sample books">](docs/screenshots/library-dark.png) | [<img src="docs/screenshots/reader.png" width="240" alt="Reader with a bookmarked sample page and page controls">](docs/screenshots/reader.png) | [<img src="docs/screenshots/bookmarks.png" width="240" alt="Contents sheet showing a saved bookmark">](docs/screenshots/bookmarks.png) |
| **Reading settings** | **History** | **Settings** |
| [<img src="docs/screenshots/reading-settings.png" width="240" alt="Reading direction, page layout, and reader preferences">](docs/screenshots/reading-settings.png) | [<img src="docs/screenshots/history.png" width="240" alt="Reading history grouped by day">](docs/screenshots/history.png) | [<img src="docs/screenshots/settings.png" width="240" alt="Appearance and file storage settings">](docs/screenshots/settings.png) |

## Your library, on your device

- **Read your own files.** Add individual books or library folders through Android's file picker. Files stay in their original locations by default.
- **Choose how you read.** Right-to-left or left-to-right page turns, continuous vertical scrolling, landscape spreads, fit options, pinch and double-tap zoom, and volume-key navigation.
- **Pick up where you left off.** Continue reading, saved positions, bookmarks, chapter navigation, and reading history.
- **Organize your collection.** Search, favorites, reading-status and language filters, series, collections, editable book details, and grid or list views. Language editions share a volume/chapter stack while keeping separate reading progress.
- **Choose your covers.** Set a custom library cover without changing the source book.
- **Save or share a page.** Export full pages through Android; archive and folder images retain their original bytes, and PDF pages export as PNG.
- **Keep copies where you want them.** Optional managed copies live in a folder you choose, organized as `Series / Book file` and accessible through your file manager.
- **Make it comfortable.** Light, dark, system, and true-black appearance options, plus brightness, orientation, contrast, and gamma controls.
- **Back up your reading data.** Export and restore library organization, reading progress, bookmarks, history, and settings as JSON.

## Supported formats

| Format | Support |
| --- | --- |
| CBZ / ZIP | PNG, JPEG, and WebP pages |
| CBR / RAR | RAR4, RAR5, and solid RAR5 archives |
| CB7 / 7Z | Solid and non-solid LZMA/LZMA2, stored, Deflate, and PPMd archives |
| PDF | Rendered with Android's PDF engine |
| Image folders | PNG, JPEG, and WebP pages in selected directory trees |

Panelgrove reads `ComicInfo.xml` and compatible `translation-manifest.json` metadata when available. Invalid optional metadata falls back to normal page ordering.

Encrypted documents, multipart archives, and EPUB are not supported. BZip2 and other unlisted 7Z compression methods are unsupported. Some RAR variants may be unsupported. Solid RAR and 7Z archives can take longer to prepare when jumping between pages because earlier compressed data must be read again. Archives retain the existing limits of 20,000 entries, 128 MiB per entry, and 8 GiB expanded in total.

## Get started

1. Download and install [panelgrove-v0.9.apk](https://github.com/SotoOnGit/PanelGrove/releases/download/v0.9/panelgrove-v0.9.apk) from the [0.9 prerelease](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.9). See the [installation guide](docs/USAGE.md#install-and-update) for Android's installation prompt and updates.
2. In **Library**, choose **Add manga → Choose files**, or add a library folder.
3. Open a book. Tap the middle of a page to show controls; tap either edge or swipe to turn pages. The default reading direction is right-to-left.

Updates are installed manually from GitHub Releases. Export a JSON backup before updating from public 0.1.6, then install 0.9 over the existing app. Both releases use the same public signing identity. If you used a local preview or debug build, follow the [signing-transition instructions](docs/USAGE.md#moving-from-a-local-preview-or-debug-build) before switching to the public APK.

## Files and privacy

Panelgrove works locally and requests neither internet access nor all-files access. Android's file and folder pickers control which documents it can read.

Removing a library entry leaves its external original intact. Deleting a managed copy is a separate choice. Backups contain reading data, language assignments, and display settings; they exclude manga files, custom cover images, and Android file-access grants. After restoring on a fresh installation, select the relevant folders again and choose custom covers again.

Panelgrove does not include manga or provide a download catalog.

## Preview status

The current public prerelease, [**0.9**](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.9), was published on **2026-10-29**. Its version code is **8** and package ID is `dev.soto.panelgrove`. The release includes the signed APK, `SHA256SUMS`, and `SIGNING-CERTIFICATE.txt`.

[GitHub CI](https://github.com/SotoOnGit/PanelGrove/actions/runs/40780054553) passed the debug build, core tests, lint, package checks, and Android tests on API 26 and 35 at the v0.9 release commit. The [release workflow](https://github.com/SotoOnGit/PanelGrove/actions/runs/40780054592) also passed core tests, release lint, the signed release build, signature verification, native/ZIP alignment checks, and checksum generation.

Final local feature suites passed **92 cases with five expected skips** on each of API 26 and 32. Earlier physical-phone checks cover public 0.1.6 and separate development builds for CB7, covers, and display adjustments; their results are recorded with their original build identities.

Physical validation of the signed 0.9 update over 0.1.6, edition/export flows with external share receivers, broader providers/tablets, actual 16 KB execution, accessibility, and sustained reading remain open. See the [validation summary](docs/VALIDATION.md) for the measured scope.

## Planned for 1.0

**Local reading statistics** are the remaining planned feature for 1.0; their initial measurements still need defining. CB7/7Z, custom covers, contrast/gamma, language editions, and page export are included in 0.9. See the [roadmap](docs/ROADMAP.md), [usage guide](docs/USAGE.md#language-editions), and [edition/export validation](docs/EDITIONS_EXPORT_VALIDATION.md).

## Development and feedback

Panelgrove is built with Kotlin, Jetpack Compose, Room, and Android's Storage Access Framework. The [development guide](docs/DEVELOPMENT.md) covers requirements, tests, localization, and release signing.

For a bug report, include the app version, Android version, document format, and steps to reproduce. Use generated or freely shareable samples when a sample is needed. Suggestions and pull requests are welcome through [GitHub](https://github.com/SotoOnGit/PanelGrove/issues).

## License

Panelgrove is licensed under **GPL-3.0-or-later**; see [LICENSE](LICENSE). Third-party licenses and notices are retained in [LICENSES](LICENSES/), the vendored libarchive source, and the app's notices. Generated test artwork is included under the project's license. Library books remain their owners' content.
