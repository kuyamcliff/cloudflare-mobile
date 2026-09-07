package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.BotManagementConfig
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Bot management configuration. Free-tier Bot Fight Mode and paid Super Bot Fight Mode live in
 * the same document, and which fields Cloudflare accepts depends on the zone's plan - a write
 * carrying a field the plan doesn't include is rejected, so the caller sends only what the
 * zone's own configuration reported.
 */
class BotManagementRepository(private val api: CloudflareApi) {

    suspend fun getConfig(zoneId: String): ApiResult<BotManagementConfig> =
        safeApiCall { api.getBotManagement(zoneId) }

    suspend fun updateConfig(zoneId: String, config: BotManagementConfig): ApiResult<BotManagementConfig> =
        safeApiCall { api.updateBotManagement(zoneId, config) }
}
