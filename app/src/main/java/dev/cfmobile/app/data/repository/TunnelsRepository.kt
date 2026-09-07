package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.TunnelRoute
import dev.cfmobile.app.data.remote.dto.TunnelRouteWrite
import dev.cfmobile.app.data.remote.dto.VirtualNetwork
import dev.cfmobile.app.data.remote.dto.VirtualNetworkWrite
import dev.cfmobile.app.data.remote.dto.CfTunnel
import dev.cfmobile.app.data.remote.dto.TunnelCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

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

    /** Private network routes reachable through a tunnel. */
    suspend fun listRoutes(accountId: String): ApiResult<List<TunnelRoute>> =
        safeApiCall { api.listTunnelRoutes(accountId) }

    suspend fun createRoute(
        accountId: String,
        network: String,
        tunnelId: String,
        comment: String?,
        virtualNetworkId: String?
    ): ApiResult<TunnelRoute> =
        safeApiCall {
            api.createTunnelRoute(
                accountId,
                TunnelRouteWrite(
                    network = network,
                    tunnelId = tunnelId,
                    comment = comment,
                    virtualNetworkId = virtualNetworkId
                )
            )
        }

    /** Cloudflare answers a route delete with the deleted route rather than an empty result,
     *  so this maps it rather than using the shared unit helper. */
    suspend fun deleteRoute(accountId: String, routeId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteTunnelRoute(accountId, routeId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }

    /** Virtual networks let overlapping private ranges coexist behind different tunnels. */
    suspend fun listVirtualNetworks(accountId: String): ApiResult<List<VirtualNetwork>> =
        safeApiCall { api.listVirtualNetworks(accountId) }

    suspend fun createVirtualNetwork(accountId: String, name: String, comment: String?): ApiResult<VirtualNetwork> =
        safeApiCall { api.createVirtualNetwork(accountId, VirtualNetworkWrite(name = name, comment = comment)) }

    suspend fun deleteVirtualNetwork(accountId: String, networkId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteVirtualNetwork(accountId, networkId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
