package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Duration
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
 * Unit tests for [NextPrayerPresenter] / [NextPrayerViewState] (task 9.3): the pure mapping
 * from a [RefreshState] to the platform-free next-prayer view-state.
 *
 * Covers the next-prayer name and countdown formatting (Requirements 4.3, 4.4), the live "tick"
 * recompute from an injected "now", the freshness / "last updated" and stale indicator
 * (Requirement 6.4), the network/parse error banner with retry (Requirements 8.1, 8.2, 8.3),
 * and the no-data case.
 */
class NextPrayerPresenterTest : StringSpec({

    val date = Date.of(2024, 6, 10).getOrThrow()

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, time(h, m)).getOrThrow()

    fun dt(h: Int, m: Int, s: Int = 0): DateTime =
        DateTime.of(date, h, m, s).getOrThrow()

    fun fullSchedule(): DaySchedule = DaySchedule.of(
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

    fun stateWith(
        schedule: DaySchedule? = fullSchedule(),
        fetchedAt: DateTime? = dt(4, 0),
        isStale: Boolean = false,
        nextPrayer: PrayerTime? = null,
        timeUntilNext: Duration? = null,
        error: RefreshError? = null,
        isCalculated: Boolean = false,
    ): RefreshState = RefreshState(
        schedule = Option.ofNullable(schedule),
        fetchedAt = Option.ofNullable(fetchedAt),
        isStale = isStale,
        nextPrayer = Option.ofNullable(nextPrayer),
        timeUntilNext = Option.ofNullable(timeUntilNext),
        error = Option.ofNullable(error),
        isCalculated = isCalculated,
    )

    // --- next-prayer name & countdown formatting (from precomputed state fields) ---

    "next prayer name comes from the state's nextPrayer" {
        val state = stateWith(
            nextPrayer = pt(Prayer.Zuhr, 12, 0),
            timeUntilNext = Duration.ofSeconds(3_661), // 1h 01m 01s
        )

        val vs = NextPrayerPresenter.present(state)

        vs.nextPrayerName shouldBe "Zuhr"
        vs.countdown shouldBe "01:01:01"
    }

    "countdown is formatted as zero-padded HH:mm:ss" {
        val state = stateWith(
            nextPrayer = pt(Prayer.Fajr, 5, 0),
            timeUntilNext = Duration.ofSeconds(5), // 5 seconds
        )

        NextPrayerPresenter.present(state).countdown shouldBe "00:00:05"
    }

    // --- live "tick" recompute from an injected now (no fetch) ---

    "present(state, now) recomputes the next prayer and countdown from the schedule" {
        // now = 13:00 -> next alerting prayer is Asr at 15:00 (Sunrise excluded), 2h away.
        val vs = NextPrayerPresenter.present(stateWith(), now = dt(13, 0, 0))

        vs.nextPrayerName shouldBe "Asr"
        vs.countdown shouldBe "02:00:00"
    }

    "advancing now shortens the countdown without changing anything else (per-second tick)" {
        val state = stateWith()

        val at1259 = NextPrayerPresenter.present(state, now = dt(11, 59, 30))
        val at1300 = NextPrayerPresenter.present(state, now = dt(12, 0, 0))

        // Before Zuhr (12:00): both ticks still target Zuhr, countdown decreases by 30s.
        at1259.nextPrayerName shouldBe "Zuhr"
        at1259.countdown shouldBe "00:00:30"
        at1300.nextPrayerName shouldBe "Asr" // Zuhr now reached; rolls to Asr
    }

    "Sunrise is never surfaced as the next prayer" {
        // now = 05:30 -> Sunrise (06:30) is skipped; next is Zuhr (12:00).
        NextPrayerPresenter.present(stateWith(), now = dt(5, 30)).nextPrayerName shouldBe "Zuhr"
    }

    // --- freshness / "last updated" & stale indicator (Req 6.4) ---

    "last updated text is derived deterministically from fetchedAt" {
        val vs = NextPrayerPresenter.present(stateWith(fetchedAt = dt(4, 5)))

        vs.lastUpdatedText shouldBe "Last updated 2024-06-10 04:05"
    }

    "stale flag is surfaced from the state" {
        NextPrayerPresenter.present(stateWith(isStale = true)).isStale shouldBe true
        NextPrayerPresenter.present(stateWith(isStale = false)).isStale shouldBe false
    }

    // --- estimated (calculated fallback) indicator ---

    "isEstimated reflects state.isCalculated in present(state)" {
        NextPrayerPresenter.present(stateWith(isCalculated = true)).isEstimated shouldBe true
        NextPrayerPresenter.present(stateWith(isCalculated = false)).isEstimated shouldBe false
    }

    "isEstimated reflects state.isCalculated in present(state, now)" {
        NextPrayerPresenter.present(stateWith(isCalculated = true), now = dt(13, 0)).isEstimated shouldBe true
        NextPrayerPresenter.present(stateWith(isCalculated = false), now = dt(13, 0)).isEstimated shouldBe false
    }

    // --- error banner + retry (Reqs 8.1, 8.2, 8.3) ---

    "a network error produces a banner with a retry affordance" {
        val error = RefreshError.from(ProviderError.NetworkError("timeout"))

        val banner = NextPrayerPresenter.present(stateWith(error = error)).errorBanner

        banner.shouldNotBeNull()
        banner.kind shouldBe ErrorBannerViewState.ErrorKind.Network
        banner.showRetry shouldBe true
    }

    "a parse error produces a parse-kind banner with retry" {
        val error = RefreshError.from(ProviderError.ParseError("bad markup"))

        val banner = NextPrayerPresenter.present(stateWith(error = error)).errorBanner

        banner.shouldNotBeNull()
        banner.kind shouldBe ErrorBannerViewState.ErrorKind.Parse
        banner.showRetry shouldBe true
    }

    "no error banner is shown when the last refresh succeeded" {
        NextPrayerPresenter.present(stateWith(error = null)).errorBanner.shouldBeNull()
    }

    "the manual refresh control is always available" {
        NextPrayerPresenter.present(stateWith()).showManualRefresh shouldBe true
        NextPrayerPresenter.present(RefreshState.EMPTY).showManualRefresh shouldBe true
    }

    // --- no-data case ---

    "the empty initial state renders no prayer, no countdown and no last-updated text" {
        val vs = NextPrayerPresenter.present(RefreshState.EMPTY)

        vs.nextPrayerName.shouldBeNull()
        vs.countdown.shouldBeNull()
        vs.lastUpdatedText.shouldBeNull()
        vs.isStale shouldBe false
        vs.errorBanner.shouldBeNull()
    }

    "present(state, now) with no schedule falls back to state fields (no crash)" {
        val state = stateWith(schedule = null, fetchedAt = null)

        val vs = NextPrayerPresenter.present(state, now = dt(13, 0))

        vs.nextPrayerName.shouldBeNull()
        vs.countdown.shouldBeNull()
    }

    "when all of today's prayers have passed there is no next prayer or countdown" {
        // now = 21:00 -> after Isha (20:00); no next-day schedule supplied.
        val vs = NextPrayerPresenter.present(stateWith(), now = dt(21, 0))

        vs.nextPrayerName.shouldBeNull()
        vs.countdown.shouldBeNull()
    }
})
