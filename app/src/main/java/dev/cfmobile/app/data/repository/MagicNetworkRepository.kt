package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.MagicGreTunnel
import dev.cfmobile.app.data.remote.dto.MagicIpsecTunnel
import dev.cfmobile.app.data.remote.dto.MagicRoute
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only Magic WAN / Transit inventory. Each list arrives nested under its own key rather
 *  than as a bare array, so each call unwraps one level. Changing network routing from a phone
 *  is not something this app offers. */
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
}
