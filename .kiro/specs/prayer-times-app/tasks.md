# Implementation Plan: Prayer Times App (Chingford Mosque)

## Overview

This plan implements the Prayer Times App in **Kotlin** (Android). It builds the app
bottom-up: first the domain model and pure logic (which has no I/O and is easiest to test
against the design's correctness properties), then the cache/repository, then the
source-specific Times Provider, then the notification scheduler and refresh coordinator,
and finally the UI and end-to-end wiring. Each step builds on the previous ones so there is
no orphaned code, and property/unit tests are placed close to the logic they validate to
catch errors early.

Property-based tests use a Kotlin PBT library (e.g., kotest-property or jqwik); unit and
integration tests use the project's standard test runner (e.g., JUnit). All website-specific
parsing is confined to the Times Provider boundary per Requirement 8.4.

## Tasks

- [x] 1. Set up project structure and core domain model
  - [x] 1.1 Create module/package structure and core domain types
    - Create package layout: `domain`, `data` (repository/provider), `service`, `notify`, `refresh`, `ui`
    - Define `Prayer` enum (Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha) and a canonical-order helper
    - Define `Time` (24h time-of-day) and `Duration` representations and a `Date`/`DateTime` abstraction so time can be injected (no hidden clock side-effects)
    - Define `Result<T, E>` / `Option<T>` helpers (or use Kotlin `Result`/sealed types) and the `ProviderError` sealed type (NetworkError, ParseError, IncompleteData)
    - Set up the test framework and the property-based testing library
    - _Requirements: 1.4, 8.4_

  - [x] 1.2 Implement PrayerTime, JummahTimes, DaySchedule, CachedSchedule and validation
    - Implement `PrayerTime { prayer, beginsAt, iqamahAt: Option<Time> }` with validation: `beginsAt` is a valid time-of-day; `iqamahAt >= beginsAt` when present; Sunrise carries no `iqamahAt`
    - Implement `JummahTimes { jamaahTimes: List<Time> }` with validation (>= 1 entry when present; ascending order)
    - Implement `DaySchedule { scheduleDate, prayers, jummah: Option<JummahTimes> }` with validation: contains the five daily salah (Fajr, Zuhr, Asr, Maghrib, Isha); begin times strictly increasing in canonical order
    - Implement `CachedSchedule { schedule, fetchedAt }` and `NotificationPreferences { enabledPrayers, playAdhanSound }`
    - _Requirements: 1.5, 1.6, 2.1, 2.3, 3.2_

  - [x]* 1.3 Write property test for schedule validation invariants
    - **Property 2: Monotonic ordering** — alerting prayer begin times are strictly increasing for any valid parsed `DaySchedule`
    - **Property 3: Iqamah not before begin** — for every `PrayerTime`, `iqamahAt` (when present) is `>= beginsAt`
    - **Validates: Requirements 1.6, 2.1**

  - [x]* 1.4 Write unit tests for domain validation edge cases
    - Test rejection of missing required salah, invalid times, Sunrise with iqamah, non-increasing begins
    - Test Jummah ascending-order validation
    - _Requirements: 1.5, 1.6, 3.2_

- [x] 2. Implement Schedule Service (next-prayer + countdown)
  - [x] 2.1 Implement getNextPrayer, timeUntilNext, and orderedPrayers
    - Implement `getNextPrayer(schedule, now)`: smallest `beginsAt` strictly after `now` among alerting prayers; exclude Sunrise; return next day's first prayer when today's are exhausted (when available) else None
    - Implement `timeUntilNext(schedule, now)` deriving remaining duration from the next prayer
    - Implement `orderedPrayers(schedule)` returning prayers in canonical order for display (including Sunrise as informational)
    - Keep the service pure: no I/O, `now` passed in
    - _Requirements: 2.1, 4.1, 4.2, 4.3, 4.5_

  - [x]* 2.2 Write property test for next-prayer correctness
    - **Property 1: Next-prayer correctness** — `getNextPrayer` returns the minimal future alerting begin time, or None/next-day when none remain
    - **Validates: Requirements 4.1, 4.5**

  - [x]* 2.3 Write property test for Sunrise exclusion
    - **Property 6: Sunrise is non-alerting** — Sunrise is never returned by `getNextPrayer`
    - **Validates: Requirements 4.2**

  - [x]* 2.4 Write unit tests for boundary cases
    - Table-driven tests: before Fajr, exactly at a begin time, between prayers, after Isha, all-passed
    - _Requirements: 4.1, 4.2, 4.5_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement Schedule Repository (local cache)
  - [x] 4.1 Implement persistence of CachedSchedule with cache-safety
    - Implement `save(schedule)` storing the `DaySchedule` plus `fetchedAt`, atomically
    - Implement `getCachedSchedule()` returning cached data with freshness metadata
    - Implement `clear()`
    - Enforce that an invalid/empty schedule never overwrites a previously cached valid schedule
    - Use a lightweight local store (e.g., DataStore/SharedPreferences/Room) behind the repository interface
    - _Requirements: 6.1, 6.2, 6.5_

  - [x]* 4.2 Write property test for cache safety
    - **Property 4: Cache safety** — a failed fetch / invalid result never replaces a previously cached valid `DaySchedule`
    - **Validates: Requirements 6.5**

  - [x]* 4.3 Write unit tests for repository round-trip
    - Save/get round-trip preserves schedule and `fetchedAt`; clear empties the cache
    - _Requirements: 6.1, 6.2_

- [x] 5. Implement Times Provider (fetch + parse + normalize)
  - [x] 5.1 Implement HTTP fetch over HTTPS
    - Implement the HTTP client call to https://chingfordmosque.com/ over HTTPS
    - Map transport failures/timeouts to `NetworkError`
    - Treat the response body as untrusted input
    - _Requirements: 1.1, 8.1_

  - [x] 5.2 Implement widget parsing and time normalization
    - Parse the "Daily Salah Times" widget into Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha entries
    - Capture both "Begins" and, where present, "Iqamah" times; Sunrise begins-only
    - Parse the "Jummah Timing" line into ascending `JummahTimes`; omit when unavailable
    - Normalize all times into the canonical 24-hour representation
    - Map unexpected markup/format to `ParseError`
    - Confine all source-specific parsing here so display/scheduling/notification logic is unaffected by source changes
    - _Requirements: 1.2, 1.3, 1.4, 3.1, 3.3, 8.2, 8.4_

  - [x] 5.3 Validate parsed schedule and assemble fetchTodaySchedule
    - Run domain validation on the parsed result; map missing/invalid required prayers or non-increasing begins to `IncompleteData`
    - Return `Result<DaySchedule, ProviderError>` from `fetchTodaySchedule()`
    - _Requirements: 1.5, 1.6, 8.4_

  - [x]* 5.4 Write unit tests against recorded HTML fixtures
    - Well-formed fixture asserts the normalized `DaySchedule` (incl. Sunrise begins-only and Jummah line)
    - Malformed/missing-data fixtures assert `ParseError` / `IncompleteData`
    - _Requirements: 1.2, 1.3, 3.1, 8.2_

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement Notification Scheduler (adhan alerts)
  - [x] 7.1 Implement reschedule, cancelAll, and setPreferences
    - Implement `reschedule(schedule, now)` arming one local notification per remaining alerting prayer begin time
    - Implement `cancelAll()` and call it before re-arming so no duplicate alerts exist per (prayer, date)
    - Implement `setPreferences(prefs)`: respect per-prayer enable/disable and play adhan audio only when `playAdhanSound` is enabled
    - Never schedule a notification for Sunrise
    - Present an azaan/adhan notification identifying the prayer when a scheduled time is reached
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 7.2 Handle notification permission state
    - When permission is not granted, keep displaying times/countdown and prompt the user (once) to enable notifications
    - _Requirements: 5.7_

  - [x]* 7.3 Write property test for no-duplicate-alerts
    - **Property 5: No duplicate alerts** — after `reschedule`, at most one pending notification per (prayer, date); re-running refresh creates no duplicates
    - **Validates: Requirements 5.5**

  - [x]* 7.4 Write property test for Sunrise non-notification
    - **Property 6: Sunrise is non-alerting** — Sunrise never schedules a notification
    - **Validates: Requirements 5.6**

  - [x]* 7.5 Write unit tests for preferences and re-arming
    - Disabled prayers are not scheduled; sound toggle respected; re-arm after a fire/refresh
    - _Requirements: 5.3, 5.4, 5.5_

- [x] 8. Implement Refresh Coordinator (scheduled + on-demand)
  - [x] 8.1 Implement onAppOpened, refreshNow, and graceful degradation
    - `onAppOpened()`: render cached schedule first (with freshness state), then attempt a refresh
    - `refreshNow()`: fetch via provider; on success save to repository, recompute next prayer, reschedule notifications, update display; on failure keep cached data and surface a network/parse error state with retry
    - _Requirements: 6.2, 6.3, 7.1, 7.3, 8.1, 8.2, 8.3_

  - [x] 8.2 Implement daily rollover refresh
    - Implement `scheduleDailyRefresh()`: when the calendar day changes while in use, refresh for the new day and reschedule notifications
    - Do not continuously poll outside launch, daily rollover, and manual refresh triggers
    - _Requirements: 7.2, 7.4_

  - [x]* 8.3 Write unit tests for refresh orchestration
    - Successful fetch updates cache/service/notifications; failed fetch preserves cache and reports error; rollover triggers refresh + reschedule
    - _Requirements: 6.3, 7.1, 7.2, 7.3, 8.1_

- [x] 9. Implement UI layer
  - [x] 9.1 Implement today's salah times view
    - Render prayers in canonical order with begin and (where available) iqamah times
    - Present Sunrise as informational only (no iqamah)
    - Show the date the times apply to
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 9.2 Implement Jummah times section
    - Display Jummah jamā'ah times in ascending order when available; omit the section without error when unavailable
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 9.3 Implement next-prayer countdown and freshness/error indicators
    - Show the next prayer and a live countdown updated by a local per-second timer without re-fetching
    - Show a "last updated" / stale indicator when data is older than one day or came from cache after a failed refresh
    - Show a network/parse error banner with a retry affordance; provide a manual refresh control
    - _Requirements: 4.3, 4.4, 6.4, 8.1, 8.3_

  - [x]* 9.4 Write unit tests for view-state formatting
    - Ordering, Sunrise-without-iqamah, Jummah omission, stale/error indicator visibility
    - _Requirements: 2.1, 2.3, 3.3, 6.4_

  - [x]* 9.5 Write property test for freshness visibility
    - **Property 7: Freshness visibility** — whenever displayed data is older than one day or came from cache after a failed refresh, the UI exposes a stale/"last updated" indicator
    - **Validates: Requirements 6.4**

- [x] 10. Integration and wiring
  - [x] 10.1 Wire all components together
    - Connect Times Provider → Repository → Schedule Service → Notification Scheduler → UI via the Refresh Coordinator
    - Wire app launch, manual refresh, and daily rollover triggers; ensure cache-first render then update
    - _Requirements: 6.2, 7.1, 7.2, 7.3_

  - [x]* 10.2 Write integration tests for end-to-end refresh
    - Stubbed HTTP layer returning recorded responses: assert cache update, next-prayer recompute, and one armed alert per remaining alerting prayer with correct re-arming after refresh
    - Assert failed fetch keeps cached data and surfaces error with retry
    - _Requirements: 5.1, 5.5, 7.3, 8.1, 8.2_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP.
- Each task references specific requirements for traceability.
- Checkpoints ensure incremental validation.
- Property tests validate the universal correctness properties from the design; unit tests validate specific examples and edge cases.
- All website-specific parsing is confined to the Times Provider (Requirement 8.4) so source/markup changes only affect that boundary.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "2.1", "4.1"] },
    { "id": 3, "tasks": ["2.2", "2.3", "2.4", "4.2", "4.3", "5.1", "7.1"] },
    { "id": 4, "tasks": ["5.2", "7.2", "7.3", "7.4", "7.5"] },
    { "id": 5, "tasks": ["5.3", "8.1", "9.1", "9.2"] },
    { "id": 6, "tasks": ["5.4", "8.2", "9.3"] },
    { "id": 7, "tasks": ["8.3", "9.4", "9.5", "10.1"] },
    { "id": 8, "tasks": ["10.2"] }
  ]
}
```
