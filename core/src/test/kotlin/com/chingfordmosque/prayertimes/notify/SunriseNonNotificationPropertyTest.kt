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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll

/**
 * Property-based test (kotest-property) for the design's correctness Property 6
 * (Sunrise is non-alerting), as it applies to the notification scheduler:
 * Sunrise NEVER schedules a notification, for any valid [DaySchedule] and any "now" —
 * including a "now" positioned before Sunrise's begin instant, where Sunrise is the very next
 * chronological event and is therefore still in the future.
 *
 * Sunrise is informational only ([Prayer.Sunrise].isAlerting == false), so
 * [AdhanNotificationScheduler.reschedule] must never arm an alert for it (Requirement 5.6).
 * The test also confirms that even if a caller *tries* to enable Sunrise via
 * [NotificationPreferences], no Sunrise alert is ever armed.
 *
 * **Validates: Requirements 5.6**
 */
class SunriseNonNotificationPropertyTest : StringSpec({

    val date: Date = Date.of(2024, 6, 10).getOrThrow()

    /**
     * Generates a valid, canonically-ordered [DaySchedule] containing all six prayers
     * (including Sunrise). Strategy mirrors [ScheduleValidationPropertyTest]: draw six distinct
     * minutes-since-midnight, sort them ascending, and assign them to the prayers in canonical
     * order — guaranteeing strictly increasing begin times. Each alerting prayer optionally
     * carries an iqamah at or after its begin; Sunrise never carries one. Building through
     * [PrayerTime.of] / [DaySchedule.of] means every generated value is accepted by the
     * validating constructors.
     */
    val validScheduleArb: Arb<DaySchedule> = arbitrary {
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
        DaySchedule.of(date, prayers).getOrThrow()
    }

    /** An arbitrary instant on the schedule's day, at second granularity. */
    val nowArb: Arb<DateTime> = arbitrary {
        val hour = Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        val second = Arb.int(0..59).bind()
        DateTime.of(date, hour, minute, second).getOrThrow()
    }

    "Property 6: reschedule never arms a Sunrise notification, for any schedule and any now" {
        checkAll(validScheduleArb, nowArb) { schedule, now ->
            val port = InMemoryAdhanAlarmPort()
            val scheduler = AdhanNotificationScheduler(port)

            scheduler.reschedule(schedule, now)

            port.pending().none { it.prayer == Prayer.Sunrise }.shouldBeTrue()
        }
    }

    "Property 6: Sunrise is not armed even when 'now' is immediately before its begin time" {
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

            val port = InMemoryAdhanAlarmPort()
            val scheduler = AdhanNotificationScheduler(port)

            scheduler.reschedule(schedule, now)

            // Sunrise is still in the future relative to `now`, yet must not be armed.
            port.pending().none { it.prayer == Prayer.Sunrise }.shouldBeTrue()
        }
    }

    "Property 6: enabling Sunrise in preferences still arms no Sunrise notification" {
        checkAll(validScheduleArb, nowArb) { schedule, now ->
            val port = InMemoryAdhanAlarmPort()
            val scheduler = AdhanNotificationScheduler(port)

            // A caller attempts to enable EVERY prayer, Sunrise included.
            val prefsEnablingSunrise = NotificationPreferences.of(
                enabledPrayers = Prayer.canonicalOrder().toSet(),
                playAdhanSound = true,
            )
            scheduler.setPreferences(prefsEnablingSunrise)

            scheduler.reschedule(schedule, now)

            port.pending().none { it.prayer == Prayer.Sunrise }.shouldBeTrue()
        }
    }
})
