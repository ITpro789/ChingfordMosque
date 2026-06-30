package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for [HttpTimesProvider] focusing on task 5.3's responsibility: assembling a
 * validated [DaySchedule] from the parsed intermediate and mapping each failure mode to the
 * correct typed [ProviderError]. The HTTP seam is a fake (no real network), and the well-formed
 * mosque fixture is reused for the happy path.
 */
class HttpTimesProviderTest : StringSpec({

    fun fixture(name: String): String =
        requireNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture: $name"
        }.bufferedReader().use { it.readText() }

    fun time(h: Int, m: Int): Time = (Time.of(h, m) as Result.Ok).value

    fun date(y: Int, mo: Int, d: Int): Date = (Date.of(y, mo, d) as Result.Ok).value

    // A clock fixed to an arbitrary "today" used only when the page omits its own date.
    val fixedToday = date(2030, 1, 15)
    val clock: Clock = FixedClock((DateTime.of(fixedToday, 12, 0, 0) as Result.Ok).value)

    fun providerOver(html: String): HttpTimesProvider =
        HttpTimesProvider(
            fetcher = HttpFetcher { Result.Ok(html) },
            parser = SalahTimesParser(),
            clock = clock,
        )

    "assembles a validated DaySchedule from a well-formed widget" {
        val schedule = (providerOver(fixture("daily-salah-times.html")).fetchTodaySchedule()
            as Result.Ok).value

        // Prayers are present, canonically ordered, with normalized begin/iqamah times.
        schedule.prayers.map { it.prayer } shouldBe Prayer.canonicalOrder()
        (schedule.prayer(Prayer.Fajr) as Option.Some).value.beginsAt shouldBe time(2, 46)
        (schedule.prayer(Prayer.Fajr) as Option.Some).value.iqamahAt shouldBe Option.Some(time(4, 15))
        (schedule.prayer(Prayer.Isha) as Option.Some).value.beginsAt shouldBe time(22, 32)
        // Jummah carried through.
        val jummah = (schedule.jummah as Option.Some).value
        jummah.jamaahTimes shouldBe listOf(time(13, 20), time(14, 0), time(14, 30))
    }

    "uses the date published in the widget when present" {
        val schedule = (providerOver(fixture("daily-salah-times.html")).fetchTodaySchedule()
            as Result.Ok).value
        schedule.scheduleDate shouldBe date(2026, 6, 30)
    }

    "falls back to the clock's today when the widget omits a date" {
        // Same valid table but with no parseable date anywhere in the markup.
        val schedule = (providerOver(salahTableHtml(date = null)).fetchTodaySchedule()
            as Result.Ok).value
        schedule.scheduleDate shouldBe fixedToday
    }

    "propagates a NetworkError from the fetcher without parsing" {
        val provider = HttpTimesProvider(
            fetcher = HttpFetcher { Result.Err(ProviderError.NetworkError("offline")) },
            parser = SalahTimesParser(),
            clock = clock,
        )
        provider.fetchTodaySchedule().errorOrNull()
            .shouldBeInstanceOf<ProviderError.NetworkError>()
    }

    "propagates a ParseError when the widget cannot be located" {
        providerOver("<html><body><p>nothing useful here</p></body></html>")
            .fetchTodaySchedule().errorOrNull()
            .shouldBeInstanceOf<ProviderError.ParseError>()
    }

    "maps a missing required salah to IncompleteData" {
        // A table that parses fine but omits Zuhr — schedule validation must reject it.
        val result = providerOver(salahTableHtml(date = null, includeZuhr = false))
            .fetchTodaySchedule()
        val error = result.errorOrNull().shouldBeInstanceOf<ProviderError.IncompleteData>()
        error.detail.lowercase().contains("zuhr") shouldBe true
    }

    "maps non-increasing begin times to IncompleteData" {
        // Asr begins before Zuhr — parses per-row, fails the strictly-increasing rule.
        val result = providerOver(salahTableHtml(date = null, asrBegins = "11:00 am"))
            .fetchTodaySchedule()
        result.errorOrNull().shouldBeInstanceOf<ProviderError.IncompleteData>()
    }
})

/**
 * Builds a minimal but realistic "Daily Salah Times" widget table for assembly tests, allowing a
 * required prayer to be dropped or a begin time overridden to exercise validation paths.
 */
private fun salahTableHtml(
    date: String?,
    includeZuhr: Boolean = true,
    asrBegins: String = "6:40 pm",
): String {
    val header = date?.let { " $it " } ?: ""
    val zuhrRow = if (includeZuhr) {
        """<tr><th class="prayerName">Zuhr</th><td>1:08 pm</td><td>1:30 pm</td></tr>"""
    } else {
        ""
    }
    return """
        <!DOCTYPE html><html><body>
        <table class="dptTimetable">
          <tr><th colspan="3">$header</th></tr>
          <tr><th>Prayer</th><th>Begins</th><th>Iqamah</th></tr>
          <tr><th class="prayerName">Fajr</th><td>2:46 am</td><td>4:15 am</td></tr>
          <tr><th class="prayerName">Sunrise</th><td colspan="2">4:46 am</td></tr>
          $zuhrRow
          <tr><th class="prayerName">Asr</th><td>$asrBegins</td><td>7:45 pm</td></tr>
          <tr><th class="prayerName">Maghrib</th><td>9:24 pm</td><td>9:24 pm</td></tr>
          <tr><th class="prayerName">Isha</th><td>10:32 pm</td><td>10:45 pm</td></tr>
        </table>
        </body></html>
    """.trimIndent()
}
