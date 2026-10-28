# Panelgrove roadmap

Updated **2026-10-28**. The current public prerelease is **0.1.6**. The existing offline reader is the foundation for 1.0; the following additions are not yet available in that prerelease.

## Priorities for 1.0

| Priority | Feature | Intended scope | Status |
| --- | --- | --- | --- |
| 1 | CB7 / 7Z formats | Read image-based CB7 and 7Z archives with the existing ordering, navigation and reading-data behavior. Document supported archive variants and limits. | Implemented in development; pending release |
| 2 | Custom covers | Choose and remove a book's custom library cover; preserve the choice through reopening and rescanning without changing the source document. | Implemented in development; pending release |
| 3 | Contrast and gamma controls | Preview, adjust, reset and retain display adjustments in the reader. Keep source images unchanged. | Implemented in development; pending release |
| 4 | Different languages for the same series | Attribute separate books in any supported format to one shared series, identify their language editions and make those editions easy to select. | Implemented in development; pending release |
| 5 | Page export | Save or share a selected page as a full-page image through Android's system controls. Keep the source document unchanged. | Implemented in development; pending release |
| 6 | Statistics | Add local reading statistics with clearly defined measurements and retained aggregates. Define the initial measures before implementation. | Planned |

CB7/7Z implementation scope and checks are recorded in [development validation](CB7_VALIDATION.md).

Custom covers and display adjustments are available in development builds. Covers are copied to private app storage; removing a custom cover restores the document's generated cover. Display adjustments follow default, series and book settings, including JSON backup and restore. Custom cover images stay on the current installation and are not included in JSON backups. See the [user guide](USAGE.md#custom-covers-and-display-adjustments-development-build) and [feature validation](COVERS_DISPLAY_VALIDATION.md).

### Series and language editions

One series should be able to contain, for example, an English CBZ and a Japanese CBR for the same volume. Each remains a separate book with its own source, identity, reading position, bookmarks and history. Grouping editions must preserve existing reading data and must not treat two editions as duplicate imports merely because their series and volume match.

Use existing series and language metadata where available and allow manual attribution/correction. Show language clearly when selecting an edition. Continuation and next-volume suggestions should respect the selected language rather than unexpectedly changing editions. Shared page positions are not assumed because editions can have different pagination and layouts. Preserve edition assignments in backups and restores.

Development builds now provide language labels, manual attribution in Book details, an existing-series chooser, a library language filter, and a language-edition picker. Editions use a shared series and volume/chapter number. The shelf shows one stacked-cover item per volume/chapter, using the most recently opened edition's cover and a version-count badge. Tapping it opens a version picker with individual covers, status, percentage/page progress and access to each edition's details. The picker is also available in Book details and Reader actions. Unnumbered books remain individually selectable. Each edition opens at its own saved position. Next-unread suggestions require the same normalized language tag, including script/region distinctions; unspecified language only continues to unspecified language. Assignments survive rescans and JSON backup/restore. See [usage](USAGE.md#language-editions-development-build) and [validation](EDITIONS_EXPORT_VALIDATION.md).

### Page export

Development builds provide **Reader actions → Export page**, with a page-number choice followed by Android **Save image** or **Share page**. Archives and image folders export original PNG/JPEG/WebP bytes; PDFs render a complete PNG at twice the page's point dimensions, bounded to 2048 × 3072 while retaining aspect ratio. Exports exclude viewport zoom, spreads, contrast and gamma. Source documents and reading data remain unchanged. See [usage](USAGE.md#page-export-development-build) and [validation](EDITIONS_EXPORT_VALIDATION.md).

### Statistics scope

Suggested initial measures are reading time, pages read and books completed, with a clear definition for each. The current history stores a bounded set of opening events; it cannot reconstruct accurate lifetime reading totals. New statistics should record the needed data going forward and identify any limited historical coverage. All statistics remain local.
