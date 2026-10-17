# Fresh review of `main` at `7721bc7` (2026-10-17)

Written after batches 1 and 2 merged, to decide what to build next. Claude Code did a read-only review of the code; Kiln then re-read the key spots (finish dialog, remove-exercise menu, duration parsing, blank-set tick, CI file, schemas) and confirmed them. Line numbers refer to `7721bc7` and will move. The item ids T01 to T18 come from `codex-review.md`; the new ones below are numbered N01 onwards in `ROADMAP.md`.

Kiln's changes to the raw report:
- The "duration cell misreads input" finding is kept as a display problem only. `Format.parseDuration` documents that a plain number is seconds ("90" is 90 seconds), and changing that would be a product decision. The fix is to show the parsed value as m:ss when the cell loses focus.
- "Cut" items are only cut until the owner asks for them.
- CI already runs `lintDebug` and `assembleRelease`, so the CI housekeeping that remains is Dependabot, branch protection (a GitHub setting) and the schema check.

---

## Still-real items
All 14 open items are still real. None is already fixed or wrong.

| Item | Verdict | Where | Smallest fix |
|---|---|---|---|
| T09 rest timer lost on process death | Real | `timer/RestTimer.kt:26` (kept in memory only), `service/WorkoutService.kt:94` (service is not restarted) | Save the end time, total, exercise and next-set label in SettingsStore prefs. On Resume, restore the timer if the end time hasn't passed, otherwise clear it. |
| T10 rejected service start never retried | Real | `WorkoutService.kt:143-149` swallows the error; `WorkoutViewModel.kt:93-96` marks the workout confirmed anyway; no resume hook in `MainActivity` | Add a `running` flag to the service and an `ensureService()` that starts it if a confirmed workout is active and the service isn't running. Call it whenever the activity resumes. |
| T11 focused cell differs from Room | Real | `ui/workout/SetInputs.kt:51-53` | When the cell loses focus, show the Room value again. Tapping a "previous" value should overwrite the cell even while it's focused. |
| Blank-set ticks | Real | `WorkoutViewModel.kt:188-201`, `WorkoutDao.kt:193-197` | Ticking a blank set fills it from "previous" (as Hevy does), or the tick is ignored if there is no previous. `finishWorkout` also deletes blank sets. |
| PR flow collected app-wide | Real | `MainActivity.kt:123` | Collect it only in the History tab (`:241`) and in workout detail (`:209`). |
| Discard confirmation on resume dialog | Real | `MainActivity.kt:335` | Add a second confirm dialog. |
| Restore error message doubled | Real | `DataViewModel.kt:205-211` | Catch `BackupFormatException` separately and show its message alone. |
| Backup duplicate ids | Real | `BackupRepository.kt:46-48` and `:61-64` | Before deleting anything, reject the file if a custom exercise id equals a built-in id or routine ids repeat. |
| Input hardening | Real | `SetInputs.kt:30` (any number of `.` or `,`), `:58` (99,999,999 kg accepted) | One input filter that allows a single decimal separator and caps weight and reps. |
| T12 Progress week rollover | Real, but narrower than reported (low) | `ProgressViewModel.kt:52-53` | The flow already recalculates after more than 5 s in the background, so this only happens when Progress stays on screen across Monday midnight. Add a refresh signal at the next local midnight. |
| T14 duplicate PRs in finish preview | Real | `WorkoutStats.kt:129-149` | Group the sets by exercise first. |
| T16 schemas and upgrade test | Real | No `app/schemas` in git; `MigrationTest` covers only versions 1 and 2 | Commit the version 4 schema and add a populated version 3 upgrade test. CI then fails if `git status app/schemas` isn't clean. |
| T18 history recompute on main thread | Real | `WorkoutRepository.kt:43-44` | Skip identical results and move the work off the main thread (`distinctUntilChanged` + `flowOn(Dispatchers.Default)`), on top of the PR-flow fix above. |
| CI housekeeping | Real | No Dependabot config; Compose keep rule still in `proguard-rules.pro` | Add Dependabot. Remove the keep rule only after the release build has been tried on a phone. Branch protection is a repo setting for the owner, not a PR. |

