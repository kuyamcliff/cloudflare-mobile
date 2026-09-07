package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.ClientCertificate
import dev.cfmobile.app.data.remote.dto.OriginTlsClientAuthSettings
import dev.cfmobile.app.data.remote.dto.TotalTlsSettings
import dev.cfmobile.app.data.remote.dto.TotalTlsWrite
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * The certificate-authentication half of a zone's TLS: client certificates the zone accepts
 * for mTLS, whether Cloudflare authenticates itself to the origin, and Total TLS. Uploading a
 * client certificate isn't here - that means generating a CSR and holding a private key, which
 * this app deliberately doesn't do.
 */
class MutualTlsRepository(private val api: CloudflareApi) {

    suspend fun listClientCertificates(zoneId: String): ApiResult<List<ClientCertificate>> =
        safeApiCall { api.listClientCertificates(zoneId) }

    /** Cloudflare keeps a revoked certificate on record rather than deleting it, so the row
     *  comes back with a revoked status instead of disappearing. */
    suspend fun revokeClientCertificate(zoneId: String, certificateId: String): ApiResult<ClientCertificate> =
        safeApiCall { api.revokeClientCertificate(zoneId, certificateId) }

    suspend fun getOriginPulls(zoneId: String): ApiResult<OriginTlsClientAuthSettings> =
        safeApiCall { api.getOriginTlsClientAuth(zoneId) }

    suspend fun setOriginPulls(zoneId: String, enabled: Boolean): ApiResult<OriginTlsClientAuthSettings> =
        safeApiCall { api.updateOriginTlsClientAuth(zoneId, OriginTlsClientAuthSettings(enabled)) }

    suspend fun getTotalTls(zoneId: String): ApiResult<TotalTlsSettings> =
        safeApiCall { api.getTotalTls(zoneId) }

    /** The certificate authority is only meaningful when turning Total TLS on. */
    suspend fun setTotalTls(zoneId: String, enabled: Boolean, certificateAuthority: String?): ApiResult<TotalTlsSettings> =
        safeApiCall {
            api.updateTotalTls(
                zoneId,
                TotalTlsWrite(enabled = enabled, certificateAuthority = certificateAuthority.takeIf { enabled })
            )
        }
}
