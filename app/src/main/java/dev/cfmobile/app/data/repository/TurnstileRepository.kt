package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.TurnstileWidget
import dev.cfmobile.app.data.remote.dto.TurnstileWidgetCreate
import dev.cfmobile.app.data.remote.safeApiCall

/** Turnstile widget management. The list response carries the public sitekey only; the secret
 *  key is deliberately never fetched or displayed by this app. */
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

    suspend fun deleteWidget(accountId: String, sitekey: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteTurnstileWidget(accountId, sitekey) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
}
