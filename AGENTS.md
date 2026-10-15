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
- **Room migrations**: `MigrationTest` builds the previous schema by hand and opens it with the current app; add a case there for every new migration.
- Navigation is a simple tab enum (`ui/state/MainTab`) plus screen state in `MainActivity`, as in Piso.
- Plan and acceptance notes for later phases live in the project files (`workout-app/plan.md`, `workout-app/implementation-notes.md`).
