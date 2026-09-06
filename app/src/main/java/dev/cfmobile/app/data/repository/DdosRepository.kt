package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.DdosRuleset
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only view of the zone's HTTP DDoS managed ruleset (the ddos_l7 phase entrypoint).
 *  Cloudflare's L7 DDoS protection is always on; this shows the overrides layered on top, so
 *  "no entrypoint ruleset" is a normal state meaning no overrides exist. */
class DdosRepository(private val api: CloudflareApi) {

    suspend fun getEntrypoint(zoneId: String): ApiResult<DdosRuleset> =
        safeApiCall { api.getDdosEntrypoint(zoneId) }
}
