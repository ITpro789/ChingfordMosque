package com.chingfordmosque.prayertimes.data.repository

import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.Clock
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.Result

/**
 * Default [ScheduleRepository] implementation, backed by a pluggable [LocalStore] and a
 * [Clock] for stamping [CachedSchedule.fetchedAt].
 *
 * Composition over inheritance keeps the platform concern (where bytes live) entirely inside
 * [LocalStore], so this class — and everything above it — is pure, deterministic, and unit
 * testable on the JVM. Swapping [InMemoryLocalStore] for an Android DataStore binding requires
 * no change here.
 *
 * Cache-safety (Requirement 6.5; design Property 4) is enforced in [save]: the incoming
 * schedule is re-validated through [DaySchedule.of] before anything is written, and the store
 * is only mutated on success. A rejected save therefore leaves any previously cached valid
 * schedule completely untouched.
 *
 * @param store durable backing store (defaults to an in-memory store for the JVM build).
 * @param clock source of the fetched-at timestamp recorded on each successful save.
 */
class LocalScheduleRepository(
    private val store: LocalStore = InMemoryLocalStore(),
    private val clock: Clock,
) : ScheduleRepository {

    override fun save(schedule: DaySchedule): SaveOutcome {
        // Defensive re-validation. A DaySchedule built via DaySchedule.of is already valid, but
        // re-running the validator here makes the cache-safety guarantee hold regardless of how
        // the value was produced (e.g. a future deserialisation path) — an invalid or empty
        // schedule can never reach the store and overwrite good data.
        return when (val revalidated = DaySchedule.of(schedule.scheduleDate, schedule.prayers, schedule.jummah)) {
            is Result.Err -> SaveOutcome.Rejected(revalidated.error)
            is Result.Ok -> {
                val cached = CachedSchedule(revalidated.value, clock.now())
                store.write(cached)
                SaveOutcome.Saved(cached)
            }
        }
    }

    override fun getCachedSchedule(): Option<CachedSchedule> = store.read()

    override fun clear() {
        store.delete()
    }
}
