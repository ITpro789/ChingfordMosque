package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.NotificationPreferences

/**
 * Default [NotificationScheduler] implementation. It contains the pure scheduling *logic* and
 * delegates every side effect to the injected [AdhanAlarmPort] seam, so it compiles and is
 * fully testable on the JVM with [InMemoryAdhanAlarmPort]; the deferred Android binding simply
 * provides a real [AdhanAlarmPort] without any change here (design, Component 4).
 *
 * @param port the platform seam alerts are armed/cancelled through.
 * @param initialPreferences starting user preferences; defaults to every alerting prayer
 *   enabled with adhan sound on ([NotificationPreferences.default]).
 */
class AdhanNotificationScheduler(
    private val port: AdhanAlarmPort,
    initialPreferences: NotificationPreferences = NotificationPreferences.default(),
) : NotificationScheduler {

    private var preferences: NotificationPreferences = initialPreferences

    override fun reschedule(schedule: DaySchedule, now: DateTime) {
        // Cancel everything first, then re-arm, so a refresh can never leave a stale or
        // duplicate alert behind (Requirement 5.5; design Property 5). Combined with the
        // deterministic per-(prayer, date) id, this yields at most one pending alert per prayer.
        port.cancelAll()

        val playSound = preferences.playAdhanSound

        schedule.prayers
            // Sunrise is informational only and must never alert (Requirement 5.6).
            .filter { it.prayer.isAlerting }
            // Honour per-prayer enable/disable (Requirement 5.4).
            .filter { preferences.isEnabled(it.prayer) }
            .forEach { prayerTime ->
                val firesAt = DateTime.of(schedule.scheduleDate, prayerTime.beginsAt)
                // Only arm prayers that are still upcoming relative to now (Requirement 5.1):
                // a begin instant at or before now has already passed and is not re-armed.
                if (firesAt > now) {
                    port.schedule(
                        ScheduledAdhanAlert(
                            id = AlertId.of(prayerTime.prayer, schedule.scheduleDate),
                            prayer = prayerTime.prayer,
                            date = schedule.scheduleDate,
                            firesAt = firesAt,
                            // The payload identifies the prayer (Requirement 5.2) and whether to
                            // play adhan audio (Requirement 5.3); rendering/audio is the binding's job.
                            playAdhanSound = playSound,
                        ),
                    )
                }
            }
    }

    override fun cancelAll() {
        port.cancelAll()
    }

    override fun setPreferences(prefs: NotificationPreferences) {
        // NotificationPreferences.of already drops Sunrise, so an enabled set can never alert it.
        preferences = prefs
    }
}
