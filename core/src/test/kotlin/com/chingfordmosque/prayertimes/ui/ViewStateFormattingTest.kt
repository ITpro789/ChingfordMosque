package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Duration
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Time
import com.chingfordmosque.prayertimes.refresh.RefreshError
import com.chingfordmosque.prayertimes.refresh.RefreshState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Consolidated, cross-cutting view-state *formatting* tests for task 9.4.
 *
 * The three presenters each have their own behavioural unit tests
 * ([DaySchedulePresenterTest], [JummahSectionPresenterTest], [NextPrayerPresenterTest]).
 * This spec deliberately focuses on the formatting concerns the design calls out across the
 * whole UI layer, exercising edge cases (shuffled input order, zero-padding of long
 * countdowns, seconds dropped from the "last updated" label, single/odd-minute Jummah times)
 * that are easy to regress when copy/format changes ripple across presenters.
 *
 * Requirements: 2.1 (canonical order), 2.3 (Sunrise informational, no iqamah),
 * 3.3 (Jummah omitted without error), 6.4 ("last updated" / stale + error indicator).
 */
class ViewStateFormattingTest : StringSpec({

    val date = Date.of(2024, 6, 14).getOrThrow() // a Friday

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun dt(h: Int, m: Int, s: Int = 0): DateTime = DateTime.of(date, h, m, s).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int, iqamah: Time? = null): PrayerTime =
        PrayerTime.of(prayer, time(h, m), Option.ofNullable(iqamah)).getOrThrow()

    // Built deliberately out of canonical order to prove the presenter sorts it.
    fun shuffledSchedule(jummah: Option<JummahTimes> = Option.None): DaySchedule = DaySchedule.of(
        scheduleDate = date,
        prayers = listOf(
            pt(Prayer.Maghrib, 18, 0, time(18, 5)),
            pt(Prayer.Fajr, 5, 0, time(5, 30)),
            pt(Prayer.Isha, 20, 0, time(20, 15)),
            pt(Prayer.Sunrise, 6, 30),
            pt(Prayer.Asr, 15, 0, time(15, 20)),
            pt(Prayer.Zuhr, 12, 0, time(12, 30)),
        ),
        jummah = jummah,
    ).getOrThrow()

    fun stateWith(
        schedule: DaySchedule? = shuffledSchedule(),
        fetchedAt: DateTime? = dt(4, 0),
        isStale: Boolean = false,
        nextPrayer: PrayerTime? = null,
        timeUntilNext: Duration? = null,
        error: RefreshError? = null,
    ): RefreshState = RefreshState(
        schedule = Option.ofNullable(schedule),
        fetchedAt = Option.ofNullable(fetchedAt),
        isStale = isStale,
        nextPrayer = Option.ofNullable(nextPrayer),
        timeUntilNext = Option.ofNullable(timeUntilNext),
        error = Option.ofNullable(error),
    )

    // --- Ordering (Requirement 2.1) ---

    "day-schedule rows are emitted in canonical order even from shuffled input" {
        val rows = DaySchedulePresenter.present(shuffledSchedule()).rows

        rows.map { it.prayerName } shouldBe listOf(
            "Fajr", "Sunrise", "Zuhr", "Asr", "Maghrib", "Isha",
        )
    }

    "row begin times appear in strictly increasing order after formatting" {
        val begins = DaySchedulePresenter.present(shuffledSchedule()).rows.map { it.begins }

        begins shouldBe listOf("05:00", "06:30", "12:00", "15:00", "18:00", "20:00")
        begins shouldBe begins.sorted()
    }

    // --- Sunrise without iqamah (Requirement 2.3) ---

    "the Sunrise row is informational and has a null iqamah, while alerting rows do not" {
        val rows = DaySchedulePresenter.present(shuffledSchedule()).rows

        val sunrise = rows.first { it.prayerName == "Sunrise" }
        sunrise.isInformational shouldBe true
        sunrise.iqamah.shouldBeNull()

        // Exactly one informational row (Sunrise); every other row is non-informational.
        rows.count { it.isInformational } shouldBe 1
        rows.filter { it.prayerName != "Sunrise" }.forEach { it.isInformational shouldBe false }
    }

    // --- Jummah omission / formatting (Requirement 3.3) ---

    "the Jummah section is hidden (omitted without error) when no data is present" {
        JummahSectionPresenter.present(shuffledSchedule(Option.None)) shouldBe
            JummahSectionViewState.Hidden
    }

    "the Jummah section renders ascending zero-padded HH:mm times when present" {
        val jummah = JummahTimes.of(listOf(time(13, 5), time(14, 0), time(14, 30))).getOrThrow()

        val state = JummahSectionPresenter.present(shuffledSchedule(Option.Some(jummah)))

        state shouldBe JummahSectionViewState.Visible(
            listOf("13:05", "14:00", "14:30"),
            List(3) { JummahSectionViewState.JummahStatus.Upcoming }
        )
    }

    // --- Stale / error indicator visibility & countdown formatting (Requirement 6.4, 4.3) ---

    "the stale indicator is surfaced directly from the refresh state" {
        NextPrayerPresenter.present(stateWith(isStale = true)).isStale shouldBe true
        NextPrayerPresenter.present(stateWith(isStale = false)).isStale shouldBe false
    }

    "the last-updated label is formatted to the minute and drops seconds" {
        // 04:05:59 should still render as 04:05 (seconds dropped).
        val vs = NextPrayerPresenter.present(stateWith(fetchedAt = dt(4, 5, 59)))

        vs.lastUpdatedText shouldBe "Last updated 2024-06-14 04:05"
    }

    "no last-updated label is shown when there is no data to attribute" {
        NextPrayerPresenter.present(stateWith(schedule = null, fetchedAt = null))
            .lastUpdatedText.shouldBeNull()
    }

    "a refresh error surfaces a retryable banner of the matching kind" {
        val banner = NextPrayerPresenter.present(
            stateWith(error = RefreshError.from(ProviderError.NetworkError("timeout"))),
        ).errorBanner

        banner.shouldNotBeNull()
        banner.kind shouldBe ErrorBannerViewState.ErrorKind.Network
        banner.showRetry shouldBe true
    }

    "no error banner is shown when the most recent refresh succeeded" {
        NextPrayerPresenter.present(stateWith(error = null)).errorBanner.shouldBeNull()
    }

    "the countdown is zero-padded HH:mm:ss including spans of ten-plus hours" {
        // 10h 09m 08s -> exercises two-digit hours and single-digit minute/second padding.
        val state = stateWith(
            nextPrayer = pt(Prayer.Isha, 20, 0),
            timeUntilNext = Duration.ofSeconds(10L * 3600 + 9 * 60 + 8),
        )

        NextPrayerPresenter.present(state).countdown shouldBe "10:09:08"
    }

    "a sub-minute countdown is rendered with leading zeros" {
        val state = stateWith(
            nextPrayer = pt(Prayer.Fajr, 5, 0),
            timeUntilNext = Duration.ofSeconds(7),
        )

        NextPrayerPresenter.present(state).countdown shouldBe "00:00:07"
    }
})
