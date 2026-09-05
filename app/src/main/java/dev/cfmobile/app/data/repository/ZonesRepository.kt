package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.remote.safeApiCall

class ZonesRepository(private val api: CloudflareApi) {
    suspend fun listZones(accountId: String? = null, search: String? = null): ApiResult<List<CfZone>> =
        safeApiCall { api.listZones(accountId = accountId, name = search?.ifBlank { null }) }

    suspend fun getZone(zoneId: String): ApiResult<CfZone> =
        safeApiCall { api.getZone(zoneId) }
}
