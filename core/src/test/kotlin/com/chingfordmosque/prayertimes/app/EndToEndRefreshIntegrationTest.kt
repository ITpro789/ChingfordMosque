package com.chingfordmosque.prayertimes.app

import com.chingfordmosque.prayertimes.data.provider.HttpFetcher
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.notify.InMemoryAdhanAlarmPort
import com.chingfordmosque.prayertimes.notify.InMemoryNotificationPermission
import com.chingfordmosque.prayertimes.refresh.RefreshError
import com.chingfordmosque.prayertimes.refresh.RefreshState
import com.chingfordmosque.prayertimes.ui.ErrorBannerViewState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * End-to-end integration tests for the refresh pipeline (task 10.2).
 *
 * Unlike [AppContainerTest] (which proves the graph is *wired*), these tests exercise the
 * full production graph end-to-end through the composition root [AppContainer] — driving real
 * fetch → parse → validate → cache → schedule-service → notification-scheduler → presenter
 * behaviour with only the platform seams faked (a stubbed [HttpFetcher] returning recorded
 * fixture HTML or a typed error, the in-memory store/alarm-port, granted permission, and a
 * [FixedClock]). No real network call is ever made.
 *
 * They cover the design's end-to-end refresh behaviours:
 *  - a successful refresh updates the cache, recomputes the next prayer, and arms exactly one
 *    alert per remaining alerting prayer (Requirements 5.1, 7.3, 8.1);
 *  - re-running refresh re-arms without creating duplicate alerts (Requirement 5.5);
 *  - a failed fetch (network or parse) preserves the cached schedule and surfaces a retryable
 *    error state (Requirements 8.1, 8.2, and cache safety);
 *  - cache-first-then-update across "app sessions", with re-arming matching the remaining
 *    alerting prayers for the new "now".
 *
 * Requirements: 5.1, 5.5, 7.3, 8.1, 8.2.
 */
