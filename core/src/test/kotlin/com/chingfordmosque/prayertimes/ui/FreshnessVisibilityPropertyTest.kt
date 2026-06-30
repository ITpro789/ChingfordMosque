package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.data.provider.TimesProvider
import com.chingfordmosque.prayertimes.data.repository.SaveOutcome
import com.chingfordmosque.prayertimes.data.repository.ScheduleRepository
import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import com.chingfordmosque.prayertimes.notify.NotificationScheduler
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
import com.chingfordmosque.prayertimes.refresh.RefreshCoordinator
import com.chingfordmosque.prayertimes.refresh.RefreshError
import com.chingfordmosque.prayertimes.refresh.RefreshState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property-based test (kotest-property) for the design's "Correctness Properties" #7.
 *
 * - **Property 7 (Freshness visibility):** whenever the displayed data is older than one day
 *   OR came from the cache after a failed refresh, the UI exposes a stale / "last updated"
 *   indicator (`isStale == true` and a non-null `lastUpdatedText`). Conversely, when the data
 *   is fresh (recent and not following a failed refresh) the stale indicator is not raised.
 *
 * **Validates: Requirements 6.4**
 *
 * Two complementary strategies are used:
 *
 * - **(A) Generator over [RefreshState] (primary, fully deterministic):** drive
 *   [NextPrayerPresenter] directly with states whose freshness has been computed by the *same*
 *   rule the [RefreshCoordinator] applies (cache-after-failure OR fetched more than one day
 *   before "now"). The expected staleness is derived independently from the domain
 *   [DateTime.durationUntil] computation — not copied from the state — so the test states the
 *   freshness rule declaratively and cross-checks that the presenter surfaces the indicator
 *   exactly when the rule fires. `fetchedAt` ages are generated both under and over one day
 *   (the one-day-back band straddles the boundary), exercising both outcomes.
 *
 * - **(B) End-to-end through [RefreshCoordinator] (stronger):** seed a repository with a
 *   schedule fetched at a generated instant, fail the provider, and assert that the resulting
 *   states (the age-based cache-first render and the cache-after-failure render) expose the
 *   indicator exactly per the rule. This validates the freshness computation itself, not just
 *   its pass-through.
 */
