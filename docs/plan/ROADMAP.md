# Tonnage roadmap and handoff

This file is the single place to learn what is done, what is next, and how work on Tonnage is run. Any agent or person can take over from here, including the owner working alone with Claude Code or Codex, one PR at a time. Keep it current: every batch PR updates the status table below.

**Last updated:** 2026-10-17, after batch 2 merged (`main` at `7721bc7`). A fresh review of `main` produced batches 3 to 6 below. None of them is started.

## What Tonnage is
A private, offline strength logger for Android (a Hevy replacement with no social features), built with Kotlin, Compose, Material 3 and Room. Weights are stored in kg, and the owner uses kg. There is no `INTERNET` permission, no analytics and no cloud sync, and none of that changes. Architecture and conventions are in `AGENTS.md`. Read it first.

## Where the work stands

| Batch | Branch | Scope | Status |
|---|---|---|---|
| 1. Backup and restore safety | `fix/backup-restore-safety` | T01 to T04, T13 | Merged (PR #13, `fddc9b5`). Not yet tried on a phone. |
| 2. Hevy import fidelity and bounded reads | `fix/hevy-import-fidelity` | T05 to T08, T15, T17, Hevy side of T02 | Merged (PR #14, `7721bc7`). Being phone-tested. |
| 3. Logging data safety | `fix/logging-data-safety` | blank-set ticks, finish and discard safety, T11, input hardening, keep screen on | Not started. Pack: `handoffs/batch3-logging-data-safety.md` |
| 4. Backup and schema safety | `fix/backup-schema-safety` | restore message, duplicate ids, export size, last-backed-up line, T16, Dependabot | Not started. Pack: `handoffs/batch4-backup-schema-safety.md` |
| 5. Stats and speed | `fix/stats-and-speed` | PR flow scope, T18, T14, T12, blank-history rule, redraw cost | Not started. Pack: `handoffs/batch5-stats-and-speed.md` |
| 6. Timer and service lifecycle | `fix/timer-service-lifecycle` | T09, T10, notifications-off notice, accessibility | Not started. Pack: `handoffs/batch6-timer-service-lifecycle.md` |

Do them in this order unless the owner says otherwise. The one allowed swap: if typing feels laggy after a big Hevy import, do batch 5 before batch 4. Batch 6 is last because it is the riskiest and needs a phone test.

## Item index
Item ids T01 to T18 come from `reviews/codex-review.md`, which reproduced most of them with test probes. The fresh review `reviews/kiln-review-2.md` (on `main` at `7721bc7`) confirmed that every open T item is still real and added the N items below. The older product review `reviews/kiln-review.md` has the logging-friction findings. Line numbers in all three reviews have moved.

| Id | What | Batch |
|---|---|---|
| T09 | Rest timer is lost if the process dies | 6 |
| T10 | A rejected foreground-service start is never retried | 6 |
| T11 | A focused numeric cell can differ from Room after tapping a "previous" value | 3 |
| T12 | Progress stays on the previous week if it stays on screen across Monday midnight (narrower than first reported) | 5 |
| T14 | Finish preview shows duplicate PRs when an exercise is in more than one block | 5 |
| T16 | No Room schema JSON in git, and no populated v3 upgrade test | 4 |
| T18 | History recompute runs on the main thread while typing | 5 |
| (T-less) | Blank-set tick counts as completed | 3 |
| (T-less) | Input hardening (second decimal separator, absurd values) | 3 |
| (T-less) | Discard confirmation on the resume dialog | 3 |
| (T-less) | PR flow is collected app-wide | 5 |
| (T-less) | Doubled restore error message | 4 |
| (T-less) | Duplicate ids in a backup fail on a raw database constraint | 4 |
| (T-less) | Dependabot missing; Compose keep rule still in `proguard-rules.pro` | 4 (Dependabot), later (keep rule) |
| N01 | Finish with nothing ticked offers a one-tap "Discard workout" that deletes typed but unticked sets | 3 |
| N02 | "Remove exercise" deletes logged sets without a confirm | 3 |
| N03 | Duration cell: typing "130" stores 130 seconds and keeps showing "130" (display fix only) | 3 |
| N04 | A blank ticked set counts as an exercise's first session, so later PRs say "was 0 kg" | 5 |
| N05 | If the app dies while a finished workout is being edited, blank "completed" sets stay in it | 5 |
| N06 | Routine editor draft lives only in memory | later |
| N07 | Backup export has no size limit but restore refuses files over 50 MiB | 4 |
| N08 | Every exercise card redraws once a second while a rest timer runs | 5 |
| N09 | Exercise picker loses its selection on rotation | later |
| N10 | Rest timer ends silently if notifications are denied | 6 |
| N11 | Set inputs have no screen-reader labels and touch targets are under 48 dp | 6 |
| TG | Test gaps: nothing tests `WorkoutViewModel`, `WorkoutService`, set-input filtering or restore failure paths | each batch adds its own |

## Decisions the owner still owes
Each default below is what the pack will do unless the owner says otherwise.
1. **Blank-set tick (batch 3):** ticking an empty set fills it from the "previous" value when there is one, and is ignored when there is none. This is how Hevy behaves.
2. **Estimated 1RM cap (batch 5, optional):** ignore sets above 12 reps for the Epley estimate. Not in the pack by default, because it changes the PRs shown for old workouts. Say "include the 1RM cap" to add it.
3. **Phone-to-phone transfer (batch 4):** `data_extraction_rules.xml` currently excludes everything from device transfer. Leave as is unless the owner wants it, since it is a privacy choice.

## Later ideas, not scheduled
Do these only after batches 3 to 6 and only when the owner asks. They came from the review, ranked by value.
- **Progression hint** at the point of logging, for example "Last: 60 x 8,8,7, try 62.5 x 8", using a simple double-progression rule, tap to fill. Medium effort.
- **Per-exercise history table** of recent sessions with a "stalled for X sessions" marker. Small, the data already exists.
- **Routine editor draft survives process death** (N06) and **picker selection survives rotation** (N09). Low value, small.
- **Remove the Compose keep rule** from `proguard-rules.pro`, only after the release build has run on a phone.

## Cut for now
The review recommends not building these. Revisit only if the owner asks.
- **kg/lb setting.** The owner uses kg.
- Weight plus/minus buttons, a plate calculator, an RPE or notes UI on every set, supersets, more themes or milestones, more body-map work.
- Optional app lock. Add it only if the owner wants it for this app.
- Cloud sync, social features and any network access. They go against the offline and private brief.

## How work is run
1. **Plan:** Kiln (product strategist) writes a handoff pack for each batch: goal, repo and branch, scope and out-of-scope, acceptance criteria, risks and engine. Packs live in `handoffs/`.
2. **Go:** the owner (Jan) says go for one batch.
3. **Build:** one agent (Codex or Claude Code) builds on the named feature branch, runs `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`, and opens a PR. Every fix needs a test that fails on current `main`.
4. **Review:** a different engine than the builder checks the diff against the pack's acceptance criteria and says "approve" or "changes needed". After a fix, only the new commit is re-checked. One review round is the default.
5. **Merge:** squash-merge, pinned to the exact commit that was approved, only when it is approved, CI is green and the owner has OK'd that PR. Anything pushed after approval needs another check.
6. **Never:** push to `main`, force-push, create tags or releases, or add network access. The owner publishes releases himself.
7. **Docs:** every batch PR updates the status table here, and `AGENTS.md` when a convention changes.

The coordination room is the "Coding" group chat in Grok Bot (Jan, Atlas, Kiln, Nightshift).

### When the owner works alone with Claude Code or Codex
Open the repo (Claude Code reads `CLAUDE.md`, which points to `AGENTS.md`; Codex reads `AGENTS.md`). A good first message: "Read `docs/plan/ROADMAP.md` and `AGENTS.md`, then do the next not-started batch from its pack in `docs/plan/handoffs/`. Make the feature branch named in the pack, keep to its scope, add the tests it requires, run `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`, update the status table, and open a PR. Do not merge." For review, start a fresh session (or the other tool) and say: "Review this PR against the acceptance criteria in the pack. Report blockers, should-fix and nits with file and line. Do not edit." Build with one engine and review with the other.

## Open verification
Nothing below has been checked on a real phone:
- A real backup and restore of the owner's data (batch 1).
- A Hevy re-import after batch 2: the preview says "Times read as <zone>", nothing duplicates, a renamed workout is skipped, Plank records by time.
- The R8 release build at runtime. CI only compiles it.
- Rest timer behaviour under Doze and OEM battery savers (batch 6).

## Facts a new owner needs
- The repo is public. Release signing keys come from CI secrets only. Release builds fall back to the debug key if no keystore is configured.
- Room is at version 4 with migrations 1 to 2, 2 to 3 and 3 to 4. Entity changes need a version bump, a non-destructive migration and a `MigrationTest` case. The schema export is configured (`room.schemaLocation`) but no JSON is committed yet (batch 4).
- `workouts.routineId` is deliberately not a foreign key, so deleting a routine keeps history.
- Backups use `format` and `schemaVersion`. Batch 1 made the reader strict. The exporter has never changed since it was added (commit `c735e04`), so every released backup matches the strict reader.
- CI (`.github/workflows/android-ci.yml`) runs `test`, `lintDebug`, `assembleDebug` and `assembleRelease` on every push and pull request.
- Personal records are never stored. They are recomputed from sets (`WorkoutStats.recordsByWorkout`).
