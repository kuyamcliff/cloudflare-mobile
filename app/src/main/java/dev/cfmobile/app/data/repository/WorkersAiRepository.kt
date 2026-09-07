package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AiModel
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only catalogue of the models available to this account. Running inference is a
 *  Worker's job and would bill per request, so this app browses rather than invokes. */
class WorkersAiRepository(private val api: CloudflareApi) {

    suspend fun listModels(accountId: String): ApiResult<List<AiModel>> =
        safeApiCall { api.searchAiModels(accountId) }
}
