package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-mostly (PRD scope trim): list projects and their deployment history only - triggering
 *  a new deployment or editing project/build config isn't implemented here. */
class PagesRepository(private val api: CloudflareApi) {

    suspend fun listProjects(accountId: String): ApiResult<List<PagesProject>> =
        safeApiCall { api.listPagesProjects(accountId) }

    suspend fun listDeployments(accountId: String, projectName: String): ApiResult<List<PagesDeployment>> =
        safeApiCall { api.listPagesDeployments(accountId, projectName) }
}
