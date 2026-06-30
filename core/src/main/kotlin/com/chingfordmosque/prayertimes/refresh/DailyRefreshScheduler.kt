package com.chingfordmosque.prayertimes.refresh

import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date

/**
 * Daily-rollover trigger for the [RefreshCoordinator] (design, Component 5;
 * Requirements 7.2, 7.4).
 *
 * When midnight passes while the app is in use, "today's" schedule becomes yesterday's and
 * must be refreshed for the new day, re-arming notifications accordingly (design Error
 * Scenario 5). This collaborator owns that single responsibility: it watches for a change in
 * the calendar date and, when it sees one, delegates to [RefreshCoordinator.refreshNow] —
 * which already persists the new schedule, recomputes the next prayer, and re-arms
 * notifications.
 *
 * **No busy loop.** The design restricts network refresh triggers to launch, daily rollover,
 * and manual refresh only (Requirement 7.4). Accordingly this class does *not* poll. Instead
 * the host drives it: a lifecycle callback or the same per-second countdown timer the UI
 * already runs calls [tick] on each beat. [tick] is a cheap date comparison and only touches
 * the network on the single tick where the day actually flips, so frequent ticks are safe and
 * cannot cause repeated fetches within the same day.
 *
 * The coordinator's existing public methods are left untouched; this is a thin driver layered
 * on top of it.
 *
 * @param coordinator the coordinator to refresh when the day rolls over.
 * @param clock supplies the current instant; never read from a system clock directly.
 */
class DailyRefreshScheduler(
    private val coordinator: RefreshCoordinator,
    private val clock: Clock,
) {

    /**
     * The calendar date last observed. `null` until tracking has been armed (either explicitly
     * via [scheduleDailyRefresh] or lazily on the first [tick]). A rollover is only ever
     * reported relative to a previously-seen date, so arming never itself triggers a refresh.
     */
    private var lastSeenDate: Date? = null

    /** The date currently being tracked, or `null` if rollover detection has not been armed. */
    val trackedDate: Date? get() = lastSeenDate

    /**
     * Arm rollover detection by recording the current calendar day as the baseline, without
     * triggering a refresh. Typically called right after the launch refresh ([RefreshCoordinator.onAppOpened])
     * so the first day change *after* launch is detected. Re-arming simply re-baselines to the
     * current day.
     */
    fun scheduleDailyRefresh() {
        lastSeenDate = clock.now().date
    }

    /**
     * Lifecycle/timer beat from the host (Requirement 7.4: not a poll). Compares the current
     * calendar day against the last-seen day:
     *
     * - First call before any baseline exists: records the day and does nothing (no refresh).
     * - Same day as last seen: no-op — repeated ticks within a day never re-trigger.
     * - Day changed: updates the tracked day and fires exactly one [RefreshCoordinator.refreshNow]
     *   so the new day's schedule is fetched and notifications are re-armed.
     *
     * @return true if this tick detected a rollover and triggered a refresh; false otherwise.
     */
    fun tick(): Boolean {
        val current = clock.now().date
        val previous = lastSeenDate

        // Lazily arm on the first tick so a host that never called scheduleDailyRefresh()
        // still establishes a baseline without spuriously refreshing.
        if (previous == null) {
            lastSeenDate = current
            return false
        }

        if (current == previous) {
            return false
        }

        // Day rolled over: re-baseline first so a refresh failure (or a re-entrant tick) does
        // not cause the same rollover to fire twice.
        lastSeenDate = current
        coordinator.refreshNow()
        return true
    }
}
