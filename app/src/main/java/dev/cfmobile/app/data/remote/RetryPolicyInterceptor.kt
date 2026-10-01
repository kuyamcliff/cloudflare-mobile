package dev.cfmobile.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.random.Random

/**
 * Honors Cloudflare's rate limiting and retries only what is safe to retry.
 *
 * - 429: Cloudflare did not process the request, so any method may be retried after the
 *   server's `Retry-After` (or `ratelimit-reset`) delay. Waits longer than [maxWaitMillis]
 *   are not slept through: the 429 is returned so the UI can say when to try again.
 * - 502/503/504 and connection failures: retried with exponential backoff and jitter, but
 *   only for idempotent methods (GET/HEAD/PUT/DELETE). A POST/PATCH is never replayed
 *   automatically, because Cloudflare may already have applied it.
 * - 400/401/403/404 and every other status: never retried.
 *
 * Waiting happens on OkHttp's worker thread in short slices so a cancelled call (the user
 * left the screen) stops waiting immediately instead of sleeping out the full delay.
 */
class RetryPolicyInterceptor(
    private val status: NetworkStatus? = null,
    private val maxRetries: Int = 2,
    private val maxWaitMillis: Long = 20_000,
    private val baseBackoffMillis: Long = 500,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = request.method in IDEMPOTENT
        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (e is InterruptedIOException && chain.call().isCanceled()) throw e
                status?.onNetworkFailure()
                if (!idempotent || attempt >= maxRetries || chain.call().isCanceled()) throw e
                waitFor(chain, backoff(attempt))
                attempt++
                continue
            }

            val code = response.code
            if (code == 429) {
                val delay = retryDelayMillis(response) ?: backoff(attempt)
                status?.onRateLimited(delay)
                if (attempt >= maxRetries || delay > maxWaitMillis) return response
                response.close()
                waitFor(chain, delay)
                attempt++
                continue
            }
            if (code in RETRYABLE_SERVER && idempotent && attempt < maxRetries) {
                val delay = retryDelayMillis(response) ?: backoff(attempt)
                if (delay <= maxWaitMillis) {
                    response.close()
                    waitFor(chain, delay)
                    attempt++
                    continue
                }
            }
            return response
        }
    }

    private fun backoff(attempt: Int): Long {
        val exp = baseBackoffMillis shl attempt.coerceAtMost(6)
        return exp + Random.nextLong(0, baseBackoffMillis)
    }

    private fun waitFor(chain: Interceptor.Chain, millis: Long) {
        var remaining = millis
        while (remaining > 0) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val slice = minOf(remaining, 250)
            try {
                sleeper(slice)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while waiting to retry")
            }
            remaining -= slice
        }
    }

    companion object {
        private val IDEMPOTENT = setOf("GET", "HEAD", "PUT", "DELETE", "OPTIONS")
        private val RETRYABLE_SERVER = setOf(502, 503, 504)

        /** `Retry-After` in seconds (Cloudflare's form), falling back to `ratelimit-reset`. */
        fun retryDelayMillis(response: Response): Long? {
            val header = response.header("Retry-After") ?: response.header("ratelimit-reset") ?: return null
            val seconds = header.trim().toLongOrNull() ?: return null
            return (seconds.coerceIn(0, 3600)) * 1000
        }
    }
}
