package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.remote.fetchAllPages
import dev.cfmobile.app.data.remote.safeApiCallPaged

class AccountsRepository(private val api: CloudflareApi) {
    suspend fun listAccounts(maxPages: Int = 10): ApiResult<List<CfAccount>> =
        fetchAllPages(maxPages) { page -> safeApiCallPaged { api.listAccounts(page = page) } }
}
