package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.KvNamespace
import dev.cfmobile.app.data.remote.dto.KvNamespaceCreate
import dev.cfmobile.app.data.remote.dto.KvKey
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Workers KV: namespaces, plus browsing and editing the keys inside one. Values are read
 *  and written as text - a binary value is reported rather than rendered as mojibake. */
class KvRepository(private val api: CloudflareApi) {

    suspend fun listNamespaces(accountId: String): ApiResult<List<KvNamespace>> =
        safeApiCall { api.listKvNamespaces(accountId) }

    suspend fun createNamespace(accountId: String, title: String): ApiResult<KvNamespace> =
        safeApiCall { api.createKvNamespace(accountId, KvNamespaceCreate(title = title)) }

    suspend fun deleteNamespace(accountId: String, namespaceId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteKvNamespace(accountId, namespaceId) }

    suspend fun listKeys(accountId: String, namespaceId: String): ApiResult<List<KvKey>> =
        safeApiCall { api.listKvKeys(accountId, namespaceId) }

    /** Values come back as raw bytes rather than JSON, so this reads the body itself. A value
     *  that isn't UTF-8 text can't be shown meaningfully on a phone, and is reported as such
     *  rather than rendered as mojibake. */
    suspend fun getValue(accountId: String, namespaceId: String, key: String): ApiResult<String> {
        return try {
            val response = api.getKvValue(accountId, namespaceId, key)
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes() ?: ByteArray(0)
                val text = bytes.toString(Charsets.UTF_8)
                // Round-tripping detects bytes that aren't valid UTF-8: the decode would have
                // substituted replacement characters.
                if (text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                    ApiResult.Success(text)
                } else {
                    ApiResult.Failure("This key holds binary data, which this app can't display", response.code())
                }
            } else {
                ApiResult.Failure("HTTP ${response.code()}: ${response.message()}", response.code())
            }
        } catch (e: java.io.IOException) {
            ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
        } catch (e: Exception) {
            ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
        }
    }

    suspend fun putValue(accountId: String, namespaceId: String, key: String, value: String): ApiResult<Unit> =
        safeApiCallUnit {
            api.putKvValue(
                accountId,
                namespaceId,
                key,
                value.toRequestBody("text/plain".toMediaType())
            )
        }

    suspend fun deleteValue(accountId: String, namespaceId: String, key: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteKvValue(accountId, namespaceId, key) }
}
