package com.chingfordmosque.prayertimes.android.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fixed-location qibla geometry for Chingford Mosque.
 *
 * The mosque coordinates are constants (no location permission required), and the qibla
 * bearing toward the Kaaba is computed once with the great-circle *initial bearing* formula.
 */
object Qibla {

    const val MOSQUE_LAT = 51.6286
    const val MOSQUE_LNG = -0.0167
    const val KAABA_LAT = 21.4224779
    const val KAABA_LNG = 39.8251832

    /** Initial great-circle bearing from the mosque to the Kaaba, in degrees within [0, 360). */
    val bearingDegrees: Double = initialBearing(MOSQUE_LAT, MOSQUE_LNG, KAABA_LAT, KAABA_LNG)

    /**
     * The initial great-circle bearing from (lat1, lng1) to (lat2, lng2) in degrees, normalised
     * to [0, 360). Inputs are decimal degrees.
     */
    fun initialBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }
}
