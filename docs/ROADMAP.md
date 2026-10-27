# Panelgrove roadmap

Updated **2026-10-27**. The current public prerelease is **0.1.6**. The existing offline reader is the foundation for 1.0; the following additions are planned and are not yet available in that prerelease.

## Priorities for 1.0

| Priority | Feature | Intended scope | Status |
| --- | --- | --- | --- |
| 1 | CB7 / 7Z formats | Read image-based CB7 and 7Z archives with the existing ordering, navigation and reading-data behavior. Document supported archive variants and limits. | Planned |
| 2 | Custom covers | Choose and remove a book's custom library cover; preserve the choice through reopening and rescanning without changing the source document. | Planned |
| 3 | Contrast and gamma controls | Preview, adjust, reset and retain display adjustments in the reader. Keep source images unchanged. | Planned |
| 4 | Different languages for the same series | Attribute separate books in any supported format to one shared series, identify their language editions and make those editions easy to select. | Planned |
| 5 | Page export | Save or share a selected page as a full-page image through Android's system controls. Keep the source document unchanged. | Planned |
| 6 | Statistics | Add local reading statistics with clearly defined measurements and retained aggregates. Define the initial measures before implementation. | Planned |

### Series and language editions

One series should be able to contain, for example, an English CBZ and a Japanese CBR for the same volume. Each remains a separate book with its own source, identity, reading position, bookmarks and history. Grouping editions must preserve existing reading data and must not treat two editions as duplicate imports merely because their series and volume match.

Use existing series and language metadata where available and allow manual attribution/correction. Show language clearly when selecting an edition. Continuation and next-volume suggestions should respect the selected language rather than unexpectedly changing editions. Shared page positions are not assumed because editions can have different pagination and layouts. Preserve edition assignments in backups and restores.

### Statistics scope

Suggested initial measures are reading time, pages read and books completed, with a clear definition for each. The current history stores a bounded set of opening events; it cannot reconstruct accurate lifetime reading totals. New statistics should record the needed data going forward and identify any limited historical coverage. All statistics remain local.