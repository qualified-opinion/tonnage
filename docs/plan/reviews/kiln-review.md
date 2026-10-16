# Code review by Kiln (Claude-assisted read)

> Written 2026-10-17 against `main` at `2037924`. Read-only: nothing here was built or run on a device. Line numbers have since moved. The Codex review in this folder reproduced several of these with probes and found more severe backup bugs, so use the roadmap order, not the order in this file.

# Tonnage – read-only code review

Repo: qualified-opinion/tonnage (PUBLIC), `main` @ 2037924 (merge of PR #12, 2026-10-16 13:52 Manila).
Method: shallow clone + static read of the core paths (logging, stats/PRs, rest timer/service, backup/import, progress, build/CI).
NOT done: build/test run (no JDK/Android SDK on the box), on-device testing, a full read of every UI file (Compose layout files, body-map art, chart drawing, exercise matcher/aliases, routine editor were only skimmed/not read).
All line refs below are relative to `app/src/main/java/com/janreins/tonnage/` unless noted.

## 1. Stack and size
- Native Android: Kotlin 2.2.10, Jetpack Compose (BOM 2024.09.00) + Material 3, Room 2.7.0 (DB v4, 3 hand-written migrations), KSP, coroutines/Flow. AGP 9.1.1, minSdk 26, target/compile 35, JDK 17 in CI.
- No network permission in the manifest, no analytics, no third-party SDKs. Only libs: AndroidX, Compose, Room, coroutines (+ Robolectric for tests).
- 59 Kotlin files in main (~7.9k lines), 19 test files (~2.0k lines, ~100 @Test). Assets: exercises.json (806 KB, 876 exercises, public domain free-exercise-db), body_map.txt (236 KB, CC BY art, credited).
- History: 12 PRs, all merged, all by Claude branches (`claude/...`), 2026-10-15 → 10-16. 0 open PRs, 0 issues (open or closed).
- CI: `Android CI` (unit tests, lintDebug, assembleDebug, assembleRelease, uploads debug APK) – last runs on main and PR branches all green (latest main run 2026-10-16 13:52 Manila, 7m). `Android Release` is manual/tag, last run green. Repo default workflow token permission is `read`; branch protection is NOT enabled on main.

## 2. Bugs (ranked)
Overall: the core data model is solid (every edit written straight to Room, serialised through a mutex, FK cascades, no destructive migration, PRs recomputed not stored, weights stored in kg). I found no high-severity data-loss bug. Findings:

### MED
1. **Whole-history PR recompute runs on every set edit.** `MainActivity.kt:123` collects `historyViewModel.records` at the top level, so `observeRecordSets()` (`data/local/WorkoutDao.kt:~225-241`, joins every completed set ever) re-runs and `WorkoutStats.recordsByWorkout` (`domain/WorkoutStats.kt:93`) re-sorts/regroups all history whenever *any* touched table changes – i.e. on every keystroke/tick while logging mid-workout. Harmless with a few hundred sets; after a multi-year Hevy import (tens of thousands of sets) it is jank + battery on the main logging screen. Fix: only subscribe while History/WorkoutDetail is shown (move the collect into those screens), or debounce/filter the query to finished workouts only (`observeRecordSets` filters `endedAt IS NOT NULL` but still invalidates on active-workout writes).
2. **Ticking an empty set counts as a completed set.** `ui/workout/WorkoutViewModel.kt:188` `toggleCompleted` never checks that the set has values. An all-blank ticked set survives `finishWorkout` (`WorkoutDao.kt:193` only deletes *unticked* sets) and inflates set counts in History/summary (counts in `observeFinishedSummaries`, `WorkoutDao.kt:62-76`). Only the post-edit `tidyEdited` path drops blank sets. Fix: ignore the tick (or fill from previous / flash the field) when the required fields are empty; also run `deleteBlankSets` in `finishWorkout`.
3. **No unit setting even though AGENTS.md/README imply one.** AGENTS.md says "converting to lb is display-only" but there is no kg/lb preference anywhere (`data/settings/SettingsStore.kt` has only rest + theme); "kg" is hard-coded in `domain/Format.kt:17,57-75`, `domain/Progress.kt:25,93`, `ExerciseCard`/`WorkoutScreen`. Hevy lb exports are converted to kg on import (`data/hevy/HevyCsv.kt:93`, rounded to 0.01 kg) so an lb user sees 61.23 kg for a 135 lb set. Not a calc bug – a missing core feature for a lb user. (Jan's own units unknown – ask.)
4. **Auto-backup is fully disabled and manual export is the only safety net.** `AndroidManifest.xml:12` `allowBackup="false"` and both `res/xml/backup_rules.xml` / `data_extraction_rules.xml` exclude everything (also device-transfer). A lost/wiped phone or an uninstall = all history gone unless Jan remembered More → Back up. Privacy-positive but the data-loss risk is real for a logger. Fix: allow device-to-device transfer in `data_extraction_rules.xml` (keep cloud excluded), and add a "last backup N days ago" nudge / scheduled export to a chosen SAF folder.

### LOW
5. **Invalid decimal input silently stores null while the box still shows the text.** `ui/workout/ExerciseCard.kt:246-252` passes `Format.parseDecimal(text)` straight to the DB; the filter in `ui/workout/SetInputs.kt:~20,58` allows multiple `.`/`,` so "1.2.3" parses to null (written to DB) but stays visible because `LaunchedEffect(initial)` (`SetInputs.kt:51`) only resyncs when `initial` changes (null→null: no change). User thinks a weight is logged; ticking the set saves a blank weight. Fix: reject a second separator in the filter.
6. **Accidental discard of an unfinished workout is one tap.** `MainActivity.kt:327-335` resume dialog has "Discard" with no confirmation (and the dialog appears after any process death, which OEMs cause often). Fix: confirm, or make Resume the only primary action and move Discard behind a second dialog.
7. **No sanity bounds on values.** `SetInputs.kt:58` only limits to 8 characters, so 99,999,999 kg or reps is accepted and flows into volume totals/PRs/milestones. Negative values are impossible (not typable; `Format.parseDecimal` `domain/Format.kt:23` rejects < 0). Fix: soft caps (e.g. weight ≤ 1000 kg, reps ≤ 999).
8. **Set-count inconsistency.** History cards and the finish dialog count warm-ups as sets (`WorkoutDao.kt:66` `COUNT(ws.id)`, `WorkoutStats.summarize` line 62+), while volume, Progress tab sets and muscle sets exclude warm-ups (`WorkoutDao.kt:27/42` `type != 'WARMUP'`, `domain/Progress.kt:165`). README says "sets" without saying which.
9. **Imported/Backup `notes`, `rpe` and Hevy `superset_id` are stored (or dropped) with no UI.** Workout/exercise `notes` and `rpe` columns are written by import/backup (`data/repository/ImportRepository.kt:109,118,128`) but there is no screen that shows or edits them (grep of `ui/` finds none); `superset_id` is ignored by `HevyCsv`. Dead-weight data and a hidden gap vs Hevy.
10. **Week/streak boundaries use the phone's current time zone at view time** (`domain/Progress.kt:140`). Travelling shifts which week an old late-night workout belongs to; timestamps are stored as UTC epoch so nothing is lost.
11. **Hevy import duplicate check is start-time only** (`data/local/ImportDao.kt:28` `hevyStartTimes`, used in `ImportRepository.kt:75`). Two distinct Hevy workouts with the identical start minute would be treated as one. Extremely unlikely.
12. **Import/restore read the whole file into memory** (`ui/data/DataViewModel.kt:72-74` `readText`). Fine for normal exports; a very large file could OOM. Backup writes with mode `"wt"` (line 135) so a crash mid-write on an existing backup file truncates it – write to a temp name or verify after write.
13. **In-memory rest timer is lost on process death** (`timer/RestTimer.kt` is a plain object). With the foreground service it normally survives; if the OS does kill it, the countdown is simply gone (no data loss). `service/WorkoutService.kt` also relies on `delay()` + partial wake lock (lines ~72-82, 118-124) rather than an alarm; fine on most devices, worth an on-device Doze/OEM-battery test (unverified).
14. **Epley estimate is unbounded for high reps** (`domain/WorkoutStats.kt:48`): a 25-rep set yields est. 1RM = 1.83 × weight and can set a "best 1RM" PR from a light endurance set. Common practice: ignore sets > ~10–12 reps for 1RM, or use Brzycki/Epley cap.

### Checked and OK (so you don't redo it)
- Volume = weight×reps, completed, non-warm-up, consistent between SQL (`WorkoutDao.kt:67`) and Kotlin (`WorkoutStats.setVolume`).
- PR logic: `recordsByWorkout` (history) and `detectRecords` (finish screen) agree (strict `>`, first session of an exercise sets no record, warm-ups excluded, heaviest-weight requires reps>0). Edits/deletes correct later PRs because nothing is stored.
- Set ordering: `addSet` uses last position+1; labels derived from list order; delete leaves gaps but order stays correct; `moveExercise` rewrites all positions in a transaction.
- Write ordering: mutex in `WorkoutRepository` keeps keystroke writes in order; finish/discard are single transactions; restore is one transaction and refuses while a workout is active; Hevy import is one transaction and re-import is idempotent.
- Rest timer: monotonic clock (`SystemClock.elapsedRealtime`), service holds a bounded partial wake lock, FGS type `specialUse` + permission declared, `ForegroundServiceStartNotAllowedException` is caught (it is an IllegalStateException). Notification permission is requested before starting a workout (`ui/workout/WorkoutScreen.kt:121-127`).
- Zero/empty workout can't be saved (finish dialog blocks at 0 completed sets, `WorkoutScreen.kt:424`).
- Migrations are non-destructive and tested (`MigrationTest`), schema bumps documented in AGENTS.md.

## 3. Security / privacy
- Good: no INTERNET permission, no analytics, no network code, `allowBackup=false`, only `MainActivity` exported, all PendingIntents are IMMUTABLE, signing keystore comes from CI secrets only (`.gitignore` blocks *.jks), release workflow refuses to publish a tagged release without a keystore, default workflow token is read-only. No secrets found in the repo (grep hits are only the env-var names in the release workflow/gradle).
- The repo is **public**. Contents are an app; nothing personal found (test CSV `app/src/test/resources/hevy_sample.csv` is 9 synthetic rows). Decide if public is intended.
- Notifications use `VISIBILITY_PUBLIC` (`service/WorkoutNotifications.kt:66,101`): workout title and "next set" text show on the lock screen. Low sensitivity for gym data; matches the pending FLAG_SECURE/app-lock idea in other projects only if Jan wants it here.
- Backups are plain JSON on user-chosen storage (Drive etc.) – expected, but unencrypted.
- Release APKs fall back to the **debug key** if no keystore is configured (`app/build.gradle.kts` signing block); fine for a personal app but means an unsigned-by-owner build can't upgrade a properly signed one. Unverified whether Jan has set the 4 `TONNAGE_KEYSTORE_*` repo secrets.
- Workflow actions pinned by major tag, not SHA (`actions/checkout@v4`, `softprops/action-gh-release@v2`), release job has `contents: write`. Low risk for a personal repo.

## 4. Code health, tests, CI
- Architecture: clean and small – data/{local,model,repository,backup,hevy}, domain (pure Kotlin stats), timer/service, ui per feature with AndroidViewModels. Conventions are written down (AGENTS.md is good). Navigation is a tab enum + state in `MainActivity.kt` (337 lines, holds six ViewModels and all screen routing) – workable now, will get unwieldy.
- Largest files: WorkoutScreen.kt 472, ProgressScreen.kt 389, MainActivity.kt 337, ExerciseCard.kt 307.
- No DI; ViewModels build their own `AppDatabase`/repositories (`WorkoutViewModel.kt:38-41`), so ViewModels are untested (tests hit repositories/DAOs/domain with Robolectric + in-memory Room, which is the right place for most logic).
- Tests: ~100 unit tests on stats, progress, milestones, backup round-trip, Hevy CSV/import/matcher, migrations, rest timer, repositories. Gaps: `WorkoutViewModel` (tick/finish flow), `SetNumberCell` input filtering/parsing edge cases ("1.2.3", "62."), `WorkoutService`, record edge cases (very high reps), unit conversion (none exists), lock-screen behaviour. No instrumented/UI tests.
- CI: tests + lintDebug + assembleDebug + assembleRelease (R8) on every push/PR; green. Gaps: no lint baseline/config (warnings never fail), no check that Room schema JSON is committed (`app/schemas` is not in the repo although AGENTS.md says it is exported there; CI only uploads it as an artifact), no dependabot/renovate, main is unprotected, and CI uploads a debug APK but never installs the **release (R8) build** anywhere, so runtime R8 breakage (reflection, org.json, Room) would only show on device.
- Proguard (`app/proguard-rules.pro:21`) keeps all of `androidx.compose.**` – defeats most shrinking for the biggest library; `-keepattributes`/Room rules are fine. Safe to delete once release smoke-tested.
- Dependencies: AGP/Kotlin are very current, but Compose BOM 2024.09.00, lifecycle 2.8.7, activity 1.10.1, Room 2.7.0 are a year+ old (Oct 2026). No known-vuln check done.
- Dead/placeholder code: `ui/components/ComingSoon.kt` is only used as an empty-state widget (name is misleading); RPE/notes columns without UI (see bug 9). Seeding is triggered from `ExercisesViewModel` init (`ui/exercises/ExercisesViewModel.kt:~42`), which happens at startup because MainActivity creates it – fine but an odd home for it; the seed never updates if exercises.json changes in a later app version (`seedIfEmpty` only checks `builtInCount() > 0`).

## 5. Product improvements for "fast, clean set logging + progressive overload" (ranked by value)
1. **Progression hints at the point of logging (MVP).** The "Previous" column already exists. Add a one-line target per exercise from last session: "Last: 60×8,8,7 → try 62.5×8" using a simple double-progression rule (hit top of rep range on all sets → +increment). MVP: rep-range target + fixed increment (2.5 kg) shown, tap to prefill. Later: per-exercise increment, deload detection, plateau flag.
2. **kg/lb setting + per-exercise weight increment (MVP).** Display-only conversion (storage stays kg) plus ± buttons stepping by 2.5/1.25 kg or 5/2.5 lb. Fixes bug 3 and makes logging faster than typing. Later: plate calculator.
3. **Fix logging friction (MVP).** Keep screen on during an active workout; auto-fill next set on tick (already pre-fills on Add set); don't allow empty ticks (bug 2); numeric keyboard "Next" flows weight→reps→tick. Later: swipe-to-delete set, undo snackbar for delete/discard.
4. **Overload-focused history view (MVP).** Per-exercise screen showing last N sessions as a compact table (best set, est. 1RM, volume) with a "stalled for X sessions" marker. The charts already exist (`ExerciseProgressSection`); this is a thin read-only view. Later: weekly volume per muscle vs target.
5. **Safer data (MVP).** Allow device-transfer backups, add "last backed up" line + reminder, and an automatic export to a chosen folder after each finished workout (SAF). Later: CSV export for spreadsheets.
- Cut/avoid: notes UI on every set, RPE column, supersets, body-map polish, milestones expansion, more themes, cloud sync (against the "offline, private" brief). The heat map + milestones are already more than the core loop needs.

## 6. Recommended first batch (small, scoped, one PR each or one small PR)
1. Don't let a blank set be ticked complete; delete blank completed sets at `finishWorkout` (+ unit test in `WorkoutRepositoryTest`). (bug 2)
2. Input hardening: reject a second decimal separator in `SetInputs.kt`, soft caps on weight/reps, tests for `parseDecimal`/cell filtering. (bugs 5, 7)
3. Collect PR `records` only on History/WorkoutDetail screens (move out of `MainActivity.kt:123`) so logging never recomputes history; add a test/benchmark with ~20k generated sets. (bug 1)
4. Confirm dialog on resume-dialog "Discard" (`MainActivity.kt:327`). (bug 6)
5. Backup safety: allow device-transfer in `data_extraction_rules.xml` and show "Last backup: …" in Settings (store a timestamp in `SettingsStore`). (bug 4)
6. kg/lb setting (display-only, `SettingsStore` + `Format` helpers + weight entry/parse in the chosen unit, converting to kg on write) with tests for round-trip rounding. (bug 3 / improvement 2)
7. Keep screen on during an active workout (`FLAG_KEEP_SCREEN_ON`/`keepScreenOn` while `activeWorkout != null`).
8. Housekeeping: remove `-keep class androidx.compose.** { *; }` after a release-APK smoke test on device, enable branch protection on main, commit `app/schemas`, add Dependabot for Gradle + Actions.
Leave improvement #1 (progression hints) for batch 2 once the unit setting and increments exist.
