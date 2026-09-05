package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AuditLogEntry
import dev.cfmobile.app.data.remote.safeApiCall

class AuditLogsRepository(private val api: CloudflareApi) {
    suspend fun listEntries(accountId: String, since: String? = null): ApiResult<List<AuditLogEntry>> =
        safeApiCall { api.listAuditLogs(accountId, since = since) }
}
