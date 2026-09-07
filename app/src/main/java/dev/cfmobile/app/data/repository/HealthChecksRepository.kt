package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.HealthCheck
import dev.cfmobile.app.data.remote.dto.HealthCheckCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Standalone health checks - the zone-level monitors, separate from the ones a load balancer
 *  pool attaches. */
class HealthChecksRepository(private val api: CloudflareApi) {

    suspend fun listChecks(zoneId: String): ApiResult<List<HealthCheck>> =
        safeApiCall { api.listHealthChecks(zoneId) }

    suspend fun createCheck(
        zoneId: String,
        name: String,
        address: String,
        type: String,
        description: String?
    ): ApiResult<HealthCheck> = safeApiCall {
        api.createHealthCheck(
            zoneId,
            HealthCheckCreate(
                name = name,
                address = address,
                type = type,
                description = description?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun deleteCheck(zoneId: String, checkId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteHealthCheck(zoneId, checkId) }
}
