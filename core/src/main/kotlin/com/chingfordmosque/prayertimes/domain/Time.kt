package com.chingfordmosque.prayertimes.domain

/**
 * A canonical 24-hour time-of-day with minute granularity (the granularity the mosque
 * publishes). Stored internally as minutes since midnight (0..1439) so that comparison and
 * arithmetic are trivial and unambiguous.
 *
 * Construct via [of] which validates the range; the primary constructor is private so an
 * invalid [Time] cannot be created.
 */
class Time private constructor(val minutesSinceMidnight: Int) : Comparable<Time> {

    val hour: Int get() = minutesSinceMidnight / MINUTES_PER_HOUR
    val minute: Int get() = minutesSinceMidnight % MINUTES_PER_HOUR

    override fun compareTo(other: Time): Int =
        minutesSinceMidnight.compareTo(other.minutesSinceMidnight)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Time && other.minutesSinceMidnight == minutesSinceMidnight)

    override fun hashCode(): Int = minutesSinceMidnight

    /** Canonical "HH:mm" rendering (zero-padded), e.g. 05:03, 14:30. */
    override fun toString(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        /**
         * Create a [Time] from hour (0..23) and minute (0..59).
         * Returns [Result.Err] with a descriptive message when out of range, so callers
         * (notably the parser) can surface a typed error rather than throwing.
         */
        fun of(hour: Int, minute: Int): Result<Time, String> {
            if (hour !in 0..23) return Result.Err("hour out of range: $hour")
            if (minute !in 0..59) return Result.Err("minute out of range: $minute")
            return Result.Ok(Time(hour * MINUTES_PER_HOUR + minute))
        }

        /** Create from total minutes since midnight (0..1439). */
        fun ofMinutes(minutesSinceMidnight: Int): Result<Time, String> {
            if (minutesSinceMidnight !in 0 until MINUTES_PER_DAY) {
                return Result.Err("minutesSinceMidnight out of range: $minutesSinceMidnight")
            }
            return Result.Ok(Time(minutesSinceMidnight))
        }
    }
}
