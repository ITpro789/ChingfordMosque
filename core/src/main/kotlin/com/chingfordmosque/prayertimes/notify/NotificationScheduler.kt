package com.chingfordmosque.prayertimes.notify

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.NotificationPreferences

/**
 * Arms a device "azaan/adhan" notification for each upcoming prayer begin time and re-arms it
 * whenever the schedule changes (design, Component 4).
 *
 * The interface is platform-free: it depends only on the domain model ([DaySchedule],
 * [DateTime], [NotificationPreferences]). All side effects go through the injected
 * [AdhanAlarmPort] seam, so this layer carries no Android dependency and the current instant is
 * always supplied by the caller rather than read from a hidden clock.
 */
interface NotificationScheduler {

    /**
     * Cancel any pending alerts and arm exactly one alert per *remaining* alerting prayer whose
     * begin time is strictly after [now] (Requirements 5.1, 5.5). Sunrise is never armed
     * (Requirement 5.6) and prayers disabled in the current preferences are skipped
     * (Requirement 5.4).
     */
    fun reschedule(schedule: DaySchedule, now: DateTime)

    /** Cancel all pending adhan alerts (Requirement 5.5). */
    fun cancelAll()

    /**
     * Update the user preferences governing which prayers alert and whether the adhan audio
     * plays (Requirements 5.3, 5.4). Takes effect on the next [reschedule].
     */
    fun setPreferences(prefs: NotificationPreferences)
}
