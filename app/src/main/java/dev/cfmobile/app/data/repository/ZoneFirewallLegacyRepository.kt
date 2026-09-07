package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.UserAgentRule
import dev.cfmobile.app.data.remote.dto.UserAgentRuleWrite
import dev.cfmobile.app.data.remote.dto.ZoneLockdown
import dev.cfmobile.app.data.remote.dto.ZoneLockdownWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * Zone Lockdown and User Agent Blocking: Cloudflare's pre-Rulesets firewall products. They
 * still work and still appear in the dashboard, and they are not expressible as WAF custom
 * rules without rewriting them, which is why they get their own screen rather than being
 * folded into the rules engine.
 */
class ZoneFirewallLegacyRepository(private val api: CloudflareApi) {

    suspend fun listLockdowns(zoneId: String): ApiResult<List<ZoneLockdown>> =
        safeApiCall { api.listZoneLockdowns(zoneId) }

    suspend fun createLockdown(zoneId: String, lockdown: ZoneLockdownWrite): ApiResult<ZoneLockdown> =
        safeApiCall { api.createZoneLockdown(zoneId, lockdown) }

    suspend fun updateLockdown(zoneId: String, lockdownId: String, lockdown: ZoneLockdownWrite): ApiResult<ZoneLockdown> =
        safeApiCall { api.updateZoneLockdown(zoneId, lockdownId, lockdown) }

    suspend fun deleteLockdown(zoneId: String, lockdownId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteZoneLockdown(zoneId, lockdownId) }

    suspend fun listUserAgentRules(zoneId: String): ApiResult<List<UserAgentRule>> =
        safeApiCall { api.listUserAgentRules(zoneId) }

    suspend fun createUserAgentRule(zoneId: String, rule: UserAgentRuleWrite): ApiResult<UserAgentRule> =
        safeApiCall { api.createUserAgentRule(zoneId, rule) }

    suspend fun updateUserAgentRule(zoneId: String, ruleId: String, rule: UserAgentRuleWrite): ApiResult<UserAgentRule> =
        safeApiCall { api.updateUserAgentRule(zoneId, ruleId, rule) }

    suspend fun deleteUserAgentRule(zoneId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteUserAgentRule(zoneId, ruleId) }
}
