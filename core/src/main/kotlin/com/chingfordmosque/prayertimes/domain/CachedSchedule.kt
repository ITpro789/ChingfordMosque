package com.chingfordmosque.prayertimes.domain

/**
 * A [DaySchedule] together with the instant it was successfully retrieved. This is what the
 * repository persists so the UI can render last-known-good data offline and display a
 * freshness ("last updated…") indicator (design, Model 5).
 *
 * No additional validation is required: the wrapped [schedule] is already validated by
 * [DaySchedule.of], and any [DateTime] is structurally valid.
 */
data class CachedSchedule(
    val schedule: DaySchedule,
    val fetchedAt: DateTime,
)
