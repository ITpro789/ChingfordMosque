# Chingford Mosque Prayer Times

An Android app for the Chingford Mosque community that shows daily prayer
times and keeps you on schedule.

## What the app does

- **Daily salah times** — Fajr, Dhuhr, Asr, Maghrib, and Isha for each day.
- **Jummah times** — Friday congregation times displayed alongside the daily schedule.
- **Next-prayer countdown** — a live countdown to the upcoming prayer.
- **Adhan notifications** — local notifications/alarms that fire at prayer times.
- **Offline cache** — times are cached locally so the app works without a network connection.
- **Background refresh** — a WorkManager periodic job (every 6 hours, requiring network)
  refreshes the cached schedule and re-arms adhan alarms even if the app is never opened.
- **Calculated fallback** — when the mosque site is unreachable or its layout changes and no
  cache exists, the app shows on-device astronomically *estimated* times (clearly labelled).
  In this mode iqamah times are not available (shown as "—"), since calculation cannot know the
  mosque's congregational times.

## Reliability & monitoring

- **Scrape-health monitor (`:monitor`)** — a small headless JVM canary that runs the live
  website parser through the exact `:core` pipeline the app uses. The daily
  [`.github/workflows/scrape-health.yml`](.github/workflows/scrape-health.yml) GitHub Actions
  job runs it (~06:17 UTC) and fails loudly if the mosque site can no longer be parsed, giving
  an early warning before users ever see broken times. Scheduled workflows run from the
  repository's default branch.

## Module layout

- **`:core`** — pure Kotlin/JVM library containing the prayer-time logic, data
  providers, scheduling, and caching. Thoroughly unit tested (226 tests) and
  free of any Android dependencies.
- **`:app`** — Android application module providing the user interface, built
  with Jetpack Compose and Material 3.
- **`:monitor`** — pure Kotlin/JVM console app that performs a live scrape-health check of the
  mosque website parser and exits non-zero if the site can no longer be parsed.

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

Run the scrape-health monitor (live check against the mosque website):

```bash
./gradlew :monitor:run
```

It prints the parsed prayers and exits 0 when the site still parses, or prints the error and
exits non-zero when it cannot — the same check the daily GitHub Actions job runs.

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
