package com.chingfordmosque.prayertimes.service

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.Prayer

/**
 * The "current prayer period" status of a [com.chingfordmosque.prayertimes.domain.DaySchedule]
 * relative to a supplied instant — the data behind the circular countdown timer.
 *
 * Unlike [ScheduleService.getNextPrayer] (which answers "which alerting prayer begins next"),
 * this models the *window* the user is currently inside, encoding the period rules:
 * - Each prayer's window ends when the next prayer begins, EXCEPT Fajr (ends at Sunrise) and
 *   Isha (ends at the next day's Fajr).
 * - Before today's Fajr the active prayer is the previous night's Isha (carryover).
 * - Between Sunrise and Zuhr there is no active fard prayer (a gap → [Upcoming] Zuhr).
 */
sealed class PrayerStatus {

    /** A prayer is currently active: its window is [startsAt, endsAt). */
    data class Active(
        val prayer: Prayer,
        val startsAt: DateTime,
        val endsAt: DateTime,
    ) : PrayerStatus()

    /**
     * No prayer is currently active; [prayer] begins next at [beginsAt]. [windowStartsAt] is
     * the start of the "gap" leading up to it (used to drive a progress ring towards the begin).
     */
    data class Upcoming(
        val prayer: Prayer,
        val windowStartsAt: DateTime,
        val beginsAt: DateTime,
    ) : PrayerStatus()

    /** Not enough schedule data to determine a status. */
    data object None : PrayerStatus()
}
