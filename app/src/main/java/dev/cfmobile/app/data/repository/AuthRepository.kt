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

        return when (val verify = safeApiCall { verifierApi.verifyTokenWithAuth("Bearer $trimmed") }) {
            is ApiResult.Success -> {
                val saved = tokenStore.add(label = label.ifBlank { "Cloudflare account" }, token = trimmed)
                ApiResult.Success(saved)
            }
            is ApiResult.Failure -> verify
        }
    }

    fun switchTo(id: String) = tokenStore.setActive(id)

    fun removeToken(id: String) = tokenStore.remove(id)

    fun signOutAll() = tokenStore.clearAll()
}
