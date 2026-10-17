# Independent code review by Codex (Nightshift)

> Written 2026-10-17 against `main` at `2037924`, before batch 1 merged. Line numbers refer to that commit and have since moved. Items T01 to T04 and T13 were fixed in batch 1 (PR #13). Paths below are relative to the repo root.



- **100 tests passed**, none failed or skipped.
- **`lintDebug` passed:** 0 errors, 23 warnings. Most concern dependency versions; `ConstantLocale` can cause stale month names after changing language.
- **`assembleDebug` passed.**
- Five additional temporary Robolectric probes passed, including a check that restore rolls back earlier deletes when a later insert fails.


**Confirmed by reading**, with probe reproductions noted:

1. **T01 — high — Incomplete backups can replace valid data with empty data.**  
   [Backup.kt:160](app/src/main/java/com/janreins/tonnage/data/backup/Backup.kt:160)  
   `objects()` treats missing arrays and incorrectly typed sections as empty lists. **Scenario:** a damaged backup retains workouts but loses `customExercises`; restore deletes the existing custom exercises and commits incomplete history. Even `{"format":"tonnage-backup","schemaVersion":1}` is accepted. **Fix:** require all version-1 sections and nested arrays with their correct types before offering restore. *Probe reproduced.*

2. **T02 — med — Invalid numeric values can poison history and prevent subsequent backups.**  
   [HevyCsv.kt:122](app/src/main/java/com/janreins/tonnage/data/hevy/HevyCsv.kt:122), [Backup.kt:165](app/src/main/java/com/janreins/tonnage/data/backup/Backup.kt:165)  
   The CSV parser accepts Infinity, and `Double.toInt()` silently saturates oversized values. Backup reading also accepts `"Infinity"` as a weight. **Scenario:** importing `weight_kg=1e309` stores Infinity; JSON export subsequently fails with “Forbidden numeric value: Infinity.” **Fix:** validate finiteness, nonnegative ranges and integral fields before persistence; reject invalid nonblank cells rather than silently coercing them. *Probe reproduced.*

3. **T03 — med — Restore accepts dangling exercise references.**  
   [BackupRepository.kt:50](app/src/main/java/com/janreins/tonnage/data/backup/BackupRepository.kt:50)  
   Workout/routine exercise IDs and mapping targets are not validated, and there is no exercise foreign key to reject them. **Scenario:** a structurally valid backup references `custom-X` without defining it; restore succeeds, showing “Unknown exercise” and potentially the wrong tracking fields. **Fix:** validate every exercise reference against the built-in library plus incoming custom exercises before deleting anything.

4. **T04 — med — Fresh restore can associate old workouts with an unrelated new routine.**  
   [BackupRepository.kt:52](app/src/main/java/com/janreins/tonnage/data/backup/BackupRepository.kt:52)  
   Restore preserves historical `routineId`s but does not preserve the allocation history of deleted routines. **Scenario:** historical workouts reference deleted routine 1; restoring onto a fresh installation lets the next new routine receive ID 1. Its “previous” values can then come from that unrelated historical routine. **Fix:** reserve or remap historical routine IDs, including deleted ones, during restore. *Probe reproduced.*

5. **T05 — med — Hevy re-import safety depends on the phone’s current time zone.**  
   [HevyCsv.kt:41](app/src/main/java/com/janreins/tonnage/data/hevy/HevyCsv.kt:41)  
   Zone-less timestamps use the current device zone, while duplicate detection uses the resulting epoch. **Scenario:** import a file in UTC, then import the same file in Manila: identical timestamps shift eight hours and are imported again. Week allocation can also change. **Fix:** let users select and retain the source zone; use a stable source identity for deduplication. *Timestamp difference reproduced.*

6. **T06 — med — Malformed CSV quoting silently drops sets.**  
   [HevyCsv.kt:144](app/src/main/java/com/janreins/tonnage/data/hevy/HevyCsv.kt:144)  
   The parser never rejects an unterminated quoted field. **Scenario:** an unclosed exercise-note quote absorbs subsequent rows into that note; import succeeds with fewer sets. **Fix:** reject unterminated quotes and malformed row structures, reporting the affected row. *Two-set example imported only one set.*

7. **T07 — med — Plank and similar holds use the wrong tracking type.**  
   [ExerciseSeed.kt:33](app/src/main/java/com/janreins/tonnage/data/local/ExerciseSeed.kt:33)  
   Tracking is inferred only from category/equipment. Bundled Plank is `strength`/`body only`, so it becomes `BODYWEIGHT_REPS`. **Scenario:** an imported 60-second Plank is matched automatically, but logging exposes reps and its duration disappears from progress metrics. **Fix:** add explicit tracking overrides and update existing built-in rows; changing initial seeding alone will not repair existing installations. *Actual seeded value reproduced.*

8. **T08 — med — Hevy grouping and duplicate detection use different identities.**  
   [ImportRepository.kt:75](app/src/main/java/com/janreins/tonnage/data/repository/ImportRepository.kt:75)  
   Parsing groups by title plus start time; deduplication checks start time alone. **Scenario:** workouts A and B share an exported start minute. Importing both together retains both, but importing A first causes B to be skipped later. **Fix:** use the same stable identity during grouping, preparation and transactional insertion, including duplicate checks within the incoming batch.

9. **T09 — med — Rest countdowns disappear on process death.**  
   [RestTimer.kt:26](app/src/main/java/com/janreins/tonnage/timer/RestTimer.kt:26), [WorkoutService.kt:94](app/src/main/java/com/janreins/tonnage/service/WorkoutService.kt:94)  
   Timer state exists only in a singleton, and the service returns `START_NOT_STICKY`. **Scenario:** the process dies during a rest; the workout survives, but the deadline and alert are lost. **Fix:** persist timer recovery metadata and reconstruct or explicitly mark the interrupted timer when recovering the session.

10. **T10 — med — A rejected foreground-service start has no retry on ordinary reopening.**  
    [WorkoutService.kt:143](app/src/main/java/com/janreins/tonnage/service/WorkoutService.kt:143), [WorkoutViewModel.kt:93](app/src/main/java/com/janreins/tonnage/ui/workout/WorkoutViewModel.kt:93)  
    The exception is swallowed after setting `confirmedInThisProcess`; no activity-resume hook retries. **Scenario:** press Start, then Home while the database operation is pending. If background-start restrictions reject the service, returning to the retained activity leaves the workout without its service. **Fix:** track successful service startup and ensure it while the activity is resumed and the workout confirmed. Android documents this [background-start restriction](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

11. **T11 — med — Focused numeric cells can display values different from Room.**  
    [SetInputs.kt:49](app/src/main/java/com/janreins/tonnage/ui/workout/SetInputs.kt:49)  
    External values are ignored while focused; losing focus does not rerun `LaunchedEffect(initial)`. **Scenario:** focus an 80 kg cell and tap a 60 kg “previous” value. Room receives 60, but the cell can continue showing 80 after blur. **Fix:** reconcile external updates when focus is lost and handle explicit copy operations without discarding pending typing.

12. **T12 — med — Progress can remain on the previous week after Monday rollover.**  
    [ProgressViewModel.kt:52](app/src/main/java/com/janreins/tonnage/ui/progress/ProgressViewModel.kt:52)  
    Time and zone are sampled only when database or week-selection flows emit. **Scenario:** leave Progress open across Sunday midnight; “current week,” muscle totals and streak status remain stale until another input changes. **Fix:** include a local-date/time-zone refresh signal, including refresh on resume.

13. **T13 — low — Backup UI does not disclose that the active workout is omitted.**  
    [BackupRepository.kt:16](app/src/main/java/com/janreins/tonnage/data/backup/BackupRepository.kt:16), [SettingsScreen.kt:137](app/src/main/java/com/janreins/tonnage/ui/settings/SettingsScreen.kt:137)  
    Excluding active workouts is intentional, but the UI says it saves “your workouts.” **Scenario:** someone backs up before moving phones during an unfinished session and discovers that session is absent. **Fix:** warn explicitly or require finishing/discarding before export.

14. **T14 — low — Finish preview duplicates PRs for repeated exercise blocks.**  
    [WorkoutStats.kt:129](app/src/main/java/com/janreins/tonnage/domain/WorkoutStats.kt:129)  
    `detectRecords()` evaluates each block independently; historical records group by exercise ID. **Scenario:** bench appears twice and both blocks beat the previous maximum: preview shows four records, while history shows two. **Fix:** group current working sets by exercise ID before evaluating record kinds. *Probe reproduced.*

15. **T15 — low — Closing an import does not cancel its pending read.**  
    [DataViewModel.kt:78](app/src/main/java/com/janreins/tonnage/ui/data/DataViewModel.kt:78), [DataViewModel.kt:121](app/src/main/java/com/janreins/tonnage/ui/data/DataViewModel.kt:121)  
    The reading coroutine can publish `Ready` after `closeImport()` clears the state. **Scenario:** close a slow import and select another file; the older operation can reopen the screen or replace the newer preview. **Fix:** cancel the previous job and guard publication with an operation ID; rethrow cancellation exceptions.

16. **T16 — low — Historical Room schemas and migration coverage are incomplete.**  
    [app/build.gradle.kts:95](app/build.gradle.kts:95), [MigrationTest.kt:109](app/src/test/java/com/janreins/tonnage/MigrationTest.kt:109)  
    No schemas are tracked; this build generated only version 4. Migration tests construct versions 1 and 2, but do not start from a populated version 3 database containing routines. **Scenario:** a future migration damages existing routine contents without that preservation case being exercised. **Fix:** commit historical schema exports, add a populated v3 upgrade case, and make CI detect unexpected schema changes. Android recommends [version-controlling exported schemas](https://developer.android.com/training/data-storage/room/migrating-db-versions).

**Suspected runtime risks** — implementation observed, impact not measured on a device:

17. **T17 — med — Large imports/backups can exhaust memory.**  
    [DataViewModel.kt:72](app/src/main/java/com/janreins/tonnage/ui/data/DataViewModel.kt:72), [HevyCsv.kt:42](app/src/main/java/com/janreins/tonnage/data/hevy/HevyCsv.kt:42)  
    Files are read entirely, then expanded into multiple collections without size limits. **Scenario:** selecting a very large CSV or JSON file exhausts a small device heap; `catch (Exception)` does not catch `OutOfMemoryError`. **Fix:** enforce a bounded read immediately; stream parsing if larger histories must be supported.

18. **T18 — med — History recomputation can stall typing on the main thread.**  
    [WorkoutRepository.kt:43](app/src/main/java/com/janreins/tonnage/data/repository/WorkoutRepository.kt:43)  
    `recordsByWorkout()` runs in the collecting ViewModel’s main context. Set-table invalidations can trigger full-history sorting/grouping during active logging. **Scenario:** a large imported history causes pauses while entering set values. **Fix:** apply `distinctUntilChanged()` before recomputation and move calculation upstream of `flowOn(Dispatchers.Default)`; benchmark a representative large history.

The database replacement itself is atomic: the additional failure-injection probe confirmed rollback after deletion. Backup versions are checked before restore, active-workout protection is inside the transaction, and no destructive migration fallback is configured. Warm-up filtering, Epley calculation, historical PR recomputation, and calendar-based week arithmetic looked consistent with the documented rules.

The merged manifest has no `INTERNET` permission; backup rules exclude cloud backup and device transfer. Foreground-service type declarations and bounded wake-lock handling look appropriate. Notification permission denial does not itself prohibit starting an FGS, per [Android’s documentation](https://developer.android.com/develop/ui/compose/notifications/notification-permission). Release shrinking is enabled, CI builds the release variant, and published releases require a keystore; local release builds retain the documented debug-signing fallback.

Coverage should add the reproduced cases above, rollback preservation, non-UTC DST boundaries, process-death recovery, and service/notification lifecycle cases on API 31–35.

Suggested four-PR batching:

1. **Restore and backup safety:** T01–T04, T13; strict validation and preservation regressions.
2. **Hevy fidelity and bounded reads:** T05–T08, T15, T17.
3. **Workout and timer lifecycle:** T09–T11; recovery and focused-input tests.
4. **Statistics and migration safeguards:** T12, T14, T16, T18; rollover tests, schema exports and CI checks.
