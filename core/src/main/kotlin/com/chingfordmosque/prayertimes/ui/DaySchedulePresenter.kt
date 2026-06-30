package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.service.ScheduleService

/**
 * Maps a validated [DaySchedule] into a platform-free [DayScheduleViewState] for display of
 * today's salah times (Requirement 2).
 *
 * This is pure presentation logic: no I/O, no clock, no Android dependency. The Android UI
 * binding (deferred) renders the returned view-state directly.
 *
 * Responsibilities (Requirement 2):
 * - List prayers in canonical order (2.1) — delegated to [ScheduleService.orderedPrayers] so
 *   ordering has a single source of truth shared with the rest of the app.
 * - Show each prayer's begin time and, where available, its iqamah time (2.2).
 * - Present Sunrise as informational only, never showing an iqamah for it (2.3).
 * - Surface the date the times apply to (2.4).
 */
object DaySchedulePresenter {

    /**
     * Build the today's-salah-times view-state from [schedule].
     *
     * The date is rendered via [DaySchedule.scheduleDate]'s canonical "yyyy-MM-dd" form and the
     * rows follow canonical chronological order (Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha),
     * including Sunrise as an informational entry.
     */
    fun present(schedule: DaySchedule): DayScheduleViewState =
        DayScheduleViewState(
            date = schedule.scheduleDate.toString(),
            rows = ScheduleService.orderedPrayers(schedule).map { toRow(it) },
        )

    /**
     * Map a single [PrayerTime] to its row view-state.
     *
     * Sunrise is flagged informational and — already guaranteed by the domain to carry no
     * iqamah — renders with a `null` iqamah. For all other prayers the iqamah is shown when the
     * source provided one and omitted (`null`) otherwise.
     */
    private fun toRow(prayerTime: PrayerTime): PrayerRowViewState {
        val isInformational = prayerTime.prayer == Prayer.Sunrise
        return PrayerRowViewState(
            prayerName = prayerTime.prayer.name,
            begins = prayerTime.beginsAt.toString(),
            iqamah = prayerTime.iqamahAt.getOrNull()?.toString(),
            isInformational = isInformational,
        )
    }
}