## New findings
- **Med, data loss: one tap deletes a whole untick workout.** At `WorkoutScreen.kt:424-431`, finishing with no ticked sets offers a one-tap "Discard workout". Someone who typed every set but didn't tick (a common Hevy habit) loses the session. Fix: add "Tick filled sets and save" and a confirm before discarding.
- **Med, data loss: "Remove exercise" deletes logged sets with no confirm.** `ExerciseCard.kt:115-118`. Fix: confirm when the exercise has any filled or ticked set.
- **Med, wrong data: the duration cell misreads input.** Typing "130" stores 130 s (2:10), and because of T11 the cell keeps showing "130" (`Format.kt:29-39`). Fix: read three or more plain digits as m:ss.
- **Low, wrong stats:** a blank ticked set counts as the exercise's first session, so the next session shows PRs "was 0 kg" (`WorkoutStats.kt:107,119,134`). Fix: only count history with a value above 0.
- **Low:** if the app dies while you're editing a finished workout, blank "completed" sets stay in it (`WorkoutRepository.kt:135`, `HistoryViewModel.kt:63`). Fix: tidy the workout when it is opened.
- **Low:** the routine editor draft is kept only in memory and is lost if the app is killed (`RoutinesViewModel.kt:41`).
- **Low:** backup export has no size limit, but restore refuses files over 50 MiB (`DataViewModel.kt:165`, `BoundedRead.kt:7`). Fix: check the size before writing.
- **Low, performance:** every exercise card on the workout screen redraws once a second, because the clock is read at the top of the screen and the set actions object is rebuilt each time (`WorkoutScreen.kt:263-286`).
- **Low:** the exercise picker loses its selection when the phone rotates (`MainActivity.kt:100`).
- **Low:** if notifications are denied, the rest timer ends silently except for vibration, and nothing tells the user (`WorkoutNotifications.kt:107`).
- **Low, accessibility:** the set inputs have no screen-reader labels, and the tick button and cells are 40-44 dp, under the 48 dp minimum.
- **Test gaps:** nothing tests `WorkoutViewModel`, `WorkoutService`, the set-input filtering, or the restore failure paths.

## Product ideas, best value first
1. "Tick filled sets and save" on Finish, plus filling a blank set from "previous" when ticked. Small effort.
2. Keep the screen on during a workout. Very small.
3. A "Last backed up N days ago" line in Settings with a reminder. Small. Allowing phone-to-phone transfer is a one-line change but a privacy decision for the owner.
4. A progression hint per exercise, such as "try 62.5 × 8", tap to fill. Medium.
5. A per-exercise table of recent sessions with a "stalled" marker. Small, since the data already exists.
6. Ignore sets above about 12 reps for the 1RM estimate. Very small; the owner should pick the cutoff.

Cut: weight +/- buttons, plate calculator, kg/lb, RPE or notes UI, supersets, app lock, and more milestone or body-map work.

## Proposed batches
1. **Batch 3, logging data safety (about 450 lines).** Blank-set ticks, "Tick filled sets and save" plus the discard confirm, the Remove exercise confirm, the resume-dialog discard confirm, the T11 fix, duration parsing, input hardening and keep-screen-on.
   - Tests: repository tests for finishing, format and input-filter tests, and a stats test for the blank first session.
   - Risk: low to medium, since it touches the main logging screen; needs a phone check of the keyboard and focus.
2. **Batch 4, backup and schema safety (about 350 lines).** The restore message, the duplicate-id checks, the export size check, the "last backed up" line, T16 with the CI check, and Dependabot.
   - Tests: backup, DataViewModel, migration and settings tests.
   - Risk: low.
3. **Batch 5, stats and speed (about 300 lines).** The PR-flow scope and T18, T14, the blank-history rule, T12, the once-a-second redraw, the 1RM cap if the owner agrees, and tidying edited workouts on open.
   - Tests: stats and progress tests with a fixed clock and zone, plus a timing check on about 20k generated sets.
   - Risk: low. Move this ahead of batch 4 if the owner notices typing lag after the Hevy import.
4. **Batch 6, timer and service lifecycle (about 400 lines).** T09, T10, the notifications-off banner and the accessibility labels and touch sizes.
   - Tests: rest-timer tests with fake clocks and a Robolectric service test.
   - Risk: medium, because foreground-service rules differ across Android 12-15; needs the Doze and battery-saver phone test already listed in the roadmap.

Every batch should pass `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`. Each fix needs a test that fails on current main, and each PR updates `ROADMAP.md` and `AGENTS.md`.
