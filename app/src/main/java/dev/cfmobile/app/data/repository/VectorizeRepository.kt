package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.VectorizeIndex
import dev.cfmobile.app.data.remote.dto.VectorizeIndexConfig
import dev.cfmobile.app.data.remote.dto.VectorizeIndexCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Index management (list/create/delete). Inserting or querying vectors is a Worker's job,
 *  so there's no vector browser here. */
class VectorizeRepository(private val api: CloudflareApi) {

    suspend fun listIndexes(accountId: String): ApiResult<List<VectorizeIndex>> =
        safeApiCall { api.listVectorizeIndexes(accountId) }

    suspend fun createIndex(
        accountId: String,
        name: String,
        dimensions: Int,
        metric: String,
        description: String?
    ): ApiResult<VectorizeIndex> = safeApiCall {
        api.createVectorizeIndex(
            accountId,
            VectorizeIndexCreate(
                name = name,
                config = VectorizeIndexConfig(dimensions = dimensions, metric = metric),
                description = description?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun deleteIndex(accountId: String, indexName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteVectorizeIndex(accountId, indexName) }
}
