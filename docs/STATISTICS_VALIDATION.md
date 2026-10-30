# Reading statistics — development validation

Recorded **2026-10-30** during statistics development. The feature is now included in prepared **1.0 / code 9** and remains absent from public 0.9. The measurements below retain their original development-build scope; see [1.0 launch preparation](RELEASE_1.0.md) for versioned candidate checks and publication status.

## Implemented scope

| Requirement | Implementation and evidence |
| --- | --- |
| Week/month reading-time graphs and older comparisons | All days fit in the chart; separate period and comparison navigation, absolute/percentage change, and a matching local-time cutoff for unfinished periods. Core interval-clipping tests and `StatisticsUiTest` cover the calculation, both ends of the month chart, navigation and day details. |
| Time by series, volume/chapter and session | Rankings and daily bars open a detail sheet with edition, chapter and session portions. Unknown chapter boundaries remain in the book total. Chapter time in spreads is apportioned without multiplying elapsed time. |
| Pages viewed | Two seconds of visible reading qualifies a page; distinct by edition/page within the selected period. Core and real-reader tests cover skipped/preloaded pages, spreads and vertical mode. |
| Completions and rereads | Reader finishes are dated; manually marking Read adds no completion. Known internal chapters require all their pages to be viewed. Summaries distinguish unique works, editions and repeat finishes. Core/repository tests cover these rules. |
| Reading days and streaks | Positive recorded time makes a reading day; current streak may end yesterday; best streak considers all retained dates. Unit coverage includes gaps and language filtering. |
| Series/book rankings, language breakdown and filters | Selected-period rankings open drilldowns. Language filters apply to totals, graphs, streaks and calendar; the language breakdown remains a comparison of all languages in the selected period. |
| Session count, average and longest time | Derived from stable session IDs and selected-period portions. Native tests cover pause/resume, covering menus, backgrounding and activity recreation. |
| Activity calendar | Year navigation, horizontal scrolling, daily intensity and day drilldowns. Opens near current/latest activity. Screenshot checks include dark/light themes and larger text. |
| Local persistence and portability | Room schema 3 adds reading-record, completion and start-date tables. Version 2 JSON backups include retained snapshots; version 1 backups remain accepted. Tests cover versions 1/2 database migration, restored ID remapping, repeated/older backup merges, fresh-database restore, removed books and history clearing. |

The five-minute inactivity default is adjustable to 1, 3, 5, 10 or 30 minutes. Manual pause/play is in the reader. Render loading/failures, covering menus, loss of window focus, background use and screen locking do not accrue time. Monotonic elapsed time protects durations from wall-clock changes; local-day/time-zone snapshots and active spans preserve daily splits and comparison cutoffs. Consecutive active spans coalesce, while pauses remain explicit. Checkpoints occur every 15 seconds and on pause/exit. Goals and estimated time remaining are excluded.

See [usage and measurement definitions](USAGE.md#reading-statistics).

## Automated checks

- Core JVM suite: **81 passed, one optional sample skip, zero failures**, including **18 reading-statistics tests**.
- App JVM tests: **two passed**.
- Debug APK/test APK builds and debug lint: passed; lint reports **zero errors** (existing/advisory warnings remain).
- Full Android regression suites: **99 passed, five expected skips, zero failures** on each of **Android 8.0 / API 26** and **Android 12 / API 32**. The skips are three private-sample cases plus the two separately invoked process-restart phases.
- After the graph/comparison changes, the affected statistics, backup, database-migration and reader-lifecycle suites passed **16/16 on each API**. After clock-correction protection, the six statistics repository/UI cases passed again on both APIs, including validation of real reader-generated records.
- `scripts/test-process-restart.ps1` passed on each emulator. The external script confirmed a live process, force-stopped it, confirmed termination, and verified retained reading time/pages and completion records in a new process, alongside the existing reading-data/cover/preferences checks.
- `git diff --check`: passed.

The final calendar label/larger-text presentation adjustment passed the three statistics UI cases on each API, using the actual app at **1.5× font scale**. Final dark/light, full-month, drilldown and calendar screenshots were visually reviewed. Local reports and screenshots are retained under ignored `local-validation/statistics/`; the full regression results are preserved under `full-suite-pass/`, with process-restart evidence in `process-api26/` and `process-api32/`.

## Findings and limits

The first focused run found an uncaught statistics-start initialization in the isolated decoder process; initialization now uses the existing startup recovery error boundary. Two test setup/selector errors were corrected. Full runs then exposed a detail-sheet Close action below a long list; the sheet now keeps its title and Close action in a fixed header. Month graphs were changed to show the complete period, and large-font calendar month labels were kept on one line so weekday rows remain aligned. Failed runs are retained under `attempt-1/` and `attempt-2/` rather than discarded.

These are debug/emulator and generated-fixture results. They do not establish signed-release update-install behavior, physical-device sustained reading, human accessibility acceptance, or real travel/DST behavior; clock/day boundaries are tested with controlled inputs. Those release/device checks remain follow-up acceptance work. Statistics cannot reconstruct reading before tracking began, and an abrupt termination can lose the latest unsaved interval.
