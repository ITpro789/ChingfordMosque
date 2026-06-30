package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.DaySchedule
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result

/**
 * The Times Provider boundary (design Component 1): the single seam the rest of the system
 * uses to obtain a fully validated [DaySchedule] for "today", without knowing anything about
 * the mosque website's markup, HTTP, or time formats.
 *
 * Downstream consumers — notably the Refresh Coordinator (task 8.1) — depend only on this clean
 * interface and the validated domain model, so a change to the source site is confined to the
 * provider implementation (Requirement 8.4).
 */
interface TimesProvider {

    /**
     * Fetch, parse, normalize and validate today's schedule from the remote source.
     *
     * @return [Result.Ok] with a validated [DaySchedule], or [Result.Err] carrying a typed
     *   [ProviderError]:
     *   - [ProviderError.NetworkError] when the site could not be reached (Requirement 8.1),
     *   - [ProviderError.ParseError] when the markup/format was not as expected (Requirement 8.2),
     *   - [ProviderError.IncompleteData] when a required salah is missing or the parsed begin
     *     times are not strictly increasing (Requirements 1.5, 1.6).
     */
    fun fetchTodaySchedule(): Result<DaySchedule, ProviderError>
}
