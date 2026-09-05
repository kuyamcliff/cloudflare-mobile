package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.local.AccountStore
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Verifying and switching between locally-connected Cloudflare accounts. [accountStore] is
 * the only place a token is persisted, and only after [verifierApi] confirms it's valid - a
 * mistyped token is never written to disk. Everything this repository exposes is an
 * [AccountSummary] (label/email/id only) - the raw token never flows into ViewModel or UI
 * state (PRD §83).
 */
class AuthRepository(
    private val verifierApi: CloudflareApi,
    private val accountStore: AccountStore
) {
    val savedAccounts: List<AccountSummary> get() = accountStore.getAll()
    val activeAccount: AccountSummary? get() = accountStore.getActive()

    suspend fun addToken(label: String, rawToken: String): ApiResult<AccountSummary> {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return ApiResult.Failure("Enter an API token")

        return when (val result = verify(trimmed)) {
            is ApiResult.Success -> {
                val saved = accountStore.add(label = label.ifBlank { "Cloudflare account" }, token = trimmed)
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

    fun switchTo(id: String) = accountStore.setActive(id)

    fun removeAccount(id: String) = accountStore.remove(id)

    fun signOutAll() = accountStore.clearAll()
}
