package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PageShieldPolicy
import dev.cfmobile.app.data.remote.dto.PageShieldPolicyWrite
import dev.cfmobile.app.data.remote.dto.PageShieldConnection
import dev.cfmobile.app.data.remote.dto.PageShieldScript
import dev.cfmobile.app.data.remote.dto.PageShieldSettings
import dev.cfmobile.app.data.remote.dto.PageShieldSettingsUpdate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Page Shield: the on/off setting plus the scripts and outbound connections Cloudflare has
 *  observed on the zone's pages. Policies (allow/block lists) aren't covered. */
class PageShieldRepository(private val api: CloudflareApi) {

    suspend fun getSettings(zoneId: String): ApiResult<PageShieldSettings> =
        safeApiCall { api.getPageShieldSettings(zoneId) }

    suspend fun setEnabled(zoneId: String, enabled: Boolean): ApiResult<PageShieldSettings> =
        safeApiCall { api.updatePageShieldSettings(zoneId, PageShieldSettingsUpdate(enabled)) }

    suspend fun listScripts(zoneId: String): ApiResult<List<PageShieldScript>> =
        safeApiCall { api.listPageShieldScripts(zoneId) }

    suspend fun listConnections(zoneId: String): ApiResult<List<PageShieldConnection>> =
        safeApiCall { api.listPageShieldConnections(zoneId) }

    suspend fun listPolicies(zoneId: String): ApiResult<List<PageShieldPolicy>> =
        safeApiCall { api.listPageShieldPolicies(zoneId) }

    suspend fun createPolicy(zoneId: String, policy: PageShieldPolicyWrite): ApiResult<PageShieldPolicy> =
        safeApiCall { api.createPageShieldPolicy(zoneId, policy) }

    suspend fun updatePolicy(zoneId: String, policyId: String, policy: PageShieldPolicyWrite): ApiResult<PageShieldPolicy> =
        safeApiCall { api.updatePageShieldPolicy(zoneId, policyId, policy) }

    suspend fun deletePolicy(zoneId: String, policyId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deletePageShieldPolicy(zoneId, policyId) }
}
