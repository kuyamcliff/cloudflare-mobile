package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.data.remote.safeApiCall

/** Projects, their deployment history, and re-deploying. Editing a project's build
 *  configuration isn't implemented - that's a desktop-sized form. */
class PagesRepository(private val api: CloudflareApi) {

    suspend fun listProjects(accountId: String): ApiResult<List<PagesProject>> =
        safeApiCall { api.listPagesProjects(accountId) }

    suspend fun listDeployments(accountId: String, projectName: String): ApiResult<List<PagesDeployment>> =
        safeApiCall { api.listPagesDeployments(accountId, projectName) }

    /** Rebuilds and redeploys the project's production branch. */
    suspend fun createDeployment(accountId: String, projectName: String): ApiResult<PagesDeployment> =
        safeApiCall { api.createPagesDeployment(accountId, projectName) }

    suspend fun retryDeployment(accountId: String, projectName: String, deploymentId: String): ApiResult<PagesDeployment> =
        safeApiCall { api.retryPagesDeployment(accountId, projectName, deploymentId) }
}
