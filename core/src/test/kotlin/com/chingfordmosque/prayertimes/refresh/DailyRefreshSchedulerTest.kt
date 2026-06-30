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
 * Example-based unit tests for [DailyRefreshScheduler] (Requirements 7.2, 7.4; design Error
 * Scenario 5). They drive a real [RefreshCoordinator] through a mutable fake [Clock] so the
 * date can be rolled forward between host-driven [DailyRefreshScheduler.tick] calls, and assert
 * the rollover contract:
 *
 * - a tick on the same calendar day does NOT trigger a refresh,
 * - a day change triggers exactly one refresh (and re-arms notifications via the coordinator),
 * - multiple ticks within the same new day do not re-trigger.
 */
class DailyRefreshSchedulerTest : StringSpec({

    val day1: Date = Date.of(2024, 6, 10).getOrThrow()
    val day2: Date = Date.of(2024, 6, 11).getOrThrow()
    val day3: Date = Date.of(2024, 6, 12).getOrThrow()

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

    /** A mutable clock whose instant can be advanced between ticks. */
    class MutableClock(var instant: DateTime) : Clock {
        override fun now(): DateTime = instant
    }

    class FakeProvider(var result: Result<DaySchedule, ProviderError>) : TimesProvider {
        var calls = 0
        override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
            calls++
            return result
        }
    }

    class FakeRepository(private val clock: Clock) : ScheduleRepository {
        var cached: Option<CachedSchedule> = Option.None
        override fun save(schedule: DaySchedule): SaveOutcome {
            val cs = CachedSchedule(schedule, clock.now())
            cached = Option.Some(cs)
            return SaveOutcome.Saved(cs)
        }
        override fun getCachedSchedule(): Option<CachedSchedule> = cached
        override fun clear() { cached = Option.None }
    }

    class FakeScheduler : NotificationScheduler {
        val rescheduled = mutableListOf<Pair<DaySchedule, DateTime>>()
        override fun reschedule(schedule: DaySchedule, now: DateTime) {
            rescheduled.add(schedule to now)
        }
        override fun cancelAll() { /* no-op */ }
        override fun setPreferences(prefs: NotificationPreferences) { /* no-op */ }
    }

    fun fixtures(startInstant: DateTime): Triple<MutableClock, FakeProvider, RefreshCoordinator> {
        val clock = MutableClock(startInstant)
        val provider = FakeProvider(Result.Ok(schedule(clock.now().date)))
        val repo = FakeRepository(clock)
        val scheduler = FakeScheduler()
        val coordinator = RefreshCoordinator(provider, repo, scheduler, clock)
        return Triple(clock, provider, coordinator)
    }

    // --- Tests ---------------------------------------------------------------------------

    "scheduleDailyRefresh arms tracking without triggering a refresh" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 10, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)

        daily.scheduleDailyRefresh()

        daily.trackedDate shouldBe day1
        provider.calls shouldBe 0
    }

    "tick on the same calendar day does not trigger a refresh" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 10, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)
        daily.scheduleDailyRefresh()

        // Advance the clock by hours but stay on the same day.
        clock.instant = DateTime.of(day1, 14, 30, 0).getOrThrow()
        val fired = daily.tick()

        fired shouldBe false
        provider.calls shouldBe 0
        daily.trackedDate shouldBe day1
    }

    "a day change triggers exactly one refresh" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 23, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)
        daily.scheduleDailyRefresh()

        // Midnight passes: now it is the next day.
        clock.instant = DateTime.of(day2, 0, 5, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day2))
        val fired = daily.tick()

        fired shouldBe true
        provider.calls shouldBe 1
        daily.trackedDate shouldBe day2
        // The coordinator refreshed for the new day.
        coordinator.state.schedule.getOrNull()?.scheduleDate shouldBe day2
    }

    "multiple ticks within the same new day do not re-trigger" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 23, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)
        daily.scheduleDailyRefresh()

        // First rollover into day2 -> one refresh.
        clock.instant = DateTime.of(day2, 0, 5, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day2))
        daily.tick() shouldBe true

        // Several more ticks on the same day2 must not fetch again.
        clock.instant = DateTime.of(day2, 6, 0, 0).getOrThrow()
        daily.tick() shouldBe false
        clock.instant = DateTime.of(day2, 12, 0, 0).getOrThrow()
        daily.tick() shouldBe false
        clock.instant = DateTime.of(day2, 23, 59, 0).getOrThrow()
        daily.tick() shouldBe false

        provider.calls shouldBe 1
        daily.trackedDate shouldBe day2
    }

    "consecutive day rollovers each trigger one refresh" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 23, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)
        daily.scheduleDailyRefresh()

        clock.instant = DateTime.of(day2, 0, 1, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day2))
        daily.tick() shouldBe true

        clock.instant = DateTime.of(day3, 0, 1, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day3))
        daily.tick() shouldBe true

        provider.calls shouldBe 2
        daily.trackedDate shouldBe day3
    }

    "first tick without arming establishes a baseline without refreshing" {
        val (clock, provider, coordinator) = fixtures(DateTime.of(day1, 10, 0, 0).getOrThrow())
        val daily = DailyRefreshScheduler(coordinator, clock)

        // No scheduleDailyRefresh() call: the first tick just records the day.
        daily.tick() shouldBe false
        provider.calls shouldBe 0
        daily.trackedDate shouldBe day1

        // A subsequent rollover is then detected normally.
        clock.instant = DateTime.of(day2, 0, 1, 0).getOrThrow()
        provider.result = Result.Ok(schedule(day2))
        daily.tick() shouldBe true
        provider.calls shouldBe 1
    }
})
