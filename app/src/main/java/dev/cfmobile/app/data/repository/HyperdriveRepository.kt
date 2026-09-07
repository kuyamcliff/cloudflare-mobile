package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.HyperdriveConfig
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** List and delete only. Creating a Hyperdrive config means entering a database password,
 *  which is a bad thing to ask for on a phone keyboard - that stays in the dashboard or
 *  wrangler, see CapabilityRegistry's migrationHint. */
class HyperdriveRepository(private val api: CloudflareApi) {

    suspend fun listConfigs(accountId: String): ApiResult<List<HyperdriveConfig>> =
        safeApiCall { api.listHyperdriveConfigs(accountId) }

    suspend fun deleteConfig(accountId: String, configId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteHyperdriveConfig(accountId, configId) }
}
