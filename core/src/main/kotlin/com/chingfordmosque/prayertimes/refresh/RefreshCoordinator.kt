package com.chingfordmosque.prayertimes.refresh

import com.chingfordmosque.prayertimes.data.provider.TimesProvider
import com.chingfordmosque.prayertimes.data.repository.SaveOutcome
import com.chingfordmosque.prayertimes.data.repository.ScheduleRepository
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.notify.NotificationScheduler
import com.chingfordmosque.prayertimes.service.ScheduleService

/**
 * Orchestrates loading and refreshing the schedule, wiring
 * provider -> repository -> service -> scheduler -> UI (design, Component 5).
 *
 * The coordinator is deliberately platform-free and synchronous: it reads "now" through an
 * injected [Clock] and exposes its result as a plain [RefreshState] value. There are no
 * coroutines or Android types here, so an Android binding can call these methods from a
 * background dispatcher and republish [state] through a StateFlow/LiveData without this layer
 * changing. State changes are also pushed to an optional [onStateChange] listener so an
 * observer can react without polling.
 *
 * Two behaviours anchor the design:
 * - **Cache-first render** ([onAppOpened]): the cached schedule is shown immediately, with its
 *   freshness, *before* a network fetch is attempted (Requirements 6.2, 7.1).
 * - **Graceful degradation** ([refreshNow]): a failed fetch never disturbs the cached data; it
 *   only adds an error + stale indicator with a retry affordance (Requirements 6.3, 8.1, 8.2,
 *   8.3). A successful fetch persists the schedule, recomputes the next prayer, re-arms
 *   notifications, and publishes a fresh state (Requirement 7.3).
 *
 * Daily-rollover scheduling (`scheduleDailyRefresh`, Requirement 7.2) is intentionally not part
 * of this class — it is implemented separately (task 8.2).
 *
 * @param timesProvider the source boundary used to fetch today's validated schedule.
 * @param repository the cache that holds last-known-good data.
 * @param notificationScheduler re-armed with the latest schedule on every successful fetch.
 * @param clock supplies the current instant; never read from a system clock directly.
 * @param onStateChange optional listener invoked with each new [state] as it is published.
 */
class RefreshCoordinator(
    private val timesProvider: TimesProvider,
    private val repository: ScheduleRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clock: Clock,
    private val onStateChange: ((RefreshState) -> Unit)? = null,
) {

    private var _state: RefreshState = RefreshState.EMPTY

    /** The latest UI-facing snapshot. Updated by [onAppOpened] and [refreshNow]. */
    val state: RefreshState get() = _state

    /**
     * App launch flow (Requirements 6.2, 7.1): render whatever is cached *first* (with its
     * freshness state), then attempt a refresh. Rendering happens even when the cache is empty
     * (an empty state is published) so the UI always has a defined snapshot before the network
     * is touched.
     */
    fun onAppOpened() {
        renderCachedSchedule()
        refreshNow()
    }

    /**
     * Fetch the latest schedule and update everything on success; degrade gracefully on
     * failure (Requirements 7.3, 6.3, 8.1, 8.2, 8.3).
     *
     * On success: persist via [ScheduleRepository.saveFetchResult], recompute the next prayer,
     * re-arm notifications via [NotificationScheduler.reschedule], and publish a fresh state
     * with no error. On failure: leave the cache untouched and publish a state that keeps the
     * cached schedule but carries a typed [RefreshError] and a stale indicator.
     *
     * @return the raw [Result] from the provider, so callers (e.g. a pull-to-refresh handler)
     *   can observe the outcome directly in addition to the published [state].
     */
    fun refreshNow(): Result<DaySchedule, ProviderError> {
        val result = timesProvider.fetchTodaySchedule()
        val now = clock.now()

        return when (result) {
            is Result.Ok -> {
                val schedule = result.value

                // Persist the fresh schedule. It is already validated by the provider, so this
                // is expected to be Saved; fall back to the clock's "now" if not.
                val outcome = repository.saveFetchResult(result)
                val fetchedAt = when (outcome) {
                    is SaveOutcome.Saved -> outcome.cached.fetchedAt
                    else -> now
                }

                // Re-arm adhan notifications for the new schedule (cancel + re-add inside).
                notificationScheduler.reschedule(schedule, now)

                publish(
                    stateFor(
                        schedule = Option.Some(schedule),
                        fetchedAt = Option.Some(fetchedAt),
                        now = now,
                        cameFromCacheAfterFailure = false,
                        error = Option.None,
                    ),
                )
                result
            }

            is Result.Err -> {
                // Failed fetch: keep the cached data exactly as-is (cache safety) and surface an
                // error with a retry affordance. Notifications are left armed against the cache.
                val cached = repository.getCachedSchedule()
                publish(
                    stateFor(
                        schedule = cached.map { it.schedule },
                        fetchedAt = cached.map { it.fetchedAt },
                        now = now,
                        cameFromCacheAfterFailure = cached.isSome,
                        error = Option.Some(RefreshError.from(result.error)),
                    ),
                )
                result
            }
        }
    }

    /**
     * Publish the cached schedule (if any) with its freshness state, without touching the
     * network. Freshness here is purely age-based; a prior refresh failure is not yet known.
     */
    private fun renderCachedSchedule() {
        val now = clock.now()
        when (val cached = repository.getCachedSchedule()) {
            is Option.Some -> publish(
                stateFor(
                    schedule = Option.Some(cached.value.schedule),
                    fetchedAt = Option.Some(cached.value.fetchedAt),
                    now = now,
                    cameFromCacheAfterFailure = false,
                    error = Option.None,
                ),
            )

            is Option.None -> publish(RefreshState.EMPTY)
        }
    }

    /**
     * Assemble a [RefreshState] for a given schedule/freshness, computing the next prayer and
     * countdown via [ScheduleService] and resolving the stale flag (Requirement 6.4):
     * stale when the data came from cache after a failed refresh, or is older than one day.
     */
    private fun stateFor(
        schedule: Option<DaySchedule>,
        fetchedAt: Option<DateTime>,
        now: DateTime,
        cameFromCacheAfterFailure: Boolean,
        error: Option<RefreshError>,
    ): RefreshState {
        val nextPrayer = schedule.flatMap { ScheduleService.getNextPrayer(it, now) }
        val timeUntilNext = schedule.flatMap { ScheduleService.timeUntilNext(it, now) }
        val staleByAge = fetchedAt.fold(
            onSome = { isOlderThanOneDay(it, now) },
            onNone = { false },
        )
        return RefreshState(
            schedule = schedule,
            fetchedAt = fetchedAt,
            isStale = cameFromCacheAfterFailure || staleByAge,
            nextPrayer = nextPrayer,
            timeUntilNext = timeUntilNext,
            error = error,
        )
    }

    private fun publish(newState: RefreshState) {
        _state = newState
        onStateChange?.invoke(newState)
    }

    private fun isOlderThanOneDay(fetchedAt: DateTime, now: DateTime): Boolean =
        fetchedAt.durationUntil(now).totalSeconds > ONE_DAY_SECONDS

    private companion object {
        const val ONE_DAY_SECONDS = 86_400L
    }
}
