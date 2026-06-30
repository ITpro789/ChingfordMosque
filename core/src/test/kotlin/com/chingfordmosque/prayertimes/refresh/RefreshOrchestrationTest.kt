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
 * Orchestration-focused unit tests for the refresh layer (task 8.3).
 *
 * Where [RefreshCoordinatorTest] pins each behaviour of a *single* call in isolation, these
 * tests exercise the cross-component coordination that emerges across *sequences* of calls and
 * the end-to-end rollover path through [DailyRefreshScheduler]:
 *
 * - a success after a prior failure must update the cache, recompute the next prayer, re-arm
 *   notifications, AND clear the previously-surfaced error (Requirements 6.3, 7.1, 7.3, 8.1),
 * - a failure following a success must leave the just-saved cache intact and must NOT issue an
 *   additional notification reschedule — the reschedule count is the cross-component witness
 *   (Requirements 6.3, 8.1),
 * - a daily rollover drives a refresh whose new-day schedule flows all the way through to the
 *   notification scheduler (Requirements 7.2, 7.3).
 *
 * These cases use their own self-contained fakes (kept separate from the other refresh specs)
 * that additionally record notification cancel/reschedule counts so the orchestration between
 * the coordinator, repository, and notification scheduler can be asserted directly.
 */
class RefreshOrchestrationTest : StringSpec({

    val day1: Date = Date.of(2024, 6, 10).getOrThrow()
    val day2: Date = Date.of(2024, 6, 11).getOrThrow()

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

    // --- Fakes (distinct from the other refresh specs) -----------------------------------

    /** A clock whose instant can be moved forward between calls. */
    class MovableClock(var instant: DateTime) : Clock {
        override fun now(): DateTime = instant
    }

    /** A provider whose next result can be swapped to script success/failure sequences. */
    class ScriptedProvider(var result: Result<DaySchedule, ProviderError>) : TimesProvider {
        var calls = 0
        override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
            calls++
            return result
        }
    }

    /** An in-memory repository honouring the cache-safety invariant (success saves, failure no-ops). */
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

    /** Records the sequence of reschedules so orchestration with the coordinator is observable. */
    class RecordingScheduler : NotificationScheduler {
        val rescheduled = mutableListOf<Pair<DaySchedule, DateTime>>()
        var cancelAllCount = 0
        override fun reschedule(schedule: DaySchedule, now: DateTime) {
            rescheduled.add(schedule to now)
        }
        override fun cancelAll() { cancelAllCount++ }
        override fun setPreferences(prefs: NotificationPreferences) { /* no-op */ }
    }

    // --- Tests ---------------------------------------------------------------------------

    "a successful refresh after a failure updates cache, recomputes next prayer, re-arms notifications, and clears the error" {
        // Start at 03:00 on day1 with an empty cache; first fetch fails, then succeeds.
        val clock = MovableClock(DateTime.of(day1, 3, 0, 0).getOrThrow())
        val provider = ScriptedProvider(Result.Err(ProviderError.NetworkError("offline")))
        val repo = RecordingRepository(clock)
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)

        // First attempt fails: error surfaced, nothing saved, no notifications armed.
        coordinator.refreshNow()
        coordinator.state.canRetry shouldBe true
        repo.savedSchedules shouldBe emptyList()
        scheduler.rescheduled shouldBe emptyList()

        // Second attempt succeeds with day1's schedule.
        val fresh = schedule(day1)
        provider.result = Result.Ok(fresh)
        coordinator.refreshNow()

        // Cache now holds the fresh schedule (orchestration: provider -> repository).
        repo.savedSchedules shouldBe listOf(fresh)
        repo.getCachedSchedule().getOrNull()?.schedule shouldBe fresh
        // Notifications were armed exactly once, for the fresh schedule at "now".
        scheduler.rescheduled shouldBe listOf(fresh to clock.instant)
        // State reflects the fresh schedule, recomputed next prayer, and a cleared error.
        val state = coordinator.state
        state.schedule.getOrNull() shouldBe fresh
        state.error shouldBe Option.None
        state.canRetry shouldBe false
        state.isStale shouldBe false
        // 03:00 -> next alerting prayer is Fajr at 05:00.
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Fajr
        state.timeUntilNext.getOrNull()?.totalSeconds shouldBe 2 * 60 * 60L
    }

    "a failed refresh after a success preserves the saved cache and issues no additional reschedule" {
        // Succeed once (cache + one reschedule), then fail: the failure must not disturb either.
        val clock = MovableClock(DateTime.of(day1, 13, 0, 0).getOrThrow())
        val fresh = schedule(day1)
        val provider = ScriptedProvider(Result.Ok(fresh))
        val repo = RecordingRepository(clock)
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)

        coordinator.refreshNow()
        scheduler.rescheduled.size shouldBe 1
        repo.savedSchedules shouldBe listOf(fresh)

        // Now the source goes down.
        provider.result = Result.Err(ProviderError.ParseError("markup changed"))
        val result = coordinator.refreshNow()

        (result is Result.Err) shouldBe true
        // Cache untouched: still exactly the previously-saved schedule; no new save occurred.
        repo.savedSchedules shouldBe listOf(fresh)
        repo.getCachedSchedule().getOrNull()?.schedule shouldBe fresh
        // No extra reschedule was issued on failure (still the single success-time arming).
        scheduler.rescheduled.size shouldBe 1
        // The displayed state keeps the cached schedule but now flags an error + stale + retry.
        val state = coordinator.state
        state.schedule.getOrNull() shouldBe fresh
        (state.error.getOrNull() is RefreshError.Parse) shouldBe true
        state.canRetry shouldBe true
        state.isStale shouldBe true
        // Next prayer still computed from the retained cache (13:00 -> Asr at 15:00).
        state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Asr
    }

    "a daily rollover refreshes for the new day and re-arms notifications with the new day's schedule" {
        // Launch late on day1, then let midnight pass so the scheduler ticks into day2.
        val clock = MovableClock(DateTime.of(day1, 22, 0, 0).getOrThrow())
        val provider = ScriptedProvider(Result.Ok(schedule(day1)))
        val repo = RecordingRepository(clock)
        val scheduler = RecordingScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)
        val daily = DailyRefreshScheduler(coordinator, clock)

        // App open performs the launch refresh (day1) and we arm rollover tracking afterwards.
        coordinator.onAppOpened()
        daily.scheduleDailyRefresh()
        scheduler.rescheduled.size shouldBe 1
        scheduler.rescheduled.last().first.scheduleDate shouldBe day1

        // Midnight passes: the host's tick detects the rollover.
        clock.instant = DateTime.of(day2, 0, 10, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day2))
        val fired = daily.tick()

        // The rollover fired exactly one refresh end-to-end.
        fired shouldBe true
        // Cache now holds the new day's schedule.
        repo.getCachedSchedule().getOrNull()?.schedule?.scheduleDate shouldBe day2
        // Notifications were re-armed for the new day at the rollover instant.
        scheduler.rescheduled.size shouldBe 2
        scheduler.rescheduled.last() shouldBe (schedule(day2) to DateTime.of(day2, 0, 10, 0).getOrThrow())
        // The coordinator's published state reflects the new day and carries no error.
        coordinator.state.schedule.getOrNull()?.scheduleDate shouldBe day2
        coordinator.state.error shouldBe Option.None
        // 00:10 on day2 -> next alerting prayer is Fajr at 05:00.
        coordinator.state.nextPrayer.getOrNull()?.prayer shouldBe Prayer.Fajr
    }
})
