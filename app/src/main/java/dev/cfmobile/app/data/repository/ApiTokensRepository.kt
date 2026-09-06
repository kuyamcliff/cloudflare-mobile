package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * The account's API tokens, by metadata only. Cloudflare returns a token's value exactly once,
 * from the call that creates or rolls it, and this app makes neither of those calls - so no
 * token value ever passes through here, and the app's own credential is never displayed.
 */
class ApiTokensRepository(private val api: CloudflareApi) {

    suspend fun listTokens(): ApiResult<List<ApiToken>> = safeApiCall { api.listApiTokens() }

    suspend fun deleteToken(tokenId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteApiToken(tokenId) }
}
