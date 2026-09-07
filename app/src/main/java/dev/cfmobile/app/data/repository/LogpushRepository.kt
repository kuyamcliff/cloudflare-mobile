package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.LogpushJob
import dev.cfmobile.app.data.remote.dto.LogpushJobUpdate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Logpush job administration: list, pause/resume, delete. Creating a job means supplying a
 *  destination config string that embeds storage credentials (S3 keys, an R2 token, a
 *  Splunk token) - not something to type on a phone, so it isn't offered. */
class LogpushRepository(private val api: CloudflareApi) {

    suspend fun listJobs(accountId: String): ApiResult<List<LogpushJob>> =
        safeApiCall { api.listLogpushJobs(accountId) }

    suspend fun setEnabled(accountId: String, jobId: Long, enabled: Boolean): ApiResult<LogpushJob> =
        safeApiCall { api.updateLogpushJob(accountId, jobId, LogpushJobUpdate(enabled)) }

    suspend fun deleteJob(accountId: String, jobId: Long): ApiResult<Unit> =
        safeApiCallUnit { api.deleteLogpushJob(accountId, jobId) }
}
