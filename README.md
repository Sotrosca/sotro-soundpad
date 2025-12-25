# SotroSoundPad

SotroSoundPad is an Android soundboard app that lets you import your own audio files and play them with tappable buttons.

Repository: sotro-soundpad

## Quick start

-   Open the `sotro-soundpad` project in Android Studio.
-   Build and run on a device or emulator (recommended device API >= 21).
-   In the app, tap the "Import sounds" button to select audio files (.mp3, .wav). Imported files are saved to the app's private external files directory and appear as buttons.

## Notes

-   The app uses the Storage Access Framework to import audio files; no external storage permission is required.
-   To add sounds from a computer: connect phone and use Android Studio Device File Explorer to put files under the app's external files directory returned by `getExternalFilesDir("sounds")`, or use the import UI.

## Files of interest

-   `app/src/main/java/com/example/sotrosoundpad/MainActivity.kt`
-   `app/src/main/res/layout/activity_main.xml`

---

Feel free to tell me if you want additional features (categories, long-press edit, volume, etc.).
