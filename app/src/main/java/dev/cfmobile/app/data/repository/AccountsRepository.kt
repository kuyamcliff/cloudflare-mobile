package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.remote.safeApiCall

class AccountsRepository(private val api: CloudflareApi) {
    suspend fun listAccounts(): ApiResult<List<CfAccount>> =
        safeApiCall { api.listAccounts() }
}
