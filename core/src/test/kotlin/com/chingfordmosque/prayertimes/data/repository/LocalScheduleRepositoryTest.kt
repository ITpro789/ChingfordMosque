package com.chingfordmosque.prayertimes.data.repository

import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Example-based unit tests for [LocalScheduleRepository] covering the cache round-trip
 * behaviour described by the design (Component 2) and Requirements 6.1 / 6.2:
 *
 * - a save then getCachedSchedule round-trip preserves the schedule and records the
 *   fetched-at instant from the injected clock,
 * - a second save overwrites with the newer schedule and updated fetched-at,
 * - clear() empties the cache,
 * - getCachedSchedule on a fresh repository returns [Option.None].
 *
 * These complement [CacheSafetyPropertyTest] (Property 4 / Requirement 6.5) which covers the
 * "failed fetch never overwrites good data" invariant.
 *
 * _Requirements: 6.1 (persist schedule + fetched-at), 6.2 (cached data is retrievable)._
 */
class LocalScheduleRepositoryTest : StringSpec({

    // --- small builders to keep the cases readable ---

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun date(): Date = Date.of(2024, 6, 7).getOrThrow()

    fun pt(prayer: Prayer, begin: Time, iqamah: Time? = null): PrayerTime =
        PrayerTime.of(
            prayer,
            begin,
            if (iqamah == null) Option.None else Option.Some(iqamah),
        ).getOrThrow()

    /** A complete, canonically increasing schedule. */
    fun scheduleA(): DaySchedule = DaySchedule.of(
        date(),
        listOf(
            pt(Prayer.Fajr, time(3, 30), time(4, 0)),
            pt(Prayer.Sunrise, time(5, 0)),
            pt(Prayer.Zuhr, time(13, 0), time(13, 15)),
            pt(Prayer.Asr, time(17, 30), time(17, 45)),
            pt(Prayer.Maghrib, time(21, 15)),
            pt(Prayer.Isha, time(22, 45), time(23, 0)),
        ),
    ).getOrThrow()

    /** A different, also-valid schedule (later times) used to verify overwrite. */
    fun scheduleB(): DaySchedule = DaySchedule.of(
        Date.of(2024, 6, 8).getOrThrow(),
        listOf(
            pt(Prayer.Fajr, time(3, 25), time(3, 55)),
            pt(Prayer.Sunrise, time(5, 2)),
            pt(Prayer.Zuhr, time(13, 5)),
            pt(Prayer.Asr, time(17, 35), time(17, 50)),
            pt(Prayer.Maghrib, time(21, 20)),
            pt(Prayer.Isha, time(22, 50)),
        ),
    ).getOrThrow()

    fun instant(hour: Int, minute: Int): DateTime =
        DateTime.of(date(), hour, minute, 0).getOrThrow()

    "getCachedSchedule on a fresh repository returns None" {
        val repo = LocalScheduleRepository(clock = FixedClock(instant(8, 0)))

        repo.getCachedSchedule() shouldBe Option.None
    }

    "save then getCachedSchedule round-trip preserves the schedule and records fetchedAt from the clock" {
        val fetchedAt = instant(8, 30)
        val repo = LocalScheduleRepository(clock = FixedClock(fetchedAt))
        val schedule = scheduleA()

        val outcome = repo.save(schedule)

        // The save reports exactly what was stored, stamped with the clock's instant.
        outcome.shouldBeInstanceOf<SaveOutcome.Saved>()
        outcome.didOverwrite shouldBe true
        outcome.cached shouldBe CachedSchedule(schedule, fetchedAt)

        // The round-trip preserves both the schedule and the fetched-at metadata.
        val cached = repo.getCachedSchedule()
        cached shouldBe Option.Some(CachedSchedule(schedule, fetchedAt))
        val value = (cached as Option.Some).value
        value.schedule shouldBe schedule
        value.fetchedAt shouldBe fetchedAt
    }

    "a second save overwrites with the newer schedule and updated fetchedAt" {
        val firstAt = instant(8, 0)
        val secondAt = instant(9, 15)
        // A mutable clock so the two saves record different instants.
        val clock = object : com.chingfordmosque.prayertimes.domain.Clock {
            var current: DateTime = firstAt
            override fun now(): DateTime = current
        }
        val repo = LocalScheduleRepository(clock = clock)

        val first = scheduleA()
        repo.save(first)
        repo.getCachedSchedule() shouldBe Option.Some(CachedSchedule(first, firstAt))

        // Advance the clock and save a different schedule.
        clock.current = secondAt
        val second = scheduleB()
        val outcome = repo.save(second)

        outcome.shouldBeInstanceOf<SaveOutcome.Saved>()
        outcome.cached shouldBe CachedSchedule(second, secondAt)

        // The cache now holds the newer schedule with the updated fetched-at, not the old one.
        val cached = repo.getCachedSchedule()
        cached shouldBe Option.Some(CachedSchedule(second, secondAt))
        val value = (cached as Option.Some).value
        value.schedule shouldBe second
        value.fetchedAt shouldBe secondAt
    }

    "clear empties the cache so getCachedSchedule returns None" {
        val repo = LocalScheduleRepository(clock = FixedClock(instant(8, 30)))
        repo.save(scheduleA())
        repo.getCachedSchedule().shouldBeInstanceOf<Option.Some<CachedSchedule>>()

        repo.clear()

        repo.getCachedSchedule() shouldBe Option.None
    }
})
