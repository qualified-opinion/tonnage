# Tonnage batch 2: Hevy import fidelity and bounded reads

**Repo:** qualified-opinion/tonnage
**Branch:** `fix/hevy-import-fidelity`, from `main` at `fddc9b5` (batch 1 merged)
**Engine:** Codex builds. Kiln reviews with Claude Code, one round. Merge only after Kiln approves, CI is green and Jan OKs. No tags, no releases.
**Source:** NIGHTSHIFT-REVIEW.md items T05, T06, T07, T08, T15, T17 and the Hevy half of T02 (batch 1 fixed the backup half). Jan uses kg, so there is no unit work.

## Goal
Re-importing a Hevy export never duplicates or loses workouts, a bad file is rejected loudly instead of silently dropping sets, a held exercise like Plank records seconds, and a huge or stale file read cannot crash the app.

## Scope

1. **T08, one workout identity.** Define one identity, `(title, startedAt)`, with the title normalised the same way it is stored (blank becomes "Workout"). Use it for grouping in `HevyCsv.parse`, for the already-imported check in `ImportRepository.prepare`, and for the check inside `import()`'s transaction.
   - `ImportDao.hevyStartTimes()` becomes a query that returns title and start for `source = hevy` workouts.
   - Two different workouts that share a start minute must both import, whether in one file or across two imports. The same workout twice must import once, within a file and across files.
   - First check whether workout titles can be renamed in the app. If they can, say in the PR what happens when a renamed Hevy workout is re-imported. The preferred behaviour is to skip it, not duplicate it.

2. **T05, time zone stability.** Hevy dates carry no zone, and today the phone's current zone decides the epoch, so the same file imported from a different zone duplicates every workout.
   - Persist the zone used for Hevy imports in `SettingsStore`. A fresh `SharedPreferences` key is enough.
   - Record it on the first successful import (default is the device zone) and reuse it for every later import, so identity stays stable. An install that has already imported will pick up the device zone, which is Manila for Jan.
   - Show the zone in the import preview as plain text ("Times read as Asia/Manila"). A picker to change it is not needed this batch.
   - `HevyCsv.parse` already takes a `timeZone` parameter. Wire the stored zone through it.

3. **T06, malformed quoting.** `parseRows` must reject an unterminated quoted field, naming the row or line where the quote opened, for example "Row 41 has an unclosed quote". The import must change nothing in that case.
   - Do not add other strictness to the CSV structure: no rejecting short or long rows, and no change to how negatives are treated.
   - Keep accepting a UTF-8 byte-order mark (BOM), CRLF line endings, doubled quotes and embedded line breaks.

4. **T02, Hevy side.** Reject the whole file, naming the row and column, when a non-blank numeric cell is not finite. That covers `Infinity`, `NaN` and `1e309`. Also reject an integer column (`reps`, `duration_seconds`) whose value exceeds `Int.MAX_VALUE`, instead of `toInt()` saturating.
   - Blank cells stay allowed.
   - Negative cells keep today's behaviour (the value is ignored), because assisted exercises may export negatives. Do not change that.
   - Test that after a successful import, a backup export never throws.

5. **T07, hold exercises track duration.** Add an explicit tracking override, a map keyed by built-in exercise id, so that unambiguous isometric holds use `TrackingType.DURATION`.
   - Candidates: plank variants, side bridge, wall sit, dead hang, hollow hold, L-sit. Select them by reading the bundled asset, and list every changed exercise id and name in the PR body so I can check the list.
   - The seeder runs only when the table is empty (`ExerciseRepository.seedIfEmpty`), so existing installs need a one-time, idempotent repair that updates `trackingType` for those built-in ids only.
   - The repair must never touch custom exercises, workouts or sets, and must be safe to run on every start.
   - No Room schema change and no version bump. If you believe a migration is the only safe way, stop and say why before building it.

6. **T15, import cancellation.** `startImport` must cancel the previous reading job, tag each operation with an id, and publish `Ready` or `Failed` only if its id is still current. Closing the import must cancel the reader. `CancellationException` must be rethrown, not turned into a `Failed` state.

7. **T17, bounded reads.** Replace `readText` in `DataViewModel` with a bounded read that reads at most a limit and fails with a clear message ("This file is too large to be a Hevy export") instead of an out-of-memory crash.
   - Limits: 25 MiB for a Hevy CSV and 50 MiB for a backup file. Choose them and state them in the PR.
   - Do not rely on the reported size alone. Count bytes while reading, because some providers report none.
   - The same helper applies to the backup restore read path, which uses the same function.
   - Do not catch `OutOfMemoryError`.
   - Extract the bounded read into a small pure function so it can be unit tested.

## Out of scope
- Backup format, restore logic and the doubled restore message. That is batch 3, with the duplicate-id checks.
- Workout and timer lifecycle, stats and CI.
- A zone picker, a kg/lb setting, new dependencies and permissions.
- Any Room schema change or `schemaVersion` change.
- Changing how negative values or short rows are treated.

## Acceptance criteria
- Every existing test (117) plus the new ones pass. `./gradlew testDebugUnitTest lintDebug assembleDebug` and CI are green.
- Each of items 1 to 7 has at least one test that fails on current `main`:
  - Same-minute workouts import in either order and across files. The same workout re-imports as zero new workouts.
  - Importing the same file under `Asia/Manila` and then `UTC` imports nothing the second time.
  - An unterminated quote is rejected with a row number, and the database is untouched.
  - `weight_kg=1e309`, `Infinity`, `NaN` and an oversized `reps` are each rejected with row and column.
  - A Hevy Plank row with `duration_seconds=60` ends up as a `DURATION` exercise, in a fresh seed and after the repair on an existing database.
  - A stale reader cannot overwrite a newer import or reopen a closed one.
  - A stream over the limit fails with the clear message, and one under the limit reads fully.
- A normal Hevy export, covering both a date format with a month name and ISO dates, still imports exactly as before, including pounds and miles columns.
- No new permissions, dependencies or Room schema change.
- The diff stays inside `data/hevy/`, `ImportRepository`, `ImportDao`, `ExerciseRepository`/`ExerciseSeed`, `SettingsStore`, `DataViewModel`, the import preview string, and tests.
- The PR body maps each item to its test, lists the changed hold exercises, states the file-size limits, and says what was not verified on a device.

## Risks
- Changing the dedupe identity could re-import workouts that were imported before, if titles were stored differently from how the new identity normalises them. The fix and the test must use the same normalisation, and the test must include a blank title.
- Storing the zone on first import fixes it for later imports. This is intended, but the PR should note that it is how the behaviour works.
- The tracking-type repair touches existing user data. Keep it narrow, id-based and idempotent.
