package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WorkerDeployment
import dev.cfmobile.app.data.remote.dto.WorkerDomain
import dev.cfmobile.app.data.remote.dto.WorkerDomainWrite
import dev.cfmobile.app.data.remote.dto.WorkerRoute
import dev.cfmobile.app.data.remote.dto.WorkerSecret
import dev.cfmobile.app.data.remote.dto.WorkerSecretWrite
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

    /** Names and types only: Cloudflare never returns a secret's value, and this app never
     *  asks for one back. */
    suspend fun listSecrets(accountId: String, scriptName: String): ApiResult<List<WorkerSecret>> =
        safeApiCall { api.listWorkerSecrets(accountId, scriptName) }

    suspend fun putSecret(accountId: String, scriptName: String, name: String, value: String): ApiResult<WorkerSecret> =
        safeApiCall { api.putWorkerSecret(accountId, scriptName, WorkerSecretWrite(name = name, text = value)) }

    suspend fun deleteSecret(accountId: String, scriptName: String, secretName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWorkerSecret(accountId, scriptName, secretName) }

    /** A custom domain binds a hostname straight to a Worker, without a route on the zone. */
    suspend fun listDomains(accountId: String): ApiResult<List<WorkerDomain>> =
        safeApiCall { api.listWorkerDomains(accountId) }

    suspend fun attachDomain(
        accountId: String,
        zoneId: String,
        hostname: String,
        service: String
    ): ApiResult<WorkerDomain> =
        safeApiCall {
            api.attachWorkerDomain(
                accountId,
                WorkerDomainWrite(zoneId = zoneId, hostname = hostname, service = service)
            )
        }

    suspend fun detachDomain(accountId: String, domainId: String): ApiResult<Unit> =
        safeApiCallUnit { api.detachWorkerDomain(accountId, domainId) }

    suspend fun listDeployments(accountId: String, scriptName: String): ApiResult<List<WorkerDeployment>> =
        when (val result = safeApiCall { api.listWorkerDeployments(accountId, scriptName) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.deployments)
            is ApiResult.Failure -> result
        }
}
