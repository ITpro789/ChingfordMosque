package com.chingfordmosque.prayertimes.data.repository

import com.chingfordmosque.prayertimes.domain.CachedSchedule
import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.Option
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result

/**
 * Persistence boundary for the most recently fetched schedule (design, Component 2).
 *
 * The repository is the single source of truth the rest of the app renders from, so it always
 * has last-known-good data to show even when a fetch fails. The interface is deliberately
 * platform-free: the JVM build binds it to an in-memory/file-backed store
 * ([LocalScheduleRepository] + [LocalStore]), while an Android build can later bind it to
 * DataStore/Room **without touching domain or service logic**. See [LocalStore] for the
 * single seam an Android binding needs to implement.
 *
 * Cache-safety invariant (design Property 4; Requirement 6.5): a [save] of an
 * invalid/empty schedule, or a [saveFetchResult] carrying a failed fetch, MUST NOT overwrite
 * a previously cached valid schedule.
 */
interface ScheduleRepository {

    /**
     * Persist [schedule] together with the instant it was fetched, atomically. The fetched-at
     * timestamp is supplied by the repository's injected clock so callers cannot accidentally
     * record a stale time.
     *
     * The schedule is defensively re-validated before it is committed. If validation fails the
     * existing cache is left untouched ([SaveOutcome.Rejected]) — this is the cache-safety gate
     * that guarantees a malformed or empty schedule can never replace good data.
     *
     * @return [SaveOutcome.Saved] with the stored [CachedSchedule] on success, or
     *   [SaveOutcome.Rejected] (cache unchanged) when the schedule fails validation.
     */
    fun save(schedule: DaySchedule): SaveOutcome

    /**
     * The cached schedule with its freshness metadata ([CachedSchedule.fetchedAt]), or
     * [Option.None] when nothing has been cached yet. The UI uses [CachedSchedule.fetchedAt]
     * to render a "last updated…" / stale indicator (Requirement 6.2, 6.4).
     */
    fun getCachedSchedule(): Option<CachedSchedule>

    /** Empty the cache. After this, [getCachedSchedule] returns [Option.None]. */
    fun clear()

    /**
     * Apply the outcome of a fetch to the cache, honouring the cache-safety invariant.
     *
     * On a successful fetch the schedule is persisted via [save]. On a failed fetch the cache
     * is deliberately left intact and [SaveOutcome.NotAttempted] is returned — a failed fetch
     * never degrades the cache (Requirement 6.3, 6.5; design Property 4). This is the boundary
     * the Refresh Coordinator drives.
     */
    fun saveFetchResult(result: Result<DaySchedule, ProviderError>): SaveOutcome =
        when (result) {
            is Result.Ok -> save(result.value)
            is Result.Err -> SaveOutcome.NotAttempted(result.error)
        }
}

/**
 * The result of attempting to update the cache. Making the outcome explicit (rather than the
 * design's bare `Void`) lets callers and tests observe that the cache-safety invariant held —
 * i.e. that a rejected/failed update did not overwrite existing data.
 */
sealed class SaveOutcome {

    /** The schedule was validated and committed. [cached] is exactly what is now stored. */
    data class Saved(val cached: CachedSchedule) : SaveOutcome()

    /** The schedule failed defensive validation; the existing cache was left untouched. */
    data class Rejected(val reason: String) : SaveOutcome()

    /** A failed fetch was supplied; no write was attempted and the cache is untouched. */
    data class NotAttempted(val error: ProviderError) : SaveOutcome()

    /** True only when the cache was actually overwritten by this operation. */
    val didOverwrite: Boolean get() = this is Saved
}
