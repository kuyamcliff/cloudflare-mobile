package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.DurableObjectNamespace
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only by nature: Durable Object namespaces are created and removed by deploying a
 *  Worker that declares them, not through a management endpoint, so this lists what exists. */
class DurableObjectsRepository(private val api: CloudflareApi) {

    suspend fun listNamespaces(accountId: String): ApiResult<List<DurableObjectNamespace>> =
        safeApiCall { api.listDurableObjectNamespaces(accountId) }
}
