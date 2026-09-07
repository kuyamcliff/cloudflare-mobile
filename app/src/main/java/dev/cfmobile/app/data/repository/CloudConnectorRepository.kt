package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.CloudConnectorRule
import dev.cfmobile.app.data.remote.safeApiCall

/** Cloud Connector rules route matching requests to object storage instead of the origin.
 *  Cloudflare replaces the whole rule list on write, so every change here is a
 *  read-modify-write of the full list. */
class CloudConnectorRepository(private val api: CloudflareApi) {

    suspend fun listRules(zoneId: String): ApiResult<List<CloudConnectorRule>> =
        safeApiCall { api.listCloudConnectorRules(zoneId) }

    suspend fun putRules(zoneId: String, rules: List<CloudConnectorRule>): ApiResult<List<CloudConnectorRule>> =
        safeApiCall { api.putCloudConnectorRules(zoneId, rules) }
}
