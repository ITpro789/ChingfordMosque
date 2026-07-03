package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [JummahSectionPresenter] / [JummahSectionViewState] (task 9.2).
 *
 * Verifies the Jummah section view-state mapping:
 * - visible, ascending, "HH:mm"-formatted list when Jummah data is present (Reqs 3.1, 3.2);
 * - hidden (omitted without error) when Jummah data is absent (Req 3.3).
 */
class JummahSectionPresenterTest : StringSpec({

    val date = Date.of(2024, 6, 14).getOrThrow() // a Friday

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, time(h, m)).getOrThrow()

    fun scheduleWith(jummah: Option<JummahTimes>): DaySchedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Fajr, 5, 0),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Zuhr, 12, 0),
            pt(Prayer.Asr, 15, 0),
            pt(Prayer.Maghrib, 18, 0),
            pt(Prayer.Isha, 20, 0),
        ),
        jummah = jummah,
    ).getOrThrow()

    "Visible with the jamā'ah times in ascending order when Jummah data is present" {
        val jummah = JummahTimes.of(listOf(time(13, 20), time(14, 0), time(14, 30))).getOrThrow()

        val state = JummahSectionPresenter.present(Option.Some(jummah))

        state shouldBe JummahSectionViewState.Visible(
            listOf("13:20", "14:00", "14:30"),
            List(3) { JummahSectionViewState.JummahStatus.Upcoming }
        )
    }

    "times are formatted as zero-padded HH:mm" {
        val jummah = JummahTimes.of(listOf(time(9, 5), time(13, 0))).getOrThrow()

        val state = JummahSectionPresenter.present(Option.Some(jummah))

        state shouldBe JummahSectionViewState.Visible(
            listOf("09:05", "13:00"),
            List(2) { JummahSectionViewState.JummahStatus.Upcoming }
        )
    }

    "Visible with a single jamā'ah time" {
        val jummah = JummahTimes.of(listOf(time(13, 15))).getOrThrow()

        JummahSectionPresenter.present(Option.Some(jummah)) shouldBe
            JummahSectionViewState.Visible(
                listOf("13:15"),
                listOf(JummahSectionViewState.JummahStatus.Upcoming)
            )
    }

    "Hidden when Jummah data is unavailable (Option.None)" {
        JummahSectionPresenter.present(Option.None) shouldBe JummahSectionViewState.Hidden
    }

    "present(schedule) maps a schedule's jummah field when present" {
        val jummah = JummahTimes.of(listOf(time(13, 20), time(14, 0))).getOrThrow()
        val schedule = scheduleWith(Option.Some(jummah))

        JummahSectionPresenter.present(schedule) shouldBe
            JummahSectionViewState.Visible(
                listOf("13:20", "14:00"),
                List(2) { JummahSectionViewState.JummahStatus.Upcoming }
            )
    }

    "present(schedule) hides the section when the schedule has no jummah" {
        val schedule = scheduleWith(Option.None)

        val state = JummahSectionPresenter.present(schedule)

        state.shouldBeInstanceOf<JummahSectionViewState.Hidden>()
    }

    "the visible list preserves the ascending order of the underlying times" {
        val jummah = JummahTimes.of(listOf(time(12, 45), time(13, 30), time(14, 15))).getOrThrow()

        val state = JummahSectionPresenter.present(Option.Some(jummah))

        (state as JummahSectionViewState.Visible).times shouldBe listOf("12:45", "13:30", "14:15")
    }
})
