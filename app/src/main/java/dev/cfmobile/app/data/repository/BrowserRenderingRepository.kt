package dev.cfmobile.app.data.repository

import com.squareup.moshi.Moshi
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfError
import dev.cfmobile.app.data.remote.dto.ScreenshotRequest
import com.squareup.moshi.JsonClass
import java.io.IOException

@JsonClass(generateAdapter = true)
internal data class ScreenshotErrorEnvelope(
    val success: Boolean = false,
    val errors: List<CfError> = emptyList()
)

/**
 * Browser Rendering is a runtime API rather than a management surface: it renders a page and
 * answers with raw image bytes, not the JSON `CfEnvelope` every other endpoint uses. Failures
 * still come back as JSON, so this reads the body once and decides which it got.
 */
class BrowserRenderingRepository(private val api: CloudflareApi) {

    private val moshi = Moshi.Builder().build()
    private val errorAdapter = moshi.adapter(ScreenshotErrorEnvelope::class.java)

    suspend fun screenshot(accountId: String, url: String): ApiResult<ByteArray> {
        return try {
            val response = api.renderScreenshot(accountId, ScreenshotRequest(url))
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    ApiResult.Failure("Cloudflare returned an empty image", response.code())
                } else {
                    ApiResult.Success(bytes)
                }
            } else {
                val message = runCatching {
                    response.errorBody()?.string()
                        ?.let { errorAdapter.fromJson(it) }
                        ?.errors
                        ?.takeIf { it.isNotEmpty() }
                        ?.joinToString("; ") { it.message }
                }.getOrNull()
                ApiResult.Failure(message ?: "HTTP ${response.code()}: ${response.message()}", response.code())
            }
        } catch (e: IOException) {
            ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
        } catch (e: Exception) {
            ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
        }
    }
}
