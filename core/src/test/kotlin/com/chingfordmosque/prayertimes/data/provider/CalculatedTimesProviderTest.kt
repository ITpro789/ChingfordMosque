package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.Result
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [CalculatedTimesProvider]: it must turn a deterministic clock instant into a
 * validated [com.chingfordmosque.prayertimes.domain.DaySchedule] for the mosque location, with
 * all six prayers present and in canonical order, entirely offline.
 */
class CalculatedTimesProviderTest : StringSpec({

    fun date(y: Int, m: Int, d: Int): Date = Date.of(y, m, d).getOrThrow()

    "fetchTodaySchedule returns Ok with all six prayers in canonical order for the clock's day" {
        val today = date(2024, 8, 15)
        val clock = FixedClock(DateTime.of(today, 10, 0, 0).getOrThrow())
        val provider = CalculatedTimesProvider(clock)

        val result = provider.fetchTodaySchedule()

        (result is Result.Ok).shouldBeTrue()
        val schedule = (result as Result.Ok).value

        // Schedule is for the clock's calendar day.
        schedule.scheduleDate shouldBe today

        // All six prayers present.
        val expected = listOf(
            Prayer.Fajr, Prayer.Sunrise, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
        schedule.prayers.map { it.prayer } shouldBe expected

        // Begin times strictly increasing (DaySchedule.of already enforces this, asserted here
        // explicitly for clarity).
        for (i in 1 until schedule.prayers.size) {
            (schedule.prayers[i].beginsAt > schedule.prayers[i - 1].beginsAt).shouldBeTrue()
        }

        // Calculated entries carry no iqamah and the schedule has no Jummah.
        schedule.prayers.forEach { it.iqamahAt.isNone.shouldBeTrue() }
        schedule.jummah.isNone.shouldBeTrue()
    }

    "fetchTodaySchedule works for a winter (GMT) date too" {
        val today = date(2024, 12, 21)
        val clock = FixedClock(DateTime.of(today, 9, 0, 0).getOrThrow())

        val result = CalculatedTimesProvider(clock).fetchTodaySchedule()

        (result is Result.Ok).shouldBeTrue()
        (result as Result.Ok).value.prayers.size shouldBe 6
    }
})
