package com.chingfordmosque.prayertimes.data.repository

import com.chingfordmosque.prayertimes.domain.Date
import com.chingfordmosque.prayertimes.domain.DateTime
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.FixedClock
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Prayer
import com.chingfordmosque.prayertimes.domain.PrayerTime
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import com.chingfordmosque.prayertimes.domain.Time
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based test (kotest-property) for the design's **Property 4 (Cache safety)**:
 *
 *   A failed fetch (`Result.Err` carrying a [ProviderError]), or any attempt that does not
 *   carry valid data, NEVER replaces a previously cached valid [DaySchedule].
 *
 * Strategy: seed a [LocalScheduleRepository] with an arbitrary *valid* schedule, then apply an
 * arbitrary non-empty sequence of failed fetches ([ScheduleRepository.saveFetchResult] with
 * `Result.Err`) drawn from random [ProviderError] variants. After each failed attempt we assert
 * the outcome is [SaveOutcome.NotAttempted] (so `didOverwrite == false`) and that
 * [ScheduleRepository.getCachedSchedule] still returns the originally cached schedule, unchanged.
 *
 * A [FixedClock] is used so the cached `fetchedAt` is deterministic and any spurious overwrite
 * would be observable as a changed [com.chingfordmosque.prayertimes.domain.CachedSchedule].
 *
 * **Validates: Requirements 6.5**
 */
class CacheSafetyPropertyTest : StringSpec({

    val anyDate: Date = Date.of(2024, 1, 15).getOrThrow()
    val fixedNow: DateTime = DateTime.of(anyDate, 12, 0, 0).getOrThrow()

    /**
     * Generates a valid, canonically-ordered [DaySchedule] for all six prayers (mirrors the
     * generator used by the domain validation property tests): draw six distinct
     * minutes-since-midnight, sort ascending, and assign them to prayers in canonical order so
     * begin times are strictly increasing. Each alerting prayer optionally gets an iqamah at or
     * after its begin; Sunrise never carries one. Building through the smart constructors means
     * every generated schedule is, by construction, valid.
     */
    val validScheduleArb: Arb<DaySchedule> = arbitrary {
        val begins: List<Int> = Arb.set(Arb.int(0..(Time.MINUTES_PER_DAY - 1)), 6).bind().sorted()
        val prayers = Prayer.canonicalOrder().mapIndexed { index, prayer ->
            val beginMinutes = begins[index]
            val beginsAt = Time.ofMinutes(beginMinutes).getOrThrow()
            val iqamahAt: Option<Time> = if (prayer == Prayer.Sunrise) {
                Option.None
            } else if (Arb.boolean().bind()) {
                val offset = Arb.int(0..(Time.MINUTES_PER_DAY - 1 - beginMinutes)).bind()
                Option.Some(Time.ofMinutes(beginMinutes + offset).getOrThrow())
            } else {
                Option.None
            }
            PrayerTime.of(prayer, beginsAt, iqamahAt).getOrThrow()
        }
        DaySchedule.of(anyDate, prayers).getOrThrow()
    }

    /** A random [ProviderError] — one of the three failure variants with an arbitrary detail. */
    val providerErrorArb: Arb<ProviderError> = arbitrary {
        val detail = Arb.string(0..24).bind()
        when (Arb.int(0..2).bind()) {
            0 -> ProviderError.NetworkError(detail)
            1 -> ProviderError.ParseError(detail)
            else -> ProviderError.IncompleteData(detail)
        }
    }

    "Property 4: a failed fetch never replaces a previously cached valid schedule" {
        checkAll(validScheduleArb, Arb.list(providerErrorArb, 1..12)) { schedule, errors ->
            val repo = LocalScheduleRepository(clock = FixedClock(fixedNow))

            // Seed the cache with a known-good schedule.
            val seedOutcome = repo.save(schedule)
            seedOutcome.shouldBeInstanceOf<SaveOutcome.Saved>()

            // Snapshot what is now cached; this must survive every subsequent failed fetch.
            val seeded = repo.getCachedSchedule()
            seeded.shouldBeInstanceOf<Option.Some<*>>()
            val original = (seeded as Option.Some).value

            // Apply an arbitrary sequence of failed fetches.
            errors.forEach { error ->
                val outcome = repo.saveFetchResult(Result.Err(error))

                // The failed fetch must not be applied to the cache.
                outcome.shouldBeInstanceOf<SaveOutcome.NotAttempted>()
                outcome.didOverwrite shouldBe false

                // And the cache still holds exactly the originally seeded schedule.
                repo.getCachedSchedule() shouldBe Option.Some(original)
            }
        }
    }
})
