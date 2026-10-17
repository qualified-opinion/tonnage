# Tonnage roadmap and handoff

This file is the single place to learn what is done, what is next, and how work on Tonnage is run. Any agent or person can take over from here. Keep it current: every batch PR updates the status table below.

**Last updated:** 2026-10-17, after batch 1 merged (`main` at `fddc9b5`). Batch 2 is in progress.

## What Tonnage is
A private, offline strength logger for Android (a Hevy replacement with no social features), built with Kotlin, Compose, Material 3 and Room. Weights are stored in kg, and the owner uses kg. There is no `INTERNET` permission, no analytics and no cloud sync, and none of that changes. Architecture and conventions are in `AGENTS.md`. Read it first.

## Where the work stands

| Batch | Branch | Scope | Status |
|---|---|---|---|
| 1. Backup and restore safety | `fix/backup-restore-safety` | T01 to T04, T13 | Merged (PR #13, `fddc9b5`). Not yet tried on a phone. |
| 2. Hevy import fidelity and bounded reads | `fix/hevy-import-fidelity` | T05 to T08, T15, T17, Hevy side of T02 | In progress (Codex building). Pack: `handoffs/batch2-hevy-import-fidelity.md` |
| 3. Workout and timer lifecycle | not started | see below | Needs a handoff pack and the owner's go |
| 4. Statistics, schemas and CI | not started | see below | Needs a handoff pack and the owner's go |

Item ids (T01 to T18) come from `reviews/codex-review.md`, which reproduced most of them with test probes. The other review, `reviews/kiln-review.md`, has the product and logging-friction findings. Line numbers in both reviews refer to `main` at `2037924` and have moved.

## Batch 3: workout and timer lifecycle (next after batch 2)
- **T09:** the rest countdown is lost if the process dies. Persist the timer's recovery data, or mark the interrupted timer when the session is recovered.
- **T10:** if the foreground service start is rejected (for example after Home during Start), nothing retries when the activity returns. Track service start and ensure it while the activity is resumed with a confirmed workout.
- **T11:** a focused numeric cell can show a different value from Room after a "previous" value is tapped. Reconcile on blur and keep pending typing.
- **Blank-set ticks:** ticking an empty set counts as completed (`WorkoutViewModel.toggleCompleted`). Ignore the tick when required fields are empty, and drop blank completed sets in `finishWorkout`.
- **PR subscription scope:** `MainActivity` collects the full-history PR flow app-wide, so every set edit re-sorts all history. Collect it only on History and Workout detail.
- **Discard confirmation** on the resume dialog.
- **Restore error message:** a restore rejected by the reference check currently shows a doubled message ("The restore failed and nothing was changed: This file is incomplete or damaged. Nothing was changed."). Catch `BackupFormatException` separately in `DataViewModel`.
- **Backup duplicate ids:** duplicate routine ids, or a custom exercise id equal to a built-in id, fail on a database constraint instead of a clean check. The restore rolls back and nothing changes, but the user sees a raw SQLite message.
- **Input hardening:** reject a second decimal separator in `SetInputs.kt` and add soft caps on weight and reps.
- Tests: recovery, focused-input and service lifecycle cases.

## Batch 4: statistics, schemas and CI
- **T12:** Progress stays on the previous week after Monday rollover. Add a date and zone refresh signal, including on resume.
- **T14:** the finish preview duplicates PRs when an exercise appears in more than one block. Group current working sets by exercise id first.
- **T16:** commit the Room schema exports in `app/schemas`, add a populated version 3 upgrade test to `MigrationTest`, and make CI fail on unexpected schema changes.
- **T18:** history recomputation runs on the main thread while typing. Add `distinctUntilChanged()` and move the work to `Dispatchers.Default`, then benchmark a large history.
- **CI housekeeping:** branch protection on `main`, Dependabot for Gradle and Actions, and a release (R8) build smoke test before removing `-keep class androidx.compose.** { *; }` from `proguard-rules.pro`.

## Later ideas, not scheduled
Do these only after batches 1 to 4 and only when the owner asks.
- **Progression hints** at the point of logging, for example "Last: 60 x 8,8,7, try 62.5 x 8", using a simple double-progression rule.
- **Per-exercise weight increment buttons.**
- **Keep the screen on** during an active workout.
- **"Last backed up" line** in Settings and a reminder, plus allowing device-transfer backups (currently excluded in `data_extraction_rules.xml`).
- **Per-exercise history table** showing the last N sessions with a "stalled for X sessions" marker.
- **Epley cap:** the 1RM estimate is unbounded at high reps, so ignore sets above about 10 to 12 reps.
- **Optional app lock or secure screen**, only if the owner wants it for this app.

## Decided against
- **kg/lb setting.** The owner uses kg. Do not build it unless that changes.
- Cloud sync, social features, notes or RPE UI on every set, supersets, more themes and any network access. They go against the offline and private brief.

## How work is run
1. **Plan:** Kiln (product strategist) writes a handoff pack for each batch: goal, repo and branch, scope and out-of-scope, acceptance criteria, risks and engine. Packs live in `handoffs/`.
2. **Go:** the owner (Jan) says go. Nightshift posts a plan only if it differs from the pack.
3. **Build:** Nightshift builds with Codex on the named feature branch, runs `./gradlew testDebugUnitTest lintDebug assembleDebug`, and opens a PR. Every fix needs a test that fails on current `main`.
4. **Review:** Kiln reviews with a different engine (Claude Code or Grok Build), checks the diff against the pack's acceptance criteria, and posts "approve" or "changes needed". After a fix, only the new commit is re-checked. One review round is the default.
5. **Merge:** Nightshift squash-merges, pinned to the exact commit Kiln approved, only when Kiln has approved, CI is green and the owner has OK'd that PR. Anything pushed after approval needs another check.
6. **Never:** push to `main`, force-push, create tags or releases, or add network access. The owner publishes releases himself.
7. **Docs:** a batch that changes a convention updates `AGENTS.md` in the same PR (for example, the Hevy import identity and the strict backup reader), and updates the status table here.

The coordination room is the "Coding" group chat in Grok Bot (Jan, Atlas, Kiln, Nightshift).

## Open verification
Nothing below has been checked on a real phone:
- A real backup and restore of the owner's data (batch 1).
- A Hevy re-import after batch 2 ships.
- The R8 release build at runtime, and rest timer behaviour under Doze and OEM battery savers.

## Facts a new owner needs
- The repo is public. Release signing keys come from CI secrets only. Release builds fall back to the debug key if no keystore is configured.
- Room entity changes need a version bump, a non-destructive migration and a `MigrationTest` case.
- `workouts.routineId` is deliberately not a foreign key, so deleting a routine keeps history.
- Backups use `format` and `schemaVersion`. Batch 1 made the reader strict. The exporter has never changed since it was added (commit `c735e04`), so every released backup matches the strict reader.
