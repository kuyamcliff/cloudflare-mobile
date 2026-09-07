package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetPhaseWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall

private const val PHASE = "http_ratelimit"

/** Threshold-based Rate Limiting (PRD §9), modeled by Cloudflare as the "http_ratelimit" phase
 *  of the same Rulesets engine WAF Custom Rules use - see WafRepository for the sibling
 *  implementation this mirrors. */
class RateLimitRepository(private val api: CloudflareApi) {

    suspend fun getRuleset(zoneId: String): ApiResult<Ruleset?> =
        when (val result = safeApiCall { api.getPhaseRuleset(zoneId, PHASE) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(null) else result
        }

    suspend fun addRule(zoneId: String, existingRulesetId: String?, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        if (existingRulesetId != null) {
            safeApiCall { api.addRulesetRule(zoneId, existingRulesetId, rule) }
        } else {
            safeApiCall { api.putPhaseRuleset(zoneId, PHASE, RulesetPhaseWrite(rules = listOf(rule))) }
        }

    suspend fun updateRule(zoneId: String, rulesetId: String, ruleId: String, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        safeApiCall { api.updateRulesetRule(zoneId, rulesetId, ruleId, rule) }

    suspend fun deleteRule(zoneId: String, rulesetId: String, ruleId: String): ApiResult<Ruleset> =
        safeApiCall { api.deleteRulesetRule(zoneId, rulesetId, ruleId) }
}
