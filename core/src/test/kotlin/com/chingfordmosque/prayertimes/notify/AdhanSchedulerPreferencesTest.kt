package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Example-based unit tests for [AdhanNotificationScheduler] covering preference handling and
 * re-arming on refresh. Exhaustive universal coverage (no-duplicate / Sunrise-never) lives in
 * the dedicated property tests (7.3, 7.4); these tests pin representative behaviour:
 *
 * - Disabled prayers are not scheduled (Requirement 5.4).
 * - The adhan sound toggle is reflected in every armed alert (Requirement 5.3).
 * - Re-arming after a fire/refresh replaces alerts (no duplicates) and reflects updated
 *   preferences and schedule, arming only prayers still upcoming relative to `now`
 *   (Requirement 5.5).
 */
class AdhanSchedulerPreferencesTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    // A full day's schedule, including Sunrise (which must never be armed).
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

    fun pendingPrayers(port: InMemoryAdhanAlarmPort): List<Prayer> =
        port.pending().map { it.prayer }

    // --- Disabled prayers are not scheduled (Requirement 5.4) -----------------------------

    "only the prayers enabled in preferences are armed" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        // Enable only a subset; the rest must be skipped even though they are in the schedule.
        scheduler.setPreferences(
            NotificationPreferences.of(setOf(Prayer.Fajr, Prayer.Maghrib), playAdhanSound = true),
        )

        // 03:00 is before every prayer, so enablement (not time) is what filters here.
        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(today, now)

        pendingPrayers(port) shouldContainExactlyInAnyOrder listOf(Prayer.Fajr, Prayer.Maghrib)
    }

    "Sunrise is never armed even when every prayer is enabled" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port) // default: all alerting enabled

        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(today, now)

        pendingPrayers(port) shouldContainExactly listOf(
            Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
        port.pending().none { it.prayer == Prayer.Sunrise } shouldBe true
    }

    "disabling all prayers arms nothing" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        scheduler.setPreferences(NotificationPreferences.of(emptySet(), playAdhanSound = true))

        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(today, now)

        port.pending() shouldBe emptyList()
    }

    // --- playAdhanSound toggle is respected (Requirement 5.3) -----------------------------

    "every armed alert carries playAdhanSound=true when the sound is enabled" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        scheduler.setPreferences(
            NotificationPreferences.of(Prayer.alerting().toSet(), playAdhanSound = true),
        )

        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(today, now)

        port.pending().all { it.playAdhanSound } shouldBe true
    }

    "every armed alert carries playAdhanSound=false when the sound is disabled" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        scheduler.setPreferences(
            NotificationPreferences.of(Prayer.alerting().toSet(), playAdhanSound = false),
        )

        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(today, now)

        port.pending().isNotEmpty() shouldBe true
        port.pending().none { it.playAdhanSound } shouldBe true
    }

    // --- Re-arming after a fire/refresh (Requirement 5.5) ---------------------------------

    "re-arming only schedules prayers still upcoming relative to now" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)

        // First arm before any prayer: all five alerting prayers are pending.
        scheduler.reschedule(today, DateTime.of(date, 3, 0, 0).getOrThrow())
        pendingPrayers(port) shouldContainExactly listOf(
            Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )

        // Later in the day (after Zuhr fired, between Zuhr 12:00 and Asr 15:00) a refresh
        // re-arms: passed prayers drop off, only the still-upcoming ones remain.
        scheduler.reschedule(today, DateTime.of(date, 13, 30, 0).getOrThrow())
        pendingPrayers(port) shouldContainExactly listOf(
            Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
    }

    "a prayer exactly at now is treated as already passed and not re-armed" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)

        // now == Asr begin (15:00): Asr has begun, so only strictly-later prayers are armed.
        scheduler.reschedule(today, DateTime.of(date, 15, 0, 0).getOrThrow())
        pendingPrayers(port) shouldContainExactly listOf(Prayer.Maghrib, Prayer.Isha)
    }

    "re-running reschedule produces no duplicate alerts per (prayer, date)" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        val now = DateTime.of(date, 3, 0, 0).getOrThrow()

        scheduler.reschedule(today, now)
        val firstCount = port.pending().size
        // A second refresh at the same instant must not stack duplicates.
        scheduler.reschedule(today, now)

        port.pending().size shouldBe firstCount
        port.pending().map { it.id }.toSet().size shouldBe firstCount
    }

    "re-arming reflects updated preferences" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        val now = DateTime.of(date, 3, 0, 0).getOrThrow()

        // Initially all alerting prayers with sound on.
        scheduler.reschedule(today, now)
        pendingPrayers(port).size shouldBe 5
        port.pending().all { it.playAdhanSound } shouldBe true

        // Change preferences: narrow to two prayers, sound off — next reschedule reflects it.
        scheduler.setPreferences(
            NotificationPreferences.of(setOf(Prayer.Fajr, Prayer.Isha), playAdhanSound = false),
        )
        scheduler.reschedule(today, now)

        pendingPrayers(port) shouldContainExactlyInAnyOrder listOf(Prayer.Fajr, Prayer.Isha)
        port.pending().none { it.playAdhanSound } shouldBe true
    }

    "re-arming after the schedule changes reflects the new day's times" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        val nextDate = date.nextDay()

        scheduler.reschedule(today, DateTime.of(date, 3, 0, 0).getOrThrow())
        port.pending().all { it.date == date } shouldBe true

        // A rollover refresh for the next day re-targets every alert to the new date.
        scheduler.reschedule(schedule(nextDate), DateTime.of(nextDate, 3, 0, 0).getOrThrow())
        port.pending().all { it.date == nextDate } shouldBe true
        pendingPrayers(port) shouldContainExactly listOf(
            Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
    }

    "cancelAll clears every pending alert" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        scheduler.reschedule(today, DateTime.of(date, 3, 0, 0).getOrThrow())
        port.pending().isNotEmpty() shouldBe true

        scheduler.cancelAll()
        port.pending() shouldBe emptyList()
    }

    "adhans are scheduled 15 minutes before iqamah except maghrib" {
        val port = InMemoryAdhanAlarmPort()
        val scheduler = AdhanNotificationScheduler(port)
        
        // Custom schedule with Iqamah times
        val customSchedule = DaySchedule.of(
            scheduleDate = date,
            prayers = listOf(
                PrayerTime.of(Prayer.Fajr, Time.of(5, 0).getOrThrow(), com.chingfordmosque.prayertimes.domain.Option.Some(Time.of(5, 30).getOrThrow())).getOrThrow(),
                PrayerTime.of(Prayer.Zuhr, Time.of(12, 0).getOrThrow(), com.chingfordmosque.prayertimes.domain.Option.Some(Time.of(13, 30).getOrThrow())).getOrThrow(),
                PrayerTime.of(Prayer.Asr, Time.of(15, 0).getOrThrow(), com.chingfordmosque.prayertimes.domain.Option.Some(Time.of(15, 10).getOrThrow())).getOrThrow(), // Gap < 15m, clamps to begins
                PrayerTime.of(Prayer.Maghrib, Time.of(18, 0).getOrThrow(), com.chingfordmosque.prayertimes.domain.Option.Some(Time.of(18, 15).getOrThrow())).getOrThrow(), // Maghrib exceptions
                PrayerTime.of(Prayer.Isha, Time.of(20, 0).getOrThrow(), com.chingfordmosque.prayertimes.domain.Option.Some(Time.of(20, 45).getOrThrow())).getOrThrow()
            )
        ).getOrThrow()

        val now = DateTime.of(date, 3, 0, 0).getOrThrow()
        scheduler.reschedule(customSchedule, now)

        val pending = port.pending()
        
        // Fajr: 5:30 Iqamah - 15 mins = 5:15
        pending.first { it.prayer == Prayer.Fajr }.firesAt.time.toString() shouldBe "05:15"
        
        // Zuhr: 13:30 Iqamah - 15 mins = 13:15
        pending.first { it.prayer == Prayer.Zuhr }.firesAt.time.toString() shouldBe "13:15"
        
        // Asr: 15:10 Iqamah - 15 mins = 14:55 (clamped to begins 15:00)
        pending.first { it.prayer == Prayer.Asr }.firesAt.time.toString() shouldBe "15:00"
        
        // Maghrib: 18:15 Iqamah, but Maghrib starts at begins (18:00)
        pending.first { it.prayer == Prayer.Maghrib }.firesAt.time.toString() shouldBe "18:00"
        
        // Isha: 20:45 Iqamah - 15 mins = 20:30
        pending.first { it.prayer == Prayer.Isha }.firesAt.time.toString() shouldBe "20:30"
    }
})
