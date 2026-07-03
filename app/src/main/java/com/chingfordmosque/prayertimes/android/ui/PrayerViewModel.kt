package com.chingfordmosque.prayertimes.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chingfordmosque.prayertimes.android.PrayerTimesApp
import com.chingfordmosque.prayertimes.app.AppContainer
import com.chingfordmosque.prayertimes.ui.DayScheduleViewState
import com.chingfordmosque.prayertimes.ui.JummahSectionViewState
import com.chingfordmosque.prayertimes.ui.NextPrayerViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single, already-formatted snapshot the Compose screen renders.
 *
 * @property today today's salah-times view-state, or `null` when nothing has loaded yet.
 * @property jummah the Friday section state (Visible/Hidden).
 * @property next the next-prayer / countdown / freshness / error view-state.
 * @property isRefreshing whether a manual/automatic refresh is currently in flight.
 */
data class PrayerUiState(
    val today: DayScheduleViewState?,
    val jummah: JummahSectionViewState,
    val next: NextPrayerViewState,
    val isRefreshing: Boolean,
)

/**
 * Bridges the platform-free [AppContainer] to Compose.
 *
 * On creation it renders the cached schedule immediately (off the main thread) and triggers a
 * refresh via [AppContainer.onAppOpened]. A one-second ticker republishes only the recomputed
 * next-prayer view-state ([AppContainer.nextPrayerViewState] for the current "now") so the
 * countdown advances live WITHOUT re-fetching. [refresh] performs a manual refresh, and
 * [onAppResumed] drives the daily-rollover [AppContainer.tick].
 */
class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = (application as PrayerTimesApp).container

    private val _state = MutableStateFlow(snapshot(isRefreshing = false))
    val state: StateFlow<PrayerUiState> = _state.asStateFlow()

    init {
        // Render whatever is cached, then refresh from the network — both off the main thread.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.onAppOpened() }
            _state.value = snapshot(isRefreshing = false)
        }
        startCountdownTicker()
    }

    /** Manual / pull-to-refresh entry point. */
    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.refreshNow() }
            _state.value = snapshot(isRefreshing = false)
        }
    }

    /** Called from the Activity's onResume: advance the day-rollover tracker and republish. */
    fun onAppResumed() {
        viewModelScope.launch {
            val rolledOver = withContext(Dispatchers.IO) { container.tick() }
            // Always republish so the countdown reflects the latest "now"; a rollover will also
            // have refreshed the underlying schedule.
            _state.value = snapshot(isRefreshing = _state.value.isRefreshing)
            if (rolledOver) {
                _state.value = snapshot(isRefreshing = false)
            }
        }
    }

    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                // Recompute ONLY the next-prayer/countdown for the current instant; no fetch.
                val current = _state.value
                _state.value = current.copy(
                    next = container.nextPrayerViewState(container.clock.now()),
                )
            }
        }
    }

    /** Build a full snapshot from the container's current state. */
    private fun snapshot(isRefreshing: Boolean): PrayerUiState =
        PrayerUiState(
            today = container.todayScheduleViewState(),
            jummah = container.jummahSectionViewState(container.clock.now()),
            next = container.nextPrayerViewState(container.clock.now()),
            isRefreshing = isRefreshing,
        )
}
