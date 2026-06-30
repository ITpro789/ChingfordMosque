package com.chingfordmosque.prayertimes.android.platform

import com.chingfordmosque.prayertimes.data.provider.HttpFetcher
import com.chingfordmosque.prayertimes.domain.ProviderError
import com.chingfordmosque.prayertimes.domain.Result
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Android binding of the core [HttpFetcher] seam, backed by OkHttp.
 *
 * OkHttp is used (rather than `java.net.http.HttpClient`, which requires API 34) so the fetch
 * works on the app's `minSdk = 26`. The contract mirrors the JVM
 * [com.chingfordmosque.prayertimes.data.provider.JvmHttpFetcher]: a single HTTPS GET, refusing
 * non-HTTPS URLs, mapping any [IOException]/timeout/non-2xx response to
 * [ProviderError.NetworkError]. The raw body is returned verbatim — interpretation is left
 * entirely to the core parser.
 */
class OkHttpFetcher(
    private val client: OkHttpClient = defaultClient(),
) : HttpFetcher {

    override fun fetch(url: String): Result<String, ProviderError> {
        if (!url.startsWith("https://", ignoreCase = true)) {
            return Result.Err(ProviderError.NetworkError("Refusing non-HTTPS request to '$url'"))
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.Err(ProviderError.NetworkError("HTTP ${response.code} from '$url'"))
                } else {
                    Result.Ok(response.body?.string() ?: "")
                }
            }
        } catch (e: IOException) {
            Result.Err(ProviderError.NetworkError("Network error fetching '$url': ${e.message}"))
        } catch (e: IllegalArgumentException) {
            Result.Err(ProviderError.NetworkError("Malformed URL '$url': ${e.message}"))
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
    }
}
