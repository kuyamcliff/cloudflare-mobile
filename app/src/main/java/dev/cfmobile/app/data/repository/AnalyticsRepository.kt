package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AnalyticsDashboard
import dev.cfmobile.app.data.remote.safeApiCall
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnalyticsRepository(private val api: CloudflareApi) {

    suspend fun getDashboard(zoneId: String, sinceHours: Long = 24): ApiResult<AnalyticsDashboard> {
        val until = Instant.now()
        val since = until.minus(sinceHours, ChronoUnit.HOURS)
        return safeApiCall { api.getAnalyticsDashboard(zoneId, since.toString(), until.toString()) }
    }
}
