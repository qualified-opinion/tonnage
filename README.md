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
- **Import from Hevy**: in Hevy, go to Settings > Export & Import Data > Export Workouts, then in Tonnage open More > Import from Hevy and pick the CSV. Exercise names are matched to the library automatically; only the ones it isn't sure about are shown for you to pick (or keep as your own exercise). A preview shows the workouts, sets and dates before anything is saved. Your choices are remembered, and importing a newer export later only adds the workouts that aren't there yet.
- **Progress tab**: this week's workouts, working sets and volume, with a bar chart of workouts per week (tap a bar, or use the arrows, to look at an earlier week), how many weeks in a row you've trained, and a muscle heat map: front and back body figures where each muscle shades deeper green the more working sets it got that week (tap a muscle for its count, or show the full list).
- **Exercise charts**: open any exercise (from the Progress tab or the Exercises tab) to see a chart over time: estimated 1RM, heaviest weight or volume for weighted exercises; reps for bodyweight ones; distance or time for cardio. Tap a point for that day's number and best set, and pick 3 months, 1 year or all time.
- **Milestones**: first workout, 5 / 10 / 25 / 50 / 100+ workouts, 10,000 kg to 10,000,000 kg lifted, 2- to 104-week streaks, and every 20 kg on bench press, squat, deadlift and overhead press from 40 kg. The Progress tab shows what's next with a progress bar, and what you've reached with the date. Like records, they're worked out from your history, so imported Hevy workouts count on the day you did them.
- **Light and dark themes**: green accents on charcoal or near-white. More > Theme picks Light, Dark, or the same as your phone (the default).
- **Backup and restore**: More > Back up to a file saves everything (workouts, routines, your own exercises, settings) as a JSON file you can keep in Drive or on a computer. Restore from a backup replaces what's in the app with the file's contents, for example on a new phone.
- **Exercises**: 876 exercises bundled from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain), searchable by name (`db`, `bb`, `kb`, `ohp`, `rdl` shorthands work) and filterable by muscle, equipment or custom-only. Add your own with the + button.

## Roadmap

| Phase | What you get |
|---|---|
| 0–1 | App skeleton, APK build, exercise library |
| 2 | Log a workout, rest timer that works with the screen locked, unfinished workouts survive app kills |
| 3 | Routines and "previous" values while logging |
| 4 | Workout detail, edit/delete, record badges, save as routine |
| 5 | Hevy CSV import and JSON backup/restore |
| 6 (this) | Progress charts, weekly stats, milestones |
| 7 | Body weight, plate calculator, progression hints, Health Connect |

## How to build & install

### Option A: Build the APK on GitHub (no Android Studio needed)

Every push to any branch runs the **Android CI** workflow, which runs the unit tests and builds a debug APK.

1. Push your changes (or open the **Actions** tab and run **Android CI** manually with **Run workflow**).
2. Open the **Actions** tab and click the latest **Android CI** run.
3. When it finishes, scroll to **Artifacts** and download **tonnage-debug-apk**. GitHub delivers it as a `.zip`; unzip it to get `app-debug.apk`.
4. Copy the APK to your phone and open it. Android asks you to allow installing apps from that source the first time.

#### Publishing a release APK

On GitHub, open **Actions → Android Release → Run workflow**, type a version such as `1.0.0` and run it: the workflow tags the commit `v1.0.0` and publishes a GitHub Release with `Tonnage-1.0.0.apk` attached. Pushing a tag such as `v1.0.0` does the same. A published release must be signed with your own key, so the run stops if the signing secrets are missing. To sign with your own key, add the `TONNAGE_KEYSTORE_BASE64`, `TONNAGE_KEYSTORE_PASSWORD`, `TONNAGE_KEY_ALIAS` and `TONNAGE_KEY_PASSWORD` repository secrets; without them the APK is signed with a temporary debug key.

### Option B: Android Studio

Open the folder in Android Studio and press Run, or use `./gradlew assembleDebug`.
