package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ZarazConfig
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Zaraz, read-only. Its configuration is one large document covering every third-party tool,
 * trigger, and consent setting, and Cloudflare's write endpoint replaces the whole thing -
 * editing a fragment of it from a phone risks dropping fields this app doesn't model.
 */
class ZarazRepository(private val api: CloudflareApi) {

    suspend fun getConfig(zoneId: String): ApiResult<ZarazConfig> =
        safeApiCall { api.getZarazConfig(zoneId) }
}
