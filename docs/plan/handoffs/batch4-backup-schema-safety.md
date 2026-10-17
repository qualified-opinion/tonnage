# Tonnage batch 4: backup and schema safety

**Repo:** qualified-opinion/tonnage
**Branch:** `fix/backup-schema-safety`, from `main` after batch 3 merges (or from `7721bc7` if batch 3 is skipped)
**Engine:** the owner picks one agent to build; the other engine reviews, one round. Merge only after approval, green CI and the owner's OK. No tags, no releases.
**Source:** `reviews/kiln-review-2.md`: doubled restore message, duplicate ids, N07, T16, CI housekeeping. Size target: about 350 changed lines, plus about 80 for items 7 and 8.

## Goal
A bad or oversized backup fails with a clear message and changes nothing, the owner can see when the last backup was made, and database upgrades are protected by committed schemas and a real upgrade test.

## Scope
1. **Doubled restore message.** `DataViewModel` (around the restore failure handler) shows "The restore failed and nothing was changed: This file is incomplete or damaged. Nothing was changed." Catch `BackupFormatException` on its own and show its message once. Other failures keep the generic message.
2. **Duplicate ids.** In `BackupRepository.restore` (and the reader, whichever is cleaner), before anything is deleted, reject a file where any primary-key list has duplicates (routines, routine exercises, routine sets, workouts, workout exercises, sets, custom exercises) or where a custom exercise id equals a built-in exercise id. The error names the kind of problem in plain words ("The file has two routines with the same id"). Nothing changes on rejection.
3. **N07, export size.** Backup export has no size limit while restore refuses files over 50 MiB (`BoundedRead.kt`). Before writing, if the JSON would be larger than the restore limit, refuse the export with a clear message instead of producing a file that cannot be restored.
4. **Last backed up.** Store the time of the last successful export in `SettingsStore` (written only after the file is fully written). Settings shows "Last backup: never" or "Last backup: N days ago" (today, yesterday, N days ago). No reminder or notification in this batch. Do not change `data_extraction_rules.xml`.
5. **T16, schemas.** Room already writes schemas to `app/schemas` (`room.schemaLocation`), but none is committed. Commit the current version 4 schema JSON (and version 3 if Room can regenerate it from git history; if not, say so). Add a populated version 3 to 4 upgrade test to `MigrationTest` (rows in workouts, exercises and routines survive and the new columns have sensible defaults). Add a CI step after the build that fails when `git status --porcelain app/schemas` is not empty, so a schema change cannot merge without its JSON.
6. **Dependabot.** Add `.github/dependabot.yml` for the `gradle` and `github-actions` ecosystems, weekly, with a small open-PR limit. Branch protection on `main` is a GitHub setting for the owner; say in the PR that it is not part of this change.
7. **N12, library updates (added by `reviews/claude-review-3.md`).** Change `ExerciseRepository.seedIfEmpty` to insert any built-in exercise from `exercises.json` whose id is missing, on every start, without touching custom rows or existing built-ins. Make `ExerciseMatcher` ignore an alias whose id is not in the library it was given. Test with a database seeded from a shorter list and with an alias pointing at a missing id.
8. **N13, release from main only (added by `reviews/claude-review-3.md`).** In `android-release.yml`, fail the run before building when a version is typed and `github.ref` is not `refs/heads/main`. Tag pushes keep working.

## Out of scope
- Phone-to-phone transfer rules, backup format changes (`SCHEMA_VERSION` stays), encryption, cloud backup, reminders.
- Removing the Compose keep rule from `proguard-rules.pro`.
- Any Room entity change or version bump. Logging UI (batch 3), stats (batch 5), timer (batch 6).

## Acceptance criteria
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and CI are green; every existing test still passes.
- New tests, each failing on current `main`:
  - a rejected format error shows one message, not two;
  - duplicate routine ids, duplicate custom exercise ids and a custom id equal to a built-in id are all rejected before anything is deleted, with the database unchanged;
  - an export that would exceed the limit is refused;
  - the last-backup text for never, today, yesterday and N days (fixed clock);
  - the v3 to v4 upgrade test passes with populated data.
- CI fails if `app/schemas` has an uncommitted change (show it failing once, locally or in the PR, and say how).
- Every previously valid backup, including `src/test/resources/backup/current-export-v1.json`, still restores.
- The PR says what a phone should check: a real export and restore of the owner's data, and the Settings line.

## Risks
- Duplicate checks must not reject valid older backups; keep the reader as strict as `BackupJson.write` has always been, no stricter.
- Regenerating the old schema: do not edit entities to make it match. If version 3 cannot be reproduced, commit version 4 only and test the upgrade with the hand-built version 3 as `MigrationTest` already does for 1 and 2.

## Process
One PR, update the status table in `ROADMAP.md`, and add an `AGENTS.md` line for the schema CI check. Do not merge.
