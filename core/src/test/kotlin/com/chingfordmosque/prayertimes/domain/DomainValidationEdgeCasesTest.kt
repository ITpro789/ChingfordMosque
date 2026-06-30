package com.chingfordmosque.prayertimes.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Example-based unit tests for the domain validation edge cases of the smart constructors
 * (PrayerTime, JummahTimes, DaySchedule). These complement the property tests by pinning
 * down specific rejection and happy-path cases described by the design's validation rules.
 *
 * _Requirements: 1.5 (reject incomplete/invalid schedules), 1.6 (strictly increasing begins),
 * 3.2 (Jummah ascending order)._
 */
class DomainValidationEdgeCasesTest : StringSpec({

    // --- small builders to keep the cases readable ---

    fun time(h: Int, m: Int): Time = Time.of(h, m).getOrThrow()

    fun date(): Date = Date.of(2024, 6, 7).getOrThrow()

    fun pt(prayer: Prayer, begin: Time, iqamah: Time? = null): PrayerTime =
        PrayerTime.of(
            prayer,
            begin,
            if (iqamah == null) Option.None else Option.Some(iqamah),
        ).getOrThrow()

    /** A complete, canonically increasing set of the five required salah plus Sunrise. */
    fun validPrayers(): List<PrayerTime> = listOf(
        pt(Prayer.Fajr, time(3, 30), time(4, 0)),
        pt(Prayer.Sunrise, time(5, 0)),
        pt(Prayer.Zuhr, time(13, 0), time(13, 15)),
        pt(Prayer.Asr, time(17, 30), time(17, 45)),
        pt(Prayer.Maghrib, time(21, 15)),
        pt(Prayer.Isha, time(22, 45), time(23, 0)),
    )

    // ---------------------------------------------------------------------
    // PrayerTime validation (Requirements 1.5)
    // ---------------------------------------------------------------------

    "PrayerTime: valid begin-only entry succeeds" {
        val result = PrayerTime.of(Prayer.Maghrib, time(21, 15))
        result.isOk shouldBe true
        val value = result.getOrThrow()
        value.prayer shouldBe Prayer.Maghrib
        value.beginsAt shouldBe time(21, 15)
        value.iqamahAt shouldBe Option.None
    }

    "PrayerTime: iqamah equal to or after begin succeeds" {
        PrayerTime.of(Prayer.Fajr, time(4, 0), Option.Some(time(4, 0))).isOk shouldBe true
        PrayerTime.of(Prayer.Fajr, time(4, 0), Option.Some(time(4, 30))).isOk shouldBe true
    }

    "PrayerTime: iqamah before begin is rejected" {
        val result = PrayerTime.of(Prayer.Zuhr, time(13, 0), Option.Some(time(12, 45)))
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "iqamah"
    }

    "PrayerTime: Sunrise carrying an iqamah is rejected" {
        val result = PrayerTime.of(Prayer.Sunrise, time(5, 0), Option.Some(time(5, 15)))
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "Sunrise"
    }

    "PrayerTime: Sunrise with no iqamah succeeds" {
        PrayerTime.of(Prayer.Sunrise, time(5, 0)).isOk shouldBe true
    }

    // ---------------------------------------------------------------------
    // Time validation (invalid times, Requirement 1.5)
    // ---------------------------------------------------------------------

    "Time: out-of-range hour is rejected" {
        Time.of(24, 0).isErr shouldBe true
        Time.of(-1, 0).isErr shouldBe true
    }

    "Time: out-of-range minute is rejected" {
        Time.of(12, 60).isErr shouldBe true
        Time.of(12, -1).isErr shouldBe true
    }

    "Time: boundary values 00:00 and 23:59 succeed" {
        Time.of(0, 0).isOk shouldBe true
        Time.of(23, 59).isOk shouldBe true
    }

    // ---------------------------------------------------------------------
    // DaySchedule validation (Requirements 1.5, 1.6)
    // ---------------------------------------------------------------------

    "DaySchedule: a complete, ordered schedule succeeds and is stored canonically" {
        val result = DaySchedule.of(date(), validPrayers())
        result.isOk shouldBe true
        val schedule = result.getOrThrow()
        schedule.prayers.map { it.prayer } shouldBe listOf(
            Prayer.Fajr, Prayer.Sunrise, Prayer.Zuhr,
            Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
    }

    "DaySchedule: input prayers in arbitrary order are reordered canonically" {
        val shuffled = validPrayers().reversed()
        val schedule = DaySchedule.of(date(), shuffled).getOrThrow()
        schedule.prayers.map { it.prayer } shouldBe Prayer.canonicalOrder()
    }

    "DaySchedule: missing a required salah is rejected" {
        // Drop Asr (a required daily salah).
        val withoutAsr = validPrayers().filterNot { it.prayer == Prayer.Asr }
        val result = DaySchedule.of(date(), withoutAsr)
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "Asr"
    }

    "DaySchedule: missing multiple required salah is rejected" {
        // Keep only Fajr + Sunrise.
        val sparse = validPrayers().filter { it.prayer == Prayer.Fajr || it.prayer == Prayer.Sunrise }
        val result = DaySchedule.of(date(), sparse)
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "missing required salah"
    }

    "DaySchedule: Sunrise is optional - schedule without it still succeeds" {
        val withoutSunrise = validPrayers().filterNot { it.prayer == Prayer.Sunrise }
        DaySchedule.of(date(), withoutSunrise).isOk shouldBe true
    }

    "DaySchedule: a duplicate prayer is rejected" {
        val withDuplicateZuhr = validPrayers() + pt(Prayer.Zuhr, time(13, 30))
        val result = DaySchedule.of(date(), withDuplicateZuhr)
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "Duplicate"
    }

    "DaySchedule: non-increasing begin times in canonical order are rejected" {
        // Make Asr begin before Zuhr so canonical order is no longer strictly increasing.
        val broken = validPrayers().map { entry ->
            if (entry.prayer == Prayer.Asr) pt(Prayer.Asr, time(12, 30)) else entry
        }
        val result = DaySchedule.of(date(), broken)
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "strictly increasing"
    }

    "DaySchedule: equal adjacent begin times are rejected (must be strictly increasing)" {
        // Maghrib equal to Asr's begin time.
        val broken = validPrayers().map { entry ->
            if (entry.prayer == Prayer.Maghrib) pt(Prayer.Maghrib, time(17, 30)) else entry
        }
        DaySchedule.of(date(), broken).isErr shouldBe true
    }

    // ---------------------------------------------------------------------
    // JummahTimes validation (Requirement 3.2)
    // ---------------------------------------------------------------------

    "JummahTimes: a single entry succeeds" {
        JummahTimes.of(listOf(time(13, 20))).isOk shouldBe true
    }

    "JummahTimes: ascending entries succeed" {
        val result = JummahTimes.of(listOf(time(13, 20), time(14, 0), time(14, 30)))
        result.isOk shouldBe true
        result.getOrThrow().jamaahTimes shouldBe listOf(time(13, 20), time(14, 0), time(14, 30))
    }

    "JummahTimes: an empty list is rejected" {
        val result = JummahTimes.of(emptyList())
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "at least one"
    }

    "JummahTimes: non-ascending (descending) entries are rejected" {
        val result = JummahTimes.of(listOf(time(14, 30), time(13, 20)))
        result.isErr shouldBe true
        result.errorOrNull()!! shouldContain "ascending"
    }

    "JummahTimes: duplicate (equal) entries are rejected as non-strictly-ascending" {
        val result = JummahTimes.of(listOf(time(13, 20), time(13, 20)))
        result.isErr shouldBe true
    }

    "DaySchedule: carries a valid JummahTimes when supplied" {
        val jummah = JummahTimes.of(listOf(time(13, 20), time(14, 0))).getOrThrow()
        val schedule = DaySchedule.of(date(), validPrayers(), Option.Some(jummah)).getOrThrow()
        schedule.jummah.shouldBeInstanceOf<Option.Some<JummahTimes>>()
        schedule.jummah.getOrNull() shouldBe jummah
    }
})
