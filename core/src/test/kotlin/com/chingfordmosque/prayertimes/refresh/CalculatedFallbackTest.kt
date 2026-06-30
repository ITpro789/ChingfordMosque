package com.chingfordmosque.prayertimes.refresh

import com.chingfordmosque.prayertimes.data.provider.TimesProvider
import com.chingfordmosque.prayertimes.data.repository.SaveOutcome
import com.chingfordmosque.prayertimes.data.repository.ScheduleRepository
import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
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
 * Tests for the OPTIONAL calculated-fallback behaviour added to [RefreshCoordinator].
 *
 * The fallback is used ONLY when a primary fetch fails AND the cache is empty; real cached data
 * is always preferred, and a fallback result is never written to the repository (so a later
 * successful scrape always replaces it). These tests assert each branch via self-contained
 * fakes, leaving the existing refresh specs untouched.
 */
class CalculatedFallbackTest : StringSpec({

    val day: Date = Date.of(2024, 6, 10).getOrThrow()

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    fun scheduleOn(d: Date, fajrHour: Int): DaySchedule = DaySchedule.of(
        scheduleDate = d,
        prayers = listOf(
            pt(Prayer.Fajr, fajrHour, 0),
            pt(Prayer.Sunrise, fajrHour + 1, 30),
            pt(Prayer.Zuhr, 12, 0),
            pt(Prayer.Asr, 15, 0),
            pt(Prayer.Maghrib, 18, 0),
            pt(Prayer.Isha, 20, 0),
        ),
    ).getOrThrow()

    class FixedClockImpl(private val instant: DateTime) : Clock {
        override fun now(): DateTime = instant
    }

    class FakeProvider(var result: Result<DaySchedule, ProviderError>) : TimesProvider {
        var calls = 0
        override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
            calls++
            return result
        }
    }

    class RecordingRepository(private val clock: Clock) : ScheduleRepository {
        var cached: Option<CachedSchedule> = Option.None
        val savedSchedules = mutableListOf<DaySchedule>()
        override fun save(schedule: DaySchedule): SaveOutcome {
            val cs = CachedSchedule(schedule, clock.now())
            cached = Option.Some(cs)
            savedSchedules.add(schedule)
            return SaveOutcome.Saved(cs)
        }
        override fun getCachedSchedule(): Option<CachedSchedule> = cached
        override fun clear() { cached = Option.None }
    }

    class RecordingScheduler : NotificationScheduler {
        val rescheduled = mutableListOf<Pair<DaySchedule, DateTime>>()
        override fun reschedule(schedule: DaySchedule, now: DateTime) {
            rescheduled.add(schedule to now)
        }
        override fun cancelAll() { /* no-op */ }
        override fun setPreferences(prefs: NotificationPreferences) { /* no-op */ }
    }

    "primary fails with a cache present and a fallback provided: cache is kept, fallback unused, repo untouched" {
        val clock = FixedClockImpl(DateTime.of(day, 13, 0, 0).getOrThrow())
        val cachedSchedule = scheduleOn(day, fajrHour = 5)
        val repo = RecordingRepository(clock).apply {
            cached = Option.Some(CachedSchedule(cachedSchedule, clock.now()))
        }
        val primary = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
        val fallback = FakeProvider(Result.Ok(scheduleOn(day, fajrHour = 4)))
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(primary, repo, scheduler, clock, fallbackProvider = fallback)

        coordinator.refreshNow()

        val state = coordinator.state
        // Real cached data is preferred: schedule is the cached one, not the fallback.
        state.schedule.getOrNull() shouldBe cachedSchedule
        state.isCalculated shouldBe false
        state.isStale shouldBe true
        state.canRetry shouldBe true
        // The fallback was never consulted; nothing new was saved or rescheduled.
        fallback.calls shouldBe 0
        repo.savedSchedules shouldBe emptyList()
        scheduler.rescheduled shouldBe emptyList()
    }

    "primary fails with an empty cache and a fallback provided: fallback is shown, calculated+stale, error present, notifications armed, repo still empty" {
        val clock = FixedClockImpl(DateTime.of(day, 3, 0, 0).getOrThrow())
        val repo = RecordingRepository(clock) // empty cache
        val primary = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
        val fallbackSchedule = scheduleOn(day, fajrHour = 4)
        val fallback = FakeProvider(Result.Ok(fallbackSchedule))
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(primary, repo, scheduler, clock, fallbackProvider = fallback)

        coordinator.refreshNow()

        val state = coordinator.state
        // The fallback schedule is displayed and flagged as a calculated estimate.
        state.schedule.getOrNull() shouldBe fallbackSchedule
        state.isCalculated shouldBe true
        state.isStale shouldBe true
        // The original primary error is still surfaced so the UI shows a notice/banner.
        (state.error.getOrNull() is RefreshError.Network) shouldBe true
        // Next prayer was recomputed from the fallback (03:00 -> Fajr at 04:00).
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Fajr
        // Notifications were armed from the fallback schedule at "now".
        scheduler.rescheduled shouldBe listOf(fallbackSchedule to clock.now())
        // The fallback is NEVER persisted, so a later real scrape always wins.
        repo.savedSchedules shouldBe emptyList()
        repo.getCachedSchedule() shouldBe Option.None
    }

    "primary fails with an empty cache and the fallback also fails: empty state with the primary error" {
        val clock = FixedClockImpl(DateTime.of(day, 3, 0, 0).getOrThrow())
        val repo = RecordingRepository(clock)
        val primary = FakeProvider(Result.Err(ProviderError.ParseError("markup changed")))
        val fallback = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(primary, repo, scheduler, clock, fallbackProvider = fallback)

        coordinator.refreshNow()

        val state = coordinator.state
        state.schedule shouldBe Option.None
        state.isCalculated shouldBe false
        (state.error.getOrNull() is RefreshError.Parse) shouldBe true
        scheduler.rescheduled shouldBe emptyList()
        repo.savedSchedules shouldBe emptyList()
    }

    "primary succeeds: schedule saved as normal, not calculated (fallback ignored)" {
        val clock = FixedClockImpl(DateTime.of(day, 3, 0, 0).getOrThrow())
        val repo = RecordingRepository(clock)
        val fresh = scheduleOn(day, fajrHour = 5)
        val primary = FakeProvider(Result.Ok(fresh))
        val fallback = FakeProvider(Result.Ok(scheduleOn(day, fajrHour = 4)))
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(primary, repo, scheduler, clock, fallbackProvider = fallback)

        coordinator.refreshNow()

        val state = coordinator.state
        state.schedule.getOrNull() shouldBe fresh
        state.isCalculated shouldBe false
        state.isStale shouldBe false
        state.error shouldBe Option.None
        // Cache saved with the real schedule; fallback was not consulted.
        repo.savedSchedules shouldBe listOf(fresh)
        fallback.calls shouldBe 0
        scheduler.rescheduled shouldBe listOf(fresh to clock.now())
    }
})
