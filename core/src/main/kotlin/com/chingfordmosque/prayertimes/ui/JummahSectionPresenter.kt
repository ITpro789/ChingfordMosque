package com.chingfordmosque.prayertimes.ui

import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option

/**
 * Pure mapper from the validated domain model to a [JummahSectionViewState].
 *
 * It contains no I/O and no platform dependencies: given a [DaySchedule] (or its
 * `Option<JummahTimes>`) it deterministically produces the renderable section state.
 *
 * Behaviour (Requirements 3.1, 3.2, 3.3):
 * - When Jummah data is present, emit [JummahSectionViewState.Visible] listing the jamā'ah
 *   times as canonical "HH:mm" strings in ascending chronological order. (The order is
 *   guaranteed by [JummahTimes]'s strictly-ascending invariant; the mapper preserves it and
 *   does not reorder.)
 * - When Jummah data is absent (`Option.None`), emit [JummahSectionViewState.Hidden] so the
 *   section is omitted without error.
 */
object JummahSectionPresenter {

    /** Map a whole [schedule] to the Jummah section state, using its [DaySchedule.jummah]. */
    fun present(schedule: DaySchedule, now: DateTime? = null): JummahSectionViewState =
        present(schedule.jummah, schedule.scheduleDate, now)

    /** Map the optional [jummah] data directly to the Jummah section state. */
    fun present(
        jummah: Option<JummahTimes>,
        date: com.chingfordmosque.prayertimes.domain.Date? = null,
        now: DateTime? = null,
    ): JummahSectionViewState {
        if (date != null && !date.isFriday()) {
            return JummahSectionViewState.Hidden
        }
        return when (jummah) {
            is Option.None -> JummahSectionViewState.Hidden
            is Option.Some -> {
                val times = jummah.value.jamaahTimes
                val statuses = if (now != null && date != null && date.isFriday()) {
                    val timeNow = now.timeOfDay
                    times.map { time ->
                        val start = time
                        val end = com.chingfordmosque.prayertimes.domain.Time.ofMinutes(
                            (start.minutesSinceMidnight + 15) % com.chingfordmosque.prayertimes.domain.Time.MINUTES_PER_DAY
                        ).getOrThrow()
                        if (timeNow >= end) {
                            JummahSectionViewState.JummahStatus.Done
                        } else if (timeNow >= start && timeNow < end) {
                            JummahSectionViewState.JummahStatus.Active
                        } else {
                            JummahSectionViewState.JummahStatus.Upcoming
                        }
                    }
                } else {
                    List(times.size) { JummahSectionViewState.JummahStatus.Upcoming }
                }
                JummahSectionViewState.Visible(
                    times = times.map { it.toString() },
                    statuses = statuses,
                )
            }
        }
    }
}
