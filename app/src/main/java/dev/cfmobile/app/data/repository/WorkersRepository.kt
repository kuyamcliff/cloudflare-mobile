package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WorkerRoute
import dev.cfmobile.app.data.remote.dto.WorkerRouteWrite
import dev.cfmobile.app.data.remote.dto.WorkerSchedule
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Workers scripts: list, inspect (source, cron triggers, routes), and delete. Editing or
 *  deploying code still needs an editor and bundler, so that isn't offered here. */
class WorkersRepository(private val api: CloudflareApi) {

    suspend fun listScripts(accountId: String): ApiResult<List<WorkerScript>> =
        safeApiCall { api.listWorkerScripts(accountId) }

    suspend fun deleteScript(accountId: String, scriptName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWorkerScript(accountId, scriptName) }

    /** The deployed script itself. A module worker comes back as a multipart body rather than
     *  bare JavaScript, so what's shown is whatever Cloudflare returned - readable, but not
     *  always pretty. */
    suspend fun getScriptSource(accountId: String, scriptName: String): ApiResult<String> {
        return try {
            val response = api.getWorkerScriptContent(accountId, scriptName)
            if (response.isSuccessful) {
                ApiResult.Success(response.body()?.string().orEmpty())
            } else {
                ApiResult.Failure("HTTP ${response.code()}: ${response.message()}", response.code())
            }
        } catch (e: java.io.IOException) {
            ApiResult.Failure(e.message?.let { "Network error: $it" } ?: "Unable to reach Cloudflare")
        } catch (e: Exception) {
            ApiResult.Failure(e.message?.let { "Unexpected error: $it" } ?: "Unexpected error")
        }
    }

    suspend fun getSchedules(accountId: String, scriptName: String): ApiResult<List<WorkerSchedule>> =
        when (val result = safeApiCall { api.getWorkerSchedules(accountId, scriptName) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.schedules)
            is ApiResult.Failure -> result
        }

    /** Worker routes are zone-scoped, not account-scoped - a route binds a URL pattern on one
     *  zone to a script. */
    suspend fun listRoutes(zoneId: String): ApiResult<List<WorkerRoute>> =
        safeApiCall { api.listWorkerRoutes(zoneId) }

    suspend fun createRoute(zoneId: String, pattern: String, script: String): ApiResult<WorkerRoute> =
        safeApiCall { api.createWorkerRoute(zoneId, WorkerRouteWrite(pattern = pattern, script = script)) }

    suspend fun updateRoute(zoneId: String, routeId: String, pattern: String, script: String): ApiResult<WorkerRoute> =
        safeApiCall { api.updateWorkerRoute(zoneId, routeId, WorkerRouteWrite(pattern = pattern, script = script)) }

    suspend fun deleteRoute(zoneId: String, routeId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWorkerRoute(zoneId, routeId) }
}
