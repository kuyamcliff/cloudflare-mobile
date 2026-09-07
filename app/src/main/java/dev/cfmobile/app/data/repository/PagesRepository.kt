package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesDomain
import dev.cfmobile.app.data.remote.dto.PagesDomainCreate
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

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

    suspend fun listDomains(accountId: String, projectName: String): ApiResult<List<PagesDomain>> =
        safeApiCall { api.listPagesDomains(accountId, projectName) }

    /** Cloudflare verifies ownership asynchronously, so a domain added here starts pending. */
    suspend fun addDomain(accountId: String, projectName: String, name: String): ApiResult<PagesDomain> =
        safeApiCall { api.addPagesDomain(accountId, projectName, PagesDomainCreate(name)) }

    suspend fun deleteDomain(accountId: String, projectName: String, domainName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deletePagesDomain(accountId, projectName, domainName) }
}
