package com.chingfordmosque.prayertimes.domain

/**
 * A local instant: a [Date] plus a time-of-day with second precision. The schedule's
 * [Time] values are minute-granular, but "now" needs seconds for the countdown, so
 * [DateTime] carries its own hour/minute/second rather than embedding a [Time].
 *
 * Construct via [of]. Comparison is chronological.
 */
class DateTime private constructor(
    val date: Date,
    val hour: Int,
    val minute: Int,
    val second: Int,
) : Comparable<DateTime> {

    /** The minute-granular time-of-day component, useful for comparing against schedule times. */
    val timeOfDay: Time
        get() = Time.of(hour, minute).getOrThrow()

    private val secondsSinceMidnight: Int get() = (hour * 60 + minute) * 60 + second

    /**
     * The [Duration] from this instant until [other]. Zero when [other] is at or before this
     * instant (durations are non-negative).
     */
    fun durationUntil(other: DateTime): Duration {
        if (other <= this) return Duration.ZERO
        val dayDelta = daysBetween(date, other.date)
        val total = dayDelta * 86_400L +
            (other.secondsSinceMidnight - secondsSinceMidnight)
        return Duration.ofSeconds(total)
    }

    override fun compareTo(other: DateTime): Int {
        val d = date.compareTo(other.date)
        if (d != 0) return d
        return secondsSinceMidnight.compareTo(other.secondsSinceMidnight)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is DateTime && other.date == date && other.secondsSinceMidnight == secondsSinceMidnight)

    override fun hashCode(): Int = date.hashCode() * 31 + secondsSinceMidnight

    override fun toString(): String =
        "$date " + hour.toString().padStart(2, '0') + ":" +
            minute.toString().padStart(2, '0') + ":" +
            second.toString().padStart(2, '0')

    companion object {
        fun of(date: Date, hour: Int, minute: Int, second: Int = 0): Result<DateTime, String> {
            if (hour !in 0..23) return Result.Err("hour out of range: $hour")
            if (minute !in 0..59) return Result.Err("minute out of range: $minute")
            if (second !in 0..59) return Result.Err("second out of range: $second")
            return Result.Ok(DateTime(date, hour, minute, second))
        }

        /** Compose a [DateTime] from a [Date] and a minute-granular [Time]. */
        fun of(date: Date, time: Time): DateTime =
            DateTime(date, time.hour, time.minute, 0)

        /** Whole days from [from] to [to] (negative if [to] precedes [from]). */
        private fun daysBetween(from: Date, to: Date): Long {
            if (from == to) return 0
            // Small ranges in practice (schedule rolls over a day or two); iterate safely.
            return if (to > from) {
                var d = from
                var count = 0L
                while (d < to) { d = d.nextDay(); count++ }
                count
            } else {
                -daysBetween(to, from)
            }
        }
    }
}

/**
 * Abstraction over the current instant so time can be injected. The domain/service layers
 * never read a system clock directly; callers pass a [Clock] (real on device, [FixedClock]
 * in tests) — keeping logic pure and deterministic.
 */
interface Clock {
    fun now(): DateTime
}

/** A [Clock] that always returns a fixed instant — for deterministic tests. */
class FixedClock(private val instant: DateTime) : Clock {
    override fun now(): DateTime = instant
}
