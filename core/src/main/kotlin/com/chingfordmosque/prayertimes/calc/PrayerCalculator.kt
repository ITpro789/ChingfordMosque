package com.chingfordmosque.prayertimes.calc

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.Time
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * The angle/shadow constants that select a calculation "method" for the astronomical solar
 * algorithm. Defaults to the Muslim World League (MWL) convention used as the on-device
 * fallback when the mosque website cannot be reached and no real schedule is cached.
 *
 * @property fajrAngle the sun's depression angle below the horizon at the start of Fajr.
 * @property ishaAngle the sun's depression angle below the horizon at the start of Isha.
 * @property shadowFactor the Asr shadow-length multiplier (1 = Shafi/standard, 2 = Hanafi).
 * @property refraction the apparent horizon dip (degrees) for sunrise/sunset; ~0.833 accounts
 *   for atmospheric refraction and the sun's disc radius.
 */
data class CalculationMethod(
    val fajrAngle: Double = 18.0,
    val ishaAngle: Double = 17.0,
    val shadowFactor: Double = 1.0,
    val refraction: Double = 0.833,
) {
    companion object {
        /** Muslim World League defaults (Fajr 18°, Isha 17°, Shafi shadow factor). */
        val MWL: CalculationMethod = CalculationMethod()
    }
}

/**
 * The fixed geographic constants for Chingford Mosque. [PrayerCalculator] itself stays fully
 * parameterised by latitude/longitude/timezone/method; this object is only a convenient
 * default for the mosque's own location.
 */
object MosqueLocation {
    const val LATITUDE: Double = 51.6286
    const val LONGITUDE: Double = -0.0167
}

/**
 * A pure-Kotlin implementation of the standard PrayTimes solar algorithm that computes the
 * BEGIN time of each prayer for a given [Date], latitude, longitude and timezone offset.
 *
 * There is no I/O, no platform dependency and no hidden clock: the same inputs always produce
 * the same six times, which makes the calculator fully unit-testable. It is used by
 * [com.chingfordmosque.prayertimes.data.provider.CalculatedTimesProvider] as the on-device
 * fallback source of prayer times.
 *
 * The algorithm:
 * - derive the Julian date from the Gregorian calendar date,
 * - compute the sun's declination `D` and the equation of time `EqT` for that day,
 * - Dhuhr (solar noon, local clock) = `12 + tzOffset - lng/15 - EqT`,
 * - sunrise/sunset use the horizon-dip angle (refraction ~0.833°),
 * - Fajr/Isha use the configured depression angles,
 * - Asr uses the shadow-length altitude.
 *
 * @param latitude observer latitude in degrees (north positive).
 * @param longitude observer longitude in degrees (east positive).
 * @param timeZoneOffsetHours the local clock offset from UTC in hours for the target date
 *   (e.g. 0.0 for GMT, 1.0 for BST), so the returned times are local clock times.
 * @param method the angle/shadow constants; defaults to [CalculationMethod.MWL].
 */
class PrayerCalculator(
    private val latitude: Double,
    private val longitude: Double,
    private val timeZoneOffsetHours: Double,
    private val method: CalculationMethod = CalculationMethod.MWL,
) {

    /**
     * Compute the six prayer begin times for [date] as local clock [Time]s, keyed by [Prayer]
     * (Zuhr is solar Dhuhr). Decimal hours are rounded to the nearest minute and clamped into
     * the valid 00:00..23:59 range.
     */
    fun computeTimes(date: Date): Map<Prayer, Time> {
        val jd = julianDate(date.year, date.month, date.day)
        val (decl, eqt) = sunPosition(jd)

        // Solar noon in local clock hours.
        val dhuhr = 12.0 + timeZoneOffsetHours - longitude / 15.0 - eqt

        // Hour-angle (in hours) for a sun depressed by `angle` degrees below the horizon.
        // The cosine is clamped to [-1, 1] so extreme latitudes/dates (where a given depression
        // angle never occurs, e.g. London high summer) degrade gracefully instead of producing
        // NaN; such schedules simply fail later validation and are reported as incomplete.
        fun depressionTime(angle: Double): Double {
            val cosHourAngle =
                ((-dsin(angle) - dsin(latitude) * dsin(decl)) / (dcos(latitude) * dcos(decl)))
                    .coerceIn(-1.0, 1.0)
            return darccos(cosHourAngle) / 15.0
        }

        val horizonOffset = depressionTime(method.refraction)
        val sunrise = dhuhr - horizonOffset
        val maghrib = dhuhr + horizonOffset
        val fajr = dhuhr - depressionTime(method.fajrAngle)
        val isha = dhuhr + depressionTime(method.ishaAngle)

        // Asr: the sun altitude at which an object's shadow equals its noon shadow plus
        // `shadowFactor` times its length.
        val asrAltitude = darccot(method.shadowFactor + dtan(abs(latitude - decl)))
        val cosAsrHourAngle =
            ((dsin(asrAltitude) - dsin(latitude) * dsin(decl)) / (dcos(latitude) * dcos(decl)))
                .coerceIn(-1.0, 1.0)
        val asr = dhuhr + darccos(cosAsrHourAngle) / 15.0

        return linkedMapOf(
            Prayer.Fajr to toTime(fajr),
            Prayer.Sunrise to toTime(sunrise),
            Prayer.Zuhr to toTime(dhuhr),
            Prayer.Asr to toTime(asr),
            Prayer.Maghrib to toTime(maghrib),
            Prayer.Isha to toTime(isha),
        )
    }

    /** Convert decimal local hours into a clamped, minute-rounded [Time]. */
    private fun toTime(decimalHours: Double): Time {
        val totalMinutes = (decimalHours * 60.0).roundToInt().coerceIn(0, MINUTES_IN_DAY - 1)
        return Time.ofMinutes(totalMinutes).getOrThrow()
    }

    /** The astronomical Julian Date for the given Gregorian calendar date (at 00:00 UT). */
    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /**
     * The sun's declination (degrees) and the equation of time (hours) for the given Julian
     * date, following the PrayTimes/U.S. Naval Observatory low-precision formulae.
     */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d) // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d) // mean longitude
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g)) // ecliptic longitude
        val e = 23.439 - 0.00000036 * d // obliquity of the ecliptic
        val rightAscension = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val equationOfTime = q / 15.0 - fixHour(rightAscension)
        val declination = darcsin(dsin(e) * dsin(l))
        return declination to equationOfTime
    }

    private companion object {
        const val MINUTES_IN_DAY = 24 * 60

        // --- Degree-based trigonometry helpers ---
        fun dsin(degrees: Double): Double = sin(Math.toRadians(degrees))
        fun dcos(degrees: Double): Double = cos(Math.toRadians(degrees))
        fun dtan(degrees: Double): Double = tan(Math.toRadians(degrees))
        fun darcsin(x: Double): Double = Math.toDegrees(asin(x))
        fun darccos(x: Double): Double = Math.toDegrees(acos(x))
        fun darctan2(y: Double, x: Double): Double = Math.toDegrees(atan2(y, x))
        fun darccot(x: Double): Double = Math.toDegrees(atan2(1.0, x))

        fun fixAngle(a: Double): Double {
            val x = a - 360.0 * floor(a / 360.0)
            return if (x < 0) x + 360.0 else x
        }

        fun fixHour(a: Double): Double {
            val x = a - 24.0 * floor(a / 24.0)
            return if (x < 0) x + 24.0 else x
        }
    }
}
