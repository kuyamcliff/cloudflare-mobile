package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.RegistrarDomain
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Cloudflare Registrar domains, read-only on purpose. Every write this endpoint offers -
 * auto-renew, transfer lock, transfers themselves - either authorizes a charge or moves a
 * domain between registrars, which is the same reason the Billing screen has no path that can
 * change a plan.
 */
class RegistrarRepository(private val api: CloudflareApi) {

    suspend fun listDomains(accountId: String): ApiResult<List<RegistrarDomain>> =
        safeApiCall { api.listRegistrarDomains(accountId) }
}
