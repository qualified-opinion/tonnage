# Tonnage batch 6: timer and service lifecycle

**Repo:** qualified-opinion/tonnage
**Branch:** `fix/timer-service-lifecycle`, from the latest `main`
**Engine:** the owner picks one agent to build; the other engine reviews, one round. Merge only after approval, green CI and the owner's OK. No tags, no releases.
**Source:** `reviews/kiln-review-2.md`: T09, T10, N10, N11. Size target: about 400 changed lines. Riskiest batch: foreground-service rules differ across Android 12 to 15.

## Goal
The rest timer survives the phone killing the app, the workout notification service always starts when it should, the owner is told when notifications are off, and the set inputs work with a screen reader.

## Scope
1. **T09, timer survives process death.** `RestTimer` (`timer/RestTimer.kt`) keeps its state in memory only. Persist the end time, total, exercise name and next-set label in `SettingsStore` when a rest starts, adjusts, skips or finishes. When a confirmed workout is resumed, restore the timer if its end time (on a clock that survives the process; note `SystemClock.elapsedRealtime` resets on reboot, so store wall-clock time too and compare sensibly) has not passed; otherwise clear it. After a reboot, treat the stored timer as expired.
2. **T10, service start retried.** `WorkoutService.kt` swallows a rejected `startForeground` and `WorkoutViewModel` marks the workout confirmed anyway. Track whether the service is running (a flag set by the service itself) and add an `ensureService()` that starts it when a confirmed workout is active and the service is not running. Call it whenever the activity resumes. Handle the Android 12+ background-start restriction by doing it only from a visible activity.
3. **N10, notifications denied.** If the notification permission is denied, the rest timer ends silently except for vibration. Show a short notice on the workout screen ("Notifications are off, so you won't see the rest timer end when the screen is off") with an action that opens the app's notification settings. Show it at most once per workout.
4. **N11, accessibility.** Give the weight, reps, duration and tick controls screen-reader labels that say what they are ("Set 2 weight, kilograms") and raise touch targets to at least 48 dp without making the row taller than needed (use padding or minimum touch size, not larger visuals).

## Out of scope
- New timer features (auto-start next set, sounds), exact-alarm permissions, a second service, new permissions. If you believe a permission is needed, stop and ask.
- Logging dialogs and input filtering (batch 3), stats (batch 5), backup (batch 4).

## Acceptance criteria
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease` and CI are green; every existing test still passes.
- New tests, each failing on current `main` where a test can show it:
  - rest timer persistence: start then restore with a fake clock gives the remaining time; expired and post-reboot cases clear it; adjust, skip and finish update the stored copy;
  - `ensureService()` starts the service when a confirmed workout is active and the service is not running, and does nothing otherwise (use a fake starter);
  - the notice shows once per workout when permission is denied.
- No new permission in the manifest.
- Accessibility: lint shows no new content-description or touch-target warnings.
- The PR says plainly what could not be tested without a device. The owner must check on a phone: start a rest, swipe the app away and reopen (timer restored or cleared sensibly); press Home right after Start and return (notification appears); deny notifications and see the notice; rest timer under Doze and with battery saver on (screen off for a full rest).

## Risks
- Wall-clock versus elapsed-realtime: a wrong choice makes the timer restore with the wrong remaining time after a reboot or clock change. Keep the rule simple and tested.
- Foreground-service start restrictions differ by Android version. Do not start the service from the background. Prefer failing quietly and retrying on the next resume over crashing.
- The wake lock must stay bounded by the rest length (existing rule in `AGENTS.md`).

## Process
One PR, update the status table in `ROADMAP.md`, add `AGENTS.md` lines for the timer persistence rule and `ensureService()`. Do not merge.
