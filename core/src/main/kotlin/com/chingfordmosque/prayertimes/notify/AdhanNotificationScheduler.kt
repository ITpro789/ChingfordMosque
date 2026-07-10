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
        val isFriday = schedule.scheduleDate.isFriday()
        val jummahOpt = schedule.jummah

        if (isFriday && jummahOpt is com.chingfordmosque.prayertimes.domain.Option.Some) {
            val jummah = jummahOpt.value
            // Schedule non-Zuhr alerting prayers
            schedule.prayers
                .filter { it.prayer.isAlerting && it.prayer != com.chingfordmosque.prayertimes.domain.Prayer.Zuhr }
                .filter { preferences.isEnabled(it.prayer) }
                .forEach { prayerTime ->
                    val adhanTime = calculateAzaanTime(prayerTime)
                    val firesAt = DateTime.of(schedule.scheduleDate, adhanTime)
                    if (firesAt > now) {
                        port.schedule(
                            ScheduledAdhanAlert(
                                id = AlertId.of(prayerTime.prayer, schedule.scheduleDate),
                                prayer = prayerTime.prayer,
                                date = schedule.scheduleDate,
                                firesAt = firesAt,
                                playAdhanSound = playSound,
                                playDua = preferences.playDua,
                            )
                        )
                    }
                }

            // Schedule Jummah times in place of Zuhr
            if (preferences.isEnabled(com.chingfordmosque.prayertimes.domain.Prayer.Zuhr)) {
                jummah.jamaahTimes.forEachIndexed { index, time ->
                    val label = if (jummah.jamaahTimes.size == 1) "Jummah" else "Jummah ${index + 1}"
                    val firesAt = DateTime.of(schedule.scheduleDate, time)
                    if (firesAt > now) {
                        port.schedule(
                            ScheduledAdhanAlert(
                                id = AlertId.ofCustom(label, schedule.scheduleDate),
                                prayer = com.chingfordmosque.prayertimes.domain.Prayer.Zuhr,
                                date = schedule.scheduleDate,
                                firesAt = firesAt,
                                playAdhanSound = playSound,
                                playDua = preferences.playDua,
                                label = label,
                            )
                        )
                    }
                }
            }
        } else {
            // Standard scheduling for non-Fridays
            schedule.prayers
                .filter { it.prayer.isAlerting }
                .filter { preferences.isEnabled(it.prayer) }
                .forEach { prayerTime ->
                    val adhanTime = calculateAzaanTime(prayerTime)
                    val firesAt = DateTime.of(schedule.scheduleDate, adhanTime)
                    if (firesAt > now) {
                        port.schedule(
                            ScheduledAdhanAlert(
                                id = AlertId.of(prayerTime.prayer, schedule.scheduleDate),
                                prayer = prayerTime.prayer,
                                date = schedule.scheduleDate,
                                firesAt = firesAt,
                                playAdhanSound = playSound,
                                playDua = preferences.playDua,
                            )
                        )
                    }
                }
        }
    }

    private fun calculateAzaanTime(prayerTime: com.chingfordmosque.prayertimes.domain.PrayerTime): com.chingfordmosque.prayertimes.domain.Time {
        val begins = prayerTime.beginsAt
        if (preferences.isLocalAdhan) {
            return begins
        }
        if (prayerTime.prayer == com.chingfordmosque.prayertimes.domain.Prayer.Maghrib) {
            return begins
        }
        return when (val iqamahOpt = prayerTime.iqamahAt) {
            is com.chingfordmosque.prayertimes.domain.Option.Some -> {
                val calculated = iqamahOpt.value.minusMinutes(preferences.iqamahOffset)
                if (calculated < begins) begins else calculated
            }
            is com.chingfordmosque.prayertimes.domain.Option.None -> begins
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
