package com.chingfordmosque.prayertimes.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chingfordmosque.prayertimes.android.PrayerTimesApp
import com.chingfordmosque.prayertimes.android.settings.AppSettings
import com.chingfordmosque.prayertimes.android.settings.SettingsRepository
import com.chingfordmosque.prayertimes.android.settings.ThemeMode
import com.chingfordmosque.prayertimes.app.AppContainer
import com.chingfordmosque.prayertimes.domain.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs the Settings screen. Reads/writes via [SettingsRepository] and, after every change,
 * re-applies the resulting [com.chingfordmosque.prayertimes.domain.NotificationPreferences] to
 * the core [AppContainer.notificationScheduler] and re-arms alarms from the cached schedule —
 * without triggering any network call.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PrayerTimesApp
    private val repository: SettingsRepository = app.settingsRepository
    private val container: AppContainer = app.container

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppSettings.DEFAULT,
        )

    fun setPrayerEnabled(prayer: Prayer, enabled: Boolean) {
        viewModelScope.launch {
            repository.setPrayerEnabled(prayer, enabled)
            applyNotificationSettings()
        }
    }

    fun setAdhanSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAdhanSoundEnabled(enabled)
            applyNotificationSettings()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    /**
     * Push the latest persisted preferences into the scheduler and re-arm alarms from the
     * cached schedule, if one is loaded. No network fetch is performed.
     */
    private suspend fun applyNotificationSettings() {
        val current = repository.settings.first()
        val prefs = current.toNotificationPreferences()
        container.notificationScheduler.setPreferences(prefs)
        val schedule = container.state.schedule.getOrNull() ?: return
        withContext(Dispatchers.IO) {
            container.notificationScheduler.reschedule(schedule, container.clock.now())
        }
    }
}
