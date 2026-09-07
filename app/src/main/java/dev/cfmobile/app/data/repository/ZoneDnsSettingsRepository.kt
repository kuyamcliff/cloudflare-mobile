package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettings
import dev.cfmobile.app.data.remote.dto.ZoneDnsSettingsUpdate
import dev.cfmobile.app.data.remote.safeApiCall

/** Zone-wide DNS behaviour, which is a different resource from the individual records:
 *  CNAME flattening, Foundation DNS, multi-provider, and secondary overrides. */
class ZoneDnsSettingsRepository(private val api: CloudflareApi) {

    suspend fun getSettings(zoneId: String): ApiResult<ZoneDnsSettings> =
        safeApiCall { api.getZoneDnsSettings(zoneId) }

    /** Only the changed field is sent; Cloudflare merges it into the stored settings, so the
     *  other switches are left exactly as they were. */
    suspend fun update(zoneId: String, update: ZoneDnsSettingsUpdate): ApiResult<ZoneDnsSettings> =
        safeApiCall { api.updateZoneDnsSettings(zoneId, update) }
}
