package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result

/**
 * The concrete [TimesProvider] that wires the website-specific pieces together: it fetches the
 * raw page via an [HttpFetcher], turns it into the neutral [ParsedSalahTimes] intermediate via a
 * [SalahTimesParser], and then assembles and validates a [DaySchedule] (design Component 1).
 *
 * This is the single place where the three concerns meet, yet none of them leaks: the fetcher
 * knows only HTTP, the parser knows only markup, and the domain validation (via
 * [PrayerTime.of] / [DaySchedule.of]) knows only the rules. All website-specific fragility stays
 * inside this package (Requirement 8.4).
 *
 * Error mapping (typed per [ProviderError]):
 * - transport failures → [ProviderError.NetworkError] (surfaced by the fetcher; propagated here),
 * - unexpected markup/format → [ProviderError.ParseError] (surfaced by the parser; propagated),
 * - missing required salah / non-increasing begins / per-entry rule violations →
 *   [ProviderError.IncompleteData] (Requirements 1.5, 1.6).
 *
 * @param fetcher the HTTP seam; defaults to the JVM [java.net.http]-based [JvmHttpFetcher].
 * @param parser the source-specific widget parser.
 * @param clock supplies "today" used only as a fallback when the widget does not publish a date.
 * @param url the page to fetch; defaults to the mosque homepage hosting the widget.
 */
class HttpTimesProvider(
    private val fetcher: HttpFetcher = JvmHttpFetcher(),
    private val parser: SalahTimesParser = SalahTimesParser(),
    private val clock: Clock,
    private val url: String = JvmHttpFetcher.MOSQUE_URL,
) : TimesProvider {

    override fun fetchTodaySchedule(): Result<DaySchedule, ProviderError> {
        // 1. Fetch raw (untrusted) HTML. NetworkError is produced by the fetcher; propagate it.
        val html = when (val r = fetcher.fetch(url)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }

        // 2. Parse the widget. ParseError is produced by the parser; propagate it.
        val parsed = when (val r = parser.parse(html)) {
            is Result.Ok -> r.value
            is Result.Err -> return Result.Err(r.error)
        }

        // 3. Lift each parsed row into a validated PrayerTime. The parser already enforces the
        //    per-entry invariants, but PrayerTime.of is the domain authority, so any rule it
        //    rejects is treated as incomplete/invalid data rather than trusted blindly.
        val prayerTimes = ArrayList<PrayerTime>(parsed.entries.size)
        for (entry in parsed.entries) {
            when (val r = PrayerTime.of(entry.prayer, entry.beginsAt, entry.iqamahAt)) {
                is Result.Ok -> prayerTimes += r.value
                is Result.Err -> return Result.Err(
                    ProviderError.IncompleteData("Invalid ${entry.prayer} entry: ${r.error}"),
                )
            }
        }

        // 4. Determine the schedule date: prefer the widget's own date, else fall back to the
        //    injected clock's "today" so a missing date header does not discard the times.
        val scheduleDate: Date = when (val d = parsed.scheduleDate) {
            is Option.Some -> d.value
            is Option.None -> clock.now().date
        }

        // 5. Run schedule-level validation (required salah present, strictly increasing begins).
        //    Any failure here is missing/invalid required data (Requirements 1.5, 1.6).
        return when (val r = DaySchedule.of(scheduleDate, prayerTimes, parsed.jummah)) {
            is Result.Ok -> Result.Ok(r.value)
            is Result.Err -> Result.Err(ProviderError.IncompleteData(r.error))
        }
    }
}
