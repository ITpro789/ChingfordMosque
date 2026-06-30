package com.chingfordmosque.prayertimes.android.ui

import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation-only helper that formats today's Islamic (Hijri) date for the Home header.
 *
 * Uses [HijrahDate] (available on API 26+) derived from the current civil date in the mosque's
 * timezone (Europe/London), formatted like "12 Dhuʼl-Hijjah 1447 AH". Returns `null` if the
 * platform fails to produce a value, so the caller can omit it gracefully.
 */
object HijriDate {

    private val LONDON: ZoneId = ZoneId.of("Europe/London")

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM y G", Locale.ENGLISH)
            .withChronology(HijrahChronology.INSTANCE)

    /** Today's Hijri date as a display string, or `null` if it cannot be computed. */
    fun today(): String? = runCatching {
        val hijri = HijrahDate.from(LocalDate.now(LONDON))
        FORMATTER.format(hijri)
    }.getOrNull()
}
