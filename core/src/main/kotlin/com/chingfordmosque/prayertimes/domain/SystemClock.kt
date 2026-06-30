package com.chingfordmosque.prayertimes.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Clock as JavaClock

/**
 * The real, system-backed [Clock] for the JVM build (and the default binding used by the
 * composition root). It is the production counterpart to [FixedClock]: where the latter
 * returns a pinned instant for deterministic tests, this reads the actual wall-clock time.
 *
 * "Now" is resolved in the mosque's local timezone (Europe/London by default) so that the
 * calendar date and time-of-day used for next-prayer / rollover decisions match the day the
 * mosque publishes its times for — never the device's UTC offset. The schedule's [Time]
 * values are minute-granular, but [DateTime] keeps seconds so the UI countdown can tick.
 *
 * The underlying [java.time.Clock] is injectable purely so the conversion logic can be
 * exercised deterministically; production callers use the default system clock. The domain
 * still depends only on the small [Clock] seam — nothing above this class sees `java.time`.
 *
 * @param javaClock the source of the current instant and zone; defaults to the system clock
 *   fixed to [DEFAULT_ZONE]. Injected in tests to verify the `java.time` → [DateTime] mapping.
 */
class SystemClock(
    private val javaClock: JavaClock = JavaClock.system(DEFAULT_ZONE),
) : Clock {

    /** Convenience constructor selecting a specific [ZoneId] over the system clock. */
    constructor(zone: ZoneId) : this(JavaClock.system(zone))

    override fun now(): DateTime {
        val zdt: ZonedDateTime = ZonedDateTime.now(javaClock)
        // Date.of / DateTime.of only reject out-of-range values; java.time already guarantees
        // valid calendar fields, so these succeed for every real instant.
        val date = Date.of(zdt.year, zdt.monthValue, zdt.dayOfMonth).getOrThrow()
        return DateTime.of(date, zdt.hour, zdt.minute, zdt.second).getOrThrow()
    }

    companion object {
        /** The mosque's local timezone; "today" and prayer instants are resolved here. */
        val DEFAULT_ZONE: ZoneId = ZoneId.of("Europe/London")
    }
}
