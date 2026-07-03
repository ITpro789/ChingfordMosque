package com.chingfordmosque.prayertimes.app

import com.chingfordmosque.prayertimes.data.provider.HttpFetcher
import com.chingfordmosque.prayertimes.data.provider.HttpTimesProvider
import com.chingfordmosque.prayertimes.data.provider.JvmHttpFetcher
import com.chingfordmosque.prayertimes.data.provider.TimesProvider
import com.chingfordmosque.prayertimes.data.repository.InMemoryLocalStore
import com.chingfordmosque.prayertimes.data.repository.LocalScheduleRepository
import com.chingfordmosque.prayertimes.data.repository.LocalStore
import com.chingfordmosque.prayertimes.data.repository.ScheduleRepository
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.SystemClock
import com.chingfordmosque.prayertimes.notify.AdhanAlarmPort
import com.chingfordmosque.prayertimes.notify.AdhanNotificationScheduler
import com.chingfordmosque.prayertimes.notify.InMemoryAdhanAlarmPort
import com.chingfordmosque.prayertimes.notify.InMemoryNotificationPermission
import com.chingfordmosque.prayertimes.notify.NotificationPermission
import com.chingfordmosque.prayertimes.notify.NotificationPermissionPrompt
import com.chingfordmosque.prayertimes.notify.NotificationScheduler
import com.chingfordmosque.prayertimes.notify.PermissionGatedNotificationScheduler
import com.chingfordmosque.prayertimes.notify.RecordingNotificationPermissionPrompt
import com.chingfordmosque.prayertimes.refresh.DailyRefreshScheduler
import com.chingfordmosque.prayertimes.refresh.RefreshCoordinator
import com.chingfordmosque.prayertimes.refresh.RefreshState
import com.chingfordmosque.prayertimes.ui.DayScheduleViewState
import com.chingfordmosque.prayertimes.ui.DaySchedulePresenter
import com.chingfordmosque.prayertimes.ui.JummahSectionPresenter
import com.chingfordmosque.prayertimes.ui.JummahSectionViewState
import com.chingfordmosque.prayertimes.ui.NextPrayerPresenter
import com.chingfordmosque.prayertimes.ui.NextPrayerViewState

/**
 * The application composition root (design, Component 5 wiring; task 10.1).
 *
 * This is the single place that constructs and connects the whole pipeline —
 * Times Provider -> Schedule Repository -> Schedule Service -> Notification Scheduler -> UI —
 * through the [RefreshCoordinator], using the pure-Kotlin/JVM default bindings. It owns no
 * logic of its own beyond wiring: every behaviour (cache-first render, graceful degradation,
 * daily rollover, presentation) lives in the components it composes, so this class stays a
 * thin, declarative graph that is trivial to re-point for another platform.
 *
 * **Default JVM bindings** (each overridable for tests or an Android `Application`):
 * - [clock] -> [SystemClock] (real wall-clock in the mosque's timezone),
 * - persistence -> [InMemoryLocalStore] behind [LocalScheduleRepository],
 * - notifications -> [AdhanNotificationScheduler] over an [InMemoryAdhanAlarmPort], wrapped by
 *   [PermissionGatedNotificationScheduler] (permission granted by default on the JVM),
 * - source -> [HttpTimesProvider] over a [JvmHttpFetcher] hitting [JvmHttpFetcher.MOSQUE_URL].
 *
 * The Android-level bindings (OkHttp fetcher, DataStore-backed [LocalStore], AlarmManager
 * [AdhanAlarmPort], runtime [NotificationPermission]/prompt) are intentionally deferred; an
 * Android build supplies them as constructor arguments without touching any component.
 *
 * **Entry points** mirror the app's real triggers (Requirements 6.2, 7.1, 7.2, 7.3):
 * - [onAppOpened] — launch: render the cache first, then refresh, and arm rollover tracking,
 * - [refreshNow] — manual/pull-to-refresh,
 * - [tick] — a lifecycle/timer beat that triggers a refresh exactly when the day rolls over,
 * and the view-state accessors ([todayScheduleViewState], [jummahSectionViewState],
 * [nextPrayerViewState]) project the coordinator's current [state] for the UI.
 *
 * @param clock source of "now"; defaults to the real [SystemClock].
 * @param store local persistence seam; defaults to the in-memory JVM store.
 * @param alarmPort adhan-alert seam; defaults to the in-memory JVM port.
 * @param permission notification-permission seam; defaults to granted on the JVM so alerts arm.
 * @param permissionPrompt seam used to ask the user to enable notifications when not granted.
 * @param preferences initial notification preferences; defaults to all alerting prayers + sound.
 * @param httpFetcher HTTP seam used by the default provider; defaults to the JVM fetcher.
 * @param url the page the default provider fetches; defaults to the mosque homepage.
 * @param timesProvider the source boundary; defaults to an [HttpTimesProvider] over [httpFetcher].
 * @param fallbackProvider optional on-device fallback (e.g. a calculated provider) used only
 *   when the primary fetch fails and the cache is empty; defaults to null. The Android build
 *   passes a calculated provider here.
 * @param onStateChange optional listener invoked with each new [RefreshState] the coordinator
 *   publishes, so a host can observe updates without polling [state].
 */
