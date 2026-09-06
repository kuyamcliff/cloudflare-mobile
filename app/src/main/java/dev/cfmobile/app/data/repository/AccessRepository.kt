package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccessApplication
import dev.cfmobile.app.data.remote.dto.AccessApplicationCreate
import dev.cfmobile.app.data.remote.dto.AccessIdentityProvider
import dev.cfmobile.app.data.remote.dto.AccessIdentityProviderCreate
import dev.cfmobile.app.data.remote.dto.AccessServiceToken
import dev.cfmobile.app.data.remote.dto.AccessServiceTokenCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Zero Trust Access: applications with one inline policy each, plus identity providers and
 *  service tokens. Multi-policy apps and non-email include rules (groups, IP ranges, device
 *  posture) aren't implemented here, see CapabilityRegistry's migrationHint. */
class AccessRepository(private val api: CloudflareApi) {

    suspend fun listApplications(accountId: String): ApiResult<List<AccessApplication>> =
        safeApiCall { api.listAccessApplications(accountId) }

    suspend fun createApplication(accountId: String, application: AccessApplicationCreate): ApiResult<AccessApplication> =
        safeApiCall { api.createAccessApplication(accountId, application) }

    suspend fun createPolicy(accountId: String, appId: String, policy: AccessPolicyCreate): ApiResult<Unit> =
        safeApiCallUnit { api.createAccessPolicy(accountId, appId, policy) }

    suspend fun deleteApplication(accountId: String, appId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessApplication(accountId, appId) }

    suspend fun listIdentityProviders(accountId: String): ApiResult<List<AccessIdentityProvider>> =
        safeApiCall { api.listAccessIdentityProviders(accountId) }

    /** Only Cloudflare's own one-time PIN provider can be created here: every other type needs
     *  provider credentials, which is a form this app deliberately doesn't ask for. */
    suspend fun createOneTimePinProvider(accountId: String, name: String): ApiResult<AccessIdentityProvider> =
        safeApiCall {
            api.createAccessIdentityProvider(
                accountId,
                AccessIdentityProviderCreate(name = name, type = ONE_TIME_PIN_TYPE)
            )
        }

    suspend fun deleteIdentityProvider(accountId: String, providerId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessIdentityProvider(accountId, providerId) }

    suspend fun listServiceTokens(accountId: String): ApiResult<List<AccessServiceToken>> =
        safeApiCall { api.listAccessServiceTokens(accountId) }

    /** The returned token carries its client secret, which Cloudflare never sends again. */
    suspend fun createServiceToken(accountId: String, name: String): ApiResult<AccessServiceToken> =
        safeApiCall { api.createAccessServiceToken(accountId, AccessServiceTokenCreate(name = name)) }

    suspend fun deleteServiceToken(accountId: String, tokenId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessServiceToken(accountId, tokenId) }

    companion object {
        const val ONE_TIME_PIN_TYPE = "onetimepin"
    }
}
