# Tonnage roadmap and handoff

This file is the single place to learn what is done, what is next, and how work on Tonnage is run. Any agent or person can take over from here, including the owner working alone with Claude Code or Codex, one PR at a time. Keep it current: every batch PR updates the status table below.

**Last updated:** 2026-10-18, after batches 3 and 4 merged and shipped in 1.5.0. The review of `main` at `093ac43` (`reviews/claude-review-4.md`) re-checked the remaining work, found a routine bug from batch 3 (R4-01) and split batches 5 and 6 into smaller PRs.

**The work queue is on GitHub.** Tracking issue [#25](https://github.com/qualified-opinion/tonnage/issues/25) lists the remaining PRs in order. Each issue (#26 to #37) is self-contained: pick one, read it fully, open one PR that says `Closes #<n>`. The issues replace the batch 5 and 6 packs as the spec; the packs stay for history.

## What Tonnage is
A private, offline strength logger for Android (a Hevy replacement with no social features), built with Kotlin, Compose, Material 3 and Room. Weights are stored in kg, and the owner uses kg. There is no `INTERNET` permission, no analytics and no cloud sync, and none of that changes. Architecture, the database, build and release are in `HANDOVER.md`; the rules are in `AGENTS.md`. Read both first.

## Shipped

| Release | Date | What it added |
|---|---|---|
| 1.0.0 | 2026-10-15 | Phases 0 to 6: exercise library, workout logging with the lock-screen rest timer, routines and previous values, history with edit and records, Hevy import and backup, Progress tab with charts, weekly stats and milestones, green theme with light/dark choice |
| 1.1.0 | 2026-10-16 | Muscle heat map on the Progress tab |
| 1.2.0 | 2026-10-16 | Fuller body map with 40+ named muscles |
| 1.3.0 | 2026-10-16 | Detailed anatomy art (Ryan Graves, CC BY 4.0) |
| 1.4.0 | 2026-10-17 | Batch 1 (backup and restore safety, PR #13) and batch 2 (Hevy import fidelity, PR #14) |
| 1.4.1 | 2026-10-17 | Plank and other time cells take plain digits from the number pad (N03, PR #17) |
| 1.5.0 | 2026-10-17 | Batch 3 (logging data safety, PR #18) and batch 4 (backup and schema safety, PR #19) |

## Where the work stands

| Batch | Branch | Scope | Status |
|---|---|---|---|
| 1. Backup and restore safety | `fix/backup-restore-safety` | T01 to T04, T13 | Merged (PR #13, `fddc9b5`). Released in 1.4.0 and phone-tested 2026-10-17: backup and restore of real data work, and restore is refused during a workout. |
| 2. Hevy import fidelity and bounded reads | `fix/hevy-import-fidelity` | T05 to T08, T15, T17, Hevy side of T02 | Merged (PR #14, `7721bc7`). Released in 1.4.0 and phone-tested 2026-10-17: re-import adds nothing and a renamed workout is skipped. Plank time entry needed a colon the number pad lacks (N03, fixed). |
| 3. Logging data safety | `claude/plan-and-handover-n0yv7w` (this session can only push that branch) | blank-set ticks, finish and discard safety, T11, input hardening, keep screen on | Merged (PR #18, `a3de87b`), released in 1.5.0. Needs a phone check: keyboard and focus, tapping previous while typing, finish and discard dialogs, screen staying on. Follow-up bug R4-01 (routine plan counted as logged) is #26. |
| 4. Backup and schema safety | `claude/plan-and-handover-n0yv7w` | restore message, duplicate ids, export size, last-backed-up line, T16, Dependabot, N12, N13 | Merged (PR #19, `093ac43`), released in 1.5.0. Needs a phone check: a real backup and restore, and the "Last backup" line in Settings. Follow-ups R4-03, R4-04, R4-07 are #28. |

### Remaining work, in order (from `reviews/claude-review-4.md`)

| PR | Issue | What | Findings | Size | Must follow | Status |
|---|---|---|---|---|---|---|
| 1 | #26 | A routine's planned sets aren't treated as logged | R4-01, R4-02 | M | none | Not started |
| 2 | #27 | Set cells: bad time text, focus after delete, small input gaps | R4-05, R4-06, R4-08, R4-09, N09 | S | #26 | Not started |
| 3 | #28 | Backup and release tidy-ups | R4-03, R4-04, R4-07 | S | none | Not started |
| 4 | #29 | Personal records: no duplicates, no "was 0 kg" (batch 5a) | T14, N04, N05 | M | #26 | Not started |
| 5 | #30 | Typing stays instant with years of history (batch 5b) | PR flow, T18, N08 | M | #29 | Not started |
| 6 | #31 | Progress moves to the new week at Monday midnight (batch 5c) | T12 | S | #29 | Not started |
| 7 | #32 | Screen-reader labels and bigger touch targets (batch 6c) | N11 | S | #27 | Not started |
| 8 | #33 | The rest timer never ends silently (batch 6a) | T10, N10, R4-10 to R4-13 | M | none | Not started |
| 9 | #34 | A running rest survives the app being closed (batch 6b) | T09 | M | #33 | Not started |
| 10 | #35 | Backups keep the theme and Hevy time zone (optional) | N14 | S | #28 | Not started |
| 11 | #36 | Routine draft survives the app being closed (optional) | N06 | M | none | Not started |
| 12 | #37 | Trim the release build's keep rules (optional, last) | keep rule, R4-14 | S | everything else | Not started |

Do them in this order unless the owner says otherwise. #26 comes first because it is a real bug in the released app. #33 and #34 need a phone test before a release. Open Dependabot PRs: merge the low-risk ones first (Actions, coroutines, Room) and the Kotlin 2.4 bump last, each only when green.

## Item index
Item ids T01 to T18 come from `reviews/codex-review.md`, which reproduced most of them with test probes. The fresh review `reviews/kiln-review-2.md` (on `main` at `7721bc7`) confirmed that every open T item is still real and added the N items below. The older product review `reviews/kiln-review.md` has the logging-friction findings. Line numbers in all three reviews have moved.

| Id | What | Batch |
|---|---|---|
| T09 | Rest timer is lost if the process dies | 6 (#34) |
| T10 | A rejected foreground-service start is never retried | 6 (#33) |
| T11 | A focused numeric cell can differ from Room after tapping a "previous" value | 3 |
| T12 | Progress stays on the previous week if it stays on screen across Monday midnight (narrower than first reported) | 5 (#31) |
| T14 | Finish preview shows duplicate PRs when an exercise is in more than one block | 5 (#29) |
| T16 | No Room schema JSON in git, and no populated v3 upgrade test | 4 (version 4 JSON committed; version 3 can't be regenerated, so its test builds it by hand) |
| T18 | History recompute runs on the main thread while typing | 5 (#30) |
| (T-less) | Blank-set tick counts as completed | 3 |
| (T-less) | Input hardening (second decimal separator, absurd values) | 3 |
| (T-less) | Discard confirmation on the resume dialog | 3 |
| (T-less) | PR flow is collected app-wide | 5 (#30) |
| (T-less) | Doubled restore error message | 4 |
| (T-less) | Duplicate ids in a backup fail on a raw database constraint | 4 |
| (T-less) | Dependabot missing; Compose keep rule still in `proguard-rules.pro` | Dependabot done in 4; keep rule #37 |
| N01 | Finish with nothing ticked offers a one-tap "Discard workout" that deletes typed but unticked sets | 3 |
| N02 | "Remove exercise" deletes logged sets without a confirm | 3 |
| N03 | Duration cell: typing "130" stored 130 seconds and kept showing "130", and the number pad has no colon | Fixed after the 1.4.0 phone test: plain digits now fill from the right ("130" is 1:30) and the cell shows the saved m:ss when you leave it |
| N04 | A blank ticked set counts as an exercise's first session, so later PRs say "was 0 kg" | 5 (#29) |
| N05 | If the app dies while a finished workout is being edited, blank "completed" sets stay in it | 5 (#29) |
| N06 | Routine editor draft lives only in memory | #36 |
| N07 | Backup export has no size limit but restore refuses files over 50 MiB | 4 |
| N08 | Every exercise card redraws once a second while a rest timer runs | 5 (#30) |
| N09 | Exercise picker loses its selection on rotation | #27 |
| N10 | Rest timer ends silently if notifications are denied | 6 (#33) |
| N11 | Set inputs have no screen-reader labels and touch targets are under 48 dp | 6 (#32) |
| N12 | Library updates never reach installed phones (seed only runs on an empty library), so a future alias or backup can point at an exercise the phone lacks | 4 (the matcher already dropped aliases missing from the library; batch 4 adds the test) |
| N13 | The release workflow publishes from whatever branch is picked | 4 (manual runs); tag pushes are #28 |
| N14 | Theme and the saved Hevy import zone are not in backups, so a re-import after restoring on a phone in another zone can duplicate | #35 (no format bump needed) |
| N15 | Batches 1 and 2 were merged but not released | Done: released as 1.4.0 |
| TG | Test gaps: nothing tests `WorkoutViewModel`, `WorkoutService`, set-input filtering or restore failure paths | each batch adds its own |
| R4-01 to R4-14 | New findings from the 2026-10-18 review, see `reviews/claude-review-4.md` | #26 to #37 |

## Decisions the owner still owes
Each default below is what the pack will do unless the owner says otherwise.
1. **Blank-set tick (batch 3):** ticking an empty set fills it from the "previous" value when there is one, and is ignored when there is none. This is how Hevy behaves. Built this way in batch 3; say so if you want it different.
2. **Estimated 1RM cap (batch 5, optional):** ignore sets above 12 reps for the Epley estimate. Not in the pack by default, because it changes the PRs shown for old workouts. Say "include the 1RM cap" to add it.
3. **Phone-to-phone transfer (batch 4):** `data_extraction_rules.xml` currently excludes everything from device transfer. Leave as is unless the owner wants it, since it is a privacy choice.
4. **Phase 7 extras (after batch 6):** the original plan (`workout-app/plan.md`) listed body weight, plate calculator, progression hints and Health Connect as phase 7; the review later suggested cutting the plate calculator. Default: none is built until the owner asks, then one PR each in the order below.

## Phase 7 extras, not scheduled
The last phase of the original plan. Do these only after batches 3 to 6 and only when the owner asks, one PR each.
1. **Progression hints.** At the point of logging, for example "Last: 60 x 8,8,7, try 62.5 x 8", using a simple double-progression rule (all sets hit the top of the rep range, add the smallest weight step), tap to fill. Pure logic in `domain/`, fed by `WorkoutRepository.previousSets`; no schema change. Medium effort. The review ranked it the most useful idea.
2. **Body weight log.** A new `body_weights` table (date, kg), a small entry screen under More, and a line chart on Progress using `ui/components/Charts.kt`. Needs Room version 5 with `MIGRATION_4_5` and a `MigrationTest` case, plus a backup `SCHEMA_VERSION` bump that still reads version 1. Small to medium.
3. **Plate calculator.** A dialog from a weight cell that shows the plates per side for a bar weight (default 20 kg) and the owner's plate set. Display-only, no schema change; the plate set lives in `SettingsStore`. Small. The review rated it low value, so confirm with the owner first.
4. **Health Connect export.** Write finished workouts (type, start, end, and optionally calories) to Health Connect, Android's on-phone health store. It is not a network call, so it fits the offline rule, but it adds a dependency, Health Connect permissions and a privacy choice. Export only, off by default, behind a Settings switch. Medium. Ask the owner before starting.

## Later ideas, not scheduled
Do these only after batches 3 to 6 and only when the owner asks. They came from the review, ranked by value.
- **Per-exercise history table** of recent sessions with a "stalled for X sessions" marker. Small, the data already exists.

## Cut for now
The review recommends not building these. Revisit only if the owner asks.
- **kg/lb setting.** The owner uses kg.
- Weight plus/minus buttons (the plate calculator moved to phase 7 above), an RPE or notes UI on every set, supersets, more themes or milestones, more body-map work.
- Optional app lock. Add it only if the owner wants it for this app.
- Cloud sync, social features and any network access. They go against the offline and private brief.

## How work is run
Since 2026-10-19 one AI agent builds and Claude reviews; the owner merges.
1. **Pick:** the owner gives the builder agent one issue from the work order in tracking issue #25 (or says "the next one"). Starter prompt: `docs/plan/BUILDER.md`.
2. **Build:** the builder branches from `main`, does only that issue, adds a test that fails on the old code, and opens one PR against `main` with `Closes #NN`, using the PR template. CI must be green.
3. **Review:** the owner pastes the PR link to Claude in the Tonnage project. Claude reviews against the issue and `docs/plan/REVIEW.md`, posts the verdict on the PR ("approve" or "changes needed", blockers first), and gives the owner a plain summary. The builder fixes comments with new commits; Claude re-checks only those.
4. **Merge:** the owner merges once Claude approves and CI is green. The issue closes and #25 ticks itself.
5. **Release:** only when the owner says "publish X.Y.Z" (see `HANDOVER.md`).
6. **Never** (anyone): push to `main`, force-push a reviewed branch, tag or release without the owner, add network access, or use a destructive migration.
7. **Docs:** builders don't edit the status table here (it caused merge conflicts); the tracking issue is the live status, and the reviewer refreshes this file every few merges. Builders still update `AGENTS.md` when a convention changes.

## Open verification
Not yet checked on a real phone (batches 1 and 2 passed on 2026-10-17):
- Plank time entry with the number pad after the N03 fix (released in 1.4.1).
- The R8 release build at runtime. CI only compiles it.
- Rest timer behaviour under Doze and OEM battery savers (batch 6).

## Facts a new owner needs
- The repo is public. Release signing keys come from CI secrets only (names in `HANDOVER.md`). A published release refuses to build without them; other release builds fall back to the debug key.
- Room is at version 4 with migrations 1 to 2, 2 to 3 and 3 to 4. Entity changes need a version bump, a non-destructive migration and a `MigrationTest` case. The schema JSON is committed in `app/schemas` and CI fails if a build changes it, so a schema change can't merge without its JSON.
- `workouts.routineId` is deliberately not a foreign key, so deleting a routine keeps history.
- Backups use `format` and `schemaVersion`. Batch 1 made the reader strict. The exporter has never changed since it was added (commit `c735e04`), so every released backup matches the strict reader.
- CI (`.github/workflows/android-ci.yml`) runs `test`, `lintDebug`, `assembleDebug` and `assembleRelease` on every push and pull request.
- Personal records are never stored. They are recomputed from sets (`WorkoutStats.recordsByWorkout`).
