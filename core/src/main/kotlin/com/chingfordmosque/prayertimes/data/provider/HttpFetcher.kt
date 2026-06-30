package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result

/**
 * The single HTTP seam used by the Times Provider to retrieve raw page content from the
 * mosque website (design, Component 1; Requirements 1.1, 8.1).
 *
 * This interface intentionally knows **nothing** about HTML, the "Daily Salah Times" widget,
 * or the domain model — it only performs an HTTPS GET and hands back the **raw, untrusted**
 * response body for the parser (task 5.2) to deal with. Transport-level failures (connection
 * errors, timeouts, non-success responses) are surfaced as [ProviderError.NetworkError];
 * [ProviderError.ParseError] / [ProviderError.IncompleteData] are never produced here because
 * no interpretation of the body happens at this layer.
 *
 * Keeping the fetch behind this interface is the seam that lets the JVM build bind a
 * `java.net.http`-based implementation ([JvmHttpFetcher]) while an Android build can later
 * provide an OkHttp-backed implementation **without touching parsing, domain, or scheduling
 * logic**. Tests inject a fake so no real network call is ever made.
 */
fun interface HttpFetcher {

    /**
     * Perform an HTTPS GET against [url] and return the response body verbatim.
     *
     * @return [Result.Ok] with the raw body string on a successful (2xx) response, or
     *   [Result.Err] carrying a [ProviderError.NetworkError] when the request could not be
     *   completed (unreachable host, timeout, non-HTTPS URL, or a non-success status code).
     *   The body is **not** inspected, trusted, or parsed here.
     */
    fun fetch(url: String): Result<String, ProviderError>
}
