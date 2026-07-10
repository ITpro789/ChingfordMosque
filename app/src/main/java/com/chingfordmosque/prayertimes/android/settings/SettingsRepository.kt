package com.chingfordmosque.prayertimes.android.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chingfordmosque.prayertimes.domain.NotificationPreferences
import com.chingfordmosque.prayertimes.domain.Prayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** How the app chooses between light and dark presentation. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The full set of user-controllable preferences, already projected into domain types where
 * useful. [enabledPrayers] only ever contains alerting prayers (never Sunrise).
 */
data class AppSettings(
    val enabledPrayers: Set<Prayer>,
    val adhanSoundEnabled: Boolean,
    val themeMode: ThemeMode,
    val isLocalAdhan: Boolean = false,
    val iqamahOffset: Int = 15,
    val durationSeconds: Int = 180,
) {
    /** Project these settings into the core [NotificationPreferences] value. */
    fun toNotificationPreferences(): NotificationPreferences =
        NotificationPreferences.of(
            enabledPrayers, 
            adhanSoundEnabled,
            isLocalAdhan,
            iqamahOffset,
            durationSeconds
        )

    companion object {
        /** Defaults: all five alerting prayers on, adhan sound on, follow the system theme. */
        val DEFAULT = AppSettings(
            enabledPrayers = SettingsRepository.ALERTING_PRAYERS.toSet(),
            adhanSoundEnabled = true,
            themeMode = ThemeMode.SYSTEM,
            isLocalAdhan = false,
            iqamahOffset = 15,
            durationSeconds = 180,
        )
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "chingford_settings")

/**
 * Preferences-DataStore backed store for the app's user settings: per-prayer notification
 * toggles (Fajr, Zuhr, Asr, Maghrib, Isha ?" Sunrise never alerts so it is not persisted),
 * the adhan-sound switch, and the theme mode. Exposes reactive [Flow]s plus suspend setters,
 * and a synchronous [readBlocking] used once at process startup.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /** The reactive snapshot of all settings, with defaults applied for any missing key. */
    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    /** Just the theme mode, for the Activity to drive the Compose theme. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs -> prefs.readThemeMode() }

    suspend fun setPrayerEnabled(prayer: Prayer, enabled: Boolean) {
        val key = KEYS[prayer] ?: return
        dataStore.edit { it[key] = enabled }
    }

    suspend fun setAdhanSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[ADHAN_SOUND] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setLocalAdhan(isLocal: Boolean) {
        dataStore.edit { it[LOCAL_ADHAN] = isLocal }
    }

    suspend fun setIqamahOffset(offset: Int) {
        dataStore.edit { it[IQAMAH_OFFSET] = offset }
    }

    suspend fun setDurationSeconds(seconds: Int) {
        dataStore.edit { it[DURATION_SECONDS] = seconds }
    }

    /** Read the current snapshot synchronously (used once from Application.onCreate). */
    fun readBlocking(): AppSettings = runBlocking { settings.first() }

    private fun Preferences.toAppSettings(): AppSettings {
        val enabled = ALERTING_PRAYERS.filter { prayer ->
            val key = KEYS.getValue(prayer)
            this[key] ?: true
        }.toSet()
        return AppSettings(
            enabledPrayers = enabled,
            adhanSoundEnabled = this[ADHAN_SOUND] ?: true,
            themeMode = readThemeMode(),
            isLocalAdhan = this[LOCAL_ADHAN] ?: false,
            iqamahOffset = this[IQAMAH_OFFSET] ?: 15,
            durationSeconds = this[DURATION_SECONDS] ?: 180,
        )
    }

    private fun Preferences.readThemeMode(): ThemeMode {
        val raw = this[THEME_MODE] ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    companion object {
        /** The alerting prayers, in canonical order; the ones we persist a toggle for. */
        val ALERTING_PRAYERS: List<Prayer> =
            listOf(Prayer.Fajr, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha)

        private val KEYS: Map<Prayer, Preferences.Key<Boolean>> = mapOf(
            Prayer.Fajr to booleanPreferencesKey("notify_fajr"),
            Prayer.Zuhr to booleanPreferencesKey("notify_zuhr"),
            Prayer.Asr to booleanPreferencesKey("notify_asr"),
            Prayer.Maghrib to booleanPreferencesKey("notify_maghrib"),
            Prayer.Isha to booleanPreferencesKey("notify_isha"),
        )

        private val ADHAN_SOUND = booleanPreferencesKey("adhan_sound_enabled")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val LOCAL_ADHAN = booleanPreferencesKey("local_adhan")
        private val IQAMAH_OFFSET = intPreferencesKey("iqamah_offset")
        private val DURATION_SECONDS = intPreferencesKey("duration_seconds")
    }
}