class EndToEndRefreshIntegrationTest : StringSpec({

    // The recorded fixture is for June 30, 2026 (Fajr 02:46, Sunrise 04:46, Zuhr 13:08,
    // Asr 18:40, Maghrib 21:24, Isha 22:32; Jummah 13:20/14:00/14:30).
    val scheduleDate: Date = Date.of(2026, 6, 30).getOrThrow()
    // Pin "now" to midday: Fajr has passed, Sunrise never alerts, so the remaining alerting
    // prayers are Zuhr, Asr, Maghrib, Isha and the next prayer is Zuhr (13:08).
    val midday: DateTime = DateTime.of(scheduleDate, 12, 0, 0).getOrThrow()
    // A later instant after Asr (18:40) but before Maghrib (21:24): remaining alerting prayers
    // are just Maghrib and Isha — used to prove re-arming tracks the current "now".
    val evening: DateTime = DateTime.of(scheduleDate, 19, 0, 0).getOrThrow()

    fun fixtureHtml(name: String): String =
        requireNotNull(EndToEndRefreshIntegrationTest::class.java.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture: $name"
        }.bufferedReader().use { it.readText() }

    val goodHtml: String = fixtureHtml("daily-salah-times.html")

    /**
     * A fake fetcher whose [result] can be flipped between calls, letting a single container
     * script "success then failure" without any real network (per the task's option (b)).
     */
    class MutableFetcher(var result: Result<String, ProviderError>) : HttpFetcher {
        override fun fetch(url: String): Result<String, ProviderError> = result
    }

    fun container(
        clock: DateTime,
        fetcher: HttpFetcher,
        alarmPort: InMemoryAdhanAlarmPort = InMemoryAdhanAlarmPort(),
        store: com.chingfordmosque.prayertimes.data.repository.LocalStore =
            com.chingfordmosque.prayertimes.data.repository.InMemoryLocalStore(),
        onStateChange: ((RefreshState) -> Unit)? = null,
    ): AppContainer =
        AppContainer(
            clock = FixedClock(clock),
            store = store,
            alarmPort = alarmPort,
            permission = InMemoryNotificationPermission(granted = true),
            httpFetcher = fetcher,
            onStateChange = onStateChange,
        )

    "successful refresh updates the cache, recomputes the next prayer, and arms one alert per remaining alerting prayer" {
        val alarmPort = InMemoryAdhanAlarmPort()
        val app = container(clock = midday, fetcher = MutableFetcher(Result.Ok(goodHtml)), alarmPort = alarmPort)

        val result = app.refreshNow()

        // The provider returned a validated schedule end-to-end.
        result.shouldBeInstanceOf<Result.Ok<com.chingfordmosque.prayertimes.domain.DaySchedule>>()

        // Cache was updated with the fetched schedule for the fixture's date.
        val cached = app.repository.getCachedSchedule()
        cached.shouldBeInstanceOf<Option.Some<com.chingfordmosque.prayertimes.domain.CachedSchedule>>()
        cached.value.schedule.scheduleDate shouldBe scheduleDate

        // Next prayer recomputed relative to midday → Zuhr (13:08), with a countdown.
        app.state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Zuhr
        app.state.timeUntilNext.getOrNull().shouldNotBeNull()
        app.state.error shouldBe Option.None

        // Exactly one armed alert per remaining alerting prayer — Sunrise excluded, Fajr passed.
        val pending = alarmPort.pending()
        pending.map { it.prayer } shouldContainExactly listOf(Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
        // Each alert is for the fixture's date.
        pending.map { it.date }.toSet() shouldBe setOf(scheduleDate)
    }

    "re-running refresh re-arms the alerts without creating duplicates" {
        val alarmPort = InMemoryAdhanAlarmPort()
        val app = container(clock = midday, fetcher = MutableFetcher(Result.Ok(goodHtml)), alarmPort = alarmPort)

        app.refreshNow()
        app.refreshNow()
        app.refreshNow()

        // After several refreshes the same four prayers are armed once each — no duplicates.
        val pending = alarmPort.pending()
        pending shouldHaveSize 4
        pending.map { it.prayer } shouldContainExactly listOf(Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
        // At most one pending alert per (prayer, date): distinct ids match the pending count.
        pending.map { it.id }.toSet() shouldHaveSize pending.size
    }

    "a failed network fetch keeps the cached schedule and surfaces a retryable error" {
        val alarmPort = InMemoryAdhanAlarmPort()
        val fetcher = MutableFetcher(Result.Ok(goodHtml))
        val app = container(clock = midday, fetcher = fetcher, alarmPort = alarmPort)

        // First, a successful refresh populates the cache and arms alerts.
        app.refreshNow()
        val cachedBefore = app.repository.getCachedSchedule().getOrNull().shouldNotBeNull()

        // Now the network goes down: flip the fetcher to a transport error.
        fetcher.result = Result.Err(ProviderError.NetworkError("connection refused"))
        val result = app.refreshNow()

        // The refresh reports the failure ...
        result.shouldBeInstanceOf<Result.Err<ProviderError>>()
        // ... but the cached schedule is preserved unchanged (cache safety).
        val cachedAfter = app.repository.getCachedSchedule().getOrNull().shouldNotBeNull()
        cachedAfter.schedule.scheduleDate shouldBe cachedBefore.schedule.scheduleDate
        cachedAfter.fetchedAt shouldBe cachedBefore.fetchedAt

        // The state still shows the cached schedule, but now carries a retryable error.
        app.state.schedule shouldNotBe Option.None
        app.state.schedule.getOrNull()?.scheduleDate shouldBe scheduleDate
        app.state.error.getOrNull().shouldBeInstanceOf<RefreshError.Network>()
        app.state.canRetry.shouldBeTrue()
        app.state.isStale.shouldBeTrue()

        // The UI surfaces an error banner with a retry affordance and keeps the manual refresh.
        val next = app.nextPrayerViewState()
        val banner = next.errorBanner.shouldNotBeNull()
        banner.kind shouldBe ErrorBannerViewState.ErrorKind.Network
        banner.showRetry.shouldBeTrue()
        next.showManualRefresh.shouldBeTrue()
    }

    "a parse failure (unexpected markup) keeps the cached schedule and surfaces a retryable error" {
        val fetcher = MutableFetcher(Result.Ok(goodHtml))
        val app = container(clock = midday, fetcher = fetcher)

        // Seed a good cache first.
        app.refreshNow()
        val cachedBefore = app.repository.getCachedSchedule().getOrNull().shouldNotBeNull()

        // Now the site returns markup without the salah widget → ParseError end-to-end.
        fetcher.result = Result.Ok(fixtureHtml("no-salah-widget.html"))
        val result = app.refreshNow()

        result.shouldBeInstanceOf<Result.Err<ProviderError>>()
        // Cache safety: the previously cached valid schedule is retained.
        val cachedAfter = app.repository.getCachedSchedule().getOrNull().shouldNotBeNull()
        cachedAfter.schedule.scheduleDate shouldBe cachedBefore.schedule.scheduleDate
        cachedAfter.fetchedAt shouldBe cachedBefore.fetchedAt

        // Error state is a retryable parse error, with the cached schedule still displayed.
        app.state.schedule.getOrNull()?.scheduleDate shouldBe scheduleDate
        app.state.error.getOrNull().shouldBeInstanceOf<RefreshError.Parse>()
        app.state.canRetry.shouldBeTrue()

        val banner = app.nextPrayerViewState().errorBanner.shouldNotBeNull()
        banner.kind shouldBe ErrorBannerViewState.ErrorKind.Parse
        banner.showRetry.shouldBeTrue()
    }

    "cache-first render then update across app sessions re-arms alerts for the remaining prayers" {
        // A shared in-memory store + alarm port simulate persistence across two "app sessions".
        val store = com.chingfordmosque.prayertimes.data.repository.InMemoryLocalStore()
        val alarmPort = InMemoryAdhanAlarmPort()

        // Session 1: open the app at midday and refresh — populates the cache and arms 4 alerts.
        val session1 = container(clock = midday, fetcher = MutableFetcher(Result.Ok(goodHtml)), alarmPort = alarmPort, store = store)
        session1.onAppOpened()
        alarmPort.pending().map { it.prayer } shouldContainExactly
            listOf(Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)

        // Session 2: a fresh container later in the evening, sharing the same store + alarm port.
        // Record every published state so we can prove cache-first render precedes the update.
        val published = mutableListOf<RefreshState>()
        val session2 = container(
            clock = evening,
            fetcher = MutableFetcher(Result.Ok(goodHtml)),
            alarmPort = alarmPort,
            store = store,
            onStateChange = { published.add(it) },
        )

        session2.onAppOpened()

        // Cache-first: the very first state session2 published already had the cached schedule,
        // before the network fetch completed.
        published.first().schedule.getOrNull()?.scheduleDate shouldBe scheduleDate
        published.first().error shouldBe Option.None

        // After the refresh, the next prayer is recomputed for the evening "now" → Maghrib.
        session2.state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Maghrib
        session2.state.error shouldBe Option.None

        // Re-arming reflects the remaining-after-now prayers only: Zuhr (now past) is
        // gone, leaving exactly Asr (triggers 19:30), Maghrib (21:24), and Isha (22:30) — and no duplicates.
        val pending = alarmPort.pending()
        pending.map { it.prayer } shouldContainExactly listOf(Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
        pending.map { it.id }.toSet() shouldHaveSize pending.size
    }
})
