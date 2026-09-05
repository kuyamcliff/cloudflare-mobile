package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.D1Database
import dev.cfmobile.app.data.remote.dto.D1DatabaseCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** D1 database management only (list/create/delete) - running queries against a database is a
 *  separate, much larger surface (a SQL console) and isn't implemented here. */
class D1Repository(private val api: CloudflareApi) {

    suspend fun listDatabases(accountId: String): ApiResult<List<D1Database>> =
        safeApiCall { api.listD1Databases(accountId) }

    suspend fun createDatabase(accountId: String, name: String): ApiResult<D1Database> =
        safeApiCall { api.createD1Database(accountId, D1DatabaseCreate(name = name)) }

    suspend fun deleteDatabase(accountId: String, databaseId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteD1Database(accountId, databaseId) }
}
