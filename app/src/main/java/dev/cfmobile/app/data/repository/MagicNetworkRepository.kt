package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.MagicGreTunnel
import dev.cfmobile.app.data.remote.dto.MagicIpsecTunnel
import dev.cfmobile.app.data.remote.dto.MagicRoute
import dev.cfmobile.app.data.remote.dto.MagicRouteWrite
import dev.cfmobile.app.data.remote.dto.MagicSite
import dev.cfmobile.app.data.remote.dto.MagicSiteLan
import dev.cfmobile.app.data.remote.dto.MagicSiteWan
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Magic WAN / Transit. Most lists arrive nested under their own key rather than as a bare
 * array, so those calls unwrap one level.
 *
 * The tunnels stay read-only: a GRE or IPsec tunnel carries the interface addresses and health
 * check settings of a physical link, and getting one wrong takes a site offline until someone
 * is at a console. Static routes are different - a route is one prefix pointed at one next hop,
 * and adding or removing one is the change that gets made under time pressure.
 */
class MagicNetworkRepository(private val api: CloudflareApi) {

    suspend fun listGreTunnels(accountId: String): ApiResult<List<MagicGreTunnel>> =
        when (val result = safeApiCall { api.listMagicGreTunnels(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.greTunnels)
            is ApiResult.Failure -> result
        }

    suspend fun listIpsecTunnels(accountId: String): ApiResult<List<MagicIpsecTunnel>> =
        when (val result = safeApiCall { api.listMagicIpsecTunnels(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.ipsecTunnels)
            is ApiResult.Failure -> result
        }

    suspend fun listRoutes(accountId: String): ApiResult<List<MagicRoute>> =
        when (val result = safeApiCall { api.listMagicRoutes(accountId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.routes)
            is ApiResult.Failure -> result
        }

    /** Cloudflare answers with the routes it created, since one call can add several. */
    suspend fun createRoute(accountId: String, route: MagicRouteWrite): ApiResult<List<MagicRoute>> =
        when (val result = safeApiCall { api.createMagicRoute(accountId, route) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.routes)
            is ApiResult.Failure -> result
        }

    /** Answers with the deleted route rather than an empty result. */
    suspend fun deleteRoute(accountId: String, routeId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteMagicRoute(accountId, routeId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }

    suspend fun listSites(accountId: String): ApiResult<List<MagicSite>> =
        safeApiCall { api.listMagicSites(accountId) }

    suspend fun listSiteLans(accountId: String, siteId: String): ApiResult<List<MagicSiteLan>> =
        when (val result = safeApiCall { api.listMagicSiteLans(accountId, siteId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.lans)
            is ApiResult.Failure -> result
        }

    suspend fun listSiteWans(accountId: String, siteId: String): ApiResult<List<MagicSiteWan>> =
        when (val result = safeApiCall { api.listMagicSiteWans(accountId, siteId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.wans)
            is ApiResult.Failure -> result
        }
}
