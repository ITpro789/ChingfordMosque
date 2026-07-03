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
    ): JummahSectionViewState = when (jummah) {
        is Option.None -> JummahSectionViewState.Hidden
        is Option.Some -> {
            val times = jummah.value.jamaahTimes
            val highlightIndex = if (now != null && date != null && date.isFriday()) {
                getHighlightIndex(times, now)
            } else {
                null
            }
            JummahSectionViewState.Visible(
                times = times.map { it.toString() },
                activeIndex = highlightIndex,
            )
        }
    }

    private fun getHighlightIndex(
        times: List<com.chingfordmosque.prayertimes.domain.Time>,
        now: DateTime,
    ): Int {
        val timeNow = now.timeOfDay
        for (i in 0 until times.size - 1) {
            val tCurr = times[i]
            val tNext = times[i + 1]
            if (timeNow >= tCurr && timeNow < tNext) {
                return i
            }
        }
        if (timeNow >= times.last()) {
            return times.size - 1
        }
        return 0
    }
}
