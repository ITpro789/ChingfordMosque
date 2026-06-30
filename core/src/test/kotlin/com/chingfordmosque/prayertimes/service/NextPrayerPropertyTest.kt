package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/**
 * Property-based tests (kotest-property) for the design's "Correctness Properties" #1.
 *
 * - **Property 1 (Next-prayer correctness):** for any valid [DaySchedule] and any "now",
 *   [ScheduleService.getNextPrayer] returns the *alerting* prayer with the smallest begin
 *   instant strictly after "now". When today's alerting prayers are all in the past it returns
 *   the next day's first alerting prayer if that schedule is supplied, otherwise [Option.None].
 *
 * Each property is checked against an independent brute-force computation over the generated
 * schedule, so the test does not merely re-implement the service's selection strategy — it
 * states the property declaratively (minimal future alerting begin) and cross-checks.
 *
 * **Validates: Requirements 4.1, 4.5**
 */
class NextPrayerPropertyTest : StringSpec({

    val today: Date = Date.of(2024, 6, 10).getOrThrow()
    val tomorrow: Date = today.nextDay()

    // --- Generators -------------------------------------------------------------------------

    /**
     * A valid, canonically-ordered [DaySchedule] for all six prayers on [date].
     *
     * Begin times are six distinct minutes-since-midnight drawn from 0..1438 (capping the
     * latest begin at 23:58 leaves room to place a "now" strictly after Isha within the same
     * day), sorted ascending and assigned in canonical order so begins are strictly increasing.
     * Alerting prayers optionally get an iqamah at/after their begin; Sunrise never does.
     * Building through the smart constructors guarantees every value is a valid schedule.
     */
    fun validScheduleArb(date: Date): Arb<DaySchedule> = arbitrary {
        val begins: List<Int> =
            Arb.set(Arb.int(0..(Time.MINUTES_PER_DAY - 2)), 6).bind().sorted()
        val prayers = Prayer.canonicalOrder().mapIndexed { index, prayer ->
            val beginMinutes = begins[index]
            val beginsAt = Time.ofMinutes(beginMinutes).getOrThrow()
            val iqamahAt: Option<Time> = if (prayer == Prayer.Sunrise) {
                Option.None
            } else if (Arb.boolean().bind()) {
                val offset = Arb.int(0..(Time.MINUTES_PER_DAY - 1 - beginMinutes)).bind()
                Option.Some(Time.ofMinutes(beginMinutes + offset).getOrThrow())
            } else {
                Option.None
            }
            PrayerTime.of(prayer, beginsAt, iqamahAt).getOrThrow()
        }
        DaySchedule.of(date, prayers).getOrThrow()
    }

    /** An arbitrary instant on [date] (any second of the day). */
    fun nowArb(date: Date): Arb<DateTime> = arbitrary {
        val hour = Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        val second = Arb.int(0..59).bind()
        DateTime.of(date, hour, minute, second).getOrThrow()
    }

    // --- Brute-force reference --------------------------------------------------------------

    /**
     * Independent reference implementation of "next alerting prayer": among the alerting
     * prayers, the one whose begin instant (on the schedule's own date) is strictly after
     * [now], minimised by that instant. None when all alerting begins are at or before [now].
     */
    fun bruteForceNext(schedule: DaySchedule, now: DateTime): Option<PrayerTime> {
        val candidate = schedule.prayers
            .filter { it.prayer.isAlerting }
            .map { it to DateTime.of(schedule.scheduleDate, it.beginsAt) }
            .filter { (_, instant) -> instant > now }
            .minByOrNull { (_, instant) -> instant }
            ?.first
        return Option.ofNullable(candidate)
    }

    /** The latest alerting begin instant of [schedule] (Isha in a canonical schedule). */
    fun lastAlertingInstant(schedule: DaySchedule): DateTime =
        schedule.prayers
            .filter { it.prayer.isAlerting }
            .map { DateTime.of(schedule.scheduleDate, it.beginsAt) }
            .max()

    /** A "now" on [date] guaranteed to fall strictly after [after] (still within the day). */
    fun nowAfterArb(date: Date, after: DateTime): Arb<DateTime> = arbitrary {
        // `after` is on `date`; begins are capped at 23:58 so a later same-day instant exists.
        val afterMinutes = after.hour * 60 + after.minute
        val nowMinutes = Arb.int(afterMinutes..(Time.MINUTES_PER_DAY - 1)).bind()
        val second = Arb.int(0..59).bind()
        val candidate = DateTime.of(date, nowMinutes / 60, nowMinutes % 60, second).getOrThrow()
        // Guard the boundary minute where seconds could leave us at-or-before `after`.
        if (candidate > after) candidate else DateTime.of(date, 23, 59, 59).getOrThrow()
    }

    // --- Property 1: Next-prayer correctness ------------------------------------------------

    "Property 1: getNextPrayer equals the minimal future alerting begin (brute force), no next day" {
        checkAll(validScheduleArb(today), nowArb(today)) { schedule, now ->
            ScheduleService.getNextPrayer(schedule, now) shouldBe bruteForceNext(schedule, now)
        }
    }

    "Property 1: the returned prayer is alerting, strictly after now, and truly minimal" {
        checkAll(validScheduleArb(today), nowArb(today)) { schedule, now ->
            when (val result = ScheduleService.getNextPrayer(schedule, now)) {
                is Option.Some -> {
                    val chosen = result.value
                    chosen.prayer.isAlerting.shouldBeTrue()
                    val chosenInstant = DateTime.of(schedule.scheduleDate, chosen.beginsAt)
                    (chosenInstant > now).shouldBeTrue()
                    // No other alerting prayer begins in the open interval (now, chosenInstant).
                    val earlierExists = schedule.prayers
                        .filter { it.prayer.isAlerting }
                        .map { DateTime.of(schedule.scheduleDate, it.beginsAt) }
                        .any { it > now && it < chosenInstant }
                    earlierExists shouldBe false
                }
                is Option.None -> {
                    // None only when no alerting prayer begins strictly after now.
                    val anyFuture = schedule.prayers
                        .filter { it.prayer.isAlerting }
                        .map { DateTime.of(schedule.scheduleDate, it.beginsAt) }
                        .any { it > now }
                    anyFuture shouldBe false
                }
            }
        }
    }

    "Property 1: when today's alerting prayers are exhausted and no next day is supplied, returns None" {
        checkAll(validScheduleArb(today)) { schedule ->
            val now = lastAlertingInstant(schedule)
            // `now` sits exactly on Isha's begin; strictly-after semantics leave nothing today.
            ScheduleService.getNextPrayer(schedule, now) shouldBe Option.None
        }
    }

    "Property 1: when today is exhausted (now after Isha), falls back to next day's first prayer" {
        val genTriple = arbitrary {
            val todaySchedule = validScheduleArb(today).bind()
            val tomorrowSchedule = validScheduleArb(tomorrow).bind()
            val now = nowAfterArb(today, lastAlertingInstant(todaySchedule)).bind()
            Triple(todaySchedule, tomorrowSchedule, now)
        }
        checkAll(genTriple) { (todaySchedule, tomorrowSchedule, now) ->
            val result = ScheduleService.getNextPrayer(
                todaySchedule,
                now,
                Option.Some(tomorrowSchedule),
            )
            // Today yields nothing; the fallback is the next day's minimal alerting begin,
            // which for a canonical schedule is the first alerting prayer (Fajr).
            bruteForceNext(todaySchedule, now) shouldBe Option.None
            result shouldBe bruteForceNext(tomorrowSchedule, now)
            result.getOrNull()?.prayer shouldBe Prayer.Fajr
        }
    }
})
