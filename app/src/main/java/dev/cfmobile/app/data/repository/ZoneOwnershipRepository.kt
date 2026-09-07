package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CustomNameserver
import dev.cfmobile.app.data.remote.dto.ZoneCustomNameservers
import dev.cfmobile.app.data.remote.dto.ZoneCustomNameserversWrite
import dev.cfmobile.app.data.remote.dto.ZoneHold
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Who owns the zone's namespace: the custom nameservers it answers on, and the hold that stops
 * the same domain being claimed by another Cloudflare account.
 */
class ZoneOwnershipRepository(private val api: CloudflareApi) {

    /** Custom nameserver sets are configured per account; a zone only picks one. */
    suspend fun listAccountNameservers(accountId: String): ApiResult<List<CustomNameserver>> =
        safeApiCall { api.listAccountCustomNameservers(accountId) }

    suspend fun getZoneNameservers(zoneId: String): ApiResult<ZoneCustomNameservers> =
        safeApiCall { api.getZoneCustomNameservers(zoneId) }

    suspend fun setZoneNameservers(zoneId: String, enabled: Boolean, nsSet: Int?): ApiResult<ZoneCustomNameservers> =
        safeApiCall {
            api.updateZoneCustomNameservers(
                zoneId,
                ZoneCustomNameserversWrite(enabled = enabled, nsSet = nsSet.takeIf { enabled })
            )
        }

    suspend fun getHold(zoneId: String): ApiResult<ZoneHold> =
        safeApiCall { api.getZoneHold(zoneId) }

    suspend fun createHold(zoneId: String, includeSubdomains: Boolean): ApiResult<ZoneHold> =
        safeApiCall { api.createZoneHold(zoneId, includeSubdomains) }

    /** Removing the hold is what has to happen before the domain can move to another account -
     *  including a move you're making on purpose. */
    suspend fun removeHold(zoneId: String): ApiResult<ZoneHold> =
        safeApiCall { api.removeZoneHold(zoneId) }
}