class FreshnessVisibilityPropertyTest : StringSpec({

    val today: Date = Date.of(2024, 6, 10).getOrThrow()
    // A late "now" so that any same-day fetched time is strictly earlier (non-zero age).
    val now: DateTime = DateTime.of(today, 23, 59, 0).getOrThrow()
    val oneDaySeconds = 86_400L

    fun pt(prayer: Prayer, h: Int, m: Int): PrayerTime =
        PrayerTime.of(prayer, Time.of(h, m).getOrThrow()).getOrThrow()

    // Schedule content is irrelevant to freshness; any valid schedule suffices.
    fun schedule(on: Date = today): DaySchedule = DaySchedule.of(
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

    // --- Generators -------------------------------------------------------------------------

    /**
     * An instant strictly before [now], between 0 and 4 days earlier. Days-back of 1 makes the
     * age straddle the one-day boundary (depending on the time-of-day), so both "older than one
     * day" and "within one day" cases are generated.
     */
    val fetchedAtArb: Arb<DateTime> = arbitrary {
        val daysBack = Arb.int(0..4).bind()
        // Same month window (June 6..10) so Date.of stays valid.
        val date = Date.of(2024, 6, 10 - daysBack).getOrThrow()
        // For day 0 keep the hour below now's hour so the instant is strictly earlier.
        val hour = if (daysBack == 0) Arb.int(0..22).bind() else Arb.int(0..23).bind()
        val minute = Arb.int(0..59).bind()
        DateTime.of(date, hour, minute, 0).getOrThrow()
    }

    /** The freshness rule, computed independently of the state (design Property 7 / Req 6.4). */
    fun ruleSaysStale(fetchedAt: DateTime, cameFromCacheAfterFailure: Boolean): Boolean {
        val olderThanOneDay = fetchedAt.durationUntil(now).totalSeconds > oneDaySeconds
        return cameFromCacheAfterFailure || olderThanOneDay
    }

    /** Build a [RefreshState] the way the coordinator would, given freshness inputs. */
    fun stateFor(fetchedAt: DateTime, cameFromCacheAfterFailure: Boolean): RefreshState =
        RefreshState(
            schedule = Option.Some(schedule()),
            fetchedAt = Option.Some(fetchedAt),
            isStale = ruleSaysStale(fetchedAt, cameFromCacheAfterFailure),
            nextPrayer = Option.None,
            timeUntilNext = Option.None,
            error = if (cameFromCacheAfterFailure) {
                Option.Some(RefreshError.from(ProviderError.NetworkError("offline")))
            } else {
                Option.None
            },
        )

    // --- (A) Property 7 via the presenter ---------------------------------------------------

    "Property 7 (A): when data is stale (older than a day OR cache-after-failure) the UI exposes the indicator" {
        checkAll(fetchedAtArb, Arb.boolean()) { fetchedAt, cameFromCacheAfterFailure ->
            val state = stateFor(fetchedAt, cameFromCacheAfterFailure)
            val expectedStale = ruleSaysStale(fetchedAt, cameFromCacheAfterFailure)

            val vs = NextPrayerPresenter.present(state)

            // The stale indicator visibility matches the freshness rule exactly.
            vs.isStale shouldBe expectedStale
            // Whenever the rule fires, a "last updated" indicator is also exposed.
            if (expectedStale) {
                vs.isStale.shouldBeTrue()
                vs.lastUpdatedText.shouldNotBeNull()
            }
        }
    }

    "Property 7 (A): a 'last updated' label is exposed whenever there is fetched data to attribute" {
        checkAll(fetchedAtArb, Arb.boolean()) { fetchedAt, cameFromCacheAfterFailure ->
            val state = stateFor(fetchedAt, cameFromCacheAfterFailure)
            // fetchedAt is Some, so the last-updated text must always be present.
            NextPrayerPresenter.present(state).lastUpdatedText.shouldNotBeNull()
        }
    }

    "Property 7 (A): data that came from cache after a failed refresh always shows the indicator" {
        checkAll(fetchedAtArb) { fetchedAt ->
            // cameFromCacheAfterFailure = true forces staleness regardless of age.
            val state = stateFor(fetchedAt, cameFromCacheAfterFailure = true)
            val vs = NextPrayerPresenter.present(state)
            vs.isStale.shouldBeTrue()
            vs.lastUpdatedText.shouldNotBeNull()
        }
    }

    // --- (B) Property 7 end-to-end through the RefreshCoordinator ---------------------------

    // Minimal in-memory fakes mirroring RefreshCoordinatorTest.
    class FakeProvider(private val result: Result<DaySchedule, ProviderError>) : TimesProvider {
        override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> = result
    }

    class SeededRepository(initial: Option<CachedSchedule>, private val clock: Clock) : ScheduleRepository {
        var cached: Option<CachedSchedule> = initial
        override fun save(schedule: DaySchedule): SaveOutcome {
            val cs = CachedSchedule(schedule, clock.now())
            cached = Option.Some(cs)
            return SaveOutcome.Saved(cs)
        }
        override fun getCachedSchedule(): Option<CachedSchedule> = cached
        override fun clear() { cached = Option.None }
    }

    class NoopScheduler : NotificationScheduler {
        override fun reschedule(schedule: DaySchedule, now: DateTime) { /* no-op */ }
        override fun cancelAll() { /* no-op */ }
        override fun setPreferences(prefs: NotificationPreferences) { /* no-op */ }
    }

    "Property 7 (B): cache-first render exposes the indicator exactly when the cached data is older than one day" {
        checkAll(fetchedAtArb) { fetchedAt ->
            val clock = FixedClock(now)
            val repo = SeededRepository(
                initial = Option.Some(CachedSchedule(schedule(), fetchedAt)),
                clock = clock,
            )
            // Provider fails so the coordinator's snapshots are driven purely by the cache.
            val provider = FakeProvider(Result.Err(ProviderError.NetworkError("offline")))
            val states = mutableListOf<RefreshState>()
            val coordinator = RefreshCoordinator(provider, repo, NoopScheduler(), clock) { states.add(it) }

            coordinator.onAppOpened()

            // First published state = cache-first render (no failure known yet): age-based only.
            val ageStale = fetchedAt.durationUntil(now).totalSeconds > oneDaySeconds
            val firstVs = NextPrayerPresenter.present(states.first())
            firstVs.isStale shouldBe ageStale
            if (ageStale) firstVs.lastUpdatedText.shouldNotBeNull()

            // Final state = cache retained after the failed refresh: always stale, indicator shown.
            val finalVs = NextPrayerPresenter.present(coordinator.state)
            finalVs.isStale.shouldBeTrue()
            finalVs.lastUpdatedText.shouldNotBeNull()
        }
    }
})
