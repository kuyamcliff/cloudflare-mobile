package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Pipeline
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Pipelines: an HTTP or Worker source batching records into R2. Creating one needs a source,
 *  a destination bucket, and its credentials, which is a desktop-sized form. */
class PipelinesRepository(private val api: CloudflareApi) {

    suspend fun listPipelines(accountId: String): ApiResult<List<Pipeline>> =
        safeApiCall { api.listPipelines(accountId) }

    suspend fun deletePipeline(accountId: String, pipelineName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deletePipeline(accountId, pipelineName) }
}
