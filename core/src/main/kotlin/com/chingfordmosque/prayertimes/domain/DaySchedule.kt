package com.chingfordmosque.prayertimes.domain

/**
 * A complete validated schedule for a single calendar day: the date the times apply to, the
 * ordered list of [PrayerTime]s, and the optional [JummahTimes] (present/relevant on Fridays).
 *
 * Instances are only creatable through the validating [of] smart constructor, which is the
 * single chokepoint enforcing the design's schedule-level invariants. The [prayers] list is
 * always stored in canonical chronological order regardless of the input ordering.
 *
 * Validation rules (from design, Model 4; Requirements 1.5, 1.6, 2.1):
 * - No duplicate prayers.
 * - Must contain the five required daily salah (Fajr, Zuhr, Asr, Maghrib, Isha); Sunrise is
 *   optional but expected.
 * - Prayer begin times must be strictly increasing in canonical order
 *   (Fajr < Sunrise < Zuhr < Asr < Maghrib < Isha).
 */
class DaySchedule private constructor(
    val scheduleDate: Date,
    val prayers: List<PrayerTime>,
    val jummah: Option<JummahTimes>,
) {

    /** Look up a prayer's entry by type, or [Option.None] if it is not in the schedule. */
    fun prayer(prayer: Prayer): Option<PrayerTime> =
        Option.ofNullable(prayers.firstOrNull { it.prayer == prayer })

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DaySchedule &&
                other.scheduleDate == scheduleDate &&
                other.prayers == prayers &&
                other.jummah == jummah)

    override fun hashCode(): Int = (scheduleDate.hashCode() * 31 + prayers.hashCode()) * 31 + jummah.hashCode()

    override fun toString(): String =
        "DaySchedule($scheduleDate, prayers=$prayers, jummah=$jummah)"

    companion object {
        /**
         * Create a validated [DaySchedule]. The input [prayers] may be in any order; the
         * result stores them in canonical chronological order.
         *
         * @return [Result.Ok] with the value, or [Result.Err] with a descriptive message when
         *   a required prayer is missing, a prayer is duplicated, or begin times are not
         *   strictly increasing in canonical order.
         */
        fun of(
            scheduleDate: Date,
            prayers: List<PrayerTime>,
            jummah: Option<JummahTimes> = Option.None,
        ): Result<DaySchedule, String> {
            // No duplicate prayers.
            val seen = mutableSetOf<Prayer>()
            for (pt in prayers) {
                if (!seen.add(pt.prayer)) {
                    return Result.Err("Duplicate prayer in schedule: ${pt.prayer}")
                }
            }

            // All five required daily salah must be present.
            val missing = Prayer.requiredDaily.filter { it !in seen }
            if (missing.isNotEmpty()) {
                return Result.Err("Schedule is missing required salah: ${missing.joinToString(", ")}")
            }

            // Order canonically and verify begin times are strictly increasing.
            val ordered = prayers.sortedBy { it.prayer.canonicalIndex }
            for (i in 1 until ordered.size) {
                val prev = ordered[i - 1]
                val curr = ordered[i]
                if (curr.beginsAt <= prev.beginsAt) {
                    return Result.Err(
                        "Begin times must be strictly increasing in canonical order: " +
                            "${curr.prayer} (${curr.beginsAt}) does not follow " +
                            "${prev.prayer} (${prev.beginsAt})",
                    )
                }
            }

            return Result.Ok(DaySchedule(scheduleDate, ordered, jummah))
        }
    }
}
