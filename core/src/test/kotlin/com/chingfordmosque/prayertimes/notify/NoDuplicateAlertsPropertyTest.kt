package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
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
 * Property-based test (kotest-property) for the design's correctness Property 5
 * (No duplicate alerts): after [NotificationScheduler.reschedule], there is AT MOST one pending
 * alert per `(prayer, date)`, and re-running `reschedule` repeatedly (simulating a refresh on
 * launch / daily rollover / manual refresh) creates NO duplicates and leaves the armed set
 * unchanged.
 *
 * Strategy: generate a valid, canonically-ordered [DaySchedule] and an arbitrary "now" on the
 * schedule's day, build an [AdhanNotificationScheduler] over an [InMemoryAdhanAlarmPort], invoke
 * `reschedule` between 1 and N times, then inspect [InMemoryAdhanAlarmPort.pending]. The armed
 * alerts must have unique [AlertId]s and unique `(prayer, date)` pairs, never include Sunrise,
 * and number exactly the alerting prayers whose begin instant is strictly after "now" — the set
 * of "remaining" prayers — regardless of how many times `reschedule` ran.
 *
 * **Validates: Requirements 5.5**
 */
class NoDuplicateAlertsPropertyTest : StringSpec({

    val date: Date = Date.of(2024, 6, 10).getOrThrow()

    /**
     * A valid, canonically-ordered [DaySchedule] for all six prayers on [on].
     *
     * Six distinct minutes-since-midnight are drawn, sorted ascending, and assigned to the
     * prayers in canonical order, guaranteeing strictly-increasing begin times. Alerting prayers
     * optionally carry an iqamah at/after their begin; Sunrise never does. Building through the
     * smart constructors guarantees every generated value is a valid schedule.
     */
    fun validScheduleArb(on: Date): Arb<DaySchedule> = arbitrary {
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

    /** An arbitrary instant on the schedule's day, at second granularity. */
    val nowArb: Arb<DateTime> = arbitrary {
        val hour = Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        val second = Arb.int(0..59).bind()
        DateTime.of(date, hour, minute, second).getOrThrow()
    }

    /** How many times to re-run reschedule (simulating repeated refreshes). */
    val repeatsArb: Arb<Int> = Arb.int(1..6)

    /**
     * Independent reference: the alerting prayers whose begin instant is strictly after [now]
     * — i.e. the prayers that should remain armed after a reschedule. Default preferences enable
     * every alerting prayer, so enablement does not reduce this set here.
     */
    fun remainingAlerting(schedule: DaySchedule, now: DateTime): List<PrayerTime> =
        schedule.prayers
            .filter { it.prayer.isAlerting }
            .filter { DateTime.of(schedule.scheduleDate, it.beginsAt) > now }

    "Property 5: reschedule arms at most one alert per (prayer, date) and exactly the remaining alerting prayers" {
        checkAll(validScheduleArb(date), nowArb, repeatsArb) { schedule, now, repeats ->
            val port = InMemoryAdhanAlarmPort()
            val scheduler = AdhanNotificationScheduler(port)

            // Simulate repeated refreshes: launch, daily rollover, manual refresh, etc.
            repeat(repeats) { scheduler.reschedule(schedule, now) }

            val pending = port.pending()

            // At most one alert per (prayer, date): AlertIds are unique...
            pending.map { it.id }.toSet().size shouldBe pending.size
            // ...and so are the underlying (prayer, date) pairs.
            pending.map { it.prayer to it.date }.toSet().size shouldBe pending.size

            // Sunrise is never armed (Requirement 5.6) — reinforces uniqueness expectations.
            pending.none { it.prayer == Prayer.Sunrise }.shouldBeTrue()

            // The armed set is exactly the remaining alerting prayers after now, no matter how
            // many times reschedule ran — repeated refreshes create no duplicates.
            val expected = remainingAlerting(schedule, now)
            pending.size shouldBe expected.size
            pending.map { it.prayer }.toSet() shouldBe expected.map { it.prayer }.toSet()
        }
    }

    "Property 5: re-running reschedule N times leaves the same armed set as a single reschedule (idempotent)" {
        checkAll(validScheduleArb(date), nowArb, repeatsArb) { schedule, now, repeats ->
            // One scheduler armed exactly once.
            val singlePort = InMemoryAdhanAlarmPort()
            AdhanNotificationScheduler(singlePort).reschedule(schedule, now)

            // Another scheduler armed repeatedly with the same inputs (refresh storm).
            val repeatedPort = InMemoryAdhanAlarmPort()
            val repeatedScheduler = AdhanNotificationScheduler(repeatedPort)
            repeat(repeats) { repeatedScheduler.reschedule(schedule, now) }

            // No duplicates accumulate: the repeated run matches the single run by id set...
            repeatedPort.pending().map { it.id }.toSet() shouldBe
                singlePort.pending().map { it.id }.toSet()
            // ...and by count.
            repeatedPort.pending().size shouldBe singlePort.pending().size
        }
    }

    "Property 5: duplicates do not accumulate even when each refresh uses default preferences explicitly" {
        checkAll(validScheduleArb(date), nowArb, repeatsArb) { schedule, now, repeats ->
            val port = InMemoryAdhanAlarmPort()
            val scheduler = AdhanNotificationScheduler(port, NotificationPreferences.default())

            repeat(repeats) {
                // Re-apply preferences before each refresh, as a coordinator might.
                scheduler.setPreferences(NotificationPreferences.default())
                scheduler.reschedule(schedule, now)
            }

            val pending = port.pending()
            pending.map { it.id }.toSet().size shouldBe pending.size
            pending.size shouldBe remainingAlerting(schedule, now).size
        }
    }
})
