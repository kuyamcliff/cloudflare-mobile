package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetPhaseWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall

/** Transform Rules (PRD §9) span three separate Rulesets phases - URL Rewrite
 *  ("http_request_transform"), Request Header Transform ("http_request_late_transform"), and
 *  Response Header Transform ("http_response_headers_transform") - so unlike WafRepository/
 *  RateLimitRepository this one takes the phase per call rather than fixing it as a constant.
 *  See TransformRuleKind for the phase each kind of rule lives in. */
class TransformRulesRepository(private val api: CloudflareApi) {

    suspend fun getRuleset(zoneId: String, phase: String): ApiResult<Ruleset?> =
        when (val result = safeApiCall { api.getPhaseRuleset(zoneId, phase) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(null) else result
        }

    suspend fun addRule(zoneId: String, phase: String, existingRulesetId: String?, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        if (existingRulesetId != null) {
            safeApiCall { api.addRulesetRule(zoneId, existingRulesetId, rule) }
        } else {
            safeApiCall { api.putPhaseRuleset(zoneId, phase, RulesetPhaseWrite(rules = listOf(rule))) }
        }

    suspend fun updateRule(zoneId: String, rulesetId: String, ruleId: String, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        safeApiCall { api.updateRulesetRule(zoneId, rulesetId, ruleId, rule) }

    suspend fun deleteRule(zoneId: String, rulesetId: String, ruleId: String): ApiResult<Ruleset> =
        safeApiCall { api.deleteRulesetRule(zoneId, rulesetId, ruleId) }
}
