package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.calc.CalculationMethod
import com.chingfordmosque.prayertimes.calc.MosqueLocation
import com.chingfordmosque.prayertimes.calc.PrayerCalculator
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import java.time.LocalDate
import java.time.ZoneId

/**
 * A [TimesProvider] that produces today's schedule from the on-device astronomical
 * [PrayerCalculator] rather than the mosque website. It is the calculated fallback used by the
 * Refresh Coordinator ONLY when the website scrape fails AND there is no cached real schedule,
 * so the app can still show plausible prayer times entirely offline.
 *
 * Unlike [HttpTimesProvider] this provider touches no network: it reads "today" from the
 * injected [clock], resolves the local timezone offset for that calendar day via `java.time`
 * (the same library [com.chingfordmosque.prayertimes.domain.SystemClock] already uses), and
 * computes the six begin times. The produced [PrayerTime]s carry only begin times (no iqamah),
 * and no Jummah, because astronomical calculation cannot know the mosque's congregational
 * times.
 *
 * @param clock supplies "today"; never read from a system clock directly.
 * @param latitude observer latitude; defaults to [MosqueLocation.LATITUDE].
 * @param longitude observer longitude; defaults to [MosqueLocation.LONGITUDE].
 * @param zoneId the IANA timezone whose UTC offset converts solar time to local clock time.
 * @param method the angle/shadow constants; defaults to [CalculationMethod.MWL].
 */
class CalculatedTimesProvider(
    private val clock: Clock,
    private val latitude: Double = MosqueLocation.LATITUDE,
    private val longitude: Double = MosqueLocation.LONGITUDE,
    private val zoneId: String = "Europe/London",
    private val method: CalculationMethod = CalculationMethod.MWL,
) : TimesProvider {

    override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
        val today = clock.now().date

        // Resolve the local clock offset (in hours) for this calendar day. Using local noon as
        // the reference instant keeps us safely away from DST transition boundaries.
        val zone = ZoneId.of(zoneId)
        val localNoon = LocalDate.of(today.year, today.month, today.day).atTime(12, 0)
        val tzOffsetHours = zone.rules.getOffset(localNoon).totalSeconds / 3600.0

        val calculator = PrayerCalculator(latitude, longitude, tzOffsetHours, method)
        val times = calculator.computeTimes(today)

        // Build begin-only entries (iqamah = None) in canonical order. PrayerTime.of only
        // rejects iqamah-related rules, so begin-only entries always succeed.
        val prayers = mutableListOf<PrayerTime>()
        for (prayer in times.keys) {
            val time = times.getValue(prayer)
            when (val r = PrayerTime.of(prayer, time)) {
                is Result.Ok -> prayers += r.value
                is Result.Err -> return Result.Err(
                    ProviderError.IncompleteData("Invalid calculated $prayer entry: ${r.error}"),
                )
            }
        }

        // Validate as a schedule; any failure (e.g. non-increasing times at extreme latitudes)
        // is surfaced as incomplete data so the caller treats it like any other provider miss.
        return when (val r = DaySchedule.of(today, prayers)) {
            is Result.Ok -> Result.Ok(r.value)
            is Result.Err -> Result.Err(ProviderError.IncompleteData(r.error))
        }
    }
}
