package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ApiOperation
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only list of the API operations Cloudflare has discovered on this zone. Schema
 *  validation, mTLS certificates, and per-operation rate limits aren't covered. */
class ApiShieldRepository(private val api: CloudflareApi) {

    suspend fun listOperations(zoneId: String): ApiResult<List<ApiOperation>> =
        safeApiCall { api.listApiOperations(zoneId) }
}
