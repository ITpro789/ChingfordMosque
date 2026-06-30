package com.chingfordmosque.prayertimes.refresh

import com.chingfordmosque.prayertimes.data.provider.TimesProvider
import com.chingfordmosque.prayertimes.data.repository.SaveOutcome
import com.chingfordmosque.prayertimes.data.repository.ScheduleRepository
import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import com.chingfordmosque.prayertimes.notify.NotificationScheduler
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Example-based unit tests for [RefreshCoordinator] using in-memory fakes for the provider,
 * repository, and notification scheduler. These pin the three behaviours called out by the
 * design (Component 5) and Requirements 6.2/6.3/7.1/7.3/8.1/8.2/8.3:
 *
 * - cache-first render on app open (cached data shown *before* the network fetch),
 * - a successful refresh updates the cache, re-arms notifications, and publishes fresh state,
 * - a failed fetch preserves the cache and surfaces an error + stale flag with retry.
 */
class RefreshCoordinatorTest : StringSpec({

    val today: Date = Date.of(2024, 6, 10).getOrThrow()
    val yesterday: Date = Date.of(2024, 6, 9).getOrThrow()
    val twoDaysAgoAt10: DateTime = DateTime.of(Date.of(2024, 6, 8).getOrThrow(), 10, 0, 0).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

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

    // --- Fakes ---------------------------------------------------------------------------

    class FakeProvider(var result: Result<DaySchedule, ProviderError>) : TimesProvider {
        var calls = 0
        override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
            calls++
            return result
        }
    }

    class FakeRepository(private val clock: Clock) : ScheduleRepository {
        var cached: Option<CachedSchedule> = Option.None
        val savedSchedules = mutableListOf<DaySchedule>()

        override fun save(schedule: DaySchedule): SaveOutcome {
            val cs = CachedSchedule(schedule, clock.now())
            cached = Option.Some(cs)
            savedSchedules.add(schedule)
            return SaveOutcome.Saved(cs)
        }

        override fun getCachedSchedule(): Option<CachedSchedule> = cached

        override fun clear() {
            cached = Option.None
        }
    }

    class FakeScheduler : NotificationScheduler {
        val rescheduled = mutableListOf<Pair<DaySchedule, DateTime>>()
        var cancelAllCount = 0
        override fun reschedule(schedule: DaySchedule, now: DateTime) {
            rescheduled.add(schedule to now)
        }
        override fun cancelAll() { cancelAllCount++ }
        override fun setPreferences(prefs: NotificationPreferences) { /* no-op */ }
    }

    // --- Tests ---------------------------------------------------------------------------

    "onAppOpened renders cached schedule before attempting a refresh" {
        val now = DateTime.of(today, 3, 0, 0).getOrThrow()
        val clock = object : Clock { override fun now(): DateTime = now }
        val repo = FakeRepository(clock).apply {
            // Seed the cache with yesterday's schedule, fetched yesterday.
            cached = Option.Some(CachedSchedule(schedule(yesterday), DateTime.of(yesterday, 10, 0, 0).getOrThrow()))
        }
        val provider = FakeProvider(Result.Ok(schedule(today)))
        val scheduler = FakeScheduler()

        val states = mutableListOf<RefreshState>()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock) { states.add(it) }

        coordinator.onAppOpened()

        // The very first published state must be the cached (yesterday) schedule, emitted
        // before the network fetch updated anything.
        states.first().schedule.getOrNull()?.scheduleDate shouldBe yesterday
        states.first().error shouldBe Option.None
        // The final state reflects the fresh (today) schedule from the successful fetch.
        states.last().schedule.getOrNull()?.scheduleDate shouldBe today
        provider.calls shouldBe 1
    }

    "successful refresh saves to cache, reschedules notifications, and publishes fresh state" {
        val now = DateTime.of(today, 3, 0, 0).getOrThrow()
        val clock = object : Clock { override fun now(): DateTime = now }
        val repo = FakeRepository(clock)
        val fresh = schedule(today)
        val provider = FakeProvider(Result.Ok(fresh))
        val scheduler = FakeScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)

        val result = coordinator.refreshNow()

        (result is Result.Ok) shouldBe true
        // Cache updated with the fresh schedule.
        repo.savedSchedules shouldBe listOf(fresh)
        repo.getCachedSchedule().getOrNull()?.schedule shouldBe fresh
        // Notifications re-armed once for the fresh schedule at "now".
        scheduler.rescheduled shouldBe listOf(fresh to now)
        // State reflects fresh data, no error, next prayer computed (Fajr at 05:00 from 03:00).
        val state = coordinator.state
        state.schedule.getOrNull() shouldBe fresh
        state.error shouldBe Option.None
        state.isStale shouldBe false
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Fajr
        state.canRetry shouldBe false
    }

    "failed fetch preserves cache and surfaces a network error with retry" {
        val now = DateTime.of(today, 13, 0, 0).getOrThrow()
        val clock = object : Clock { override fun now(): DateTime = now }
        val cached = CachedSchedule(schedule(today), DateTime.of(today, 6, 0, 0).getOrThrow())
        val repo = FakeRepository(clock).apply { this.cached = Option.Some(cached) }
        val provider = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
        val scheduler = FakeScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)

        val result = coordinator.refreshNow()

        (result is Result.Err) shouldBe true
        // Cache untouched: still the original cached schedule; nothing saved.
        repo.savedSchedules shouldBe emptyList()
        repo.getCachedSchedule().getOrNull()?.schedule shouldBe cached.schedule
        // Notifications were NOT re-armed on failure.
        scheduler.rescheduled shouldBe emptyList()
        // State keeps the cached schedule but flags an error + stale, with retry available.
        val state = coordinator.state
        state.schedule.getOrNull() shouldBe cached.schedule
        (state.error.getOrNull() is RefreshError.Network) shouldBe true
        state.canRetry shouldBe true
        state.isStale shouldBe true
        // Next prayer is still computed from the cached schedule (13:00 -> Asr at 15:00).
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Asr
    }

    "failed fetch with no cache yields empty data and a parse error" {
        val now = DateTime.of(today, 13, 0, 0).getOrThrow()
        val clock = object : Clock { override fun now(): DateTime = now }
        val repo = FakeRepository(clock) // empty cache
        val provider = FakeProvider(Result.Err(ProviderError.ParseError("markup changed")))
        val scheduler = FakeScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)

        coordinator.refreshNow()

        val state = coordinator.state
        state.hasData shouldBe false
        (state.error.getOrNull() is RefreshError.Parse) shouldBe true
        state.canRetry shouldBe true
        // No cached data after failure => no stale-from-cache claim.
        state.isStale shouldBe false
    }

    "cached data older than one day is rendered as stale on open" {
        val now = DateTime.of(today, 3, 0, 0).getOrThrow()
        val clock = object : Clock { override fun now(): DateTime = now }
        val repo = FakeRepository(clock).apply {
            // Fetched two days ago => older than one day relative to now.
            cached = Option.Some(CachedSchedule(schedule(today), twoDaysAgoAt10))
        }
        // Provider also fails so the stale cached state remains the latest snapshot.
        val provider = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
        val scheduler = FakeScheduler()
        val states = mutableListOf<RefreshState>()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock) { states.add(it) }

        coordinator.onAppOpened()

        // The cache-first render (before any failure is known) is already stale by age.
        states.first().isStale shouldBe true
        // And the post-failure state remains stale with an error.
        coordinator.state.isStale shouldBe true
        (coordinator.state.error.getOrNull() is RefreshError.Network) shouldBe true
    }
})
