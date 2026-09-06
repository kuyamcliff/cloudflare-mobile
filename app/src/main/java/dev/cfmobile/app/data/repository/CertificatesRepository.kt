package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CertificatePack
import dev.cfmobile.app.data.remote.dto.CustomHostname
import dev.cfmobile.app.data.remote.dto.CustomHostnameCreate
import dev.cfmobile.app.data.remote.dto.CustomHostnameSsl
import dev.cfmobile.app.data.remote.dto.DnssecStatus
import dev.cfmobile.app.data.remote.dto.DnssecUpdate
import dev.cfmobile.app.data.remote.safeApiCall

/** DNSSEC, custom hostnames (SSL for SaaS), and the zone's edge certificate inventory. */
class CertificatesRepository(private val api: CloudflareApi) {

    suspend fun getDnssec(zoneId: String): ApiResult<DnssecStatus> =
        safeApiCall { api.getDnssec(zoneId) }

    /** Cloudflare models DNSSEC as a status string; "active" enables it, "disabled" turns it
     *  off. Enabling only generates the DS record - the registrar still has to publish it. */
    suspend fun setDnssecEnabled(zoneId: String, enabled: Boolean): ApiResult<DnssecStatus> =
        safeApiCall { api.updateDnssec(zoneId, DnssecUpdate(if (enabled) "active" else "disabled")) }

    suspend fun listCustomHostnames(zoneId: String): ApiResult<List<CustomHostname>> =
        safeApiCall { api.listCustomHostnames(zoneId) }

    suspend fun createCustomHostname(zoneId: String, hostname: String, method: String): ApiResult<CustomHostname> =
        safeApiCall {
            api.createCustomHostname(
                zoneId,
                CustomHostnameCreate(hostname = hostname, ssl = CustomHostnameSsl(method = method, type = "dv"))
            )
        }

    suspend fun deleteCustomHostname(zoneId: String, hostnameId: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteCustomHostname(zoneId, hostnameId) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }

    suspend fun listCertificatePacks(zoneId: String): ApiResult<List<CertificatePack>> =
        safeApiCall { api.listCertificatePacks(zoneId) }
}
