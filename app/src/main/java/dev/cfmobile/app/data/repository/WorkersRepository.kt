package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Workers script list/view/delete only - editing or deploying script code needs an editor
 *  and bundler that don't belong on mobile, so that's not implemented here. */
class WorkersRepository(private val api: CloudflareApi) {

    suspend fun listScripts(accountId: String): ApiResult<List<WorkerScript>> =
        safeApiCall { api.listWorkerScripts(accountId) }

    suspend fun deleteScript(accountId: String, scriptName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteWorkerScript(accountId, scriptName) }
}
