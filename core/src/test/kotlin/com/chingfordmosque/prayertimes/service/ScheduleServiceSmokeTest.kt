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
 * Smoke tests confirming [ScheduleService] (next-prayer + countdown + ordering) behaves
 * correctly on representative examples. Exhaustive property/boundary coverage lives in the
 * dedicated test tasks (2.2–2.4).
 */
class ScheduleServiceSmokeTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()
    val nextDate = date.nextDay()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

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

    "before the first prayer, the next prayer is Fajr" {
        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        ScheduleService.getNextPrayer(today, now).getOrNull()?.prayer shouldBe Prayer.Fajr
    }

    "between Zuhr and Asr, the next prayer is Asr (Sunrise never considered)" {
        val now = DateTime.of(date, 13, 30, 0).getOrThrow()
        ScheduleService.getNextPrayer(today, now).getOrNull()?.prayer shouldBe Prayer.Asr
    }

    "exactly at a begin time selects the following prayer (strictly-after semantics)" {
        val now = DateTime.of(date, 12, 0, 0).getOrThrow()
        ScheduleService.getNextPrayer(today, now).getOrNull()?.prayer shouldBe Prayer.Asr
    }

    "Sunrise is never returned even when it is the next chronological event" {
        val now = DateTime.of(date, 6, 0, 0).getOrThrow()
        // 06:00 is after Fajr (05:00) and before Sunrise (06:30); next must skip to Zuhr.
        ScheduleService.getNextPrayer(today, now).getOrNull()?.prayer shouldBe Prayer.Zuhr
    }

    "after Isha with no next-day schedule, none remain" {
        val now = DateTime.of(date, 21, 0, 0).getOrThrow()
        ScheduleService.getNextPrayer(today, now) shouldBe Option.None
        ScheduleService.timeUntilNext(today, now) shouldBe Option.None
    }

    "after Isha with a next-day schedule, returns the next day's Fajr" {
        val now = DateTime.of(date, 21, 0, 0).getOrThrow()
        val result = ScheduleService.getNextPrayer(today, now, Option.Some(schedule(nextDate)))
        result.getOrNull()?.prayer shouldBe Prayer.Fajr
        // 21:00 today until 05:00 next day = 8 hours.
        ScheduleService.timeUntilNext(today, now, Option.Some(schedule(nextDate))) shouldBe
            Option.Some(Duration.ofMinutes(8 * 60))
    }

    "timeUntilNext derives the remaining duration from the next prayer" {
        val now = DateTime.of(date, 4, 30, 0).getOrThrow()
        // 04:30 until Fajr 05:00 = 30 minutes.
        ScheduleService.timeUntilNext(today, now) shouldBe Option.Some(Duration.ofMinutes(30))
    }

    "orderedPrayers lists all six prayers in canonical order including Sunrise" {
        ScheduleService.orderedPrayers(today).map { it.prayer } shouldBe listOf(
            Prayer.Fajr, Prayer.Sunrise, Prayer.Zuhr,
            Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
    }
})
