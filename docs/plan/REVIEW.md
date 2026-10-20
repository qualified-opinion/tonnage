# Reviewing a Tonnage PR

The reviewer (Claude, in Jan's project) uses this list for every builder PR. Builders can read it to know what will be checked.

## Verdict
Post one review on the PR, and a short plain-language summary to the owner:
- **Approve:** safe to merge once CI is green. Say what the owner should check on the phone after the next release.
- **Changes needed:** list blockers first, each with file and line, what goes wrong for the user, and what would fix it. Should-fix and nits after, clearly marked as not blocking.
A push after a verdict needs another look at the new commits.

## Checklist
1. **Scope.** The PR does what its issue's **Change** and **Done when** say, and nothing else. One issue, `Closes #NN`, under about 300 changed lines.
2. **Order.** Every issue it must follow (first line of the issue) is merged. It's based on the latest `main`, not on another PR.
3. **CI** is green on the latest commit, including the Room schema check.
4. **Data safety.** No path can lose or silently change workout history, routines, custom exercises or backups. Writes still go straight to Room. Any migration keeps every row and has a `MigrationTest` case. Older backups still restore.
5. **Hard rules** from `docs/plan/BUILDER.md`: package name, no network, kg storage, no secrets, workflow file name, no ROADMAP status edits.
6. **Tests.** Each logic change has a test that fails on the old code (read it and check it would). No test was skipped, deleted or weakened.
7. **Correctness.** Trace the issue's "How to test on your phone" steps through the code. Check edge cases the issue names, plus empty values, rotation and process death where it's relevant.
8. **Conventions.** Matches the surrounding code; `AGENTS.md` is updated if a convention changed.
9. **PR text.** The template sections are filled in plainly enough for the owner, and "Not tested here" is honest.

## After merge
The reviewer ticks the issue off in the tracking issue #25 checklist (GitHub closes the issue but doesn't tick the box). Every few merges, the reviewer updates the status table in `docs/plan/ROADMAP.md` and suggests a release when there's something worth testing on the phone.
