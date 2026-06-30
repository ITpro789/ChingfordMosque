package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [ScheduleService.getPrayerStatus]: the "current prayer period" rule driving
 * the circular countdown timer. Sample schedule: Fajr 05:00, Sunrise 06:30, Zuhr 13:00,
 * Asr 16:00, Maghrib 19:00, Isha 21:00.
 */
class PrayerStatusTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    fun dt(h: Int, m: Int, s: Int = 0): DateTime = DateTime.of(date, h, m, s).getOrThrow()

    val schedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Fajr, 5, 0),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Zuhr, 13, 0),
            pt(Prayer.Asr, 16, 0),
            pt(Prayer.Maghrib, 19, 0),
            pt(Prayer.Isha, 21, 0),
        ),
    ).getOrThrow()

    "before Fajr -> Active(Isha) ending at Fajr (carryover)" {
        val status = ScheduleService.getPrayerStatus(schedule, dt(3, 0))
        val active = status.shouldBeInstanceOf<PrayerStatus.Active>()
        active.prayer shouldBe Prayer.Isha
        active.startsAt shouldBe dt(0, 0, 0)
        active.endsAt shouldBe DateTime.of(date, Time.of(5, 0).getOrThrow())
    }

    "inside Fajr -> Active(Fajr) ending at Sunrise" {
        val active = ScheduleService.getPrayerStatus(schedule, dt(5, 30))
            .shouldBeInstanceOf<PrayerStatus.Active>()
        active.prayer shouldBe Prayer.Fajr
        active.endsAt shouldBe DateTime.of(date, Time.of(6, 30).getOrThrow())
    }

    "at Sunrise -> Upcoming(Zuhr) (gap, no active fard)" {
        val up = ScheduleService.getPrayerStatus(schedule, dt(6, 30))
            .shouldBeInstanceOf<PrayerStatus.Upcoming>()
        up.prayer shouldBe Prayer.Zuhr
        up.beginsAt shouldBe DateTime.of(date, Time.of(13, 0).getOrThrow())
    }

    "after Sunrise but before Zuhr -> Upcoming(Zuhr)" {
        val up = ScheduleService.getPrayerStatus(schedule, dt(9, 0))
            .shouldBeInstanceOf<PrayerStatus.Upcoming>()
        up.prayer shouldBe Prayer.Zuhr
        up.windowStartsAt shouldBe DateTime.of(date, Time.of(6, 30).getOrThrow())
        up.beginsAt shouldBe DateTime.of(date, Time.of(13, 0).getOrThrow())
    }

    "inside Zuhr -> Active(Zuhr) ending at Asr" {
        val active = ScheduleService.getPrayerStatus(schedule, dt(14, 0))
            .shouldBeInstanceOf<PrayerStatus.Active>()
        active.prayer shouldBe Prayer.Zuhr
        active.endsAt shouldBe DateTime.of(date, Time.of(16, 0).getOrThrow())
    }

    "inside Maghrib -> Active(Maghrib) ending at Isha" {
        val active = ScheduleService.getPrayerStatus(schedule, dt(19, 30))
            .shouldBeInstanceOf<PrayerStatus.Active>()
        active.prayer shouldBe Prayer.Maghrib
        active.endsAt shouldBe DateTime.of(date, Time.of(21, 0).getOrThrow())
    }

    "inside Isha (23:00) -> Active(Isha) ending at next-day Fajr" {
        val active = ScheduleService.getPrayerStatus(schedule, dt(23, 0))
            .shouldBeInstanceOf<PrayerStatus.Active>()
        active.prayer shouldBe Prayer.Isha
        active.startsAt shouldBe DateTime.of(date, Time.of(21, 0).getOrThrow())
        active.endsAt shouldBe DateTime.of(date.nextDay(), Time.of(5, 0).getOrThrow())
    }

    "progress sanity: now-start lies within [0, total] for an active window" {
        val now = dt(20, 0)
        val active = ScheduleService.getPrayerStatus(schedule, now)
            .shouldBeInstanceOf<PrayerStatus.Active>()
        val total = active.startsAt.durationUntil(active.endsAt).totalSeconds
        val elapsed = active.startsAt.durationUntil(now).totalSeconds
        (elapsed in 0..total) shouldBe true
    }
})
