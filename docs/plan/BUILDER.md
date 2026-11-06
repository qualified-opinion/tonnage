# Building Tonnage: start here

This is for the AI agent (or person) who builds fixes for Tonnage. Since 2026-10-19 the work is split three ways:

| Who | Does | Never does |
|---|---|---|
| **Builder** (you) | Picks one GitHub issue, builds it, opens one PR, fixes review comments | Merges, publishes a release, pushes to `main` |
| **Reviewer** (Claude, in Jan's project) | Reviews each PR against its issue and `docs/plan/REVIEW.md`, posts the verdict on the PR | Pushes code to your PR |
| **Owner** (Jan) | Hands you an issue, asks for the review, merges, decides when to release | Reads code. Jan is an accountant, not a developer, so write in plain language |

## Starter prompt (the owner pastes this)
> Read `docs/plan/BUILDER.md`, then `HANDOVER.md`, `AGENTS.md` and `docs/plan/ROADMAP.md` in qualified-opinion/tonnage. Do issue #NN, and only that issue. Open one PR against `main` that says `Closes #NN`, fill in the PR template, get CI green, and stop. Do not merge.

If no issue number is given, take the **first open issue in the work order** of tracking issue #65 whose "must follow" issues are all closed.

## How to do one issue
1. **Read** the issue in full, then `HANDOVER.md` (how the app is built), `AGENTS.md` (rules) and the files the issue names. Each issue is self-contained: Problem, Change, Done when, How to test on your phone.
2. **Check it is unblocked.** The issue's first line says which issues must be merged first. If one is still open, stop and say so on the issue.
3. **Branch** from the latest `main`: `fix/<short-name>`, `feat/<short-name>` or `refactor/<short-name>`. (A Claude Code cloud session that may only push its assigned `claude/…` branch uses that branch.) Never base a PR on another PR's branch: the owner merges from the PR page, and a stacked PR lands on the wrong branch.
4. **Build only what the issue says.** Found another problem? Comment on the tracking issue #65; don't fix it in this PR.
5. **Test.** Every logic change gets a test that fails on the old code. Run what you can: `./gradlew test lintDebug assembleDebug assembleRelease`. If your environment has no Android SDK (cloud containers often don't), say so in the PR and let CI build it.
6. **Open the PR** against `main`. The template (`.github/pull_request_template.md`) has the sections the owner reads: What was wrong / What changed / How to test on your phone / Risk & how to undo / Not tested here. Plain language, from the app user's point of view. Include `Closes #NN`.
7. **Get CI green.** CI (`Android CI`) runs tests, lint, debug and release builds, and checks that `app/schemas` is committed. A red PR is never "ready for review". Fix the root cause; never skip, disable or loosen a test to get green.
8. **Tell the owner it's ready** with the PR link. The owner asks the reviewer to review it.
9. **Fix review comments** with new commits on the same branch (no force-push once review has started), reply on each comment with what you changed or why not, and say when it's ready for another look.

## Hard rules (the reviewer checks every one)
- Package `com.janreins.tonnage` never changes. No `INTERNET` permission, no network calls, analytics or sync.
- Weights are stored in kg; lb is display-only.
- No Room entity change unless the issue says so. If it does: bump the version, add a migration that keeps every row, a `MigrationTest` case, and commit the new `app/schemas` JSON. Never a destructive migration.
- Backups: keep `BackupJson.read` able to read every older backup. Bump `SCHEMA_VERSION` only for a change an older app couldn't read.
- PR size under about 300 changed lines. One issue per PR. No drive-by refactors or formatting changes.
- Never commit secrets, keystores, `local.properties` or build output. Don't print the signing secrets.
- Don't rename `.github/workflows/android-release.yml` (`versionCode` is its run number).
- Don't edit the status table in `docs/plan/ROADMAP.md`; the reviewer ticks the tracking issue #65 after your PR merges. Do update `AGENTS.md` when you change a convention it describes.
- Never merge, tag or publish a release.

## When you're stuck
Comment on the issue with what you found and the options, and stop. Don't guess on anything that could lose the owner's workout history.
