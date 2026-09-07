package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccessApplication
import dev.cfmobile.app.data.remote.dto.AccessApplicationCreate
import dev.cfmobile.app.data.remote.dto.AccessBookmark
import dev.cfmobile.app.data.remote.dto.AccessBookmarkWrite
import dev.cfmobile.app.data.remote.dto.AccessGroup
import dev.cfmobile.app.data.remote.dto.AccessGroupWrite
import dev.cfmobile.app.data.remote.dto.AccessMtlsCertificate
import dev.cfmobile.app.data.remote.dto.AccessTag
import dev.cfmobile.app.data.remote.dto.AccessTagWrite
import dev.cfmobile.app.data.remote.dto.AccessIdentityProvider
import dev.cfmobile.app.data.remote.dto.AccessIdentityProviderCreate
import dev.cfmobile.app.data.remote.dto.AccessServiceToken
import dev.cfmobile.app.data.remote.dto.AccessServiceTokenCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyCreate
import dev.cfmobile.app.data.remote.dto.AccessPolicyIncludeRule
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

    /** A named, reusable set of include rules that policies can point at. */
    suspend fun listGroups(accountId: String): ApiResult<List<AccessGroup>> =
        safeApiCall { api.listAccessGroups(accountId) }

    suspend fun createGroup(accountId: String, name: String, include: List<AccessPolicyIncludeRule>): ApiResult<AccessGroup> =
        safeApiCall { api.createAccessGroup(accountId, AccessGroupWrite(name = name, include = include)) }

    suspend fun deleteGroup(accountId: String, groupId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessGroup(accountId, groupId) }

    /** Root certificates Access accepts client certificates from. Uploading one means holding
     *  a certificate chain, which is left to the dashboard. */
    suspend fun listMtlsCertificates(accountId: String): ApiResult<List<AccessMtlsCertificate>> =
        safeApiCall { api.listAccessMtlsCertificates(accountId) }

    suspend fun deleteMtlsCertificate(accountId: String, certificateId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessMtlsCertificate(accountId, certificateId) }

    /** Launchpad links to apps Access doesn't itself guard. */
    suspend fun listBookmarks(accountId: String): ApiResult<List<AccessBookmark>> =
        safeApiCall { api.listAccessBookmarks(accountId) }

    suspend fun createBookmark(accountId: String, name: String, domain: String): ApiResult<AccessBookmark> =
        safeApiCall { api.createAccessBookmark(accountId, AccessBookmarkWrite(name = name, domain = domain)) }

    suspend fun deleteBookmark(accountId: String, bookmarkId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessBookmark(accountId, bookmarkId) }

    suspend fun listTags(accountId: String): ApiResult<List<AccessTag>> =
        safeApiCall { api.listAccessTags(accountId) }

    suspend fun createTag(accountId: String, name: String): ApiResult<AccessTag> =
        safeApiCall { api.createAccessTag(accountId, AccessTagWrite(name)) }

    suspend fun deleteTag(accountId: String, tagName: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessTag(accountId, tagName) }

    companion object {
        const val ONE_TIME_PIN_TYPE = "onetimepin"
    }
}
