package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccessApplication
import dev.cfmobile.app.data.remote.dto.AccessApplicationCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Zero Trust Access application management with one inline policy per app - covers the
 *  common "allow/block by email domain or specific addresses" cases only. Identity providers,
 *  multi-policy apps, and non-email include rules (groups, IP ranges, service tokens, etc.)
 *  aren't implemented here, see CapabilityRegistry's migrationHint. */
class AccessRepository(private val api: CloudflareApi) {

    suspend fun listApplications(accountId: String): ApiResult<List<AccessApplication>> =
        safeApiCall { api.listAccessApplications(accountId) }

    suspend fun createApplication(accountId: String, application: AccessApplicationCreate): ApiResult<AccessApplication> =
        safeApiCall { api.createAccessApplication(accountId, application) }

    suspend fun createPolicy(accountId: String, appId: String, policy: AccessPolicyCreate): ApiResult<Unit> =
        safeApiCallUnit { api.createAccessPolicy(accountId, appId, policy) }

    suspend fun deleteApplication(accountId: String, appId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessApplication(accountId, appId) }
}
