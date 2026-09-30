package dev.cfmobile.app.data.remote

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The only HTTP logging in the app. It never touches headers or bodies and it templates
 * identifiers out of the path, so a debug log line looks like
 * `GET /zones/{id}/dns_records status=200 duration=381ms` and can be pasted into a bug report.
 * Release builds install it with [enabled] = false, which makes it a pass-through.
 */
class RedactingLogInterceptor(
    private val enabled: Boolean,
    private val sink: (String) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())
        val request = chain.request()
        val started = System.nanoTime()
        val line = "${request.method} ${templatePath(request.url)}"
        return try {
            val response = chain.proceed(request)
            sink("$line status=${response.code} duration=${(System.nanoTime() - started) / 1_000_000}ms")
            response
        } catch (e: Exception) {
            sink("$line failed=${e.javaClass.simpleName} duration=${(System.nanoTime() - started) / 1_000_000}ms")
            throw e
        }
    }

    companion object {
        private val ID_SEGMENT = Regex("^[A-Za-z0-9_-]{16,}$")

        /** Replaces identifier-looking path segments with `{id}` and drops the query string. */
        fun templatePath(url: HttpUrl): String = url.pathSegments.joinToString("/", prefix = "/") { segment ->
            if (ID_SEGMENT.matches(segment) && segment.any { it.isDigit() }) "{id}" else segment
        }
    }
}
