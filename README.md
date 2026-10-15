# Tonnage

Fast, private strength training tracker: a Hevy-style set logger focused on progressive overload and clean logging, with no social features.

Built with Kotlin, Jetpack Compose, Material 3 and Room. Everything is stored on the phone; the app makes no network calls.

## What's in it so far

- **Exercises**: 876 exercises bundled from [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain), searchable by name (`db`, `bb`, `kb`, `ohp`, `rdl` shorthands work) and filterable by muscle, equipment or custom-only. Each has muscles, equipment and step-by-step instructions. Add your own custom exercises with the + button.
- Workout, History, Progress and Settings tabs are placeholders for the next phases (see the plan below).

## Roadmap

| Phase | What you get |
|---|---|
| 0–1 (this) | App skeleton, APK build, exercise library |
| 2 | Log a workout, rest timer that works with the screen locked, unfinished workouts survive app kills |
| 3 | Routines and "previous" values while logging |
| 4 | History, edit/delete, personal records |
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
