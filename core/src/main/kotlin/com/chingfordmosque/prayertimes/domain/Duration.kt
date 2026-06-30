package com.chingfordmosque.prayertimes.domain

/**
 * A non-negative span of time stored as whole seconds. Used for the next-prayer countdown.
 *
 * Construct via [ofSeconds] / [ofMinutes] / [between] which clamp/validate to a non-negative
 * value; the primary constructor is private.
 */
class Duration private constructor(val totalSeconds: Long) : Comparable<Duration> {

    val seconds: Int get() = (totalSeconds % 60).toInt()
    val minutes: Int get() = ((totalSeconds / 60) % 60).toInt()
    val hours: Long get() = totalSeconds / 3600

    val inWholeMinutes: Long get() = totalSeconds / 60

    fun isZero(): Boolean = totalSeconds == 0L

    override fun compareTo(other: Duration): Int = totalSeconds.compareTo(other.totalSeconds)

    override fun equals(other: Any?): Boolean =
        this === other || (other is Duration && other.totalSeconds == totalSeconds)

    override fun hashCode(): Int = totalSeconds.hashCode()

    /** "HH:mm:ss" rendering suitable for a countdown display. */
    override fun toString(): String =
        hours.toString().padStart(2, '0') + ":" +
            minutes.toString().padStart(2, '0') + ":" +
            seconds.toString().padStart(2, '0')

    companion object {
        val ZERO = Duration(0)

        /** A duration of [seconds]; negative inputs are clamped to zero. */
        fun ofSeconds(seconds: Long): Duration = Duration(if (seconds < 0) 0 else seconds)

        /** A duration of [minutes]; negative inputs are clamped to zero. */
        fun ofMinutes(minutes: Long): Duration = ofSeconds(minutes * 60)
    }
}
