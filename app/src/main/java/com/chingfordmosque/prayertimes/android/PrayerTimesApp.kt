package com.chingfordmosque.prayertimes.android

import android.app.Application
import com.chingfordmosque.prayertimes.android.platform.AlarmManagerAdhanPort
import com.chingfordmosque.prayertimes.android.platform.AndroidNotificationPermission
import com.chingfordmosque.prayertimes.android.platform.AndroidNotificationPermissionPrompt
import com.chingfordmosque.prayertimes.android.platform.OkHttpFetcher
import com.chingfordmosque.prayertimes.android.platform.PrefsLocalStore
import com.chingfordmosque.prayertimes.android.settings.SettingsRepository
import com.chingfordmosque.prayertimes.app.AppContainer
import com.chingfordmosque.prayertimes.domain.SystemClock

/**
 * The Android composition root. Builds the single [AppContainer] for the process, supplying the
 * Android platform bindings for every seam the core defines:
 *
 * - clock            -> [SystemClock] (Europe/London),
 * - store            -> [PrefsLocalStore] (SharedPreferences + org.json),
 * - alarmPort        -> [AlarmManagerAdhanPort] (AlarmManager),
 * - permission       -> [AndroidNotificationPermission] (runtime POST_NOTIFICATIONS),
 * - permissionPrompt -> [AndroidNotificationPermissionPrompt] (Activity-driven request),
 * - httpFetcher      -> [OkHttpFetcher].
 *
 * The high-importance "adhan" notification channel is created on startup so alerts can post
 * immediately. The container (and the prompt holder) are exposed via a global accessor so the
 * [ui.MainActivity]/`PrayerViewModel` and the receivers can reach them.
 */
class PrayerTimesApp : Application() {

    /** Activity-observed holder used to drive the runtime permission request from the UI. */
    val permissionPrompt: AndroidNotificationPermissionPrompt = AndroidNotificationPermissionPrompt()

    /** Persisted user settings (notification toggles, adhan sound, theme). */
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Notifications.createAdhanChannel(this)

        settingsRepository = SettingsRepository(this)

        container = AppContainer(
            clock = SystemClock(),
            store = PrefsLocalStore(this),
            alarmPort = AlarmManagerAdhanPort(this),
            permission = AndroidNotificationPermission(this),
            permissionPrompt = permissionPrompt,
            httpFetcher = OkHttpFetcher(),
        )

        // Apply the user's saved notification preferences before any schedule is armed, so the
        // first reschedule (during onAppOpened) already honours them. A synchronous read here
        // mirrors how PrefsLocalStore loads the cached schedule.
        val saved = settingsRepository.readBlocking()
        container.notificationScheduler.setPreferences(saved.toNotificationPreferences())
    }

    companion object {
        @Volatile
        lateinit var instance: PrayerTimesApp
            private set

        /** Convenience accessor for the process-wide container. */
        fun container(): AppContainer = instance.container
    }
}
