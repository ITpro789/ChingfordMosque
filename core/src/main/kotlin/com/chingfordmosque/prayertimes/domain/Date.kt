package com.chingfordmosque.prayertimes.domain

/**
 * A calendar date (year, month, day) in the mosque's local timezone. Deliberately a thin,
 * dependency-free value type so the domain never reaches for a platform clock.
 *
 * Construct via [of] which validates month/day ranges.
 */
class Date private constructor(val year: Int, val month: Int, val day: Int) : Comparable<Date> {

    /** The date immediately after this one, rolling month/year boundaries correctly. */
    fun nextDay(): Date {
        val dim = daysInMonth(year, month)
        return if (day < dim) {
            Date(year, month, day + 1)
        } else if (month < 12) {
            Date(year, month + 1, 1)
        } else {
            Date(year + 1, 1, 1)
        }
    }

    /** Returns day of week: 0 = Sunday, 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday */
    fun dayOfWeek(): Int {
        val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
        var y = year
        if (month < 3) {
            y -= 1
        }
        return (y + y / 4 - y / 100 + y / 400 + t[month - 1] + day) % 7
    }

    /** Returns true if this date is a Friday. */
    fun isFriday(): Boolean = dayOfWeek() == 5

    override fun compareTo(other: Date): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is Date && other.year == year && other.month == month && other.day == day)

    override fun hashCode(): Int = (year * 31 + month) * 31 + day

    /** ISO-style "yyyy-MM-dd" rendering. */
    override fun toString(): String =
        year.toString().padStart(4, '0') + "-" +
            month.toString().padStart(2, '0') + "-" +
            day.toString().padStart(2, '0')

    companion object {
        fun of(year: Int, month: Int, day: Int): Result<Date, String> {
            if (month !in 1..12) return Result.Err("month out of range: $month")
            val dim = daysInMonth(year, month)
            if (day !in 1..dim) return Result.Err("day out of range for $year-$month: $day")
            return Result.Ok(Date(year, month, day))
        }

        private fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }
}
