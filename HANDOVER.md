# Tonnage handover

Read this, then `AGENTS.md` (the rules) and `docs/plan/ROADMAP.md` (status and what is next), before changing anything. This file says how the app is put together and how it gets built and released. The roadmap says what to work on.

## The short version
- **What it is:** a private, offline strength logger for Android that replaces Hevy, minus the social features. One user (the owner, Jan), data on the phone only.
- **Stack:** Kotlin, Jetpack Compose, Material 3, Room (SQLite). minSdk 26, targetSdk/compileSdk 35, JDK 17 in CI, Java 11 bytecode. Set up like the owner's other app, Piso.
- **Package / application id:** `com.janreins.tonnage`. **Never change it.** Android treats a new id as a different app, so the owner's installed history would be stranded.
- **No network.** There is no `INTERNET` permission, no analytics and no sync. Keep it that way (see `AGENTS.md`).
- **The owner is not a developer.** The owner installs APKs from GitHub Releases and merges PRs from the PR page. Explain changes in plain language, and always open PRs against `main` (a PR based on another branch merges into that branch, not `main`).

## Where things are

```
app/src/main/java/com/janreins/tonnage/
  MainActivity.kt           tabs (ui/state/MainTab) and which screen is open; no Navigation library
  data/model/               Room entities (Exercise, Workout*, Routine*, HevyMapping)
  data/local/               AppDatabase (version + migrations), DAOs, ExerciseSeed, Converters
  data/repository/          Workout, Routine, Exercise and Import repositories
  data/hevy/                HevyCsv (parser), ExerciseMatcher, HevyAliases, BoundedRead
  data/backup/              BackupJson (file format) and BackupRepository (export/restore)
  data/settings/            SettingsStore (SharedPreferences: rest time, theme, Hevy zone)
  domain/                   pure logic: WorkoutStats (records), Progress, Milestones, Format
  timer/                    RestTimer (countdown state) and WorkoutSession
  service/                  WorkoutService (foreground service) and WorkoutNotifications
  ui/<area>/                one folder per screen area, each with a ViewModel
  ui/components/            Charts (Canvas), BodyMap (heat map), shared bits
app/src/main/assets/        exercises.json (library), body_map.txt (anatomy art)
app/src/test/               JVM + Robolectric unit tests (no instrumented tests)
tools/body-map/convert.py   regenerates body_map.txt from the CC BY 4.0 SVGs
docs/plan/                  roadmap, batch handoff packs, reviews
```

How a change flows: a screen calls its ViewModel, the ViewModel calls a repository, the repository writes to Room. Screens observe Room through `Flow`s, so the screen updates itself after the write. Nothing important is held only in memory during a workout.

## Data model (Room, database `tonnage_database`, version 4)

| Table | Holds | Notes |
|---|---|---|
| `exercises` | the 876-exercise library plus custom exercises | id is the free-exercise-db id, or `custom-<uuid>`. `trackingType` decides the set inputs (weight+reps, reps, time, distance+time). |
| `workouts` | one row per workout | `endedAt = null` means the workout in progress (at most one). `source` is `app` or `hevy`. `routineId` is deliberately **not** a foreign key, so deleting a routine keeps history. |
| `workout_exercises` | exercises in a workout, in order | cascades from `workouts` |
| `workout_sets` | the sets | weight in **kg** always; `type` warm-up / normal / drop / failure; `completed` = ticked |
| `routines`, `routine_exercises`, `routine_sets` | templates | saved in one transaction by `RoutineDao.save` |
| `hevy_mappings` | remembered Hevy name to exercise matches | key is `ExerciseMatcher.normalizeName` |

Not stored on purpose: personal records, milestones and progress stats. They are recomputed from sets every time, so editing or deleting an old workout can never leave a stale record.

