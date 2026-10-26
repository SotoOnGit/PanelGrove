# Panelgrove

<p align="center">
  <img src="branding/icon.svg" width="112" height="112" alt="Panelgrove icon">
</p>

A free, open source manga reader for Android. Read CBZ, CBR, PDF, and image folders offline, with no ads, accounts, or analytics.

**Android 8.0+ · Preview 0.1.6 · GPL-3.0-or-later**

[Releases](https://github.com/SotoOnGit/PanelGrove/releases) · [User guide](docs/USAGE.md) · [Build from source](docs/DEVELOPMENT.md) · [Report an issue](https://github.com/SotoOnGit/PanelGrove/issues)

## Screenshots

Actual app screens using original demo books. [View all screenshots](docs/screenshots/README.md), including the light theme and book details.

| Library | Reader | Bookmarks |
| --- | --- | --- |
| [<img src="docs/screenshots/library-dark.png" width="240" alt="Library in dark mode with Continue reading and sample books">](docs/screenshots/library-dark.png) | [<img src="docs/screenshots/reader.png" width="240" alt="Reader with a bookmarked sample page and page controls">](docs/screenshots/reader.png) | [<img src="docs/screenshots/bookmarks.png" width="240" alt="Contents sheet showing a saved bookmark">](docs/screenshots/bookmarks.png) |
| **Reading settings** | **History** | **Settings** |
| [<img src="docs/screenshots/reading-settings.png" width="240" alt="Reading direction, page layout, and reader preferences">](docs/screenshots/reading-settings.png) | [<img src="docs/screenshots/history.png" width="240" alt="Reading history grouped by day">](docs/screenshots/history.png) | [<img src="docs/screenshots/settings.png" width="240" alt="Appearance and file storage settings">](docs/screenshots/settings.png) |

## Your library, on your device

- **Read your own files.** Add individual books or library folders through Android's file picker. Files stay in their original locations by default.
- **Choose how you read.** Right-to-left or left-to-right page turns, continuous vertical scrolling, landscape spreads, fit options, pinch and double-tap zoom, and volume-key navigation.
- **Pick up where you left off.** Continue reading, saved positions, bookmarks, chapter navigation, and reading history.
- **Organize your collection.** Search, favorites, reading-status filters, series, collections, editable book details, and grid or list views.
- **Keep copies where you want them.** Optional managed copies live in a folder you choose, organized as `Series / Book file` and accessible through your file manager.
- **Make it comfortable.** Light, dark, system, and true-black appearance options, plus brightness and orientation controls.
- **Back up your reading data.** Export and restore library organization, reading progress, bookmarks, history, and settings as JSON.

## Supported formats

| Format | Support |
| --- | --- |
| CBZ / ZIP | PNG, JPEG, and WebP pages |
| CBR / RAR | RAR4, RAR5, and solid RAR5 archives |
| PDF | Rendered with Android's PDF engine |
| Image folders | PNG, JPEG, and WebP pages in selected directory trees |

Panelgrove reads `ComicInfo.xml` and compatible `translation-manifest.json` metadata when available. Invalid optional metadata falls back to normal page ordering.

Encrypted documents, multipart archives, EPUB, and CB7/7Z are not supported. Some RAR variants may be unsupported, and sequential RAR preparation can take longer than ZIP access.

## Get started

1. Download a signed APK from [GitHub Releases](https://github.com/SotoOnGit/PanelGrove/releases) when one is available, or [build from source](docs/DEVELOPMENT.md).
2. In **Library**, choose **Add manga → Choose files**, or add a library folder.
3. Open a book. Tap the middle of a page to show controls; tap either edge or swipe to turn pages. The default reading direction is right-to-left.

Updates are installed manually from GitHub Releases. See the [user guide](docs/USAGE.md) for reading controls, copies, backups, and recovering folder access.

## Files and privacy

Panelgrove works locally and requests neither internet access nor all-files access. Android's file and folder pickers control which documents it can read.

Removing a library entry leaves its external original intact. Deleting a managed copy is a separate choice. Backups contain reading data, not manga files or Android file-access grants; after restoring, select the relevant folders again to restore access.

Panelgrove does not include manga or provide a download catalog.

## Preview status

The current version is **0.1.6**, version code **7**, with package ID `dev.soto.panelgrove`.

Local validation recorded 49 passing core tests and 66 applicable Android test cases on each of API 26, 32, and 36. API 36 coverage spans two runs. Signed release launches and CBZ/RAR/PDF reading were also checked. These are recorded local results, not a GitHub Actions run or a guarantee for every device.

Broader physical-device and storage-provider coverage, removable SD cards, actual 16 KB memory-page execution, accessibility review, and sustained reading sessions remain open. See the [validation summary](docs/VALIDATION.md) for the measured scope and limitations.

## Development and feedback

Panelgrove is built with Kotlin, Jetpack Compose, Room, and Android's Storage Access Framework. The [development guide](docs/DEVELOPMENT.md) covers requirements, tests, localization, and release signing.

For a bug report, include the app version, Android version, document format, and steps to reproduce. Use generated or freely shareable samples when a sample is needed. Suggestions and pull requests are welcome through [GitHub](https://github.com/SotoOnGit/PanelGrove/issues).

## License

Panelgrove is licensed under **GPL-3.0-or-later**; see [LICENSE](LICENSE). Third-party licenses and notices are retained in [LICENSES](LICENSES/), the vendored libarchive source, and the app's notices. Generated test artwork is included under the project's license. Library books remain their owners' content.
