# Tonnage

Fast, private strength training tracker: a Hevy-style set logger focused on progressive overload and clean logging, with no social features.

Built with Kotlin, Jetpack Compose, Material 3 and Room. Everything is stored on the phone; the app makes no network calls.

## What's in it so far

- **Workout logging**: start an empty workout, add exercises (multi-select from the library), and log sets with weight and reps (or reps, time, or distance and time, depending on the exercise). Tap the set number to mark it as a warm-up, drop set or failure set, or to delete it. "Add set" copies the previous set's numbers.
- **Routines**: save the workouts you repeat (e.g. "Push A") with their exercises, rest times and planned sets, then start one with a tap from the Workout tab and the sets are filled in. Edit, duplicate or delete them from the routine's menu.
- **Previous values**: each set shows what you did last time for that exercise (from the last time you did the same routine, otherwise from the last time anywhere). Tap it to copy the numbers into the set. If last time had fewer sets, the extra rows just show a dash.
- **Reorder**: move exercises up or down from the exercise's menu, in a workout or a routine.
- **Saved as you go**: every change is written to the phone's database immediately, so a workout survives the app being closed, killed or the phone restarting. When you reopen the app after that, it offers to resume or discard the unfinished workout.
- **Rest timer**: ticking off a set starts the exercise's rest timer (default 1:30, changeable per exercise and in Settings). It keeps counting with the screen locked, shows in a notification with +15s / Skip / Open buttons, and vibrates when time is up.
- **Ongoing-workout notification**: shows the workout's running time and sets done, so you can jump back in from the lock screen.
- **Finish summary**: duration, sets, volume (warm-ups excluded) and any new records (heaviest weight, best estimated 1RM using Epley, most reps for bodyweight exercises) before saving. Unticked sets are dropped.
- **History**: a list of finished workouts with duration, sets, volume and how many records each set. Tap one to see every set, with a trophy next to record sets.
- **Edit or delete past workouts**: fix a typo in a weight, add a forgotten set or exercise, rename it, or delete it. Records are worked out from all your sets each time, so they correct themselves after an edit.
- **Save as routine**: turn a logged workout into a routine from its ⋮ menu.
- **Exercises**: 876 exercises bundled from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain), searchable by name (`db`, `bb`, `kb`, `ohp`, `rdl` shorthands work) and filterable by muscle, equipment or custom-only. Add your own with the + button.

## Roadmap

| Phase | What you get |
|---|---|
| 0–1 | App skeleton, APK build, exercise library |
| 2 | Log a workout, rest timer that works with the screen locked, unfinished workouts survive app kills |
| 3 | Routines and "previous" values while logging |
| 4 (this) | Workout detail, edit/delete, record badges, save as routine |
| 5 | Hevy CSV import and JSON backup/restore |
| 6+ | Progress charts, weekly stats, milestones |

## How to build & install

### Option A: Build the APK on GitHub (no Android Studio needed)

Every push to any branch runs the **Android CI** workflow, which runs the unit tests and builds a debug APK.

1. Push your changes (or open the **Actions** tab and run **Android CI** manually with **Run workflow**).
2. Open the **Actions** tab and click the latest **Android CI** run.
3. When it finishes, scroll to **Artifacts** and download **tonnage-debug-apk**. GitHub delivers it as a `.zip`; unzip it to get `app-debug.apk`.
4. Copy the APK to your phone and open it. Android asks you to allow installing apps from that source the first time.

#### Publishing a release APK

Push a tag such as `v0.1.0` and the **Android Release** workflow attaches `Tonnage-0.1.0.apk` to a GitHub Release. To sign with your own key, add the `TONNAGE_KEYSTORE_BASE64`, `TONNAGE_KEYSTORE_PASSWORD`, `TONNAGE_KEY_ALIAS` and `TONNAGE_KEY_PASSWORD` repository secrets; without them the APK is signed with a temporary debug key.

### Option B: Android Studio

Open the folder in Android Studio and press Run, or use `./gradlew assembleDebug`.
