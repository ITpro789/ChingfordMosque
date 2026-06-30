# Chingford Mosque Prayer Times

An Android app for the Chingford Mosque community that shows daily prayer
times and keeps you on schedule.

## What the app does

- **Daily salah times** — Fajr, Dhuhr, Asr, Maghrib, and Isha for each day.
- **Jummah times** — Friday congregation times displayed alongside the daily schedule.
- **Next-prayer countdown** — a live countdown to the upcoming prayer.
- **Adhan notifications** — local notifications/alarms that fire at prayer times.
- **Offline cache** — times are cached locally so the app works without a network connection.

## Module layout

- **`:core`** — pure Kotlin/JVM library containing the prayer-time logic, data
  providers, scheduling, and caching. Thoroughly unit tested (204 tests) and
  free of any Android dependencies.
- **`:app`** — Android application module providing the user interface, built
  with Jetpack Compose and Material 3.

## Building locally

You need the **Android SDK** and **JDK 17** installed.

Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

The resulting APK is written to:

```
app/build/outputs/apk/debug/*.apk
```

Run the core unit tests:

```bash
./gradlew :core:test
```

## Download the APK

You don't need to build the app yourself. Every push triggers the
[`.github/workflows/android.yml`](.github/workflows/android.yml) GitHub Actions
workflow, which builds the debug APK and publishes it two ways:

- **GitHub Releases** — grab the latest APK from the repository's
  **Releases** page.
- **Actions artifacts** — open the corresponding workflow run under the
  **Actions** tab and download the `ChingfordMosque-debug-apk` artifact.

After downloading, install the APK on your Android device (you may need to
enable "install from unknown sources").
