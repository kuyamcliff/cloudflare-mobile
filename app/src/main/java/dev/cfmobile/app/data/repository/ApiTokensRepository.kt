package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.emptyListOnNullResult
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.remote.dto.CfUser
import dev.cfmobile.app.data.remote.dto.CreatedToken
import dev.cfmobile.app.data.remote.dto.PermissionGroup
import dev.cfmobile.app.data.remote.dto.TokenWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Whose collection a token lives in. Kept apart everywhere (spec 173). */
sealed class TokenOwner {
    data object User : TokenOwner()
    data class Account(val accountId: String) : TokenOwner()
}

/**
 * User and account-owned API tokens (spec 9). A token secret only ever appears in the
 * results of [create] and [roll]; it is handed straight to the UI that shows it once and is
 * never persisted by this repository.
 */
class ApiTokensRepository(private val api: CloudflareApi) {

    suspend fun listTokens(): ApiResult<List<ApiToken>> = safeApiCall { api.listApiTokens() }

    suspend fun deleteToken(tokenId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteApiToken(tokenId) }

    suspend fun listAccountTokens(accountId: String): ApiResult<List<ApiToken>> =
        safeApiCall { api.listAccountApiTokens(accountId) }.emptyListOnNullResult()

    suspend fun deleteAccountToken(accountId: String, tokenId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccountApiToken(accountId, tokenId) }

    suspend fun get(owner: TokenOwner, tokenId: String): ApiResult<ApiToken> = when (owner) {
        TokenOwner.User -> safeApiCall { api.getApiToken(tokenId) }
        is TokenOwner.Account -> safeApiCall { api.getAccountApiToken(owner.accountId, tokenId) }
    }

    suspend fun create(owner: TokenOwner, body: TokenWrite): ApiResult<CreatedToken> = when (owner) {
        TokenOwner.User -> safeApiCall { api.createApiToken(body) }
        is TokenOwner.Account -> safeApiCall { api.createAccountApiToken(owner.accountId, body) }
    }

    suspend fun update(owner: TokenOwner, tokenId: String, body: TokenWrite): ApiResult<ApiToken> = when (owner) {
        TokenOwner.User -> safeApiCall { api.updateApiToken(tokenId, body) }
        is TokenOwner.Account -> safeApiCall { api.updateAccountApiToken(owner.accountId, tokenId, body) }
    }

    /** Returns the new secret. The previous secret stops working immediately. */
    suspend fun roll(owner: TokenOwner, tokenId: String): ApiResult<String> = when (owner) {
        TokenOwner.User -> safeApiCall { api.rollApiToken(tokenId) }
        is TokenOwner.Account -> safeApiCall { api.rollAccountApiToken(owner.accountId, tokenId) }
    }

    suspend fun delete(owner: TokenOwner, tokenId: String): ApiResult<Unit> = when (owner) {
        TokenOwner.User -> deleteToken(tokenId)
        is TokenOwner.Account -> deleteAccountToken(owner.accountId, tokenId)
    }

    /** Fetched live every time the editor opens: the catalog is never hardcoded (spec 9). */
    suspend fun permissionGroups(owner: TokenOwner): ApiResult<List<PermissionGroup>> = when (owner) {
        TokenOwner.User -> safeApiCall { api.listUserPermissionGroups() }
        is TokenOwner.Account -> safeApiCall { api.listAccountPermissionGroups(owner.accountId) }
    }

    suspend fun currentUser(): ApiResult<CfUser> = safeApiCall { api.getUser() }
}
