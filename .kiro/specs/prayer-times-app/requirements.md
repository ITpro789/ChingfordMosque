# Requirements Document

## Introduction

The Prayer Times App provides Muslims in the Chingford area with up-to-date salah (prayer)
times and Jummah (Friday) jamā'ah times sourced from the Chingford Mosque website
(https://chingfordmosque.com/). The app shows today's prayer schedule, highlights the next
upcoming prayer with a live countdown, and raises an azaan/adhan notification when each
prayer time arrives. It keeps a local cache of the most recent schedule so it continues to
work when the device is offline, and it refreshes data on launch, at the daily rollover, and
on demand.

These requirements are derived from the approved design document
(`.kiro/specs/prayer-times-app/design.md`).

## Requirements

### Requirement 1: Fetch up-to-date prayer times from the mosque website

**User Story:** As a worshipper, I want the app to pull the latest salah times directly from
the Chingford Mosque website, so that the times I see match what the mosque has published.

#### Acceptance Criteria
1. WHEN the app needs to refresh, THE SYSTEM SHALL request the daily salah schedule over HTTPS from https://chingfordmosque.com/.
2. WHEN the website responds successfully, THE SYSTEM SHALL parse the "Daily Salah Times" widget into Fajr, Sunrise, Zuhr, Asr, Maghrib, and Isha entries.
3. WHEN parsing each prayer, THE SYSTEM SHALL capture both the "Begins" time and, where provided, the "Iqamah" time.
4. WHEN times are parsed, THE SYSTEM SHALL normalize them into a canonical 24-hour internal representation.
5. IF a required daily salah (Fajr, Zuhr, Asr, Maghrib, Isha) is missing or a time is invalid, THEN THE SYSTEM SHALL reject the fetched schedule as incomplete and SHALL retain the last valid cached schedule.
6. WHEN the parsed prayer begin times are not strictly increasing in canonical order, THE SYSTEM SHALL treat the schedule as invalid and SHALL NOT overwrite the cache.

### Requirement 2: Display today's salah times

**User Story:** As a worshipper, I want to see all of today's prayer times at a glance, so
that I can plan my day around salah.

#### Acceptance Criteria
1. WHEN the app displays the schedule, THE SYSTEM SHALL list the prayers in canonical order (Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha).
2. WHEN displaying each prayer, THE SYSTEM SHALL show its begin time and, where available, its iqamah time.
3. WHEN displaying Sunrise, THE SYSTEM SHALL present it as informational only and SHALL NOT show an iqamah time for it.
4. WHEN a schedule is available, THE SYSTEM SHALL show the date the times apply to.

### Requirement 3: Display Jummah (Friday) prayer times

**User Story:** As a worshipper, I want to see the Friday Jummah jamā'ah times, so that I
know when to attend Friday prayer.

#### Acceptance Criteria
1. WHEN Jummah timing data is available from the website, THE SYSTEM SHALL display the listed jamā'ah times (e.g., 1st, 2nd, 3rd).
2. WHEN multiple Jummah jamā'ah times exist, THE SYSTEM SHALL present them in ascending chronological order.
3. IF Jummah timing data is unavailable, THEN THE SYSTEM SHALL omit the Jummah section without error.

### Requirement 4: Determine and surface the next upcoming prayer

**User Story:** As a worshipper, I want the app to tell me which prayer is next and how long
until it begins, so that I can prepare in time.

#### Acceptance Criteria
1. WHEN computing the next prayer for the current time, THE SYSTEM SHALL select the alerting prayer with the smallest begin time strictly after the current time.
2. WHEN determining the next prayer, THE SYSTEM SHALL exclude Sunrise from consideration.
3. WHEN a next prayer exists, THE SYSTEM SHALL display a countdown of the time remaining until it begins.
4. WHILE the app is open, THE SYSTEM SHALL update the countdown without re-fetching from the network.
5. IF all of today's prayers have passed, THEN THE SYSTEM SHALL show the next day's first prayer when that schedule is available, otherwise indicate none remain today.

### Requirement 5: Azaan/adhan notification at prayer time

**User Story:** As a worshipper, I want to be notified when a prayer time arrives, so that I
do not miss the prayer.

#### Acceptance Criteria
1. WHEN a schedule is loaded or refreshed, THE SYSTEM SHALL schedule a notification for each upcoming alerting prayer's begin time.
2. WHEN a scheduled prayer begin time is reached, THE SYSTEM SHALL present an azaan/adhan notification identifying the prayer.
3. WHEN the user has enabled the adhan sound, THE SYSTEM SHALL play the adhan audio with the notification; otherwise it SHALL present the notification without adhan audio.
4. WHEN the user disables notifications for a specific prayer, THE SYSTEM SHALL NOT schedule a notification for that prayer.
5. WHEN the schedule is refreshed, THE SYSTEM SHALL cancel previously scheduled notifications and re-arm them so that no duplicate alerts exist for the same prayer and date.
6. THE SYSTEM SHALL NOT schedule a notification for Sunrise.
7. IF notification permission has not been granted, THEN THE SYSTEM SHALL continue to display times and the countdown and SHALL prompt the user to enable notifications.

### Requirement 6: Offline support and data freshness

**User Story:** As a worshipper, I want the app to keep showing the last known times when I'm
offline, so that it is still useful without a connection.

#### Acceptance Criteria
1. WHEN a schedule is fetched successfully, THE SYSTEM SHALL persist it locally together with the time it was fetched.
2. WHEN the app launches, THE SYSTEM SHALL display the cached schedule immediately before attempting a refresh.
3. IF a refresh fails due to a network or parse error, THEN THE SYSTEM SHALL keep the cached schedule and SHALL indicate that the data could not be updated.
4. WHEN displayed data came from cache after a failed refresh or is older than one day, THE SYSTEM SHALL show a "last updated" / stale indicator.
5. THE SYSTEM SHALL NOT overwrite a valid cached schedule with an invalid or empty result.

### Requirement 7: Refreshing data

**User Story:** As a worshipper, I want the times to stay current automatically and let me
refresh manually, so that I always see accurate information.

#### Acceptance Criteria
1. WHEN the app is opened, THE SYSTEM SHALL attempt to refresh the schedule.
2. WHEN the calendar day changes while the app is in use, THE SYSTEM SHALL refresh the schedule for the new day and reschedule notifications accordingly.
3. WHEN the user requests a manual refresh, THE SYSTEM SHALL fetch the latest schedule and update the display and notifications on success.
4. THE SYSTEM SHALL NOT continuously poll the network outside of launch, daily rollover, and manual refresh triggers.

### Requirement 8: Error handling and resilience

**User Story:** As a worshipper, I want the app to handle failures gracefully, so that errors
in the source or network don't break my experience.

#### Acceptance Criteria
1. WHEN the website is unreachable or times out, THE SYSTEM SHALL report a network error state while continuing to show cached times.
2. WHEN the website's markup differs from what is expected, THE SYSTEM SHALL report a parse error state and SHALL retain cached data.
3. WHEN any fetch error occurs, THE SYSTEM SHALL offer the user a way to retry.
4. THE SYSTEM SHALL confine all website-specific parsing to a single provider boundary so that source changes do not affect display, scheduling, or notification logic.
