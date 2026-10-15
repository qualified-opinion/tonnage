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
- Navigation is a simple tab enum (`ui/state/MainTab`) plus screen state in `MainActivity`, as in Piso.
- Plan and acceptance notes for later phases live in the project files (`workout-app/plan.md`, `workout-app/implementation-notes.md`).
