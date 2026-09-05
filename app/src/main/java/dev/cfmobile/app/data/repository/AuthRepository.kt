package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.local.SavedToken
import dev.cfmobile.app.data.local.TokenStore
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Verifying and switching between locally-stored API tokens. [tokenStore] is the only place
 * a token is persisted, and only after [verifierApi] confirms it's valid - a mistyped token
 * is never written to disk.
 */
class AuthRepository(
    private val verifierApi: CloudflareApi,
    private val tokenStore: TokenStore
) {
    val savedTokens: List<SavedToken> get() = tokenStore.getAll()
    val activeToken: SavedToken? get() = tokenStore.getActive()

    suspend fun addToken(label: String, rawToken: String): ApiResult<SavedToken> {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return ApiResult.Failure("Enter an API token")

        return when (val result = verify(trimmed)) {
            is ApiResult.Success -> {
                val saved = tokenStore.add(label = label.ifBlank { "Cloudflare account" }, token = trimmed)
                ApiResult.Success(saved)
            }
            is ApiResult.Failure -> result
        }
    }

    /**
     * User API tokens (created from a profile) are checked with `/user/tokens/verify`, which
     * returns a clear active/disabled/expired status. Account-owned tokens (the `cfat_`
     * prefix, created under Account > API Tokens) aren't tied to any user, so that same
     * endpoint always answers "Invalid API Token" for them even when they work perfectly -
     * confirmed directly against Cloudflare's API, not assumed. For those, falling back to a
     * lightweight `/zones` call (which is what this app needs the token for anyway) is the
     * only way to tell a genuinely bad token from a valid account-owned one.
     */
    private suspend fun verify(token: String): ApiResult<Unit> {
        val header = "Bearer $token"

        val userTokenCheck = safeApiCall { verifierApi.verifyTokenWithAuth(header) }
        if (userTokenCheck is ApiResult.Success) return ApiResult.Success(Unit)

        val zoneAccessCheck = safeApiCall { verifierApi.listZonesWithAuth(header) }
        if (zoneAccessCheck is ApiResult.Success) return ApiResult.Success(Unit)

        return userTokenCheck as ApiResult.Failure
    }

    fun switchTo(id: String) = tokenStore.setActive(id)

    fun removeToken(id: String) = tokenStore.remove(id)

    fun signOutAll() = tokenStore.clearAll()
}
