package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.GatewayRule
import dev.cfmobile.app.data.remote.dto.GatewayRuleCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Zero Trust Gateway DNS policies only - block or allow traffic to a single FQDN. Network
 *  and HTTP policies, more complex Wirefilter expressions (categories, identity, device
 *  posture), and rule ordering aren't implemented here, see CapabilityRegistry's migrationHint. */
class GatewayRepository(private val api: CloudflareApi) {

    suspend fun listRules(accountId: String): ApiResult<List<GatewayRule>> =
        safeApiCall { api.listGatewayRules(accountId) }

    suspend fun createRule(accountId: String, rule: GatewayRuleCreate): ApiResult<GatewayRule> =
        safeApiCall { api.createGatewayRule(accountId, rule) }

    suspend fun deleteRule(accountId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteGatewayRule(accountId, ruleId) }
}
