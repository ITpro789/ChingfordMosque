package com.chingfordmosque.prayertimes.app

import com.chingfordmosque.prayertimes.data.provider.HttpFetcher
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.notify.InMemoryAdhanAlarmPort
import com.chingfordmosque.prayertimes.notify.InMemoryNotificationPermission
import com.chingfordmosque.prayertimes.ui.JummahSectionViewState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Integration-style wiring test for the composition root [AppContainer] (task 10.1).
 *
 * It builds the real container with only the platform seams faked — a fake [HttpFetcher]
 * returning the recorded mosque fixture HTML, the in-memory store/alarm-port, granted
 * permission, and a [FixedClock] — and exercises the actual production graph
 * (HttpTimesProvider -> SalahTimesParser -> LocalScheduleRepository -> ScheduleService ->
 * AdhanNotificationScheduler -> presenters) end-to-end through the container's entry points.
 *
 * The goal here is to prove the wiring is correct: that [AppContainer.onAppOpened] drives a
 * fetch through every layer, populates the coordinator state, arms the right notifications,
 * and that the view-state accessors render from that state. Broader end-to-end assertions
 * (re-arming after refresh, failure preserving the cache, etc.) are task 10.2.
 *
 * Requirements: 6.2, 7.1, 7.2, 7.3.
 */
class AppContainerTest : StringSpec({

    // The recorded fixture is for June 30, 2026; pin "now" to midday that day so the set of
    // remaining alerting prayers (Zuhr 13:08, Asr 18:40, Maghrib 21:24, Isha 22:32) and the
    // next prayer (Zuhr) are deterministic.
    val scheduleDate: Date = Date.of(2026, 6, 30).getOrThrow()
    val now: DateTime = DateTime.of(scheduleDate, 12, 0, 0).getOrThrow()

    fun fixtureHtml(name: String): String =
        requireNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture: $name"
        }.bufferedReader().use { it.readText() }

    /** A fake fetcher that hands back recorded HTML, so no real network call is ever made. */
    fun fakeFetcher(html: String): HttpFetcher = HttpFetcher { Result.Ok(html) }

    fun container(
        alarmPort: InMemoryAdhanAlarmPort = InMemoryAdhanAlarmPort(),
        html: String = fixtureHtml("daily-salah-times.html"),
    ): AppContainer =
        AppContainer(
            clock = FixedClock(now),
            alarmPort = alarmPort,
            permission = InMemoryNotificationPermission(granted = true),
            httpFetcher = fakeFetcher(html),
        )

    "onAppOpened drives the full graph and populates coordinator state" {
        val app = container()

        app.onAppOpened()

        val state = app.state
        // A fresh schedule for the fixture's date was fetched, parsed, validated, and published.
        state.hasData shouldBe true
        state.schedule.getOrNull()?.scheduleDate shouldBe scheduleDate
        state.error shouldBe com.chingfordmosque.prayertimes.domain.Option.None
        // Next alerting prayer after midday is Zuhr (13:08), with a countdown computed.
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Zuhr
        state.timeUntilNext.getOrNull().shouldNotBeNull()
        // Rollover tracking was armed for the fixture day.
        app.dailyRefreshScheduler.trackedDate shouldBe scheduleDate
    }

    "onAppOpened arms exactly one notification per remaining alerting prayer" {
        val alarmPort = InMemoryAdhanAlarmPort()
        val app = container(alarmPort = alarmPort)

        app.onAppOpened()

        // Fajr (02:46) has passed and Sunrise never alerts, so the remaining alerting prayers
        // after midday are Zuhr, Asr, Maghrib, Isha — one armed alert each, never Sunrise.
        alarmPort.pending().map { it.prayer } shouldContainExactly
            listOf(Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)
    }

    "presenters render the populated state through the container accessors" {
        val app = container()
        app.onAppOpened()

        // Today's schedule: all six prayers in canonical order, with the fixture's date.
        val today = app.todayScheduleViewState().shouldNotBeNull()
        today.date shouldBe scheduleDate.toString()
        today.rows.map { it.prayerName } shouldContainExactly
            Prayer.canonicalOrder().map { it.name }
        // Sunrise is informational and carries no iqamah.
        val sunrise = today.rows.first { it.prayerName == Prayer.Sunrise.name }
        sunrise.isInformational shouldBe true
        sunrise.iqamah shouldBe null

        // Jummah section visible with the three ascending jamā'ah times from the fixture.
        val jummah = app.jummahSectionViewState().shouldBeInstanceOf<JummahSectionViewState.Visible>()
        jummah.times shouldContainExactly listOf("13:20", "14:00", "14:30")

        // Next-prayer panel: Zuhr with a countdown, a manual-refresh control, and no error.
        val next = app.nextPrayerViewState()
        next.nextPrayerName shouldBe Prayer.Zuhr.name
        next.countdown.shouldNotBeNull()
        next.errorBanner shouldBe null
        next.showManualRefresh shouldBe true
    }

    "default JVM container constructs without test fixtures (real SystemClock wiring)" {
        // Building the container with all production defaults must not require any test seam;
        // a missing real Clock would make this impossible. We only assert it constructs and
        // exposes its wired collaborators — no network call is made (refreshNow is not invoked).
        val app = AppContainer()
        app.refreshCoordinator.state shouldBe com.chingfordmosque.prayertimes.refresh.RefreshState.EMPTY
        app.repository.getCachedSchedule() shouldBe com.chingfordmosque.prayertimes.domain.Option.None
    }
})
