# Tonnage batch 5: stats and speed

> **Superseded on 2026-10-18.** This batch is split into GitHub issues #29 (records), #30 (speed) and #31 (week rollover), which carry the current spec and corrections from `reviews/claude-review-4.md`. Kept for history.

**Repo:** qualified-opinion/tonnage
**Branch:** `fix/stats-and-speed`, from the latest `main`
**Engine:** the owner picks one agent to build; the other engine reviews, one round. Merge only after approval, green CI and the owner's OK. No tags, no releases.
**Source:** `reviews/kiln-review-2.md`: PR flow scope, T18, T14, T12, N04, N05, N08. Size target: about 300 changed lines. Do this before batch 4 if typing feels laggy after a big Hevy import.

## Goal
Personal records and weekly stats are right, and logging stays smooth with years of history.

## Scope
1. **PR flow scope.** `MainActivity.kt` collects the full-history record flow for the whole app, so every set edit re-sorts all history. Collect it only where it is shown: the History tab and workout detail.
2. **T18, main-thread recompute.** In `WorkoutRepository` (around the record-set flow) the history recompute runs on the main thread. Add `distinctUntilChanged()` on the input and move the work off the main thread with `flowOn(Dispatchers.Default)`.
3. **T14, duplicate PRs in the finish preview.** `WorkoutStats.detectRecords` (around lines 129 to 149) double counts when an exercise appears in more than one block. Group the current working sets by exercise id first, then compare to history.
4. **N04, blank history.** A blank ticked set counts as an exercise's first session, so a later workout shows PRs "was 0 kg". Count an exercise as already done only when a set has a value above 0 for the relevant record kind. Apply the same rule in `recordsByWorkout` and the finish preview so they agree.
5. **N05, edited workout tidy.** If the app dies while a finished workout is being edited, blank "completed" sets stay. Run the same tidy that Done runs (`tidyEdited`) when a finished workout is opened for editing.
6. **T12, week rollover.** `ProgressViewModel` recalculates after more than 5 s in the background, but not if Progress stays on screen across Monday midnight. Add a refresh signal that fires at the next local midnight (and on resume), using the phone's zone and a clock that tests can replace.
7. **N08, redraw cost.** The rest-timer clock is read at the top of the workout screen and the set actions object is rebuilt each time, so every exercise card redraws once a second. Read the clock only in the part of the UI that shows it and keep the actions object stable (`remember`).
8. **Optional, only if the owner says "include the 1RM cap":** ignore sets above 12 reps for the Epley estimate in `WorkoutStats`. Off by default because it changes the PRs shown for old workouts.

## Out of scope
- Logging UI and dialogs (batch 3), backup (batch 4), timer and service (batch 6).
- New charts or stats. Storing PRs (they stay computed). Any Room schema change.

## Acceptance criteria
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and CI are green; every existing test still passes.
- New tests, each failing on current `main` where a test can show it:
  - finish preview with the same exercise in two blocks lists each PR once;
  - a blank ticked set in a first session does not make the next session show "was 0 kg", in both `recordsByWorkout` and the finish preview;
  - opening a finished workout with blank completed sets removes them;
  - the week boundary with a fixed clock and zone, including the rollover signal at Monday 00:00 and a DST zone;
  - a generated history of about 20,000 sets recomputes records in a measured time and the flow emits nothing for an unchanged input (state the numbers in the PR; this is a guard, not a benchmark).
- No behaviour change to PR results for normal histories (add a regression test with a realistic small history).
- The PR says what a phone should check: typing a set after importing a large Hevy file feels instant, and Progress is right after midnight on Monday.

## Risks
- PR logic is subtle. Do not change the rule that an exercise's first session sets no records, only what counts as a session.
- Moving the recompute to `Dispatchers.Default` must not reorder emissions or show stale records; check with a test that two quick edits end on the latest state.

## Process
One PR, update the status table in `ROADMAP.md`, add an `AGENTS.md` line if the "first session" rule wording changes. Do not merge.
