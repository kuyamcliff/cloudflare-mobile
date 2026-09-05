package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.KvNamespace
import dev.cfmobile.app.data.remote.dto.KvNamespaceCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** PRD §9 Workers KV: namespace management only (list/create/delete) - browsing or editing
 *  individual keys inside a namespace is a separate, larger surface and isn't implemented
 *  here. */
class KvRepository(private val api: CloudflareApi) {

    suspend fun listNamespaces(accountId: String): ApiResult<List<KvNamespace>> =
        safeApiCall { api.listKvNamespaces(accountId) }

    suspend fun createNamespace(accountId: String, title: String): ApiResult<KvNamespace> =
        safeApiCall { api.createKvNamespace(accountId, KvNamespaceCreate(title = title)) }

    suspend fun deleteNamespace(accountId: String, namespaceId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteKvNamespace(accountId, namespaceId) }
}
