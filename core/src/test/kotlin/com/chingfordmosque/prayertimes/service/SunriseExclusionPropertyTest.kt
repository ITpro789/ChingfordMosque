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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/**
 * Property-based test (kotest-property) for the design's correctness Property 6
 * (Sunrise is non-alerting): Sunrise is NEVER returned by [ScheduleService.getNextPrayer],
 * for any valid [DaySchedule] and any "now" — including a "now" positioned immediately before
 * Sunrise's begin instant, where Sunrise is the very next chronological event.
 *
 * Sunrise is informational only ([Prayer.Sunrise].isAlerting == false), so the next-prayer
 * computation must always skip it (Requirement 4.2).
 *
 * **Validates: Requirements 4.2**
 */
class SunriseExclusionPropertyTest : StringSpec({

    val date: Date = Date.of(2024, 6, 10).getOrThrow()
    val nextDate: Date = date.nextDay()

    /**
     * Generates a valid, canonically-ordered [DaySchedule] containing all six prayers
     * (including Sunrise). Strategy: draw six distinct minutes-since-midnight, sort them
     * ascending, and assign them to the prayers in canonical order — guaranteeing strictly
     * increasing begin times. Each alerting prayer optionally carries an iqamah at or after
     * its begin; Sunrise never carries one. Building through [PrayerTime.of] / [DaySchedule.of]
     * means every generated value is accepted by the validating constructors.
     */
    fun scheduleArb(on: Date): Arb<DaySchedule> = arbitrary {
        val begins: List<Int> = Arb.set(Arb.int(0..(Time.MINUTES_PER_DAY - 1)), 6).bind().sorted()
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
        DaySchedule.of(on, prayers).getOrThrow()
    }

    val validScheduleArb: Arb<DaySchedule> = scheduleArb(date)

    /** An arbitrary instant on the schedule's day, at second granularity. */
    val nowArb: Arb<DateTime> = arbitrary {
        val hour = Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        val second = Arb.int(0..59).bind()
        DateTime.of(date, hour, minute, second).getOrThrow()
    }

    "Property 6: getNextPrayer never returns Sunrise for any schedule and any now" {
        checkAll(validScheduleArb, nowArb, scheduleArb(nextDate)) { schedule, now, nextDay ->
            // With no next-day schedule.
            when (val next = ScheduleService.getNextPrayer(schedule, now)) {
                is Option.Some -> (next.value.prayer != Prayer.Sunrise).shouldBeTrue()
                is Option.None -> { /* nothing returned — trivially excludes Sunrise */ }
            }
            // And when a next-day schedule is available (covers the all-passed fallback path,
            // where the next day's first alerting prayer — never Sunrise — is returned).
            when (val withNextDay = ScheduleService.getNextPrayer(schedule, now, Option.Some(nextDay))) {
                is Option.Some -> (withNextDay.value.prayer != Prayer.Sunrise).shouldBeTrue()
                is Option.None -> { }
            }
        }
    }

    "Property 6: Sunrise is skipped even when 'now' is immediately before its begin time" {
        checkAll(validScheduleArb) { schedule ->
            val sunrise = schedule.prayers.first { it.prayer == Prayer.Sunrise }
            // One second before Sunrise's begin instant. Sunrise begins strictly after Fajr,
            // so its begin minute is >= 1 and (beginMinutes * 60 - 1) >= 59 is always valid.
            val secondsBefore = sunrise.beginsAt.minutesSinceMidnight * 60 - 1
            val now = DateTime.of(
                date,
                secondsBefore / 3600,
                (secondsBefore % 3600) / 60,
                secondsBefore % 60,
            ).getOrThrow()
            when (val next = ScheduleService.getNextPrayer(schedule, now)) {
                is Option.Some -> (next.value.prayer != Prayer.Sunrise).shouldBeTrue()
                is Option.None -> { }
            }
        }
    }
})
