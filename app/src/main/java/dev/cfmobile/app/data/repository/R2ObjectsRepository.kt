package dev.cfmobile.app.data.repository

import dev.cfmobile.app.core.transfers.encodeObjectKey
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.R2Object
import dev.cfmobile.app.data.remote.safeApiCallPaged
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.toApiResult
import okhttp3.RequestBody
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException

data class R2Listing(val objects: List<R2Object>, val folders: List<String>, val cursor: String?, val truncated: Boolean)

data class PutResult(val isSuccess: Boolean, val status: Int?, val etag: String?, val message: String)

class OpenedObject(val stream: InputStream, val etag: String?, val contentType: String?, val length: Long)

/**
 * R2 objects over Cloudflare's REST API (spec 35): folder-style listing with server-side
 * prefix and cursor pagination, delete, streamed get and put. Large and resumable uploads use
 * the S3 path in core/transfers instead.
 */
class R2ObjectsRepository(private val api: CloudflareApi) {

    suspend fun list(accountId: String, bucket: String, prefix: String, cursor: String?, jurisdiction: String?, perPage: Int = 200): ApiResult<R2Listing> =
        when (val r = safeApiCallPaged { api.listR2Objects(accountId, bucket, prefix.ifEmpty { null }, "/", cursor, perPage, jurisdiction?.takeIf { it != "default" }) }) {
            is ApiResult.Success -> ApiResult.Success(
                R2Listing(
                    objects = r.data.items.filter { it.key != prefix },
                    folders = r.data.info?.delimited.orEmpty(),
                    cursor = r.data.info?.cursor?.takeIf { it.isNotBlank() },
                    truncated = r.data.info?.isTruncated == true
                )
            )
            is ApiResult.Failure -> r
        }

    suspend fun delete(accountId: String, bucket: String, key: String, jurisdiction: String?): ApiResult<Unit> =
        when (val r = safeApiCall { api.deleteR2Object(accountId, bucket, encodeObjectKey(key), jurisdiction?.takeIf { it != "default" }) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            // A null result with success=true is still a successful delete.
            is ApiResult.Failure -> if (r.httpCode in 200..299) ApiResult.Success(Unit) else r
        }

    /** Deletes one key at a time so each outcome is reported individually (spec 332). */
    suspend fun deleteMany(accountId: String, bucket: String, keys: List<String>, jurisdiction: String?): Map<String, ApiResult<Unit>> =
        keys.associateWith { delete(accountId, bucket, it, jurisdiction) }

    suspend fun putObjectRaw(accountId: String, bucket: String, key: String, body: RequestBody, jurisdiction: String?): PutResult = try {
        val response = api.putR2Object(accountId, bucket, encodeObjectKey(key), body, jurisdiction = jurisdiction?.takeIf { it != "default" })
        when (val r = response.toApiResult()) {
            is ApiResult.Success -> PutResult(true, response.code(), r.data.etag, "Uploaded")
            is ApiResult.Failure -> PutResult(false, r.httpCode, null, r.message)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        PutResult(false, null, null, e.message ?: "Network error")
    }

    /** Streams an object body. The caller owns and closes [OpenedObject.stream]. */
    suspend fun openObject(accountId: String, bucket: String, key: String, jurisdiction: String?): OpenedObject {
        val response = api.getR2Object(accountId, bucket, encodeObjectKey(key), jurisdiction?.takeIf { it != "default" })
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            val code = response.code()
            response.errorBody()?.close()
            throw if (code == 429 || code >= 500) IOException("Cloudflare returned HTTP $code") else R2RequestFailed(code)
        }
        return OpenedObject(body.byteStream(), response.headers()["ETag"], response.headers()["Content-Type"], body.contentLength())
    }

    /** Reads at most [maxBytes] for previews; never the whole of a large object (spec 188). */
    suspend fun readPrefix(accountId: String, bucket: String, key: String, jurisdiction: String?, maxBytes: Int): ApiResult<ByteArray> = try {
        val opened = openObject(accountId, bucket, key, jurisdiction)
        opened.stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() < maxBytes) {
                val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            ApiResult.Success(out.toByteArray())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: R2RequestFailed) {
        ApiResult.Failure("Cloudflare returned HTTP ${e.status}", e.status)
    } catch (e: IOException) {
        ApiResult.Failure("Network error: ${e.message}")
    }

    class R2RequestFailed(val status: Int) : IOException("Cloudflare returned HTTP $status")
}
