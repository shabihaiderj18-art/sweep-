# Sweep

A photo-cleaning app for Android 11 and newer. Your gallery is split by month; swipe left to delete, right to keep, then review and confirm. Android asks for one final confirmation before anything is removed.

## Get the APK without Android Studio (GitHub builds it for you)

1. Create a new empty repository on github.com, for example `sweep`.
2. In this folder, run:
   ```
   git init
   git add .
   git commit -m "First version"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/sweep.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab. The "Build APK" job takes about 5 minutes.
4. Open the finished run and download **sweep-apk** under Artifacts. Unzip it to get `app-debug.apk`.
5. Send the APK to your phone, open it, and allow "Install unknown apps" when asked.

Every time you push a change, GitHub builds a new APK.

## Or build with Android Studio

Open this folder in Android Studio (File > Open), wait for Gradle sync, plug in your phone with USB debugging on, and press Run.

## Settings in the code

- `USE_TRASH` in `MainActivity.kt`: `true` sends photos to the phone's bin (recoverable for about 30 days). Set it to `false` to delete permanently and free space immediately.

## Files

- `Photos.kt` reads the gallery and groups photos by month
- `SweepViewModel.kt` holds the swipe logic, undo, and the delete list
- `Screens.kt` has the month list, swipe card, and review screen
- `MainActivity.kt` handles permissions and the delete confirmation
