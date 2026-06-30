package com.chingfordmosque.prayertimes.domain

/**
 * The salah (prayers) published by the mosque, declared in canonical chronological order.
 *
 * The declaration order of this enum IS the canonical order
 * (Fajr < Sunrise < Zuhr < Asr < Maghrib < Isha), so [Enum.ordinal] can be relied upon by
 * [canonicalOrder] and [canonicalIndex].
 *
 * Sunrise is informational only: it is displayed but never alerts (no notification / never
 * returned as the "next prayer"). See [isAlerting].
 */
enum class Prayer {
    Fajr,
    Sunrise,
    Zuhr,
    Asr,
    Maghrib,
    Isha;

    /**
     * Whether this prayer participates in "next prayer" computation and adhan notifications.
     * Sunrise is informational only and therefore non-alerting.
     */
    val isAlerting: Boolean
        get() = this != Sunrise

    /** Position of this prayer in canonical order (0-based). */
    val canonicalIndex: Int
        get() = ordinal

    companion object {
        /** All prayers in canonical chronological order. */
        fun canonicalOrder(): List<Prayer> = entries.toList()

        /** Required daily salah that every valid schedule must contain (Sunrise excluded). */
        val requiredDaily: List<Prayer> = listOf(Fajr, Zuhr, Asr, Maghrib, Isha)

        /** Prayers that alert (everything except Sunrise), in canonical order. */
        fun alerting(): List<Prayer> = entries.filter { it.isAlerting }
    }
}
