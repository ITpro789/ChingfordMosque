package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Smoke tests for [SalahTimesParser] driving the parser from a representative HTML fixture that
 * mirrors the real chingfordmosque.com "Daily Salah Times" widget (Begins/Iqamah columns,
 * Sunrise begins-only, and the "Jummah Timing" line). Exhaustive fixture coverage incl. malformed
 * inputs is task 5.4; this confirms the happy path and the core normalization behaviour.
 */
class SalahTimesParserSmokeTest : StringSpec({

    val parser = SalahTimesParser()

    fun fixture(name: String): String =
        requireNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) {
            "missing test fixture: $name"
        }.bufferedReader().use { it.readText() }

    fun time(h: Int, m: Int): Time = (Time.of(h, m) as Result.Ok).value

    "parses all six prayers from the widget into normalized 24-hour begin/iqamah times" {
        val parsed = (parser.parse(fixture("daily-salah-times.html")) as Result.Ok).value

        val byPrayer = parsed.entries.associateBy { it.prayer }
        byPrayer.keys shouldBe Prayer.canonicalOrder().toSet()

        byPrayer.getValue(Prayer.Fajr).beginsAt shouldBe time(2, 46)
        byPrayer.getValue(Prayer.Fajr).iqamahAt shouldBe Option.Some(time(4, 15))
        byPrayer.getValue(Prayer.Zuhr).beginsAt shouldBe time(13, 8)
        byPrayer.getValue(Prayer.Zuhr).iqamahAt shouldBe Option.Some(time(13, 30))
        byPrayer.getValue(Prayer.Asr).beginsAt shouldBe time(18, 40)
        byPrayer.getValue(Prayer.Maghrib).beginsAt shouldBe time(21, 24)
        byPrayer.getValue(Prayer.Isha).beginsAt shouldBe time(22, 32)
        byPrayer.getValue(Prayer.Isha).iqamahAt shouldBe Option.Some(time(22, 45))
    }

    "treats Sunrise as begins-only with no iqamah" {
        val parsed = (parser.parse(fixture("daily-salah-times.html")) as Result.Ok).value
        val sunrise = parsed.entries.first { it.prayer == Prayer.Sunrise }
        sunrise.beginsAt shouldBe time(4, 46)
        sunrise.iqamahAt shouldBe Option.None
    }

    "parses the Jummah line into ascending jamā'ah times" {
        val parsed = (parser.parse(fixture("daily-salah-times.html")) as Result.Ok).value
        val jummah = parsed.jummah.shouldBeInstanceOf<Option.Some<*>>().value
        jummah.shouldNotBeNull()
        (jummah as com.chingfordmosque.prayertimes.domain.JummahTimes).jamaahTimes shouldBe
            listOf(time(13, 20), time(14, 0), time(14, 30))
    }

    "captures the schedule date the widget applies to" {
        val parsed = (parser.parse(fixture("daily-salah-times.html")) as Result.Ok).value
        val date = parsed.scheduleDate.shouldBeInstanceOf<Option.Some<*>>().value
        date.toString() shouldBe "2026-06-30"
    }

    "reports a ParseError when the salah widget is absent" {
        val result = parser.parse("<html><body><p>No widget here</p></body></html>")
        result.errorOrNull().shouldBeInstanceOf<ProviderError.ParseError>()
    }

    // --- time normalization ---

    "normalizes 12-hour am/pm times to canonical 24-hour" {
        SalahTimesParser.normalizeTime("2:46 am") shouldBe Result.Ok(time(2, 46))
        SalahTimesParser.normalizeTime("9:24 pm") shouldBe Result.Ok(time(21, 24))
        SalahTimesParser.normalizeTime("1:08pm") shouldBe Result.Ok(time(13, 8))
    }

    "treats 12 am as midnight and 12 pm as noon" {
        SalahTimesParser.normalizeTime("12:00 am") shouldBe Result.Ok(time(0, 0))
        SalahTimesParser.normalizeTime("12:30 pm") shouldBe Result.Ok(time(12, 30))
    }

    "accepts bare 24-hour input" {
        SalahTimesParser.normalizeTime("21:24") shouldBe Result.Ok(time(21, 24))
        SalahTimesParser.normalizeTime("00:05") shouldBe Result.Ok(time(0, 5))
    }

    "rejects nonsense or out-of-range times" {
        SalahTimesParser.normalizeTime("not a time").isErr shouldBe true
        SalahTimesParser.normalizeTime("25:00").isErr shouldBe true
        SalahTimesParser.normalizeTime("13:00 pm").isErr shouldBe true
    }
})
