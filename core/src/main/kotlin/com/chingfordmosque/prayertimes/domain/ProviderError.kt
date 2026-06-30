package com.chingfordmosque.prayertimes.domain

/**
 * The typed failures the Times Provider may surface. Keeping these as a sealed type lets the
 * Refresh Coordinator and UI react per-case (network vs. parse vs. incomplete) while all
 * website-specific fragility stays behind the provider boundary (Requirement 8.4).
 */
sealed class ProviderError {

    /** A human-readable detail for logging/diagnostics; not shown verbatim to users. */
    abstract val detail: String

    /** The site could not be reached (connection failure / timeout). */
    data class NetworkError(override val detail: String) : ProviderError()

    /** The markup/format was not as expected and could not be parsed. */
    data class ParseError(override val detail: String) : ProviderError()

    /** Required prayers were missing or the parsed schedule failed validation. */
    data class IncompleteData(override val detail: String) : ProviderError()
}
