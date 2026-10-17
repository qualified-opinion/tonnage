# Tonnage batch 1: backup and restore safety

Repo: qualified-opinion/tonnage. Base: main at 2037924. Branch: fix/backup-restore-safety.
Engine: Codex (build). Review: Kiln via Claude Code, one round. Merge: only after Kiln approves, CI is green, and Jan OKs. No tags or releases.
Sources: docs/plan/reviews/codex-review.md (T01, T03, T04, T13, backup half of T02), docs/plan/reviews/kiln-review.md.

## Goal
A restore can never make a user's data worse. A bad or damaged backup is rejected with a clear message before anything is deleted, and a good restore keeps history linked to the right routine.

## Scope
1. T01 Strict backup parsing (Backup.kt `objects()` and readers). A version-1 backup must contain every top-level section (customExercises, workouts, routines, hevyMappings, plus the existing metadata) with the correct JSON types, and every nested exercises/sets array must be present and an array. A missing or wrong-typed section is an error, not an empty list. `{"format":"tonnage-backup","schemaVersion":1}` alone must be rejected. Error text must tell the user the file is incomplete or damaged and that nothing was changed.
2. Backup-side numbers (part of T02). Reject non-finite weights, distances and RPE in a backup ("Infinity", "NaN"), negative values, and integer fields that overflow or are not integral. Reject the file, do not coerce.
3. T03 Reference check before any delete. Every workout and routine exerciseId, and every Hevy mapping target, must exist in the built-in library or in the incoming customExercises. Duplicate custom exercise ids in the file are also an error. Validation runs before `deleteAllWorkouts` and the other deletes, so a rejected file leaves the database untouched.
4. T04 Routine ids. After restore, historical workouts must never link to an unrelated new routine. Make sure the routine id sequence is advanced past the highest routine id that the restored routines or workouts reference, so the next routine created gets a fresh id. If a workout references a routine id that is not in the backup, either null it or keep it reserved. Say in the PR which you chose and why.
5. T13 Backup UI honesty. Settings must say that a workout in progress is not included in a backup (a short line next to the export action). Do not add new flows.
6. Tests for each of 1 to 4, each failing on current main. Include: missing customExercises with existing data stays intact after the rejected restore; the empty two-field file; wrong-typed section; Infinity weight; dangling exercise reference; fresh-install restore then create-routine does not reuse a referenced id; a good backup still round-trips exactly (export, restore, export is equal).

## Out of scope
Hevy CSV import fixes (batch 2). Memory-bounded reads (batch 2). Workout and timer lifecycle, stats, schema exports, CI changes (batches 3 and 4). kg/lb setting (cut, Jan uses kg). New backup formats, encryption, auto-backup, cloud. Any change to the schemaVersion unless a new field is unavoidable; if so, stop and ask.

## Acceptance criteria
- Existing 100 tests plus new tests pass. `./gradlew testDebugUnitTest lintDebug assembleDebug` is green and CI is green.
- A rejected file changes nothing in the database (test proves it).
- A valid backup made by the current app still restores. Backups exported before this change must still restore; do not tighten beyond what the current exporter always writes.
- No new permissions or dependencies. No Room schema change expected; if the routine id fix needs one, stop and tell Kiln.
- Diff touches only backup/, the restore repository, the settings string, and tests.
- PR body lists each of T01, T03, T04 and the backup half of T02 with the test that covers it, states the T04 choice, and says what was not verified on a device.

## Risks
- Being too strict locks users out of their own older backups. Mitigation: the strictness list must match what the exporter has always written; add a test that restores a fixture of the current exporter's output.
- The routine id fix may touch SQLite's autoincrement bookkeeping; keep it inside the restore transaction.
- A partially applied restore. Validate first, then run the existing transaction.

## Process
Post your plan in the room first and wait for Jan's go. Post the PR link and head SHA, with CI status, when ready.
