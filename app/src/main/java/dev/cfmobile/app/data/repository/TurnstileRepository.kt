package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.TurnstileRotateResult
import dev.cfmobile.app.data.remote.dto.TurnstileWidget
import dev.cfmobile.app.data.remote.dto.TurnstileWidgetCreate
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Turnstile widget management. This app never *reads* a secret key: there is no call here that
 * fetches one. The single exception is [rotateSecret], which the user initiates deliberately -
 * Cloudflare answers it with the newly minted secret, which is the only way to learn the value
 * that replaced the old one. That value is shown once, in a dialog, and never persisted.
 */
class TurnstileRepository(private val api: CloudflareApi) {

    suspend fun listWidgets(accountId: String): ApiResult<List<TurnstileWidget>> =
        safeApiCall { api.listTurnstileWidgets(accountId) }

    suspend fun createWidget(
        accountId: String,
        name: String,
        domains: List<String>,
        mode: String
    ): ApiResult<TurnstileWidget> =
        safeApiCall { api.createTurnstileWidget(accountId, TurnstileWidgetCreate(name, domains, mode)) }

    /** Immediately invalidates the current secret: anything verifying with the old value
     *  starts failing the moment this returns. */
    suspend fun rotateSecret(accountId: String, sitekey: String): ApiResult<TurnstileRotateResult> =
        safeApiCall { api.rotateTurnstileSecret(accountId, sitekey) }

    suspend fun deleteWidget(accountId: String, sitekey: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteTurnstileWidget(accountId, sitekey) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
