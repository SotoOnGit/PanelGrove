# Using Panelgrove

## Install and update

Panelgrove requires **Android 8.0 or newer**. This guide covers **1.0 / version code 9**, including reading statistics. Version 1.0 is prepared for release; the current public download remains [0.9](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.9) until it is published. See the [1.0 notes](releases/1.0.md) and [launch status](RELEASE_1.0.md).

1. Download the publicly signed APK from [GitHub Releases](https://github.com/SotoOnGit/PanelGrove/releases) on your Android device. The 1.0 asset will be named `panelgrove-v1.0.apk` when published.
2. Open the downloaded APK. If Android asks, allow installation from the browser or file manager you used, then complete the installation.
3. Open Panelgrove and add your own books through **Library → Add manga**.

Updates are manual: download the APK from a newer [GitHub release](https://github.com/SotoOnGit/PanelGrove/releases) and install it over the existing public version. Public 0.1.6 and 0.9 use the same signing identity so Android can retain the app's library, progress, bookmarks, and settings. Export a JSON backup before updating and keep your manga files separately; uninstalling is not part of a public-version update.

### Moving from a local preview or debug build

Local preview and debug builds use different signing keys from the public release. Android will reject an installation over an app signed with a different key. See [Android's signing guidance](https://developer.android.com/studio/publish/app-signing#considerations).

Before switching, export a JSON backup through **Settings → Backup & restore**, keep it and your manga files outside the app's private storage, and confirm the backup file was saved. Then uninstall the local build, install the public APK, restore the backup, and select your book folders again through Android's picker. Uninstalling clears the app's private data; the JSON backup contains reading data, not manga files or file-access grants.

### Release verification files

Each public release includes `SHA256SUMS` for checking the downloaded APK's SHA-256 hash and `SIGNING-CERTIFICATE.txt` with the public signing-certificate fingerprint. These files are available beside the APK on its [release page](https://github.com/SotoOnGit/PanelGrove/releases).

## Add your manga

In **Library**, choose **Add manga → Choose files**, or add a library folder. Android's picker grants access to the documents you select. Panelgrove supports CBZ/ZIP, CBR/RAR, CB7/7Z, PDF, and image folders containing PNG, JPEG, or WebP pages.

Files stay where they are by default. A linked library folder can be rescanned from Library options or Settings. Shared or temporary documents may require a managed copy when Android cannot retain access to the original.

## Read and organize

Tap a book or its **Continue reading** card to open it. Tap the middle of a page for controls, and tap an edge or swipe to change pages. Reading defaults to right-to-left; reader settings also offer left-to-right navigation, vertical scrolling, spreads on wide screens, fit options, brightness, orientation lock, and volume-key navigation.

Pinch or double tap to zoom the reading viewport. Pan while zoomed; double tap again to reset. **Contents** provides chapter jumps and bookmarks. **Finish book** opens the completion screen.

Long press a grid cover, or use a details button in list or history rows, to edit a book's series, volume number, favorites, and collections. Library options includes series and collection filters, grid/list views, and folder rescanning.

**History** groups repeated opens of a book into one row per day, showing its latest time and session count. Clearing history keeps reading progress and bookmarks.

## Custom covers and display adjustments

Open **Book details → Choose custom cover** and select an image with Android's picker. The cover changes immediately and is retained through reopening, rescanning and relinking. **Remove custom cover** restores the document's generated cover. Images are copied into app storage, so access to the chosen image is not required afterward. PNG, JPEG and WebP images are supported; camera orientation is respected. Input images are limited to 32 MiB and are reduced to a thumbnail of at most 720 pixels per side. The source book and selected image are left intact.

In a book, open **Reading settings → Display adjustments**. The current-page preview updates as you change **Contrast** and **Gamma** from 0.50 to 2.00. Neutral is 1.00; gamma above 1 brightens midtones. **Reset display adjustments** returns both controls to neutral. **Save** retains the draft for this book; **Cancel**, Back or dismissing the sheet discards it. **Use defaults** clears all book reading overrides, including these adjustments. Reader pages and zoom detail use the same adjustment curve; the book's images remain unchanged.

Display adjustments are also available in default and series reading settings. Book overrides take precedence, followed by series settings and then defaults. Contrast and gamma settings are included in JSON backups. **Custom cover images are local to this installation and are not included in JSON backups.** Restoring on the same installation keeps existing local covers; choose them again after moving to a fresh installation.

## Language editions

Import each edition separately, in any supported format. In **Book details**, give editions the same **Series** and **Vol/ch** number. **Choose existing series** helps match translated titles to an existing series. Select a language with **Choose language**, or enter a language code such as `en`, `ja`, or `zh-Hant`. Leave the field blank for unspecified, or choose **Use document language** to follow `ComicInfo.xml` or translation-manifest metadata. Choose **Save** to retain the assignment.

On the shelf, editions of the same volume/chapter appear as one stacked-cover item in both grid and list views. It uses the most recently opened edition's cover and shows a version-count badge. Each volume/chapter has its own item; a whole series is not collapsed into one stack. Tap a stack to open **Choose version**, with a cover thumbnail, language, reading status, progress bar and saved-page percentage for each edition. The **⋮** button beside an edition opens its Book details.

Individual books, Continue reading and History show language labels. **Library options → Filter by language** narrows each stack to matching editions, including unspecified; a single matching edition appears as an ordinary book and opens directly. Other library filters work the same way. **Language editions** in Book details or **Reader actions (⋮)** also switches between editions. Unnumbered books remain separate on the shelf; enter a volume/chapter number to link them as editions. **Continue reading** resumes its displayed edition directly.

Every edition retains its own source, reading position, bookmarks, history, favorites and book reading settings. Switching editions resumes that edition's saved place. **Read next** stays in the same language; a regional/script tag such as `zh-Hant` is distinct from `zh-Hans`, and unspecified does not guess a language. Series reading overrides still apply across the shared series. Language assignments survive rescans, relinking and JSON backups/restores.

## Page export

In the reader, open **Reader actions (⋮) → Export page**. The page field starts at the current page; enter another page number if needed, including either individual page in a spread. **Save image** opens Android's document picker to choose a location. **Share page** opens Android's share sheet. Cancelling either control returns to reading without changing your place.

Archive and folder pages retain their original PNG, JPEG or WebP bytes and dimensions. PDF pages export as full-page PNGs, rendered at twice their dimensions in points, reduced proportionally if needed to fit 2048 × 3072 pixels. Exported images exclude zoom/cropping, spread composition and display adjustments. Filenames include the book title, language when known, and page number. Export never modifies the source book or marks it read.

Sharing grants the receiving app temporary read access to an export snapshot. These snapshots are kept in app cache and expire during a later app startup after 24 hours. Save the image to retain a lasting copy. A staged save survives activity recreation; if Android has removed its cache, retry the export.

<a id="reading-statistics-development-build"></a>

## Reading statistics

The **Statistics** tab in 1.0 adds week/month reading-time graphs, daily breakdowns, pages viewed, volumes/chapters finished, reading days and streaks, series/book rankings, session summaries, language breakdowns and a yearly activity calendar. Public 0.9 does not include statistics.

Use the arrows to browse periods. The comparison initially uses the preceding week/month; its own arrows select an older comparison period. Solid bars show the selected period and light bars show the comparison. For the current week/month, the comparison stops at the same local time on the corresponding day of the earlier period. Actual active intervals preserve menu/background gaps at that cutoff. Different month lengths are limited to the days available in the comparison month. Tap a bar for that day's details, then expand a book and chapter to see session portions. Series/book rankings also open details. **All languages** filters the graph, totals, streaks and calendar; the language breakdown lists every language in the selected period and tapping one applies its filter.

Time counts only while rendered pages are visible in a resumed, focused reader. Loading, failed pages, covering menus/dialogs, background use, screen locking and manual pauses are excluded. The pause/play button beside reading settings pauses or resumes the timer. **Settings → Statistics** changes the inactivity limit (1, 3, 5, 10 or 30 minutes; default 5). Interaction resumes an idle timer. Foreground reading time is an estimate: Panelgrove cannot know whether someone is still reading an untouched page.

Time is split at local midnight, with the local date retained even if the device later changes time zone. In spreads or vertical scrolling, time is divided among visible chapters without multiplying the total. A page counts after two seconds of visible reading; a spread can count both pages. Skipped/preloaded pages do not count. Pages are distinct by edition and page within the selected period, so revisiting a page adds time without repeatedly adding pages.

Finishing through the reader records a dated volume/chapter completion. Merely marking a library item **Read**, importing old progress or restoring an old backup does not invent a finish. Internal chapters complete after all their known pages have been viewed; unlabeled pages remain in the book's total. Front/back matter does not count as a chapter. File-level finishes use chapter-book metadata when supplied; other files count as volumes. Unique work completions count a numbered volume/chapter once across language editions; editions finished are separate, and subsequent finishes of the same edition/chapter are rereads. Under a language filter, unique finishes are calculated within that language. **Read again** starts another reading pass. Chapter breakdowns depend on document chapter metadata; Panelgrove does not infer missing chapter boundaries.

A reading session ends when you leave a book or take an idle/background break at least as long as the chosen inactivity limit. Brief pauses and activity recreation retain the session. Session summaries show only the portion inside the selected period/language. Any positive recorded reading time makes a reading day. A current streak can end yesterday while today is still in progress; longest streak is across all recorded dates. Streaks follow the selected language filter. Scroll the calendar horizontally across the year, use its arrows for older years, and tap a day for its breakdown.

Statistics are kept in dedicated local database tables, separately from the bounded opening history. Removing a book or clearing history keeps its statistics and title/chapter snapshots. **Tracking since** identifies the start of measurement; past opening history cannot establish accurate time or pages. Checkpoints are saved every 15 seconds and when pausing/leaving; an abrupt process termination can lose the most recent unsaved interval. Goals and time-remaining estimates are not included.

The existing JSON backup includes all retained statistics, even for removed books. New exports use backup version 2, requiring a statistics-capable build to restore; older version 1 backups remain supported and leave existing statistics intact. Restoring the same backup repeatedly or restoring an older checkpoint merges stable records without summing them twice. Back up periodically to preserve the log across device loss or uninstall.

## Save optional copies

Enable **Settings → Files & storage → Copy imported files** and choose a writable folder. Cancelling the folder picker leaves copying disabled. Temporary imports and **Make a managed copy** also ask for a destination when needed.

Copies are organized as **Chosen folder / Series / Book file**. Panelgrove uses series metadata when present, then infers a series from the filename; unknown series go into **Unsorted**. Filename collisions receive numbered names without overwriting existing files. Editing a copied book's series reorganizes its file into the corresponding series folder.

Copies in the chosen folder are visible in your file manager. **Change import folder** changes the destination for future copies. Existing copies stay where they are until you choose **Move existing copies here**, which retains book identity, progress, bookmarks, and organization. Each old copy is removed only after its replacement has been saved and committed. Turning copying off keeps the chosen folder and existing copies.

Removing a library entry never deletes an external original. Deleting an owned managed copy is a separate choice. Unlinking a library folder stops its scans and leaves its books and files intact.

## Back up and restore

Use **Settings → Backup & restore** to export or restore a JSON backup. It includes library organization, language assignments, reading positions, bookmarks, history, reading statistics, settings (including contrast and gamma), and references to the copy destination and managed copies.

**Backups do not contain manga files, custom cover images, or Android file-access grants.** Keep the books themselves separately. Choose custom covers again on a fresh installation. When moving to another installation, keep the original installation and files until the restored books open successfully.

After restoring, **Restore import folder** asks you to select the folder again through Android's picker, even if copying was off. If the folder moved, choose its new location. Panelgrove scans existing files in place, reconnects matching documents and uniquely matching copies by their saved content hashes, and adds other readable books it finds. This recovery does not move, recopy, or delete those files.

Choose **Later** to defer folder access. The request survives restarts; retry through **Settings → Find existing books**. Read-only access is sufficient for reading existing files. Individual unavailable books can also be relinked from Settings.

Repeating a restore retains repaired access locations and avoids duplicating restored reading data. Conflicting identifiers are remapped to protect unrelated books. If an original is unavailable, an intact managed copy remains readable.

## Appearance and troubleshooting

Settings offers **System**, **Light**, **Dark**, and **True black**. New installations default to dark.

If a folder or book becomes unavailable, select it again through Android's picker. Rescanning or explicitly relinking a corrected document refreshes its content while retaining surviving page positions and bookmarks. A bookmark whose page was removed opens the nearest remaining page and is labeled accordingly.

Encrypted documents, multipart archives, and EPUB are unsupported. Corrupt documents need a sound replacement. RAR and 7Z preparation can be slower than ZIP access, particularly for solid archives and jumps to later pages.

CB7/7Z support covers LZMA, LZMA2, stored, Deflate, and PPMd compression, including solid archives, Unicode filenames, `ComicInfo.xml`, and `translation-manifest.json`. BZip2 and other unlisted compression methods are unsupported. Password-protected archives (including encrypted filenames) and split files such as `.7z.001` are unsupported. Existing extraction limits apply: 20,000 entries, 128 MiB per entry, and 8 GiB expanded in total. Pages use the same natural ordering, bookmarks, progress, and library organization as other comic archives.

For a reproducible problem, [open an issue](https://github.com/SotoOnGit/PanelGrove/issues) with the app version, Android version, file format, and reproduction steps.
