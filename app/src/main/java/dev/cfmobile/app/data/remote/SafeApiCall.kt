package dev.cfmobile.app.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.cfmobile.app.data.remote.dto.CfEnvelope
import dev.cfmobile.app.data.remote.dto.CfError
import retrofit2.Response
import java.io.IOException

@JsonClass(generateAdapter = true)
internal data class ErrorEnvelope(
    val success: Boolean = false,
    val errors: List<CfError> = emptyList()
)

private val errorMoshi = Moshi.Builder().build()
private val errorAdapter = errorMoshi.adapter(ErrorEnvelope::class.java)

private fun CfError.format() = if (code != 0) "$message (code $code)" else message

private fun errorsToMessage(errors: List<CfError>?): String? {
    if (errors.isNullOrEmpty()) return null
    return errors.joinToString("; ") { it.format() }
}

/** Turns a Cloudflare [Response] into an [ApiResult], reading Cloudflare's own error payload
 *  from the body on non-2xx responses instead of just surfacing the HTTP status line. */
fun <T> Response<CfEnvelope<T>>.toApiResult(): ApiResult<T> {
    if (isSuccessful) {
        val envelope = body()
        val result = envelope?.result
        return if (envelope != null && envelope.success && result != null) {
            ApiResult.Success(result)
        } else {
            ApiResult.Failure(errorsToMessage(envelope?.errors) ?: "Cloudflare returned an empty result", code())
        }
    }
    val parsedErrors = try {
        errorBody()?.string()?.let { errorAdapter.fromJson(it)?.errors }
    } catch (e: Exception) {
        null
    }
    return ApiResult.Failure(errorsToMessage(parsedErrors) ?: "HTTP ${code()}: ${message()}", code())
}

/** Runs a suspend Retrofit call, converting network/parse exceptions into [ApiResult.Failure]
 *  so ViewModels never need to catch exceptions themselves. */
suspend fun <T> safeApiCall(block: suspend () -> Response<CfEnvelope<T>>): ApiResult<T> {
    return try {
        block().toApiResult()
    } catch (e: IOException) {
        ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
    } catch (e: Exception) {
        ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
    }
}

/** Same as [safeApiCall] but for endpoints that return no meaningful result payload
 *  (delete/purge calls) where we only care whether `success` was true. */
suspend fun safeApiCallUnit(block: suspend () -> Response<CfEnvelope<Map<String, String>>>): ApiResult<Unit> {
    return when (val result = safeApiCall(block)) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Failure -> result
    }
}

/**
 * GraphQL counterpart to [safeApiCall]. Cloudflare's analytics GraphQL API does not use the
 * REST `CfEnvelope` shape: it answers HTTP 200 with `{"data": ..., "errors": [...]}`, and a
 * query that failed still arrives as a 200. So a non-empty `errors` array is the real failure
 * signal here, not the status line.
 */
suspend fun <T> safeGraphQlCall(block: suspend () -> Response<GraphQlResponse<T>>): ApiResult<T> {
    return try {
        val response = block()
        val body = response.body()
        val errorMessage = body?.errors?.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.message }
        val data = body?.data
        when {
            errorMessage != null -> ApiResult.Failure(errorMessage, response.code())
            !response.isSuccessful -> ApiResult.Failure("HTTP ${response.code()}: ${response.message()}", response.code())
            data == null -> ApiResult.Failure("Cloudflare returned an empty result", response.code())
            else -> ApiResult.Success(data)
        }
    } catch (e: IOException) {
        ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
    } catch (e: Exception) {
        ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
    }
}
