# Review of `main` at `093ac43` after batches 3 and 4 (2026-10-18)

Claude Code read batches 3 (`a3de87b`) and 4 (`093ac43`) line by line, then re-checked every open item in batches 5 and 6 and the "later" list against the current code. Nothing was built here: the cloud container has no Android SDK, so this is a static review. CI was green on `093ac43`. New findings are numbered R4-01 onwards. The plan is now on GitHub: tracking issue #25, one issue per PR (#26 to #37).

## Health summary
The app is in good shape for one user's daily logging: no network, no secrets in the repo, migrations covered by tests, and backups that refuse bad files before deleting anything. The one real regression is from batch 3. A routine's planned weights and reps count as "filled" sets, so the finish dialog offers to log sets you never did (R4-01). The remaining planned work (records, typing speed, rest timer reliability) is all still needed, and batches 5 and 6 are each too big for one PR, so they are split. 1.5.0 is safe to keep using. Until R4-01 is fixed, choose **Save** rather than **Tick them and save** when finishing a routine you didn't complete.

## New findings

| Id | Priority | What happens to the user | Where | Issue |
|---|---|---|---|---|
| R4-01 | P1 | Finishing a routine offers to "tick and save" planned sets you skipped, which logs sets that never happened. Discarding an untouched routine asks twice and warns about "filled" sets that are only the plan. | `WorkoutRepository.startFromRoutine`, `addSet`; `WorkoutStats.summarize`; `WorkoutScreen` finish dialog | #26 |
| R4-02 | P2 | A ticked set you later cleared still counts. Save can leave a finished workout with no exercises in History and Progress. | `WorkoutStats.summarize`; `WorkoutDao.finishWorkout` | #26 |
| R4-03 | P2 | A `v*` tag pushed on any branch still publishes a release; batch 4 only guarded the manual run. | `android-release.yml`, "Work out version" | #28 |
| R4-04 | P2 | The 800 KB exercise library is parsed and re-inserted on every app start. | `ExerciseRepository.seedLibrary` | #28 |
| R4-05 | P3 | A pasted or typed time like "1:" or "1:2:3:4" wipes the stored time, and "1:3045" saves as 51:45. | `SetInput.accept` | #27 |
| R4-06 | P3 | Ticking a set from an empty, focused cell fills the data but the cell stays blank until you leave it. | `SetNumberCell` | #27 |
| R4-07 | P3 | A backup too large to save still leaves an empty file in Drive or Downloads. | `DataViewModel.saveBackup` | #28 |
| R4-08 | P3 | Deleting the set you're typing in moves your typing to the next set. | `ExerciseCard` (rows not keyed) | #27 |
| R4-09 | P3, needs a phone check | Arabic-Indic or Persian digits show in a weight cell but save as nothing. | `SetInput.accept`, `Format.parseDecimal` | #27 |
| R4-10 | P2 | If the rest timer service isn't running, the in-app timer ends the rest silently. | `WorkoutScreen` `RestTimerBar` fallback | #33 |
| R4-11 | P2 | Blocking only the "Rest finished" notification type isn't detected, so rests end silently. | `WorkoutNotifications.post` | #33 |
| R4-12 | P3 | Resuming a workout after the app was killed never asks for notification permission. | `ResumeDialog` → `resumePending` | #33 |
| R4-13 | P3, needs a phone check | The rest vibration may be muted on silent or Do Not Disturb. | `WorkoutService.vibrate` | #33 |
| R4-14 | P3 | A keep rule for Room DAOs matches nothing (DAOs are abstract classes). Harmless. | `app/proguard-rules.pro` | #37 |

## Planned items re-checked

| Item | Still real? | Change to the plan | Issue |
|---|---|---|---|
| T14 duplicate PRs | Yes | Line refs only (`WorkoutStats.detectRecords`) | #29 |
| N04 "was 0 kg" | Partly | Batch 3 closed the blank-tick path. Still real for reps-without-weight, 0 kg, Hevy rows and pre-1.5.0 history. Rule: per record kind, an earlier value above 0 | #29 |
| N05 edited workout leftovers | Yes | Tidy in `HistoryViewModel.open()` | #29 |
| PR flow scope | Yes | Line refs only | #30 |
| T18 main-thread recompute | Yes, wider | Also hits typing in edit mode, where the data really changes; needs `flowOn` + `conflate`/`mapLatest` and a pause or debounce while editing | #30 |
| N08 card redraws | Yes, reshaped | It's the workout duration clock, so every card redraws once a second for the whole workout, not only during a rest | #30 |
| T12 week rollover | Yes | None | #31 |
| N11 accessibility | Partly | Tick already has a label; the set-type button is also under 48 dp | #32 |
| T10 refused service start | Yes | It is `startForegroundService` that is swallowed; add `ensureService()` and a resume hook in `MainActivity` | #33 |
| N10 notifications off | Yes | Also check the channel (R4-11) | #33 |
| T09 rest lost on process death | Yes | Restore rule: `0 < remaining <= total` on the elapsed clock and wall clock agreeing | #34 |
| N14 settings not in backups | Yes | No `SCHEMA_VERSION` bump needed: the reader ignores unknown keys | #35 |
| N06 routine draft in memory | Yes | Use `SavedStateHandle` | #36 |
| N09 picker selection on rotation | Yes | One-line `rememberSaveable`, folded into #27 | #27 |
| Compose keep rule | Yes | Plus R4-14 | #37 |
| Phase 7 extras | Not started | No issues until the owner asks | none |

## Checked and fine
Typing then tapping "previous" (writes stay in order); rotation; `SetInput.accept` for normal number-pad input; `complete()` and `completeFilled`; every discard path confirms; edit mode tidy and record recompute; keep-screen-on is limited to the workout screen; `seedLibrary` never touches custom rows and doesn't conflict with restore; restore checks run before any delete; the backup size check measures what restore reads; `lastBackupAt` is written only after the file closes; `Format.lastBackup` handles time-zone changes; the schema CI check covers changed and new files; `dependabot.yml` is valid; a manual release run with a version is limited to `main`.
