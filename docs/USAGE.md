# Using Panelgrove

## Install and update

Panelgrove requires **Android 8.0 or newer**. The first public prerelease is [0.1.6](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.1.6), published on **2026-10-27**.

1. Download [panelgrove-v0.1.6.apk](https://github.com/SotoOnGit/PanelGrove/releases/download/v0.1.6/panelgrove-v0.1.6.apk) on your Android device.
2. Open the downloaded APK. If Android asks, allow installation from the browser or file manager you used, then complete the installation.
3. Open Panelgrove and add your own books through **Library → Add manga**.

Updates are manual: download the APK from a newer [GitHub release](https://github.com/SotoOnGit/PanelGrove/releases) and install it over the existing public version. Public updates use the same signing identity so Android can retain the app's library, progress, bookmarks, and settings. Export a backup before updating.

### Moving from a local preview or debug build

Local preview and debug builds use different signing keys from the public release. Android will reject an installation over an app signed with a different key. See [Android's signing guidance](https://developer.android.com/studio/publish/app-signing#considerations).

Before switching, export a JSON backup through **Settings → Backup & restore**, keep it and your manga files outside the app's private storage, and confirm the backup file was saved. Then uninstall the local build, install the public APK, restore the backup, and select your book folders again through Android's picker. Uninstalling clears the app's private data; the JSON backup contains reading data, not manga files or file-access grants.

### Release verification files

Each public release includes `SHA256SUMS` for checking the downloaded APK's SHA-256 hash and `SIGNING-CERTIFICATE.txt` with the public signing-certificate fingerprint. These files are available beside the APK on the [release page](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.1.6).

## Add your manga

In **Library**, choose **Add manga → Choose files**, or add a library folder. Android's picker grants access to the documents you select. Panelgrove supports CBZ/ZIP, CBR/RAR, PDF, and image folders containing PNG, JPEG, or WebP pages.

Files stay where they are by default. A linked library folder can be rescanned from Library options or Settings. Shared or temporary documents may require a managed copy when Android cannot retain access to the original.

## Read and organize

Tap a book or its **Continue reading** card to open it. Tap the middle of a page for controls, and tap an edge or swipe to change pages. Reading defaults to right-to-left; reader settings also offer left-to-right navigation, vertical scrolling, spreads on wide screens, fit options, brightness, orientation lock, and volume-key navigation.

Pinch or double tap to zoom the reading viewport. Pan while zoomed; double tap again to reset. **Contents** provides chapter jumps and bookmarks. **Finish book** opens the completion screen.

Long press a grid cover, or use a details button in list or history rows, to edit a book's series, volume number, favorites, and collections. Library options includes series and collection filters, grid/list views, and folder rescanning.

**History** groups repeated opens of a book into one row per day, showing its latest time and session count. Clearing history keeps reading progress and bookmarks.

## Save optional copies

Enable **Settings → Files & storage → Copy imported files** and choose a writable folder. Cancelling the folder picker leaves copying disabled. Temporary imports and **Make a managed copy** also ask for a destination when needed.

Copies are organized as **Chosen folder / Series / Book file**. Panelgrove uses series metadata when present, then infers a series from the filename; unknown series go into **Unsorted**. Filename collisions receive numbered names without overwriting existing files. Editing a copied book's series reorganizes its file into the corresponding series folder.

Copies in the chosen folder are visible in your file manager. **Change import folder** changes the destination for future copies. Existing copies stay where they are until you choose **Move existing copies here**, which retains book identity, progress, bookmarks, and organization. Each old copy is removed only after its replacement has been saved and committed. Turning copying off keeps the chosen folder and existing copies.

Removing a library entry never deletes an external original. Deleting an owned managed copy is a separate choice. Unlinking a library folder stops its scans and leaves its books and files intact.

## Back up and restore

Use **Settings → Backup & restore** to export or restore a JSON backup. It includes library organization, reading positions, bookmarks, history, settings, and references to the copy destination and managed copies.

**Backups do not contain manga files or Android file-access grants.** Keep the books themselves separately. When moving to another installation, keep the original installation and files until the restored books open successfully.

After restoring, **Restore import folder** asks you to select the folder again through Android's picker, even if copying was off. If the folder moved, choose its new location. Panelgrove scans existing files in place, reconnects matching documents and uniquely matching copies by their saved content hashes, and adds other readable books it finds. This recovery does not move, recopy, or delete those files.

Choose **Later** to defer folder access. The request survives restarts; retry through **Settings → Find existing books**. Read-only access is sufficient for reading existing files. Individual unavailable books can also be relinked from Settings.

Repeating a restore retains repaired access locations and avoids duplicating restored reading data. Conflicting identifiers are remapped to protect unrelated books. If an original is unavailable, an intact managed copy remains readable.

## Appearance and troubleshooting

Settings offers **System**, **Light**, **Dark**, and **True black**. New installations default to dark.

If a folder or book becomes unavailable, select it again through Android's picker. Rescanning or explicitly relinking a corrected document refreshes its content while retaining surviving page positions and bookmarks. A bookmark whose page was removed opens the nearest remaining page and is labeled accordingly.

Encrypted documents, multipart archives, EPUB, and CB7/7Z are unsupported. Corrupt documents need a sound replacement. RAR preparation can be slower than ZIP access, particularly for sequential archives.

For a reproducible problem, [open an issue](https://github.com/SotoOnGit/PanelGrove/issues) with the app version, Android version, file format, and reproduction steps.
