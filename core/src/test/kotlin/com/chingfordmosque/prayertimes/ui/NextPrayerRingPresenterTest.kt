package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.Time
import com.chingfordmosque.prayertimes.refresh.RefreshState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

/**
 * Tests for the circular-countdown "ring" fields of [NextPrayerViewState] populated by
 * [NextPrayerPresenter.present] (state, now). Sample schedule: Fajr 05:00, Sunrise 06:30,
 * Zuhr 13:00, Asr 16:00, Maghrib 19:00, Isha 21:00.
 */
class NextPrayerRingPresenterTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    fun dt(h: Int, m: Int, s: Int = 0): DateTime = DateTime.of(date, h, m, s).getOrThrow()

    val schedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Fajr, 5, 0),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Zuhr, 13, 0),
            pt(Prayer.Asr, 16, 0),
            pt(Prayer.Maghrib, 19, 0),
            pt(Prayer.Isha, 21, 0),
        ),
    ).getOrThrow()

    fun stateWith(sched: DaySchedule? = schedule): RefreshState = RefreshState(
        schedule = Option.ofNullable(sched),
        fetchedAt = Option.ofNullable(dt(4, 0)),
        isStale = false,
        nextPrayer = Option.None,
        timeUntilNext = Option.None,
        error = Option.None,
    )

    "active window exposes ring fields with 'ends in' caption" {
        val vs = NextPrayerPresenter.present(stateWith(), now = dt(19, 30))
        vs.ringPrayerName shouldBe "Maghrib"
        vs.ringIsActive shouldBe true
        vs.ringCaption shouldBe "Maghrib ends in"
        // 19:30 -> 21:00 == 1h30m remaining
        vs.ringCountdown shouldBe "01:30:00"
        vs.ringProgress shouldBeGreaterThanOrEqual 0f
        vs.ringProgress shouldBeLessThanOrEqual 1f
    }

    "upcoming gap exposes ring fields with 'begins in' caption" {
        // 09:00 sits in the Sunrise->Zuhr gap.
        val vs = NextPrayerPresenter.present(stateWith(), now = dt(9, 0))
        vs.ringPrayerName shouldBe "Zuhr"
        vs.ringIsActive shouldBe false
        vs.ringCaption shouldBe "Zuhr begins in"
        // 09:00 -> 13:00 == 4h remaining
        vs.ringCountdown shouldBe "04:00:00"
        vs.ringProgress shouldBeGreaterThanOrEqual 0f
        vs.ringProgress shouldBeLessThanOrEqual 1f
    }

    "no schedule leaves ring fields at defaults" {
        val vs = NextPrayerPresenter.present(stateWith(sched = null), now = dt(9, 0))
        vs.ringPrayerName shouldBe null
        vs.ringIsActive shouldBe false
        vs.ringProgress shouldBe 0f
    }
})
