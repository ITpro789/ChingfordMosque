package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.refresh.RefreshError
import com.chingfordmosque.prayertimes.refresh.RefreshState
import com.chingfordmosque.prayertimes.service.ScheduleService

/**
 * Pure mapper from the coordinator's [RefreshState] to a platform-free [NextPrayerViewState]
 * (task 9.3): the next prayer + live countdown (Requirements 4.3, 4.4), the freshness / "last
 * updated" indicator (Requirement 6.4), and the error banner with a retry/manual-refresh
 * affordance (Requirements 8.1, 8.2, 8.3).
 *
 * It contains no I/O, no Android dependency, and — crucially — no clock or timer. The
 * per-second countdown is realised by the binding re-invoking [present] repeatedly:
 * - [present]`(state)` renders the countdown the coordinator already computed
 *   ([RefreshState.timeUntilNext]); or
 * - [present]`(state, now)` recomputes the next prayer and countdown from the displayed
 *   schedule for the supplied "now" (the "tick"), so a real per-second timer can drive a live
 *   countdown without ever re-fetching from the network (Requirement 4.4).
 *
 * All formatting is deterministic so the view-state is fully testable.
 */
object NextPrayerPresenter {

    /**
     * Map [state] to a [NextPrayerViewState] using the next-prayer / countdown the coordinator
     * already carries in the state.
     */
    fun present(state: RefreshState): NextPrayerViewState =
        build(
            state = state,
            nextPrayerName = state.nextPrayer.map { it.prayer.name }.getOrNull(),
            countdown = state.timeUntilNext.map { it.toString() }.getOrNull(),
        )

    /**
     * Map [state] to a [NextPrayerViewState], recomputing the next prayer and countdown from
     * the displayed schedule relative to [now]. This is the pure "per-second tick": call it
     * again with an advanced [now] to obtain an updated countdown without any fetch.
     *
     * When the state has no schedule, this behaves like [present]`(state)` (there is nothing to
     * recompute against). Freshness and error fields are taken from [state] unchanged.
     */
    fun present(state: RefreshState, now: DateTime): NextPrayerViewState {
        val schedule = state.schedule.getOrNull()
            ?: return present(state)

        val next = ScheduleService.getNextPrayer(schedule, now)
        val remaining = ScheduleService.timeUntilNext(schedule, now)
        return build(
            state = state,
            nextPrayerName = next.map { it.prayer.name }.getOrNull(),
            countdown = remaining.map { it.toString() }.getOrNull(),
        )
    }

    /** Assemble the view-state, sharing the freshness/error/refresh mapping across both entry points. */
    private fun build(
        state: RefreshState,
        nextPrayerName: String?,
        countdown: String?,
    ): NextPrayerViewState =
        NextPrayerViewState(
            nextPrayerName = nextPrayerName,
            countdown = countdown,
            lastUpdatedText = lastUpdatedText(state.fetchedAt),
            isStale = state.isStale,
            errorBanner = state.error.map { toBanner(it) }.getOrNull(),
            // The manual refresh control is always available (Requirement 7.3).
            showManualRefresh = true,
        )

    /**
     * A deterministic, absolute "last updated…" label derived from when the displayed schedule
     * was fetched, or `null` when there is no data to attribute (Requirement 6.4). Uses the
     * canonical "yyyy-MM-dd HH:mm" rendering so tests are stable.
     */
    fun lastUpdatedText(fetchedAt: Option<DateTime>): String? =
        fetchedAt.map { "Last updated ${formatFetchedAt(it)}" }.getOrNull()

    /** Format a [DateTime] for display as "yyyy-MM-dd HH:mm" (drops seconds; deterministic). */
    fun formatFetchedAt(at: DateTime): String =
        at.date.toString() + " " +
            at.hour.toString().padStart(2, '0') + ":" +
            at.minute.toString().padStart(2, '0')

    /** Project a [RefreshError] into its user-facing [ErrorBannerViewState]. */
    private fun toBanner(error: RefreshError): ErrorBannerViewState = when (error) {
        is RefreshError.Network -> ErrorBannerViewState(
            kind = ErrorBannerViewState.ErrorKind.Network,
            message = "Couldn't reach the mosque website. Showing the last saved times.",
            showRetry = error.canRetry,
        )
        is RefreshError.Parse -> ErrorBannerViewState(
            kind = ErrorBannerViewState.ErrorKind.Parse,
            message = "Couldn't read the latest times. Showing the last saved times.",
            showRetry = error.canRetry,
        )
        is RefreshError.Incomplete -> ErrorBannerViewState(
            kind = ErrorBannerViewState.ErrorKind.Incomplete,
            message = "The latest times were incomplete. Showing the last saved times.",
            showRetry = error.canRetry,
        )
    }
}
