package com.chingfordmosque.prayertimes.refresh

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Duration
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError

/**
 * The UI-facing snapshot produced by the [RefreshCoordinator] (design, Component 5).
 *
 * This is the single, platform-free value the UI renders from: it carries the schedule to
 * show (whether it came from the cache or a fresh fetch), its freshness, the computed next
 * prayer + countdown, and any error from the most recent refresh attempt. It contains no
 * Android, coroutine, or I/O concerns — an Android binding can expose it through a StateFlow
 * or LiveData without this layer knowing.
 *
 * Cache-first rendering (Requirement 6.2) and graceful degradation (Requirements 6.3, 8.1,
 * 8.2, 8.3) are expressed purely through this state: a failed refresh leaves [schedule] and
 * [fetchedAt] pointing at the cached data while [error] becomes [Option.Some] and [isStale]
 * becomes true.
 *
 * @property schedule the schedule currently being displayed, or [Option.None] when nothing has
 *   been loaded yet (no cache and no successful fetch).
 * @property fetchedAt when the displayed [schedule] was retrieved; drives the "last updated…"
 *   label (Requirement 6.4). [Option.None] when there is no data.
 * @property isStale true when the displayed data is older than one day, or came from the cache
 *   after a failed refresh (Requirement 6.4). The UI shows a stale indicator when this is set.
 * @property nextPrayer the next alerting prayer relative to "now", excluding Sunrise
 *   (Requirements 4.1, 4.2), or [Option.None] when none remain and no next-day data is known.
 * @property timeUntilNext the countdown until [nextPrayer] begins (Requirement 4.3).
 * @property error the failure from the latest refresh attempt, or [Option.None] when the last
 *   refresh succeeded (or none has run). When present the UI surfaces an error banner with a
 *   retry affordance (Requirement 8.3) — retry being a call to [RefreshCoordinator.refreshNow].
 * @property isCalculated true when the displayed schedule came from the on-device astronomical
 *   fallback rather than the mosque website. It is only ever set when a website fetch failed
 *   and there was no cached real schedule to prefer; real (scraped/cached) data always wins, so
 *   this defaults to false.
 */
data class RefreshState(
    val schedule: Option<DaySchedule>,
    val fetchedAt: Option<DateTime>,
    val isStale: Boolean,
    val nextPrayer: Option<PrayerTime>,
    val timeUntilNext: Option<Duration>,
    val error: Option<RefreshError>,
    val isCalculated: Boolean = false,
) {

    /** True when there is a schedule to display (from cache or a fresh fetch). */
    val hasData: Boolean get() = schedule.isSome

    /** True when the latest refresh failed and the UI should offer a retry control. */
    val canRetry: Boolean get() = error.isSome

    companion object {
        /** The initial state before anything has been loaded. */
        val EMPTY = RefreshState(
            schedule = Option.None,
            fetchedAt = Option.None,
            isStale = false,
            nextPrayer = Option.None,
            timeUntilNext = Option.None,
            error = Option.None,
        )
    }
}

/**
 * A refresh failure projected into UI-facing categories so the view can tailor its messaging
 * (network vs. parse vs. incomplete) while the underlying [ProviderError] stays available for
 * diagnostics. Every category is retryable: the user can always trigger a manual refresh
 * (Requirement 8.3).
 */
sealed class RefreshError {

    /** The original typed provider failure, kept for logging/diagnostics. */
    abstract val providerError: ProviderError

    /** Human-readable detail for diagnostics; not shown verbatim to users. */
    val detail: String get() = providerError.detail

    /** Every fetch error offers the user a way to retry (Requirement 8.3). */
    val canRetry: Boolean get() = true

    /** The site was unreachable or timed out (Requirement 8.1). */
    data class Network(override val providerError: ProviderError) : RefreshError()

    /** The markup/format was not as expected (Requirement 8.2). */
    data class Parse(override val providerError: ProviderError) : RefreshError()

    /** A required salah was missing or the parsed schedule failed validation. */
    data class Incomplete(override val providerError: ProviderError) : RefreshError()

    companion object {
        /** Project a typed [ProviderError] into its UI-facing [RefreshError] category. */
        fun from(error: ProviderError): RefreshError = when (error) {
            is ProviderError.NetworkError -> Network(error)
            is ProviderError.ParseError -> Parse(error)
            is ProviderError.IncompleteData -> Incomplete(error)
        }
    }
}
