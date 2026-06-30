package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Unit tests for [DaySchedulePresenter]: the pure mapping from a [DaySchedule] to the
 * platform-free [DayScheduleViewState] for today's salah times (Requirement 2).
 *
 * Covers canonical ordering (2.1), begin/iqamah rendering (2.2), Sunrise informational with no
 * iqamah (2.3), and date surfacing (2.4).
 */
class DaySchedulePresenterTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun pt(prayer: Prayer, beginsH: Int, beginsM: Int, iqamah: Time? = null): PrayerTime =
        PrayerTime.of(
            prayer,
            time(beginsH, beginsM),
            Option.ofNullable(iqamah),
        ).getOrThrow()

    // A full schedule with iqamah times for the alerting prayers; Sunrise begins-only.
    // Deliberately provided out of canonical order to prove the presenter reorders.
    fun fullSchedule(): DaySchedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Isha, 20, 0, time(20, 15)),
            pt(Prayer.Fajr, 5, 0, time(5, 30)),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Maghrib, 18, 0, time(18, 5)),
            pt(Prayer.Zuhr, 12, 0, time(12, 30)),
            pt(Prayer.Asr, 15, 0, time(15, 20)),
        ),
    ).getOrThrow()

    "rows are rendered in canonical order regardless of input order" {
        val vs = DaySchedulePresenter.present(fullSchedule())
        vs.rows.map { it.prayerName } shouldBe listOf(
            "Fajr", "Sunrise", "Zuhr", "Asr", "Maghrib", "Isha",
        )
    }

    "the schedule date is surfaced in canonical yyyy-MM-dd form" {
        DaySchedulePresenter.present(fullSchedule()).date shouldBe "2024-06-10"
    }

    "begin and iqamah times are rendered as HH:mm for alerting prayers" {
        val vs = DaySchedulePresenter.present(fullSchedule())
        val fajr = vs.rows.first { it.prayerName == "Fajr" }
        fajr.begins shouldBe "05:00"
        fajr.iqamah shouldBe "05:30"
        fajr.isInformational shouldBe false
    }

    "Sunrise is informational and carries no iqamah value" {
        val sunrise = DaySchedulePresenter.present(fullSchedule()).rows.first { it.prayerName == "Sunrise" }
        sunrise.begins shouldBe "06:30"
        sunrise.iqamah.shouldBeNull()
        sunrise.isInformational shouldBe true
    }

    "a prayer without an iqamah renders a null iqamah while remaining non-informational" {
        val schedule = DaySchedule.of(
            scheduleDate = date,
            prayers = listOf(
                pt(Prayer.Fajr, 5, 0),
                pt(Prayer.Sunrise, 6, 30),
                pt(Prayer.Zuhr, 12, 0),
                pt(Prayer.Asr, 15, 0),
                pt(Prayer.Maghrib, 18, 0),
                pt(Prayer.Isha, 20, 0),
            ),
        ).getOrThrow()
        val zuhr = DaySchedulePresenter.present(schedule).rows.first { it.prayerName == "Zuhr" }
        zuhr.iqamah.shouldBeNull()
        zuhr.isInformational shouldBe false
    }

    "every canonical prayer produces exactly one row" {
        DaySchedulePresenter.present(fullSchedule()).rows.size shouldBe 6
    }
})
