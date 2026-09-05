package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetPhaseWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall

/** Cloudflare's modern replacement for the legacy Firewall Rules engine (PRD §9: "WAF
 *  Rulesets & Custom Rules"). The custom-rules phase entrypoint doesn't exist for a zone until
 *  its first rule is created, so a 404 from Cloudflare here means "no rules yet," not an error. */
class WafRepository(private val api: CloudflareApi) {

    suspend fun getCustomRuleset(zoneId: String): ApiResult<Ruleset?> =
        when (val result = safeApiCall { api.getCustomRuleset(zoneId) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(null) else result
        }

    /** [existingRulesetId] null means the zone has no custom ruleset yet, so this creates it
     *  (via PUT) with just the new rule; otherwise the rule is appended to the existing one. */
    suspend fun addRule(zoneId: String, existingRulesetId: String?, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        if (existingRulesetId != null) {
            safeApiCall { api.addCustomRule(zoneId, existingRulesetId, rule) }
        } else {
            safeApiCall { api.putCustomRuleset(zoneId, RulesetPhaseWrite(rules = listOf(rule))) }
        }

    suspend fun updateRule(zoneId: String, rulesetId: String, ruleId: String, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        safeApiCall { api.updateCustomRule(zoneId, rulesetId, ruleId, rule) }

    suspend fun deleteRule(zoneId: String, rulesetId: String, ruleId: String): ApiResult<Ruleset> =
        safeApiCall { api.deleteCustomRule(zoneId, rulesetId, ruleId) }
}
