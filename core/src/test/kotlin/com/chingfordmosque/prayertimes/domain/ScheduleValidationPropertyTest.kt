package com.chingfordmosque.prayertimes.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/**
 * Property-based tests (kotest-property) for the schedule-validation invariants from the
 * design's "Correctness Properties" section.
 *
 * - Property 2 (Monotonic ordering): for any successfully parsed [DaySchedule], the alerting
 *   prayers' begin times are strictly increasing in canonical order.
 * - Property 3 (Iqamah not before begin): for every [PrayerTime], `iqamahAt` (when present)
 *   is `>= beginsAt`.
 *
 * The generators below produce *valid* schedules through the smart constructors (so the
 * invariants must hold), and additional checks confirm the smart constructors *reject*
 * inputs that would violate the invariants.
 *
 * **Validates: Requirements 1.6, 2.1**
 */
class ScheduleValidationPropertyTest : StringSpec({

    val anyDate: Date = Date.of(2024, 1, 15).getOrThrow()

    /**
     * Generates a valid, canonically-ordered [DaySchedule] for all six prayers.
     *
     * Strategy: draw six distinct minutes-since-midnight, sort them ascending, and assign
     * them to the prayers in canonical order — this guarantees strictly-increasing begin
     * times. Each alerting prayer optionally receives an iqamah at or after its begin time
     * (Sunrise never carries one). Building through [PrayerTime.of] / [DaySchedule.of] means
     * every generated value is, by construction, accepted by the validating constructors.
     */
    val validScheduleArb: Arb<DaySchedule> = arbitrary {
        val begins: List<Int> = Arb.set(Arb.int(0..(Time.MINUTES_PER_DAY - 1)), 6).bind().sorted()
        val prayers = Prayer.canonicalOrder().mapIndexed { index, prayer ->
            val beginMinutes = begins[index]
            val beginsAt = Time.ofMinutes(beginMinutes).getOrThrow()
            val iqamahAt: Option<Time> = if (prayer == Prayer.Sunrise) {
                // Sunrise is informational only and must not carry an iqamah.
                Option.None
            } else if (Arb.boolean().bind()) {
                // iqamah at or after begin (offset in [0 .. remaining minutes of the day]).
                val offset = Arb.int(0..(Time.MINUTES_PER_DAY - 1 - beginMinutes)).bind()
                Option.Some(Time.ofMinutes(beginMinutes + offset).getOrThrow())
            } else {
                Option.None
            }
            PrayerTime.of(prayer, beginsAt, iqamahAt).getOrThrow()
        }
        DaySchedule.of(anyDate, prayers).getOrThrow()
    }

    // --- Property 2: Monotonic ordering ---

    "Property 2: alerting prayer begin times are strictly increasing in canonical order" {
        checkAll(validScheduleArb) { schedule ->
            val alerting = schedule.prayers
                .filter { it.prayer.isAlerting }
                .sortedBy { it.prayer.canonicalIndex }
            for (i in 1 until alerting.size) {
                (alerting[i].beginsAt > alerting[i - 1].beginsAt).shouldBeTrue()
            }
        }
    }

    "Property 2 (rejection): DaySchedule.of rejects begin times that are not strictly increasing" {
        // All required salah sharing one begin time violates strict monotonicity.
        checkAll(Arb.int(0..(Time.MINUTES_PER_DAY - 1))) { minutes ->
            val sameTime = Time.ofMinutes(minutes).getOrThrow()
            val prayers = Prayer.requiredDaily.map { PrayerTime.of(it, sameTime).getOrThrow() }
            DaySchedule.of(anyDate, prayers).isErr.shouldBeTrue()
        }
    }

    // --- Property 3: Iqamah not before begin ---

    "Property 3: iqamah, when present, is never before begin (over valid schedules)" {
        checkAll(validScheduleArb) { schedule ->
            schedule.prayers.forEach { pt ->
                when (val iqamah = pt.iqamahAt) {
                    is Option.Some -> (iqamah.value >= pt.beginsAt).shouldBeTrue()
                    is Option.None -> { /* nothing to check */ }
                }
            }
        }
    }

    "Property 3 (rejection): PrayerTime.of rejects an iqamah earlier than begin" {
        // begin in 1..1439 so that an earlier minute exists.
        checkAll(Arb.int(1..(Time.MINUTES_PER_DAY - 1))) { beginMinutes ->
            val beginsAt = Time.ofMinutes(beginMinutes).getOrThrow()
            val earlier = Time.ofMinutes(beginMinutes - 1).getOrThrow()
            PrayerTime.of(Prayer.Zuhr, beginsAt, Option.Some(earlier)).isErr.shouldBeTrue()
        }
    }

    "Property 3 (rejection): PrayerTime.of rejects a Sunrise that carries an iqamah" {
        checkAll(Arb.int(0..(Time.MINUTES_PER_DAY - 1))) { minutes ->
            val time = Time.ofMinutes(minutes).getOrThrow()
            PrayerTime.of(Prayer.Sunrise, time, Option.Some(time)).isErr.shouldBeTrue()
        }
    }
})
