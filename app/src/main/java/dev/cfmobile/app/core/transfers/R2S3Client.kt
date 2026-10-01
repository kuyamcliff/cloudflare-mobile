package dev.cfmobile.app.core.transfers

import dev.cfmobile.app.core.api.await
import dev.cfmobile.app.data.remote.CloudflareHosts
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/** R2 S3 API credentials. Distinct from a Cloudflare API token (spec 36, 138). */
data class R2S3Credentials(val accessKeyId: String, val secretAccessKey: String, val jurisdiction: String? = null)

class S3Exception(val status: Int, val code: String?, message: String) : IOException(message)

/**
 * Minimal R2 S3 client for what the transfer engine needs: multipart upload and ranged GET.
 * It only ever talks to `<account>.r2.cloudflarestorage.com`, checked on every request, and
 * never carries the Cloudflare API token.
 */
class R2S3Client(private val client: OkHttpClient, private val baseUrlOverride: String? = null) {

    fun endpoint(accountId: String, jurisdiction: String?): HttpUrl {
        baseUrlOverride?.let { return it.toHttpUrl() }
        val j = jurisdiction?.takeIf { it.isNotBlank() && it != "default" }?.let { "$it." }.orEmpty()
        return "https://$accountId.${j}r2.cloudflarestorage.com/".toHttpUrl()
    }

    private fun objectUrl(base: HttpUrl, bucket: String, key: String, query: Map<String, String>): Pair<HttpUrl, String> {
        val path = "/" + SigV4.encode(bucket) + "/" + SigV4.encode(key, keepSlash = true)
        val b = base.newBuilder().encodedPath(path)
        query.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build() to path
    }

    private suspend fun call(
        creds: R2S3Credentials,
        accountId: String,
        method: String,
        bucket: String,
        key: String,
        query: Map<String, String> = emptyMap(),
        body: RequestBody? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        payloadHash: String = SigV4.UNSIGNED_PAYLOAD
    ): Response {
        val base = endpoint(accountId, creds.jurisdiction)
        val (url, path) = objectUrl(base, bucket, key, query)
        if (baseUrlOverride == null) require(CloudflareHosts.isR2S3Host(url)) { "Refusing to send R2 credentials to ${url.host}" }
        val hostHeader = if (url.port == HttpUrl.defaultPort(url.scheme)) url.host else "${url.host}:${url.port}"
        val signed = SigV4.sign(method, path, query, extraHeaders + ("host" to hostHeader), payloadHash, creds.accessKeyId, creds.secretAccessKey)
        val request = Request.Builder().url(url).method(method, body).apply {
            (extraHeaders + signed.headers).forEach { (k, v) -> header(k, v) }
        }.build()
        return client.newCall(request).await()
    }

    private fun failure(r: Response): S3Exception {
        val text = runCatching { r.body.string() }.getOrDefault("")
        val code = Regex("<Code>([^<]+)</Code>").find(text)?.groupValues?.get(1)
        val msg = Regex("<Message>([^<]+)</Message>").find(text)?.groupValues?.get(1)
        return S3Exception(r.code, code, listOfNotNull("R2 returned HTTP ${r.code}", code, msg).joinToString(": "))
    }

    suspend fun createMultipartUpload(creds: R2S3Credentials, accountId: String, bucket: String, key: String, contentType: String?): String {
        val empty = ByteArray(0)
        call(
            creds, accountId, "POST", bucket, key, mapOf("uploads" to ""),
            body = empty.toRequestBody(null),
            extraHeaders = contentType?.let { mapOf("content-type" to it) }.orEmpty(),
            payloadHash = SigV4.sha256Hex(empty)
        ).use { r ->
            if (!r.isSuccessful) throw failure(r)
            val xml = r.body.string()
            return Regex("<UploadId>([^<]+)</UploadId>").find(xml)?.groupValues?.get(1) ?: throw IOException("R2 did not return an UploadId")
        }
    }

    /** Returns the part's ETag (without quotes). */
    suspend fun uploadPart(creds: R2S3Credentials, accountId: String, bucket: String, key: String, uploadId: String, partNumber: Int, body: RequestBody): String {
        call(creds, accountId, "PUT", bucket, key, mapOf("partNumber" to partNumber.toString(), "uploadId" to uploadId), body = body).use { r ->
            if (!r.isSuccessful) throw failure(r)
            return r.header("ETag")?.trim('"') ?: throw IOException("R2 returned no ETag for part $partNumber")
        }
    }

    suspend fun completeMultipartUpload(creds: R2S3Credentials, accountId: String, bucket: String, key: String, uploadId: String, parts: List<Pair<Int, String>>): String? {
        val xml = buildString {
            append("<CompleteMultipartUpload>")
            parts.sortedBy { it.first }.forEach { (n, etag) -> append("<Part><PartNumber>$n</PartNumber><ETag>\"$etag\"</ETag></Part>") }
            append("</CompleteMultipartUpload>")
        }.toByteArray()
        call(
            creds, accountId, "POST", bucket, key, mapOf("uploadId" to uploadId),
            body = xml.toRequestBody("application/xml".toMediaTypeOrNull()),
            extraHeaders = mapOf("content-type" to "application/xml"),
            payloadHash = SigV4.sha256Hex(xml)
        ).use { r ->
            val text = r.body.string()
            // S3 can report an error inside a 200 response for CompleteMultipartUpload.
            if (!r.isSuccessful || text.contains("<Error>")) throw S3Exception(r.code, Regex("<Code>([^<]+)</Code>").find(text)?.groupValues?.get(1), "R2 could not complete the upload")
            return Regex("<ETag>\"?([^<\"]+)\"?</ETag>").find(text)?.groupValues?.get(1)
        }
    }

    suspend fun abortMultipartUpload(creds: R2S3Credentials, accountId: String, bucket: String, key: String, uploadId: String) {
        call(creds, accountId, "DELETE", bucket, key, mapOf("uploadId" to uploadId)).use { r ->
            if (!r.isSuccessful && r.code != 404) throw failure(r)
        }
    }

    /** Ranged GET for resumable downloads. Caller closes the response. */
    suspend fun getObject(creds: R2S3Credentials, accountId: String, bucket: String, key: String, fromByte: Long): Response {
        val headers = if (fromByte > 0) mapOf("range" to "bytes=$fromByte-") else emptyMap()
        val r = call(creds, accountId, "GET", bucket, key, extraHeaders = headers)
        if (!r.isSuccessful) { val e = failure(r); r.close(); throw e }
        return r
    }

    /** Cheap credential check: HEAD on the bucket. */
    suspend fun headBucket(creds: R2S3Credentials, accountId: String, bucket: String): Int {
        val base = endpoint(accountId, creds.jurisdiction)
        val path = "/" + SigV4.encode(bucket)
        val url = base.newBuilder().encodedPath(path).build()
        val hostHeader = if (url.port == HttpUrl.defaultPort(url.scheme)) url.host else "${url.host}:${url.port}"
        val signed = SigV4.sign("HEAD", path, emptyMap(), mapOf("host" to hostHeader), SigV4.sha256Hex(ByteArray(0)), creds.accessKeyId, creds.secretAccessKey)
        val request = Request.Builder().url(url).head().apply { signed.headers.forEach { (k, v) -> header(k, v) } }.build()
        return client.newCall(request).await().use { it.code }
    }
}
