package com.chingfordmosque.prayertimes.ui

/**
 * Platform-free view-state for the "next prayer" panel: the upcoming prayer, its live
 * countdown, the data-freshness ("last updated" / stale) indicator, and any error banner with
 * a retry/manual-refresh affordance (Requirements 4.3, 4.4, 6.4, 8.1, 8.3).
 *
 * Like the other UI view-states in this package, this type carries only already-formatted,
 * platform-agnostic data. The Android binding (deferred) renders it directly and drives the
 * per-second countdown by re-invoking [NextPrayerPresenter] with a fresh "now" — there is no
 * timer or clock here, and re-rendering never triggers a network fetch (Requirement 4.4).
 *
 * @property nextPrayerName the display name of the next alerting prayer (e.g. "Fajr"), or
 *   `null` when none remains and no next-day schedule is known (Requirements 4.1, 4.2, 4.5).
 * @property countdown the time remaining until [nextPrayerName] begins, formatted as
 *   "HH:mm:ss", or `null` when there is no next prayer (Requirement 4.3).
 * @property lastUpdatedText a pre-formatted "last updated…" label derived from when the
 *   displayed schedule was fetched, or `null` when there is no data to attribute a time to
 *   (Requirement 6.4).
 * @property isStale `true` when the displayed data is older than one day or came from the
 *   cache after a failed refresh; the binding renders a stale indicator when set
 *   (Requirement 6.4).
 * @property errorBanner the error banner to show when the latest refresh failed, or `null`
 *   when the last refresh succeeded / none has run (Requirements 8.1, 8.2, 8.3).
 * @property showManualRefresh whether to expose the manual-refresh control; always available
 *   so the user can refresh on demand (Requirement 7.3).
 */
data class NextPrayerViewState(
    val nextPrayerName: String?,
    val countdown: String?,
    val lastUpdatedText: String?,
    val isStale: Boolean,
    val errorBanner: ErrorBannerViewState?,
    val showManualRefresh: Boolean,
    /** The current (Active) or upcoming prayer name driving the countdown ring, or `null`. */
    val ringPrayerName: String? = null,
    /** `true` when a prayer is currently active (its window is in progress). */
    val ringIsActive: Boolean = false,
    /** Caption for the ring, e.g. "Maghrib ends in" or "Zuhr begins in". */
    val ringCaption: String? = null,
    /** "HH:mm:ss" until the window ends (active) or begins (upcoming). */
    val ringCountdown: String? = null,
    /** Fraction (0f..1f) of the current/upcoming window that has elapsed. */
    val ringProgress: Float = 0f,
)

/**
 * Platform-free view-state for the network/parse error banner shown after a failed refresh
 * (Requirements 8.1, 8.2, 8.3).
 *
 * The banner distinguishes the failure [kind] so the binding can tailor copy/iconography while
 * keeping the message itself pre-formatted here. Every failure is retryable, so [showRetry] is
 * the retry affordance the user taps to trigger another refresh attempt (Requirement 8.3).
 *
 * @property kind the failure category (network, parse, or incomplete data).
 * @property message a human-readable, already-formatted message safe to display.
 * @property showRetry whether to render a retry control (always `true` — every fetch error
 *   offers a retry per Requirement 8.3).
 */
data class ErrorBannerViewState(
    val kind: ErrorKind,
    val message: String,
    val showRetry: Boolean,
) {
    /** The user-facing category of a refresh failure. */
    enum class ErrorKind { Network, Parse, Incomplete }
}
