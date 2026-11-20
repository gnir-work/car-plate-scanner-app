# License Plate Scanner (Android)

MVP: pick a photo, then run on-device OCR (ML Kit Text Recognition v2, bundled model)
and pick the most plate-like string out of the text. Tuned for Israeli plates
(7 digits `12-345-67` or 8 digits `123-45-678`).

## Layout

- `app/src/main/java/com/lpscanner/app/ocr/PlateRecognizer.kt` – ML Kit wrapper
- `app/src/main/java/com/lpscanner/app/ocr/PlateExtractor.kt` – plain-Kotlin plate heuristic (unit tested)
- `app/src/main/java/com/lpscanner/app/ui/` – Compose screen + ViewModel

## First run

1. Open this `android/` folder in Android Studio and let it sync. It installs the SDK and
   downloads Gradle using `gradle/wrapper/gradle-wrapper.properties`.
2. To build from the terminal, generate the wrapper scripts once:
   `gradle wrapper` (or run Studio's *Gradle > Tasks > build setup > wrapper*). After that,
   `./gradlew test` and `./gradlew installDebug` work.
3. Create an emulator (Device Manager), e.g. Pixel 8 on API 35, and run the app.

## Getting test photos into the emulator

Drag an image from Finder onto the emulator window. It's copied to `/sdcard/Download`
and appears in the photo picker. From the terminal you can also run:

    adb push car.jpg /sdcard/Download/
    adb shell content call --method scan_volume --uri content://media --arg external_primary

The second command tells Android to index new files so they show up in the photo picker.
(`adb` lives in `~/Library/Android/sdk/platform-tools/`.)

## Tests

- `./gradlew test` – JVM unit tests for the plate heuristic (`PlateExtractorTest`), no emulator needed.
- `./gradlew connectedDebugAndroidTest` – runs real ML Kit OCR on the photos in `../resources/`
  (`PlateRecognizerImageTest`). Needs a running emulator. Photos of real cars stay out of git
  (`resources/` is ignored except `simple-license.jpg`). To add a case, drop the photo in
  `resources/`, add a line `file.jpg=1234567` to `resources/expected-plates.properties`
  (also not committed; escape spaces in the name with `\ `), and add an
  `assertLocalPlate("file.jpg")` test. Tests whose photo or plate isn't there are skipped.
  On failure the message includes the raw OCR text, so you can see what ML Kit actually read.
