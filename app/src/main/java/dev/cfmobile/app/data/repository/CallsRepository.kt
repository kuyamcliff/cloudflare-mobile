package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CallsApp
import dev.cfmobile.app.data.remote.dto.CallsAppWrite
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Cloudflare Calls applications - the credential pair a client uses to reach the SFU or TURN
 * service. The app secret comes back exactly once, in the create response, the same way an
 * Access service token does.
 */
class CallsRepository(private val api: CloudflareApi) {

    suspend fun listApps(accountId: String): ApiResult<List<CallsApp>> =
        safeApiCall { api.listCallsApps(accountId) }

    suspend fun createApp(accountId: String, name: String): ApiResult<CallsApp> =
        safeApiCall { api.createCallsApp(accountId, CallsAppWrite(name)) }

    suspend fun deleteApp(accountId: String, appId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteCallsApp(accountId, appId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
