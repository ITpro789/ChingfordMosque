package com.chingfordmosque.prayertimes.ui

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
    fun present(schedule: DaySchedule): JummahSectionViewState = present(schedule.jummah)

    /** Map the optional [jummah] data directly to the Jummah section state. */
    fun present(jummah: Option<JummahTimes>): JummahSectionViewState = when (jummah) {
        is Option.None -> JummahSectionViewState.Hidden
        is Option.Some -> JummahSectionViewState.Visible(
            times = jummah.value.jamaahTimes.map { it.toString() },
        )
    }
}
