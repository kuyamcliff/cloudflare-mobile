package dev.cfmobile.app.core.api

import dev.cfmobile.app.data.remote.CloudflareHosts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RawRequest(
    val method: String,
    /** Relative to the API base (`zones/abc/dns_records`), never an absolute URL. */
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
    val contentType: String = "application/json",
    /** Streams a local file as the body instead of [body] (spec 334). */
    val streamingBody: RequestBody? = null
)

data class RawResponse(
    val statusCode: Int,
    val headers: List<Pair<String, String>>,
    val contentType: String?,
    /** At most [RawApiClient.MAX_INLINE_BYTES] of the body, decoded as UTF-8. */
    val body: String,
    val truncated: Boolean,
    val totalBytes: Long,
    val durationMillis: Long
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}

class RequestRejected(message: String) : IllegalArgumentException(message)

/**
 * Executes arbitrary Cloudflare API requests for the API Explorer, GraphQL console and the
 * generic resource browser. It shares the app's OkHttp stack, so retries, history and the
 * host-bound [dev.cfmobile.app.data.remote.AuthInterceptor] all apply, and adds its own
 * boundary on top (spec 110, 292):
 *
 * - the target is always built from the configured API base; absolute URLs, scheme-relative
 *   paths and `..` segments are rejected rather than resolved;
 * - credential and routing headers a user types are refused, so the explorer can neither
 *   override the injected token nor smuggle a second one.
 */
class RawApiClient(
    private val client: OkHttpClient,
    private val hosts: CloudflareHosts
) {

    fun buildUrl(path: String, query: List<Pair<String, String>>): HttpUrl {
        val trimmed = path.trim()
        if (trimmed.contains("://") || trimmed.startsWith("//") || trimmed.contains('\\')) {
            throw RequestRejected("Only Cloudflare API paths are allowed. Enter a path such as zones/{zone_id}/dns_records.")
        }
        val clean = trimmed.removePrefix("/").removePrefix(hosts.apiBase.encodedPath.removePrefix("/")).substringBefore('?')
        val segments = clean.split('/').filter { it.isNotEmpty() }
        if (segments.any { it == ".." || it == "." }) throw RequestRejected("Relative path segments are not allowed.")
        if (segments.any { it.startsWith("{") && it.endsWith("}") }) {
            throw RequestRejected("Fill in every path parameter before sending.")
        }
        val builder = hosts.apiBase.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.filter { it.first.isNotBlank() }.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        val url = builder.build()
        if (!hosts.isApiHost(url)) throw RequestRejected("Requests are limited to the Cloudflare API.")
        return url
    }

    fun buildRequest(raw: RawRequest): Request {
        val method = raw.method.uppercase()
        if (method !in ALLOWED_METHODS) throw RequestRejected("Unsupported method $method")
        raw.headers.forEach { (name, _) ->
            if (name.trim().lowercase() in FORBIDDEN_HEADERS || name.trim().lowercase().startsWith("proxy-")) {
                throw RequestRejected("The $name header is managed by the app and cannot be set here.")
            }
        }
        val body: RequestBody? = when {
            method == "GET" -> null
            raw.streamingBody != null -> raw.streamingBody
            raw.body != null && raw.body.isNotBlank() -> raw.body.toRequestBody(raw.contentType.toMediaTypeOrNull())
            method == "DELETE" -> null
            else -> ByteArray(0).toRequestBody(null)
        }
        val builder = Request.Builder().url(buildUrl(raw.path, raw.query)).method(method, body)
        raw.headers.filter { it.first.isNotBlank() }.forEach { (k, v) -> builder.header(k.trim(), v) }
        return builder.build()
    }

    /** Runs the request. When [saveTo] is given the whole body is streamed there as well, so a
     *  large response can be saved without ever being held in memory (spec 335). */
    suspend fun execute(raw: RawRequest, saveTo: OutputStream? = null): RawResponse = withContext(Dispatchers.IO) {
        val request = buildRequest(raw)
        val started = System.nanoTime()
        client.newCall(request).await().use { response -> readResponse(response, started, saveTo) }
    }

    private fun readResponse(response: Response, started: Long, saveTo: OutputStream?): RawResponse {
        val body = response.body
        val source = body.source()
        val inline = okio.Buffer()
        var total = 0L
        val chunk = ByteArray(64 * 1024)
        val stream = source.inputStream()
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            saveTo?.write(chunk, 0, read)
            if (inline.size < MAX_INLINE_BYTES) {
                inline.write(chunk, 0, minOf(read.toLong(), MAX_INLINE_BYTES - inline.size).toInt())
            } else if (saveTo == null) {
                // Keep counting nothing further: without a destination there is no reason to
                // pull the rest of a huge body over the network.
                return RawResponse(
                    response.code, safeHeaders(response), response.header("Content-Type"),
                    inline.readUtf8(), truncated = true, totalBytes = total,
                    durationMillis = (System.nanoTime() - started) / 1_000_000
                )
            }
        }
        saveTo?.flush()
        return RawResponse(
            response.code, safeHeaders(response), response.header("Content-Type"),
            inline.readUtf8(), truncated = total > MAX_INLINE_BYTES, totalBytes = total,
            durationMillis = (System.nanoTime() - started) / 1_000_000
        )
    }

    private fun safeHeaders(response: Response): List<Pair<String, String>> =
        response.headers.filter { (name, _) -> name.lowercase() !in HIDDEN_RESPONSE_HEADERS }

    companion object {
        const val MAX_INLINE_BYTES = 2L * 1024 * 1024
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val FORBIDDEN_HEADERS = setOf(
            "authorization", "cookie", "host", "x-auth-key", "x-auth-email", "x-auth-user-service-key",
            "cf-access-client-secret", "cf-access-token", "content-length", "transfer-encoding", "connection"
        )
        private val HIDDEN_RESPONSE_HEADERS = setOf("set-cookie")
    }
}

/** Suspends on an OkHttp call and cancels it if the coroutine is cancelled (spec 158). */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
