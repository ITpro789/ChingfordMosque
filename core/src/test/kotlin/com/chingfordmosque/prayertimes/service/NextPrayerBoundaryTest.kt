package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Duration
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Exhaustive, table-driven boundary tests for [ScheduleService.getNextPrayer] /
 * [ScheduleService.timeUntilNext] (Task 2.4).
 *
 * These complement [ScheduleServiceSmokeTest] (representative examples) by pinning down the
 * exact edges that the design's "smallest begin time strictly after now, Sunrise excluded,
 * next-day fallback" rule must satisfy:
 *
 * - before the first prayer (next = Fajr),
 * - exactly at a begin time (strictly-after => the following prayer, never the current one),
 * - between prayers (next = the upcoming one),
 * - Sunrise is skipped even when it is the next chronological event,
 * - after Isha with no next-day schedule (None),
 * - after Isha with a next-day schedule (next day's Fajr; countdown crosses midnight),
 * - all prayers passed.
 *
 * Validates: Requirements 4.1, 4.2, 4.5
 */
class NextPrayerBoundaryTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()
    val nextDate = date.nextDay()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    // Fajr 05:00, Sunrise 06:30, Zuhr 12:00, Asr 15:00, Maghrib 18:00, Isha 20:00
    fun schedule(on: Date): DaySchedule = DaySchedule.of(
        scheduleDate = on,
        prayers = listOf(
            pt(Prayer.Fajr, 5, 0),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Zuhr, 12, 0),
            pt(Prayer.Asr, 15, 0),
            pt(Prayer.Maghrib, 18, 0),
            pt(Prayer.Isha, 20, 0),
        ),
    ).getOrThrow()

    val today = schedule(date)
    val tomorrow = schedule(nextDate)

    fun now(h: Int, m: Int, s: Int = 0): DateTime = DateTime.of(date, h, m, s).getOrThrow()

    // ----------------------------------------------------------------------------------------
    // Table-driven next-prayer selection with NO next-day schedule (Requirements 4.1, 4.2, 4.5)
    // ----------------------------------------------------------------------------------------
    data class NextCase(
        val desc: String,
        val h: Int,
        val m: Int,
        val s: Int,
        val expected: Prayer?,
    )

    listOf(
        // before the first prayer => Fajr
        NextCase("at midnight (well before Fajr) -> Fajr", 0, 0, 0, Prayer.Fajr),
        NextCase("one second before Fajr -> Fajr", 4, 59, 59, Prayer.Fajr),
        // exactly at a begin time => strictly-after selects the FOLLOWING alerting prayer
        NextCase("exactly at Fajr begin -> Zuhr (Sunrise excluded)", 5, 0, 0, Prayer.Zuhr),
        NextCase("exactly at Zuhr begin -> Asr", 12, 0, 0, Prayer.Asr),
        NextCase("exactly at Asr begin -> Maghrib", 15, 0, 0, Prayer.Maghrib),
        NextCase("exactly at Maghrib begin -> Isha", 18, 0, 0, Prayer.Isha),
        // Sunrise is never returned, even when it is the next chronological event
        NextCase("between Fajr and Sunrise -> Zuhr (Sunrise skipped)", 6, 0, 0, Prayer.Zuhr),
        NextCase("exactly at Sunrise -> Zuhr (Sunrise non-alerting)", 6, 30, 0, Prayer.Zuhr),
        NextCase("between Sunrise and Zuhr -> Zuhr", 9, 0, 0, Prayer.Zuhr),
        // between prayers => the upcoming one
        NextCase("between Zuhr and Asr -> Asr", 13, 30, 0, Prayer.Asr),
        NextCase("between Asr and Maghrib -> Maghrib", 16, 45, 0, Prayer.Maghrib),
        NextCase("between Maghrib and Isha -> Isha", 19, 0, 0, Prayer.Isha),
        NextCase("one second before Isha -> Isha", 19, 59, 59, Prayer.Isha),
        // all of today's alerting prayers passed => none remain (no next-day schedule)
        NextCase("exactly at Isha begin -> none remain (strictly-after)", 20, 0, 0, null),
        NextCase("just after Isha -> none remain", 20, 0, 1, null),
        NextCase("late evening after Isha -> none remain", 21, 0, 0, null),
        NextCase("end of day -> none remain", 23, 59, 59, null),
    ).forEach { c ->
        "no next-day: ${c.desc}" {
            ScheduleService.getNextPrayer(today, now(c.h, c.m, c.s)).getOrNull()?.prayer shouldBe
                c.expected
        }
    }

    // ----------------------------------------------------------------------------------------
    // After Isha WITH a next-day schedule => next day's first alerting prayer (Requirement 4.5)
    // ----------------------------------------------------------------------------------------
    "exactly at Isha with a next-day schedule -> next day's Fajr" {
        ScheduleService.getNextPrayer(today, now(20, 0, 0), Option.Some(tomorrow))
            .getOrNull()?.prayer shouldBe Prayer.Fajr
    }

    "after Isha with a next-day schedule -> next day's Fajr" {
        ScheduleService.getNextPrayer(today, now(23, 30, 0), Option.Some(tomorrow))
            .getOrNull()?.prayer shouldBe Prayer.Fajr
    }

    "today's prayers still remaining take precedence over the next-day schedule" {
        // 19:00 still has Isha today; the next-day fallback must NOT pre-empt it.
        ScheduleService.getNextPrayer(today, now(19, 0, 0), Option.Some(tomorrow))
            .getOrNull()?.prayer shouldBe Prayer.Isha
    }

    // ----------------------------------------------------------------------------------------
    // Countdown (timeUntilNext) boundaries, including crossing midnight (Requirements 4.1, 4.5)
    // ----------------------------------------------------------------------------------------
    "countdown before Fajr: 04:30 -> 05:00 = 30 minutes" {
        ScheduleService.timeUntilNext(today, now(4, 30)) shouldBe
            Option.Some(Duration.ofMinutes(30))
    }

    "countdown exactly at Fajr begin: skips to Zuhr, 05:00 -> 12:00 = 7 hours" {
        ScheduleService.timeUntilNext(today, now(5, 0)) shouldBe
            Option.Some(Duration.ofMinutes(7 * 60))
    }

    "countdown with second precision between prayers: 13:30:30 -> Asr 15:00 = 1h29m30s" {
        ScheduleService.timeUntilNext(today, now(13, 30, 30)) shouldBe
            Option.Some(Duration.ofSeconds(1L * 3600 + 29 * 60 + 30))
    }

    "countdown after Isha with no next-day schedule -> None" {
        ScheduleService.timeUntilNext(today, now(21, 0)) shouldBe Option.None
    }

    "countdown crosses midnight: 21:00 today -> next day Fajr 05:00 = 8 hours" {
        ScheduleService.timeUntilNext(today, now(21, 0), Option.Some(tomorrow)) shouldBe
            Option.Some(Duration.ofMinutes(8 * 60))
    }

    "countdown crosses midnight at Isha begin: 20:00 -> next day Fajr 05:00 = 9 hours" {
        ScheduleService.timeUntilNext(today, now(20, 0), Option.Some(tomorrow)) shouldBe
            Option.Some(Duration.ofMinutes(9 * 60))
    }

    "countdown crosses midnight with seconds: 23:30:30 -> next day Fajr 05:00 = 5h29m30s" {
        ScheduleService.timeUntilNext(today, now(23, 30, 30), Option.Some(tomorrow)) shouldBe
            Option.Some(Duration.ofSeconds(5L * 3600 + 29 * 60 + 30))
    }

    // ----------------------------------------------------------------------------------------
    // "All prayers passed" summary cases (Requirement 4.5) — None without next day, fallback with it
    // ----------------------------------------------------------------------------------------
    "all prayers passed and no next-day schedule -> getNextPrayer is None" {
        ScheduleService.getNextPrayer(today, now(22, 0)) shouldBe Option.None
    }

    "all prayers passed with a next-day schedule -> next day's first alerting prayer (Fajr)" {
        ScheduleService.getNextPrayer(today, now(22, 0), Option.Some(tomorrow))
            .getOrNull()?.prayer shouldBe Prayer.Fajr
    }
})
