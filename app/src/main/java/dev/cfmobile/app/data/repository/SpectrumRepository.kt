package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.SpectrumApp
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Spectrum applications: list and delete. Creating one needs origin, protocol, edge IP and
 *  TLS decisions together, which is a form better filled in on a desktop - see
 *  CapabilityRegistry's migrationHint. */
class SpectrumRepository(private val api: CloudflareApi) {

    suspend fun listApps(zoneId: String): ApiResult<List<SpectrumApp>> =
        safeApiCall { api.listSpectrumApps(zoneId) }

    suspend fun deleteApp(zoneId: String, appId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteSpectrumApp(zoneId, appId) }
}
