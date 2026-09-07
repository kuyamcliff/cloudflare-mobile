package dev.cfmobile.app.data.repository

import com.squareup.moshi.Moshi
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CustomPage
import dev.cfmobile.app.data.remote.dto.CustomPageWrite
import dev.cfmobile.app.data.remote.safeApiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Cloudflare's error and challenge pages. A customized page is HTML Cloudflare fetches from a
 *  URL you host; reverting sets the page back to Cloudflare's default. */
class CustomPagesRepository(private val api: CloudflareApi) {

    /**
     * Reverting means sending `"url": null` - Cloudflare wants the field present and empty, not
     * absent - and Moshi drops null fields when writing. `serializeNulls()` keeps this one
     * adapter honest without changing how every other request body is encoded.
     */
    private val writeAdapter = Moshi.Builder().build()
        .adapter(CustomPageWrite::class.java)
        .serializeNulls()

    private fun body(write: CustomPageWrite): RequestBody =
        writeAdapter.toJson(write).toRequestBody(JSON_MEDIA_TYPE)

    suspend fun listPages(zoneId: String): ApiResult<List<CustomPage>> =
        safeApiCall { api.listCustomPages(zoneId) }

    suspend fun customize(zoneId: String, pageId: String, url: String): ApiResult<CustomPage> =
        safeApiCall {
            api.updateCustomPage(zoneId, pageId, body(CustomPageWrite(url = url, state = STATE_CUSTOMIZED)))
        }

    suspend fun revert(zoneId: String, pageId: String): ApiResult<CustomPage> =
        safeApiCall {
            api.updateCustomPage(zoneId, pageId, body(CustomPageWrite(url = null, state = STATE_DEFAULT)))
        }

    companion object {
        const val STATE_DEFAULT = "default"
        const val STATE_CUSTOMIZED = "customized"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
