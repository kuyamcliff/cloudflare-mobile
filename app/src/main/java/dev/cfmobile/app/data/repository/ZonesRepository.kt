package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.fetchAllPages
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallPaged

class ZonesRepository(private val api: CloudflareApi) {
    /** Up to [maxPages] x 50 zones. Search is server-side (spec 156). */
    suspend fun listZones(accountId: String? = null, search: String? = null, maxPages: Int = 20): ApiResult<List<CfZone>> =
        fetchAllPages(maxPages) { page ->
            safeApiCallPaged { api.listZones(accountId = accountId, name = search?.ifBlank { null }?.let { "contains:$it" }, page = page) }
        }

    suspend fun getZone(zoneId: String): ApiResult<CfZone> =
        safeApiCall { api.getZone(zoneId) }
}
