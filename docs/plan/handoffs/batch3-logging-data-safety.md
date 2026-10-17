# Tonnage batch 3: logging data safety

**Repo:** qualified-opinion/tonnage
**Branch:** `fix/logging-data-safety`, from `main` at `7721bc7` (or later)
**Engine:** the owner picks one agent (Codex or Claude Code) to build; the other engine reviews, one round. Merge only after approval, green CI and the owner's OK. No tags, no releases.
**Source:** `reviews/kiln-review-2.md` items: blank-set ticks, N01, N02, N03, T11, input hardening, discard confirmation. Size target: about 450 changed lines.

## Goal
Typing a workout in the gym can never silently throw it away, and the set cells show what is really stored.

## Scope
1. **Blank-set tick.** `WorkoutViewModel.toggleCompleted` currently completes a set with no values. Change it so that ticking a blank set (no weight and no reps for weight and reps exercises; no duration for time exercises; the equivalent for the other tracking types) fills it from the "previous" value for that set index when one exists, and ignores the tick when there is none. `finishWorkout` also drops any blank completed sets. Put the "is this set blank or filled" rule in one pure function in `domain/` so every caller shares it.
2. **N01, finish with nothing ticked.** In `FinishDialog` (`WorkoutScreen.kt`), when no set is ticked but some sets are filled, offer **Tick filled sets and save** (marks the filled sets completed, then runs the normal finish) next to **Keep going**. **Discard workout** must need a second confirmation that says how many filled sets will be lost. In the normal finish dialog, when some unticked sets are filled, show "N filled sets aren't ticked" with a **Tick them and save** button, instead of silently removing them.
3. **N02, remove exercise.** `ExerciseCard.kt` removes an exercise and its sets at once. Ask for confirmation when the exercise has any filled or ticked set. No confirmation for an exercise with only blank sets.
4. **Resume dialog.** `MainActivity.kt` discards the interrupted workout in one tap. Add a confirmation.
5. **T11, focused cell.** In `SetInputs.kt` a focused cell keeps its own text, so after tapping a "previous" value it can show a different value from Room. Tapping a previous value must overwrite the cell even while focused. When the cell loses focus it shows the stored value again, formatted. For duration cells the stored value is shown as m:ss after blur ("130" typed becomes "2:10"). Parsing does not change: a plain number is seconds, as `Format.parseDuration` documents. State this in the PR.
6. **Input hardening.** One input filter used by every numeric cell: at most one decimal separator (`.` or `,`), digits only otherwise, and soft caps: weight up to 1000 kg, reps up to 999, duration up to 99:59:59. Choose the exact caps if you disagree and state them in the PR. Existing stored values above a cap must still display and save unchanged.
7. **Keep screen on.** While the workout screen is showing a confirmed active workout, keep the screen on (`FLAG_KEEP_SCREEN_ON` or the Compose equivalent) and clear it when leaving. No setting, no new permission.

## Out of scope
- Timer, service and notification changes (batch 6). Stats and PR logic (batch 5). Backup and schema (batch 4).
- Changing how duration text is parsed. A kg/lb setting. Weight plus/minus buttons.
- Any Room schema change.

## Acceptance criteria
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and CI are green; every existing test still passes.
- New tests, each failing on current `main`:
  - the blank/filled rule for each tracking type (pure function);
  - ticking a blank set fills it from previous, and is ignored with no previous (view model or repository test with fakes);
  - `finishWorkout` drops blank completed sets;
  - "tick filled sets" completes exactly the filled sets;
  - the input filter: second separator rejected, caps applied, stored values above the cap still shown;
  - duration display after blur ("130" becomes "2:10").
- Typing 3 filled sets without ticking and pressing Finish never deletes them without an explicit second confirmation.
- Removing an exercise with a logged set asks first.
- No new permission, no `INTERNET`, no schema change.
- The PR body lists what was and was not checked on a phone. Needs a phone check by the owner of: keyboard behaviour and focus in the set cells, tapping a previous value while a cell is focused, finish and discard dialogs, screen staying on.

## Risks
- This touches the main logging screen. Keep changes small and do not restructure `ExerciseCard` or `SetInputs`.
- Compose focus handling is easy to get subtly wrong. Prefer a test of the pure state logic over a UI test, and say what a phone must confirm.
- Filling from "previous" must use the same set-index rule the card already uses (`WorkoutRepository.previousSets`), not a new one.

## Process
Branch, build, open one PR, update the status table in `ROADMAP.md` (batch 2 is already Merged), add `AGENTS.md` lines if a convention changed (for example the blank-set rule). Reviewer checks the diff against these criteria. Do not merge.
