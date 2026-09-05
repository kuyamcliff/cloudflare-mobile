package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfTunnel
import dev.cfmobile.app.data.remote.dto.TunnelCreate
import dev.cfmobile.app.data.remote.safeApiCall

/** Zero Trust Tunnels: list/create/delete only. Creates remotely-managed tunnels
 *  (config_src "cloudflare") so no tunnel_secret needs generating on-device. Actually running
 *  a tunnel still requires the cloudflared daemon on a machine elsewhere - that's out of
 *  scope for a mobile app, see CapabilityRegistry's migrationHint. */
class TunnelsRepository(private val api: CloudflareApi) {

    suspend fun listTunnels(accountId: String): ApiResult<List<CfTunnel>> =
        safeApiCall { api.listTunnels(accountId) }

    suspend fun createTunnel(accountId: String, name: String): ApiResult<CfTunnel> =
        safeApiCall { api.createTunnel(accountId, TunnelCreate(name = name)) }

    suspend fun deleteTunnel(accountId: String, tunnelId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteTunnel(accountId, tunnelId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
