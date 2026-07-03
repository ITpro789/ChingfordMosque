package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FridayJummahServiceTest : StringSpec({

    // 2026-07-03 is a Friday
    val friday = Date.of(2026, 7, 3).getOrThrow()
    val saturday = Date.of(2026, 7, 4).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    fun dt(date: Date, h: Int, m: Int, s: Int = 0): DateTime = DateTime.of(date, h, m, s).getOrThrow()

    val jummahTimes = JummahTimes.of(
        listOf(
            Time.of(13, 20).getOrThrow(),
            Time.of(14, 0).getOrThrow()
        )
    ).getOrThrow()

    val fridaySchedule = DaySchedule.of(
        scheduleDate = friday,
        prayers = listOf(
            pt(Prayer.Fajr, 4, 30),
            pt(Prayer.Sunrise, 5, 45),
            pt(Prayer.Zuhr, 13, 0), // Base Zuhr exists in schedule
            pt(Prayer.Asr, 17, 15),
            pt(Prayer.Maghrib, 21, 0),
            pt(Prayer.Isha, 22, 30)
        ),
        jummah = Option.ofNullable(jummahTimes)
    ).getOrThrow()

    "Date.isFriday sanity checks" {
        friday.isFriday() shouldBe true
        saturday.isFriday() shouldBe false
    }

    "getNextPrayer on Friday before Jummah 1 -> returns Jummah 1 at 13:20" {
        val next = ScheduleService.getNextPrayer(fridaySchedule, dt(friday, 11, 0))
        val prayerTime = next.shouldBeInstanceOf<Option.Some<PrayerTime>>().value
        prayerTime.name shouldBe "Jummah 1"
        prayerTime.beginsAt shouldBe Time.of(13, 20).getOrThrow()
    }

    "getNextPrayer on Friday between Jummah 1 and Jummah 2 -> returns Jummah 2 at 14:00" {
        val next = ScheduleService.getNextPrayer(fridaySchedule, dt(friday, 13, 25))
        val prayerTime = next.shouldBeInstanceOf<Option.Some<PrayerTime>>().value
        prayerTime.name shouldBe "Jummah 2"
        prayerTime.beginsAt shouldBe Time.of(14, 0).getOrThrow()
    }

    "getNextPrayer on Friday after Jummah 2 -> returns Asr at 17:15" {
        val next = ScheduleService.getNextPrayer(fridaySchedule, dt(friday, 14, 0))
        val prayerTime = next.shouldBeInstanceOf<Option.Some<PrayerTime>>().value
        prayerTime.name shouldBe "Asr"
        prayerTime.beginsAt shouldBe Time.of(17, 15).getOrThrow()
    }

    "getPrayerStatus on Friday before Jummah 1 -> Upcoming Jummah 1" {
        val status = ScheduleService.getPrayerStatus(fridaySchedule, dt(friday, 11, 0))
        val upcoming = status.shouldBeInstanceOf<PrayerStatus.Upcoming>()
        upcoming.customName shouldBe "Jummah 1"
        upcoming.name shouldBe "Jummah 1"
        upcoming.beginsAt shouldBe dt(friday, 13, 20)
    }

    "getPrayerStatus on Friday during Jummah 1 (13:20 - 13:50) -> Active Jummah 1" {
        val status = ScheduleService.getPrayerStatus(fridaySchedule, dt(friday, 13, 25))
        val active = status.shouldBeInstanceOf<PrayerStatus.Active>()
        active.customName shouldBe "Jummah 1"
        active.name shouldBe "Jummah 1"
        active.startsAt shouldBe dt(friday, 13, 20)
        active.endsAt shouldBe dt(friday, 13, 50)
    }

    "getPrayerStatus on Friday in gap between Jummah 1 end and Jummah 2 start (13:50 - 14:00) -> Upcoming Jummah 2" {
        val status = ScheduleService.getPrayerStatus(fridaySchedule, dt(friday, 13, 55))
        val upcoming = status.shouldBeInstanceOf<PrayerStatus.Upcoming>()
        upcoming.customName shouldBe "Jummah 2"
        upcoming.name shouldBe "Jummah 2"
        upcoming.beginsAt shouldBe dt(friday, 14, 0)
    }

    "getPrayerStatus on Friday during Jummah 2 (14:00 - 14:30) -> Active Jummah 2" {
        val status = ScheduleService.getPrayerStatus(fridaySchedule, dt(friday, 14, 15))
        val active = status.shouldBeInstanceOf<PrayerStatus.Active>()
        active.customName shouldBe "Jummah 2"
        active.name shouldBe "Jummah 2"
        active.startsAt shouldBe dt(friday, 14, 0)
        active.endsAt shouldBe dt(friday, 14, 30)
    }

    "getPrayerStatus on Friday after last Jummah finishes (14:30+) -> Upcoming Asr" {
        val status = ScheduleService.getPrayerStatus(fridaySchedule, dt(friday, 15, 0))
        val upcoming = status.shouldBeInstanceOf<PrayerStatus.Upcoming>()
        upcoming.prayer shouldBe Prayer.Asr
        upcoming.customName shouldBe null
        upcoming.name shouldBe "Asr"
        upcoming.beginsAt shouldBe dt(friday, 17, 15)
    }
})
