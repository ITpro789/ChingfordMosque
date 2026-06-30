# Chingford Mosque — Android app (`:app`)

The Android front-end for the Chingford Mosque prayer-times platform. It hosts the
platform-free `:core` engine inside an `Application`-scoped `AppContainer` and renders a modern
Jetpack Compose (Material 3) UI with an emerald/teal + gold theme and full light/dark support.

## Features

- **Prayer times + countdown ring** — today's salah schedule with a circular countdown ring
  highlighting the current/next prayer, plus the Jummah (Friday) section.
- **Hijri date** — the Islamic (Hijri) date is shown on the Home header alongside the
  Gregorian date, computed from `java.time.chrono.HijrahDate` for Europe/London.
- **Adhan notifications** — per-prayer notification toggles (Fajr, Zuhr, Asr, Maghrib, Isha;
  Sunrise never alerts) and an "adhan sound" switch, wired through the core
  `NotificationScheduler`. Changing a toggle re-arms alarms from the cached schedule without a
  network call.
- **Qibla compass** — a live compass that points to the Kaaba using the device orientation
  sensors (rotation-vector, with accelerometer + magnetometer fallback). The qibla bearing
  from the mosque is fixed (~119° from north); no location permission is required.
- **Theme selection** — System / Light / Dark, persisted and applied app-wide.
- **Offline cache** — the last-known-good schedule is cached locally and rendered immediately
  on launch, with graceful degradation and a stale indicator when refreshes fail.
- **Bottom navigation** — Home, Qibla, Settings, and About destinations.

## Adhan sound

Dropping an `adhan.mp3` file into `app/src/main/res/raw/adhan.mp3` enables the real adhan
audio for notifications; without it, alerts use the standard notification sound.

## Building

```bash
export JAVA_HOME=... ANDROID_SDK_ROOT=/root/android-sdk ANDROID_HOME=/root/android-sdk
./gradlew :app:assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