class AppContainer(
    val clock: Clock = SystemClock(),
    store: LocalStore = InMemoryLocalStore(),
    alarmPort: AdhanAlarmPort = InMemoryAdhanAlarmPort(),
    permission: NotificationPermission = InMemoryNotificationPermission(granted = true),
    permissionPrompt: NotificationPermissionPrompt = RecordingNotificationPermissionPrompt(),
    preferences: NotificationPreferences = NotificationPreferences.default(),
    httpFetcher: HttpFetcher = JvmHttpFetcher(),
    url: String = JvmHttpFetcher.MOSQUE_URL,
    val timesProvider: TimesProvider = HttpTimesProvider(fetcher = httpFetcher, clock = clock, url = url),
    fallbackProvider: TimesProvider? = null,
    onStateChange: ((RefreshState) -> Unit)? = null,
) {

    /** Single source of truth for last-known-good data the UI renders from (cache-safe). */
    val repository: ScheduleRepository = LocalScheduleRepository(store, clock)

    /**
     * Notification path: the pure scheduling logic ([AdhanNotificationScheduler]) decorated
     * with the permission gate so alerts are only armed when permission is granted, and the
     * user is prompted (once) otherwise — leaving times/countdown unaffected (Requirement 5.7).
     */
    val notificationScheduler: NotificationScheduler =
        PermissionGatedNotificationScheduler(
            delegate = AdhanNotificationScheduler(alarmPort, preferences),
            permission = permission,
            prompt = permissionPrompt,
        )

    /**
     * The orchestrator wiring provider -> repository -> service -> scheduler -> UI state.
     * Cache-first render and graceful degradation live here; this container only drives it.
     */
    val refreshCoordinator: RefreshCoordinator =
        RefreshCoordinator(
            timesProvider = timesProvider,
            repository = repository,
            notificationScheduler = notificationScheduler,
            clock = clock,
            fallbackProvider = fallbackProvider,
            onStateChange = onStateChange,
        )

    /** Daily-rollover driver layered on the coordinator (Requirements 7.2, 7.4). */
    val dailyRefreshScheduler: DailyRefreshScheduler =
        DailyRefreshScheduler(refreshCoordinator, clock)

    /** The latest UI-facing snapshot produced by the coordinator. */
    val state: RefreshState get() = refreshCoordinator.state

    /**
     * App-launch entry point (Requirements 6.2, 7.1): render the cached schedule immediately,
     * attempt a refresh, and arm daily-rollover tracking from the current day so a subsequent
     * day change is detected by [tick].
     */
    fun onAppOpened() {
        refreshCoordinator.onAppOpened()
        dailyRefreshScheduler.scheduleDailyRefresh()
    }

    /**
     * Manual/pull-to-refresh entry point (Requirement 7.3): fetch the latest schedule, updating
     * the cache, next prayer, notifications, and [state] on success; preserving the cache and
     * surfacing an error with retry on failure.
     */
    fun refreshNow(): Result<DaySchedule, ProviderError> = refreshCoordinator.refreshNow()

    /**
     * Lifecycle/timer beat (Requirement 7.2, 7.4): refresh exactly once when the calendar day
     * rolls over while in use; a no-op otherwise. Returns true when a rollover triggered a
     * refresh.
     */
    fun tick(): Boolean = dailyRefreshScheduler.tick()

    /**
     * Today's salah-times view-state for the current [state], or `null` when nothing has been
     * loaded yet (no cache and no successful fetch).
     */
    fun todayScheduleViewState(): DayScheduleViewState? =
        state.schedule.getOrNull()?.let { DaySchedulePresenter.present(it) }

    /**
     * The Jummah section view-state for the current [state]: [JummahSectionViewState.Visible]
     * with ascending times when present, otherwise [JummahSectionViewState.Hidden] (also when
     * there is no schedule at all).
     */
    fun jummahSectionViewState(now: DateTime = clock.now()): JummahSectionViewState =
        state.schedule.fold(
            onSome = { JummahSectionPresenter.present(it, now) },
            onNone = { JummahSectionViewState.Hidden },
        )

    /** The next-prayer / countdown / freshness / error view-state for the current [state]. */
    fun nextPrayerViewState(): NextPrayerViewState = NextPrayerPresenter.present(state)

    /**
     * The next-prayer view-state recomputed relative to [now]; this is the pure per-second
     * "tick" the UI uses to advance the countdown without re-fetching (Requirement 4.4).
     */
    fun nextPrayerViewState(now: DateTime): NextPrayerViewState =
        NextPrayerPresenter.present(state, now)
}
