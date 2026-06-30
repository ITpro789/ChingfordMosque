package com.chingfordmosque.prayertimes.ui

/**
 * Platform-free view-state for rendering today's salah times (Requirement 2).
 *
 * These types are intentionally free of any Android UI dependency: they are plain,
 * already-formatted data the Android binding layer (Compose/Views, deferred) consumes
 * directly. All domain → display mapping lives in [DaySchedulePresenter] so the rendering
 * layer needs no knowledge of the domain model.
 *
 * @property date the date the times apply to, pre-formatted for display (Requirement 2.4).
 * @property rows the prayers in canonical order, one row each (Requirement 2.1).
 */
data class DayScheduleViewState(
    val date: String,
    val rows: List<PrayerRowViewState>,
)

/**
 * A single renderable prayer row.
 *
 * @property prayerName the display name of the prayer (e.g. "Fajr", "Sunrise").
 * @property begins the begin/adhan time as "HH:mm" (Requirement 2.2).
 * @property iqamah the congregational/iqamah time as "HH:mm", or `null` when not provided
 *   (e.g. Sunrise, or any prayer the source omits an iqamah for) (Requirements 2.2, 2.3).
 * @property isInformational `true` for Sunrise, which is shown for information only and never
 *   carries an iqamah value (Requirement 2.3).
 */
data class PrayerRowViewState(
    val prayerName: String,
    val begins: String,
    val iqamah: String?,
    val isInformational: Boolean,
)
