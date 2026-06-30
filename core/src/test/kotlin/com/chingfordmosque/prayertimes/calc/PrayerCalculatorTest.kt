package com.chingfordmosque.prayertimes.calc

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the pure astronomical [PrayerCalculator] used as the on-device fallback.
 *
 * Exact-minute assertions are intentionally avoided (different methods/rounding shift times by
 * a minute or two); instead these tests pin the structural properties that any sane solar
 * computation for London must satisfy: the six times are in strictly increasing canonical order,
 * Dhuhr sits near solar noon, sunrise lies between Fajr and Dhuhr, and everything is a valid
 * time of day. Both a summer (BST) and a winter (GMT) date are checked.
 */
class PrayerCalculatorTest : StringSpec({

    fun date(y: Int, m: Int, d: Int): Date = Date.of(y, m, d).getOrThrow()

    // London (Chingford Mosque). Summer uses BST (+1), winter uses GMT (0).
    fun summerCalculator(): PrayerCalculator =
        PrayerCalculator(MosqueLocation.LATITUDE, MosqueLocation.LONGITUDE, timeZoneOffsetHours = 1.0)

    fun winterCalculator(): PrayerCalculator =
        PrayerCalculator(MosqueLocation.LATITUDE, MosqueLocation.LONGITUDE, timeZoneOffsetHours = 0.0)

    val canonical = listOf(
        Prayer.Fajr, Prayer.Sunrise, Prayer.Zuhr, Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
    )

    fun assertPlausible(times: Map<Prayer, Time>) {
        // All six prayers present.
        canonical.forEach { times.containsKey(it).shouldBeTrue() }

        val ordered = canonical.map { times.getValue(it) }

        // Strictly increasing in canonical order.
        for (i in 1 until ordered.size) {
            (ordered[i] > ordered[i - 1]).shouldBeTrue()
        }

        // Every time is a valid time of day (0..23 / 0..59).
        ordered.forEach {
            (it.hour in 0..23).shouldBeTrue()
            (it.minute in 0..59).shouldBeTrue()
        }

        val fajr = times.getValue(Prayer.Fajr)
        val sunrise = times.getValue(Prayer.Sunrise)
        val dhuhr = times.getValue(Prayer.Zuhr)
        val asr = times.getValue(Prayer.Asr)
        val maghrib = times.getValue(Prayer.Maghrib)
        val isha = times.getValue(Prayer.Isha)

        // Dhuhr near solar noon (local clock): between 11:30 and 13:30.
        (dhuhr.minutesSinceMidnight in (11 * 60 + 30)..(13 * 60 + 30)).shouldBeTrue()

        // Sunrise after Fajr and before Dhuhr.
        (sunrise > fajr).shouldBeTrue()
        (sunrise < dhuhr).shouldBeTrue()

        // Maghrib after Asr; Isha after Maghrib.
        (maghrib > asr).shouldBeTrue()
        (isha > maghrib).shouldBeTrue()
    }

    "London summer date yields six plausible, strictly increasing times" {
        assertPlausible(summerCalculator().computeTimes(date(2024, 8, 15)))
    }

    "London winter date yields six plausible, strictly increasing times" {
        assertPlausible(winterCalculator().computeTimes(date(2024, 12, 21)))
    }

    "the calculator returns exactly the six canonical prayers" {
        val times = summerCalculator().computeTimes(date(2024, 8, 15))
        times.keys.toSet() shouldBe canonical.toSet()
    }
})
