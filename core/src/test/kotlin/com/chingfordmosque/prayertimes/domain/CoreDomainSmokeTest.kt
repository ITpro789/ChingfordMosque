package com.chingfordmosque.prayertimes.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Smoke tests confirming the core domain foundation compiles and runs, and that BOTH the
 * unit test framework (kotest/JUnit5) AND the property-based testing library (kotest-property)
 * are correctly wired. The exhaustive numbered property tests live in their own tasks.
 */
class CoreDomainSmokeTest : StringSpec({

    // --- Unit tests (framework wiring) ---

    "Prayer canonical order matches the published chronological order" {
        Prayer.canonicalOrder() shouldBe listOf(
            Prayer.Fajr, Prayer.Sunrise, Prayer.Zuhr,
            Prayer.Asr, Prayer.Maghrib, Prayer.Isha,
        )
    }

    "Sunrise is the only non-alerting prayer" {
        Prayer.entries.filter { !it.isAlerting } shouldBe listOf(Prayer.Sunrise)
    }

    "Result map/flatMap/fold behave on Ok and Err" {
        val ok: Result<Int, String> = Result.Ok(2)
        ok.map { it * 3 }.getOrNull() shouldBe 6
        val err: Result<Int, String> = Result.Err("boom")
        err.map { it * 3 }.errorOrNull() shouldBe "boom"
        err.getOrElse(0) shouldBe 0
    }

    "Option absence is first-class" {
        Option.ofNullable<Int>(null) shouldBe Option.None
        Option.ofNullable(5).getOrElse(0) shouldBe 5
    }

    "DateTime durationUntil is non-negative and crosses day boundaries" {
        val d1 = Date.of(2024, 1, 31).getOrThrow()
        val d2 = Date.of(2024, 2, 1).getOrThrow()
        val late = DateTime.of(d1, 23, 30, 0).getOrThrow()
        val early = DateTime.of(d2, 0, 30, 0).getOrThrow()
        late.durationUntil(early) shouldBe Duration.ofMinutes(60)
        // a target at or before "now" yields zero, never a negative duration
        early.durationUntil(late) shouldBe Duration.ZERO
        late.durationUntil(late) shouldBe Duration.ZERO
    }

    // --- Property-based test (PBT library wiring) ---

    "Time round-trips through minutes-since-midnight for all valid times" {
        checkAll(Arb.int(0, 23), Arb.int(0, 59)) { h, m ->
            val time = Time.of(h, m).getOrThrow()
            time.hour shouldBe h
            time.minute shouldBe m
            Time.ofMinutes(time.minutesSinceMidnight).getOrThrow() shouldBe time
        }
    }
})
