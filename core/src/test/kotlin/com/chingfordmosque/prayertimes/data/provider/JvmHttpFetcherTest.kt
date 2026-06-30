package com.chingfordmosque.prayertimes.data.provider

import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession

/**
 * Unit tests for [JvmHttpFetcher] that exercise its HTTPS guard, success path, and
 * transport-failure mapping **without making any real network call** — the network exchange is
 * injected. The body is asserted to be returned verbatim (untrusted) so task 5.2 can parse it.
 */
class JvmHttpFetcherTest : StringSpec({

    val url = JvmHttpFetcher.MOSQUE_URL

    fun fetcherReturning(status: Int, body: String?): JvmHttpFetcher =
        JvmHttpFetcher(exchange = { request -> FakeStringResponse(status, body, request.uri()) })

    fun fetcherThrowing(error: Throwable): JvmHttpFetcher =
        JvmHttpFetcher(exchange = { throw error })

    "a 2xx response returns the body verbatim, untouched" {
        val rawBody = "<html><body>Daily Salah Times &amp; <script>noop()</script></body></html>"
        val result = fetcherReturning(200, rawBody).fetch(url)
        result shouldBe Result.Ok(rawBody)
    }

    "a null body on success is normalised to an empty string" {
        fetcherReturning(204, null).fetch(url) shouldBe Result.Ok("")
    }

    "a non-2xx status maps to NetworkError including the status code" {
        val result = fetcherReturning(503, "service unavailable").fetch(url)
        val error = result.errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
        error.detail shouldContain "503"
    }

    "a 404 status maps to NetworkError (not treated as a successful body)" {
        fetcherReturning(404, "not found").fetch(url)
            .errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
    }

    "a connection IOException maps to NetworkError" {
        val result = fetcherThrowing(IOException("connection refused")).fetch(url)
        val error = result.errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
        error.detail shouldContain "connection refused"
    }

    "a request timeout maps to NetworkError" {
        val result = fetcherThrowing(HttpConnectTimeoutException("timed out")).fetch(url)
        val error = result.errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
        error.detail shouldContain "Timed out"
    }

    "a non-HTTPS URL is refused without attempting any exchange" {
        var exchanged = false
        val fetcher = JvmHttpFetcher(exchange = {
            exchanged = true
            FakeStringResponse(200, "should not happen", it.uri())
        })
        val result = fetcher.fetch("http://chingfordmosque.com/")
        result.errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
        exchanged shouldBe false
    }

    "a malformed URL maps to NetworkError" {
        fetcherReturning(200, "x").fetch("ht!tp://not a url")
            .errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
    }
})

/**
 * A fake [HttpFetcher] (the seam) returning a canned result — used to demonstrate the provider
 * boundary is injectable/mockable so downstream tests never touch the network.
 */
class FakeHttpFetcherTest : StringSpec({

    "a fake fetcher can stand in for the seam and return a canned body" {
        val fake = HttpFetcher { Result.Ok("canned-body") }
        fake.fetch("https://example.test/") shouldBe Result.Ok("canned-body")
    }

    "a fake fetcher can simulate a network failure" {
        val fake = HttpFetcher { Result.Err(ProviderError.NetworkError("offline")) }
        fake.fetch("https://example.test/")
            .errorOrNull().shouldBeInstanceOf<ProviderError.NetworkError>()
    }
})

/**
 * Minimal [HttpResponse] of [String] used to drive [JvmHttpFetcher] without real I/O. Only the
 * members the fetcher reads (status code and body) carry meaningful values.
 */
private class FakeStringResponse(
    private val status: Int,
    private val body: String?,
    private val uri: URI,
) : HttpResponse<String> {
    override fun statusCode(): Int = status
    override fun request(): HttpRequest = HttpRequest.newBuilder(uri).build()
    override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()
    override fun headers(): HttpHeaders = HttpHeaders.of(emptyMap()) { _, _ -> true }
    override fun body(): String? = body
    override fun sslSession(): Optional<SSLSession> = Optional.empty()
    override fun uri(): URI = uri
    override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
}
