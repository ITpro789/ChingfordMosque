package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

/**
 * Pure-Kotlin/JVM implementation of [HttpFetcher] built on [java.net.http.HttpClient].
 *
 * This is the default binding for the JVM build. It performs a single GET, enforces HTTPS
 * (design Security Considerations; Requirement 1.1), and maps every transport failure —
 * connection problems, timeouts, and non-2xx responses — to [ProviderError.NetworkError]
 * (Requirement 8.1). The response body is returned **verbatim and untrusted**: no parsing or
 * validation happens here, that is task 5.2's responsibility.
 *
 * The actual network exchange is injected via [exchange] so the mapping/guard logic can be
 * exercised without making real network calls; production callers use the default, which
 * builds a real [HttpClient]. An Android build would supply an OkHttp-backed [HttpFetcher]
 * instead of this class, leaving the rest of the provider untouched.
 *
 * @param requestTimeout per-request timeout applied to each [HttpRequest].
 * @param connectTimeout connection-establishment timeout for the default client.
 * @param exchange the seam that actually sends the request and returns the response; defaults
 *   to a real [HttpClient.send]. Injected in tests to simulate success/failure deterministically.
 */
class JvmHttpFetcher(
    private val requestTimeout: Duration = DEFAULT_TIMEOUT,
    private val connectTimeout: Duration = DEFAULT_TIMEOUT,
    private val exchange: (HttpRequest) -> HttpResponse<String> = defaultExchange(connectTimeout),
) : HttpFetcher {

    override fun fetch(url: String): Result<String, ProviderError> {
        val uri = try {
            URI.create(url)
        } catch (e: IllegalArgumentException) {
            return Result.Err(ProviderError.NetworkError("Malformed URL '$url': ${e.message}"))
        }

        // Security: only HTTPS is permitted for requests to the mosque site
        // (design Security Considerations; Requirement 1.1). Reject anything else up front so
        // a misconfiguration can never downgrade to cleartext.
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result.Err(
                ProviderError.NetworkError("Refusing non-HTTPS request to '$url'"),
            )
        }

        val request = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Accept", "text/html,application/xhtml+xml")
            .GET()
            .build()

        return try {
            val response = exchange(request)
            val status = response.statusCode()
            if (status in SUCCESS_RANGE) {
                // The body is untrusted input handed straight to the parser (task 5.2);
                // a null body is normalised to an empty string so the parser sees a value.
                Result.Ok(response.body() ?: "")
            } else {
                Result.Err(ProviderError.NetworkError("HTTP $status from '$url'"))
            }
        } catch (e: HttpTimeoutException) {
            // Covers both request and connect timeouts (HttpConnectTimeoutException is a subtype).
            Result.Err(ProviderError.NetworkError("Timed out fetching '$url': ${e.message}"))
        } catch (e: IOException) {
            Result.Err(ProviderError.NetworkError("Network error fetching '$url': ${e.message}"))
        } catch (e: InterruptedException) {
            // Preserve the interrupt status for cooperative cancellation, then surface as network.
            Thread.currentThread().interrupt()
            Result.Err(ProviderError.NetworkError("Interrupted while fetching '$url'"))
        }
    }

    companion object {
        /** The mosque homepage that hosts the "Daily Salah Times" widget (Requirement 1.1). */
        const val MOSQUE_URL: String = "https://chingfordmosque.com/"

        /** Conservative default timeout; the payload is tiny so a slow site is treated as down. */
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)

        private val SUCCESS_RANGE = 200..299

        private fun defaultExchange(
            connectTimeout: Duration,
        ): (HttpRequest) -> HttpResponse<String> {
            val client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            return { request -> client.send(request, HttpResponse.BodyHandlers.ofString()) }
        }
    }
}
