# Handover review of `main` at `1816b12` (2026-10-17)

Written while adding `HANDOVER.md`. Claude Code re-read the code, the workflows and the earlier reviews. Every open item in `kiln-review-2.md` still stands; this review did not re-check them line by line. The findings below are new, numbered N12 onwards in `ROADMAP.md`.

## New findings

- **N12, Med (latent): library updates never reach installed phones.** `ExerciseRepository.seedIfEmpty` loads `exercises.json` only when there are no built-in exercises, so a later app version that adds or renames exercises leaves existing installs on the old list. Two places then go wrong: `ExerciseMatcher` returns an alias id (`ExerciseMatcher.kt:55`) without checking it is in the phone's library, so a Hevy import can write an exercise id the phone doesn't have, with no review; and `BackupRepository.restore` refuses a backup from a newer phone that uses a new built-in id. Nothing is broken today, because `exercises.json` hasn't changed since the first install (`5181b10`). Fix before the first library change: seed by upserting built-ins whose id is missing (never touching custom rows), and have the matcher drop aliases whose id isn't in the library. Test with a database seeded from a smaller list.
- **N13, Low: a release can be published from any branch.** `android-release.yml` tags and publishes whatever branch the Run workflow form picks. Fix: fail the publish steps unless `github.ref` is `refs/heads/main` or a `v*` tag. Until then, `HANDOVER.md` says to pick `main`.
- **N14, Low: theme and the Hevy import zone aren't in backups.** `BackupContents` carries only the default rest time; `SettingsStore` also has the theme and the zone saved on the first Hevy import. After a restore on a new phone in a different zone, a Hevy re-import reads times in the new zone and can duplicate workouts. Fix: add both to the backup (needs a `SCHEMA_VERSION` bump that still reads version 1) and restore the zone only if none is saved. The README's "settings" line should say "default rest time" until then.
- **N15, Low: unreleased fixes.** Batches 1 and 2 merged after 1.3.0, so the backup-safety and Hevy-fidelity fixes are not on the phone yet. Publish 1.4.0 from `main` once the owner is happy with the phone checks in `ROADMAP.md`.
- **Docs:** the README roadmap table still marked phase 6 as current, and `plan.md`'s phase 7 extras (body weight, plate calculator, progression hints, Health Connect) disagreed with the roadmap's "Cut for now" list. Both are resolved in `ROADMAP.md` (phase 7 section) and the README in the same PR as this review.

## Checked and fine
- No network permission, no `INTERNET` use, no analytics.
- Signing secrets are only read from Actions secrets; `.gitignore` blocks keystores.
- No destructive migration; migrations 1 to 4 are additive.
- `versionCode` comes from the release workflow's run number, so it only increases (keep the workflow file name).
