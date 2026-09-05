package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.AccessRule
import dev.cfmobile.app.data.remote.dto.AccessRuleConfiguration
import dev.cfmobile.app.data.remote.dto.AccessRuleWrite
import dev.cfmobile.app.data.remote.dto.FirewallFilterWrite
import dev.cfmobile.app.data.remote.dto.FirewallRule
import dev.cfmobile.app.data.remote.dto.FirewallRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

class FirewallRepository(private val api: CloudflareApi) {

    suspend fun listRules(zoneId: String): ApiResult<List<FirewallRule>> =
        safeApiCall { api.listFirewallRules(zoneId) }

    suspend fun createRule(zoneId: String, expression: String, action: String, description: String?): ApiResult<List<FirewallRule>> =
        safeApiCall {
            api.createFirewallRule(
                zoneId,
                listOf(FirewallRuleWrite(filter = FirewallFilterWrite(expression = expression), action = action, description = description))
            )
        }

    suspend fun deleteRule(zoneId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteFirewallRule(zoneId, ruleId) }

    suspend fun listAccessRules(zoneId: String): ApiResult<List<AccessRule>> =
        safeApiCall { api.listAccessRules(zoneId) }

    suspend fun createAccessRule(zoneId: String, mode: String, ip: String, notes: String?): ApiResult<AccessRule> =
        safeApiCall {
            api.createAccessRule(zoneId, AccessRuleWrite(mode = mode, configuration = AccessRuleConfiguration(target = "ip", value = ip), notes = notes))
        }

    suspend fun deleteAccessRule(zoneId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteAccessRule(zoneId, ruleId) }
}
