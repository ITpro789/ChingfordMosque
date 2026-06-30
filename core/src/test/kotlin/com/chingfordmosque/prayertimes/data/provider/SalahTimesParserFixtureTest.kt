package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.JummahTimes
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Fixture-driven unit tests for [SalahTimesParser] (task 5.4). These exercise the parser against
 * recorded HTML pages and assert its *intermediate* [ParsedSalahTimes] output and its mapping of
 * unexpected markup to [ProviderError.ParseError].
 *
 * Scope note (task boundary): schedule-level validation/assembly into a `DaySchedule`
 * (missing required salah, non-increasing begins → `IncompleteData`) is the provider's job
 * (task 5.3 / HttpTimesProvider), NOT the parser's. So these tests assert what the parser
 * normalizes and the `ParseError` cases it raises — not `DaySchedule`/`IncompleteData`.
 *
 * Coverage:
 *  - Well-formed widget → fully normalized structure incl. Sunrise begins-only and the Jummah line.
 *  - Widget table absent / garbled time tokens / Jummah label present but unparseable → `ParseError`.
 *  - Jummah line absent → `jummah == Option.None` (omitted without error, Requirement 3.3).
 *
 * Requirements: 1.2, 1.3, 3.1, 8.2.
 */
class SalahTimesParserFixtureTest : StringSpec({

    val parser = SalahTimesParser()

    fun fixture(name: String): String =
        requireNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture: $name"
        }.bufferedReader().use { it.readText() }

    fun time(h: Int, m: Int): Time = (Time.of(h, m) as Result.Ok).value

    fun parseOk(name: String): ParsedSalahTimes =
        when (val r = parser.parse(fixture(name))) {
            is Result.Ok -> r.value
            is Result.Err -> error("expected $name to parse, got error: ${r.error}")
        }

    // --- Well-formed widget: full normalized structure (Requirements 1.2, 1.3) ---

    "well-formed fixture parses all six prayers into normalized 24-hour begin/iqamah entries" {
        val parsed = parseOk("daily-salah-times.html")

        // All six prayers present, in the canonical order they appear on the page.
        parsed.entries.map { it.prayer } shouldContainExactly Prayer.canonicalOrder()

        val byPrayer = parsed.entries.associateBy { it.prayer }
        byPrayer.getValue(Prayer.Fajr).beginsAt shouldBe time(2, 46)
        byPrayer.getValue(Prayer.Fajr).iqamahAt shouldBe Option.Some(time(4, 15))
        byPrayer.getValue(Prayer.Zuhr).beginsAt shouldBe time(13, 8)
        byPrayer.getValue(Prayer.Zuhr).iqamahAt shouldBe Option.Some(time(13, 30))
        byPrayer.getValue(Prayer.Asr).beginsAt shouldBe time(18, 40)
        byPrayer.getValue(Prayer.Asr).iqamahAt shouldBe Option.Some(time(19, 45))
        byPrayer.getValue(Prayer.Maghrib).beginsAt shouldBe time(21, 24)
        byPrayer.getValue(Prayer.Maghrib).iqamahAt shouldBe Option.Some(time(21, 24))
        byPrayer.getValue(Prayer.Isha).beginsAt shouldBe time(22, 32)
        byPrayer.getValue(Prayer.Isha).iqamahAt shouldBe Option.Some(time(22, 45))
    }

    "well-formed fixture treats Sunrise as begins-only with no iqamah" {
        val sunrise = parseOk("daily-salah-times.html").entries.first { it.prayer == Prayer.Sunrise }
        sunrise.beginsAt shouldBe time(4, 46)
        sunrise.iqamahAt shouldBe Option.None
    }

    "well-formed fixture parses the Jummah line into ascending jamā'ah times" {
        val jummah = parseOk("daily-salah-times.html").jummah
            .shouldBeInstanceOf<Option.Some<JummahTimes>>().value
        jummah.jamaahTimes shouldContainExactly listOf(time(13, 20), time(14, 0), time(14, 30))
    }

    // --- Jummah line absent: omitted without error (Requirement 3.3) ---

    "fixture without a Jummah line yields jummah == Option.None without error" {
        val parsed = parseOk("no-jummah-line.html")
        // The salah entries still parse fine ...
        parsed.entries.map { it.prayer } shouldContainExactly Prayer.canonicalOrder()
        // ... and the absent Jummah line is reported as None, not an error.
        parsed.jummah shouldBe Option.None
    }

    // --- Malformed / missing data: ParseError (Requirements 8.2, 1.2) ---

    "missing salah widget maps to ParseError" {
        val result = parser.parse(fixture("no-salah-widget.html"))
        result.shouldBeInstanceOf<Result.Err<ProviderError>>()
            .error.shouldBeInstanceOf<ProviderError.ParseError>()
    }

    "garbled begin-time token maps to ParseError" {
        val result = parser.parse(fixture("garbled-times.html"))
        result.shouldBeInstanceOf<Result.Err<ProviderError>>()
            .error.shouldBeInstanceOf<ProviderError.ParseError>()
    }

    "Jummah label present but with no parseable times maps to ParseError" {
        val result = parser.parse(fixture("jummah-no-times.html"))
        result.shouldBeInstanceOf<Result.Err<ProviderError>>()
            .error.shouldBeInstanceOf<ProviderError.ParseError>()
    }
})