**Migrations:** 1 to 2 (workouts), 2 to 3 (routines, `workouts.routineId`), 3 to 4 (`hevy_mappings`), all in `AppDatabase.kt`. Rules for the next one:
1. Bump `version`, write a `MIGRATION_4_5` that keeps every row, and add it to `addMigrations`.
2. Add a case to `MigrationTest` that builds version 4 with real rows and opens it with the new app.
3. Never use `fallbackToDestructiveMigration`. It would wipe the owner's history.
4. The schema JSON export is switched on but not yet committed to `app/schemas` (roadmap item T16). CI uploads the generated files as the `room-schemas` artifact; commit them when you touch the schema.

**Backups** (`data/backup`) are JSON with `format` and `schemaVersion`. Bump `BackupJson.SCHEMA_VERSION` for any change an older app could not read, and keep the reader able to read every older version. Restore checks everything first, then replaces data in one transaction, and refuses while a workout is in progress.

## Build, test and release

Nobody needs Android Studio. GitHub Actions does all of it.

| What | How | Where |
|---|---|---|
| Tests, lint, debug and release builds | Runs on every push and PR: `./gradlew test lintDebug assembleDebug assembleRelease` | `.github/workflows/android-ci.yml`. The debug APK is the `tonnage-debug-apk` artifact on the run. |
| Publish a release | GitHub > **Actions** > **Android Release** > **Run workflow**, pick branch `main`, type a version such as `1.4.0`, run. It tests, builds a signed APK, tags the commit `v1.4.0` and publishes the GitHub Release with `Tonnage-1.4.0.apk`. Pushing a `v*` tag does the same. | `.github/workflows/android-release.yml` |

Things to know about releases:
- **Always run it from `main`.** The workflow will publish from whatever branch is picked.
- `versionCode` is the Android Release workflow's run number, so it only goes up. Don't rename or recreate that workflow file, or the numbers restart and phones refuse the update as a downgrade.
- A published release must be signed with the owner's key; the run stops if the secrets are missing. The secrets are repository secrets (Settings > Secrets and variables > Actions), set and working since 1.0.0: `TONNAGE_KEYSTORE_BASE64`, `TONNAGE_KEYSTORE_PASSWORD`, `TONNAGE_KEY_ALIAS`, `TONNAGE_KEY_PASSWORD`. Never print them or commit a keystore (`.gitignore` blocks `*.jks` and `*.keystore`).
- CI debug APKs are signed with a different key than releases, so they can't be installed over the owner's release install. The owner tests through published releases. Switching between the two needs Back up, uninstall, install, Restore.
- Releases so far: 1.0.0 (2026-10-15), 1.1.0, 1.2.0 and 1.3.0 (2026-10-16). 1.4.0 (2026-10-17) carries batches 1 and 2 (PRs #13 and #14).
- The cloud containers Claude runs in have no Android SDK (Google's download host is blocked), so builds there are checked only by CI. Run whatever JVM-only checks you can locally, then let CI build.

## Conventions that bite
The full list is in `AGENTS.md`. The ones most likely to cause damage if missed:
- Weights are stored in kg; any lb is display-only.
- Every workout edit goes straight to Room. Don't add in-memory state that a killed app would lose.
- The rest timer's wake lock must stay bounded by the rest length.
- Hevy re-import must stay safe: workout identity is `(title, startedAt)`, and aliases may only point at ids that exist in `exercises.json` (`ExerciseMatcherTest` checks).
- Keep the body-map credit (Ryan Graves, CC BY 4.0) in `CREDITS.md` and Settings.
- Every fix gets a test that fails on the old code. Every batch PR updates `docs/plan/ROADMAP.md`, and `AGENTS.md` when a convention changes.

## Starting a session
Say: "Read `HANDOVER.md`, `AGENTS.md` and `docs/plan/ROADMAP.md`, then do the next not-started batch from its pack in `docs/plan/handoffs/`. Keep to its scope, add the tests it asks for, update the roadmap status table, and open one PR against `main`. Do not merge." Review with a fresh session against the pack's acceptance criteria.
