package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AiGateway
import dev.cfmobile.app.data.remote.dto.AiGatewayWrite
import dev.cfmobile.app.data.remote.safeApiCall

/** AI Gateways: the proxy that sits in front of model providers to cache, rate-limit, and log.
 *  The logs themselves are a separate dataset and aren't fetched here. */
class AiGatewayRepository(private val api: CloudflareApi) {

    suspend fun listGateways(accountId: String): ApiResult<List<AiGateway>> =
        safeApiCall { api.listAiGateways(accountId) }

    suspend fun createGateway(accountId: String, gateway: AiGatewayWrite): ApiResult<AiGateway> =
        safeApiCall { api.createAiGateway(accountId, gateway) }

    /** Cloudflare answers with the deleted gateway rather than an empty result. */
    suspend fun deleteGateway(accountId: String, gatewayId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteAiGateway(accountId, gatewayId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
