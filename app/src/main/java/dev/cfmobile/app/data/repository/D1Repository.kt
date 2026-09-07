package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.D1Database
import dev.cfmobile.app.data.remote.dto.D1DatabaseCreate
import dev.cfmobile.app.data.remote.dto.D1QueryRequest
import dev.cfmobile.app.data.remote.dto.D1QueryResult
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** D1 databases plus a SQL console. Cloudflare runs whatever SQL it is given, so the console
 *  is as capable - and as dangerous - as the token's permissions allow. */
class D1Repository(private val api: CloudflareApi) {

    suspend fun listDatabases(accountId: String): ApiResult<List<D1Database>> =
        safeApiCall { api.listD1Databases(accountId) }

    suspend fun createDatabase(accountId: String, name: String): ApiResult<D1Database> =
        safeApiCall { api.createD1Database(accountId, D1DatabaseCreate(name = name)) }

    suspend fun deleteDatabase(accountId: String, databaseId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteD1Database(accountId, databaseId) }

    /** Runs SQL against a database. Cloudflare answers with one result per statement, so a
     *  multi-statement script returns several. */
    suspend fun query(accountId: String, databaseId: String, sql: String): ApiResult<List<D1QueryResult>> =
        safeApiCall { api.queryD1Database(accountId, databaseId, D1QueryRequest(sql)) }
}
