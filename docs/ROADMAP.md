# Panelgrove roadmap

Updated **2026-10-30**. The current public prerelease is [**0.9**](https://github.com/SotoOnGit/PanelGrove/releases/tag/v0.9), version code **8**, published on **2026-10-29**. It includes the first five feature priorities below.

The [0.9 release notes](releases/0.9.md) describe the shipped features. Local reading statistics are implemented in development for 1.0. Outstanding acceptance work, including a signed 0.9 update over 0.1.6, is tracked in the [validation summary](VALIDATION.md).

## Priorities for 1.0

| Priority | Feature | Intended scope | Status |
| --- | --- | --- | --- |
| 1 | CB7 / 7Z formats | Read image-based CB7 and 7Z archives with the existing ordering, navigation and reading-data behavior. Document supported archive variants and limits. | Released in 0.9 |
| 2 | Custom covers | Choose and remove a book's custom library cover; preserve the choice through reopening and rescanning without changing the source document. | Released in 0.9 |
| 3 | Contrast and gamma controls | Preview, adjust, reset and retain display adjustments in the reader. Keep source images unchanged. | Released in 0.9 |
| 4 | Different languages for the same series | Attribute separate books in any supported format to one shared series, identify their language editions and make those editions easy to select. | Released in 0.9 |
| 5 | Page export | Save or share a selected page as a full-page image through Android's system controls. Keep the source document unchanged. | Released in 0.9 |
| 6 | Statistics | Reading time and comparisons, pages viewed, completions, streaks, rankings, sessions, language breakdowns and an activity calendar, retained locally and included in backups. | Implemented in development; pending release |

CB7/7Z implementation scope and checks are recorded in [feature validation](CB7_VALIDATION.md).

Custom covers and display adjustments are available in 0.9. Covers are copied to private app storage; removing a custom cover restores the document's generated cover. Display adjustments follow default, series and book settings, including JSON backup and restore. Custom cover images stay on the current installation and are not included in JSON backups. See the [user guide](USAGE.md#custom-covers-and-display-adjustments) and [feature validation](COVERS_DISPLAY_VALIDATION.md).

### Series and language editions

One series can contain, for example, an English CBZ and a Japanese CBR for the same volume. Each remains a separate book with its own source, identity, reading position, bookmarks and history. Matching series and volume numbers group editions without merging their reading data or treating them as duplicate imports.

Existing series and language metadata are used where available, with manual attribution/correction in Book details. The version picker shows each edition's language and progress. Next-volume suggestions respect the selected language. Page positions remain independent because editions can have different pagination and layouts. Edition assignments are retained in backups and restores.

Version 0.9 provides language labels, manual attribution in Book details, an existing-series chooser, a library language filter, and a language-edition picker. Editions use a shared series and volume/chapter number. The shelf shows one stacked-cover item per volume/chapter, using the most recently opened edition's cover and a version-count badge. Tapping it opens a version picker with individual covers, status, percentage/page progress and access to each edition's details. The picker is also available in Book details and Reader actions. Unnumbered books remain individually selectable. Each edition opens at its own saved position. Next-unread suggestions require the same normalized language tag, including script/region distinctions; unspecified language only continues to unspecified language. Assignments survive rescans and JSON backup/restore. See [usage](USAGE.md#language-editions) and [validation](EDITIONS_EXPORT_VALIDATION.md).

### Page export

Version 0.9 provides **Reader actions → Export page**, with a page-number choice followed by Android **Save image** or **Share page**. Archives and image folders export original PNG/JPEG/WebP bytes; PDFs render a complete PNG at twice the page's point dimensions, bounded to 2048 × 3072 while retaining aspect ratio. Exports exclude viewport zoom, spreads, contrast and gamma. Source documents and reading data remain unchanged. See [usage](USAGE.md#page-export) and [validation](EDITIONS_EXPORT_VALIDATION.md).

### Statistics scope

Development builds include weekly/monthly time graphs with older-period comparisons and day → book → chapter → session details; pages viewed; unique volumes/chapters completed, editions and rereads; reading days/current and best streaks; most-read series/books; session count/average/longest duration; language filters and breakdowns; and a yearly activity heatmap. See the [measurement rules](USAGE.md#reading-statistics-development-build).

Statistics use dedicated Room tables and version 2 JSON backups; version 1 backups remain importable. Records are retained after removing a book or clearing opening history. Existing opening history cannot reconstruct past reading time, so tracking starts with the feature. Reading goals and time-remaining estimates are outside this release; goals may be considered in a later gamification update.

See [statistics validation](STATISTICS_VALIDATION.md) for the measured implementation and verification scope.
