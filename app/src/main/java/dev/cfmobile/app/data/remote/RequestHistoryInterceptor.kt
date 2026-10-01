package dev.cfmobile.app.data.remote

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Safe metadata about one Cloudflare API call. Never contains headers, bodies, or any
 *  query parameter whose name suggests a credential. */
data class RequestRecord(
    val method: String,
    /** Path relative to the API base, e.g. `zones/abc/dns_records`. */
    val path: String,
    val query: String?,
    val statusCode: Int?,
    val durationMillis: Long,
    val timestamp: Long,
    val errorClass: String?
)

fun interface RequestRecorder {
    fun record(record: RequestRecord)
}

/**
 * Feeds [RequestRecorder] and [NetworkStatus] for every request to the API host. Sits
 * outside the retry interceptor so one user action is one history row, with the final status.
 */
class RequestHistoryInterceptor(
    private val hosts: CloudflareHosts,
    private val recorder: RequestRecorder?,
    private val status: NetworkStatus?,
    private val clock: () -> Long = System::currentTimeMillis
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!hosts.isApiHost(request.url)) return chain.proceed(request)
        val started = System.nanoTime()
        val timestamp = clock()
        val relative = relativePath(request.url)
        val query = sanitizedQuery(request.url)
        try {
            val response = chain.proceed(request)
            val duration = (System.nanoTime() - started) / 1_000_000
            status?.onResponse(response.code, duration, response.header("ratelimit-remaining")?.substringBefore(';')?.trim()?.toIntOrNull())
            recorder?.record(RequestRecord(request.method, relative, query, response.code, duration, timestamp, null))
            return response
        } catch (e: Exception) {
            val duration = (System.nanoTime() - started) / 1_000_000
            recorder?.record(RequestRecord(request.method, relative, query, null, duration, timestamp, e.javaClass.simpleName))
            throw e
        }
    }

    private fun relativePath(url: HttpUrl): String {
        val base = hosts.apiBase.encodedPath
        val full = url.encodedPath
        return (if (full.startsWith(base)) full.removePrefix(base) else full.trimStart('/'))
    }

    companion object {
        private val SENSITIVE = Regex("(token|secret|key|signature|password|credential|auth|cf_clearance|x-amz)", RegexOption.IGNORE_CASE)

        fun sanitizedQuery(url: HttpUrl): String? {
            if (url.querySize == 0) return null
            return (0 until url.querySize).joinToString("&") { i ->
                val name = url.queryParameterName(i)
                val value = if (SENSITIVE.containsMatchIn(name)) "REDACTED" else url.queryParameterValue(i).orEmpty()
                "$name=$value"
            }
        }
    }
}
