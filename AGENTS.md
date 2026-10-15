# AGENTS.md

## Project Overview
qualified-opinion/tonnage is a private, offline-only strength training logger for Android (a Hevy replacement without social features), built with Kotlin, Jetpack Compose, Material 3 and Room. It follows the same setup as Piso.

- **Package Name / Application ID**: `com.janreins.tonnage`
- **Database**: On-device SQLite via Room (`tonnage_database`), schemas exported to `app/schemas`.
- **Network**: None. No remote API calls, analytics or cloud sync.
- **Exercise library**: `app/src/main/assets/exercises.json`, a trimmed copy of free-exercise-db (public domain), seeded into Room on first launch by `ExerciseRepository.seedIfEmpty`.

## Key Build & Test Commands
- **Run Unit Tests**: `./gradlew test`
- **Lint**: `./gradlew lintDebug`
- **Build Debug APK**: `./gradlew assembleDebug`
- **Build Release APK**: `./gradlew assembleRelease`

## Codebase Conventions & Constraints
- **Offline Integrity**: Do not add network calls, remote API integrations, or cloud sync services.
- **Units**: Store weights in kg; converting to lb is display-only.
- **Schema changes**: Any Room entity change needs a version bump and a migration with a test. Never use destructive migration; users' workout history must survive updates.
- **Active workout**: a `workouts` row with `endedAt = null`. Every edit goes straight to Room (`WorkoutRepository`), so nothing lives only in memory. `WorkoutSession.confirmedInThisProcess` decides whether to show the resume/discard prompt after process death.
- **Rest timer**: `timer/RestTimer` holds the countdown (elapsed-realtime clock); `service/WorkoutService` is a `specialUse` foreground service that waits it out under a partial wake lock and vibrates/notifies when it ends. Keep the wake lock bounded by the rest length.
- **Routines**: `routines` → `routine_exercises` → `routine_sets`. The editor works on an in-memory `RoutineDraft` (`ui/routines`) and saves it in one transaction (`RoutineDao.save`); it reuses the workout screen's `ExerciseCard` by mapping the draft to `ExerciseBlock`s. `workouts.routineId` records the routine a workout was started from; it is deliberately not a foreign key so deleting a routine keeps history.
- **Previous values**: `WorkoutRepository.previousSets` takes the completed sets from the latest finished workout of the same routine, falling back to the latest anywhere, and the card shows them by set index (no forced matching).
- **Personal records** are never stored: `WorkoutStats.recordsByWorkout` walks every completed working set in date order (`WorkoutDao.observeRecordSets`) and recomputes them, so editing or deleting old workouts stays correct. An exercise's first session sets no records, matching the finish screen's `detectRecords`.
- **Editing a finished workout** writes straight to Room like logging; new sets are added already completed, and `tidyEdited` drops blank sets and empty exercises on Done.
- **Hevy import** (`data/hevy`, `ImportRepository`): `HevyCsv` finds columns by header name (kg or lb, km or miles) and groups rows into workouts by title + start time. `ExerciseMatcher` tries a saved mapping, then an exact match on sorted name words, then `HevyAliases`, then a fuzzy word score; only LOW/NONE matches go to review. Every choice is saved in `hevy_mappings` (keyed by `ExerciseMatcher.normalizeName`), and workouts with `source = 'hevy'` and the same start time are skipped, so re-importing is safe. Add aliases only with ids that exist in `exercises.json` (`ExerciseMatcherTest` checks).
- **Backup** (`data/backup`): `BackupJson` writes `format` + `schemaVersion`. Bump `SCHEMA_VERSION` for any change a previous app version couldn't read, and keep `read` able to read every older version. Restore replaces workouts, routines, custom exercises and mappings in one transaction and is refused while a workout is in progress.
- **Progress** (`domain/Progress.kt`, `domain/Milestones.kt`, `ui/progress`): everything is computed from `WorkoutDao.observeProgressSets` (completed working sets of finished workouts) and the finished workout list; nothing is stored, so no migration was needed. Weeks run Monday to Sunday in the phone's time zone. Charts are drawn with Compose `Canvas` (`ui/components/Charts.kt`) rather than a chart library.
- **Room migrations**: `MigrationTest` builds the previous schema by hand and opens it with the current app; add a case there for every new migration.
- Navigation is a simple tab enum (`ui/state/MainTab`) plus screen state in `MainActivity`, as in Piso.
- Plan and acceptance notes for later phases live in the project files (`workout-app/plan.md`, `workout-app/implementation-notes.md`).
