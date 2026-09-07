package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetPhaseWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Rules that live in a Rulesets phase entrypoint, with the phase passed per call rather than
 * fixed as a constant the way WafRepository and RateLimitRepository fix theirs. Every
 * rules-engine family in this app shares these four operations and differs only in its phase
 * and the action_parameters it writes:
 *
 *  - Transform Rules: "http_request_transform", "http_request_late_transform",
 *    "http_response_headers_transform"
 *  - Redirect Rules: "http_request_dynamic_redirect"
 *  - Origin Rules: "http_request_origin"
 *  - Cache Rules: "http_request_cache_settings"
 *
 * A phase with no rules yet has no entrypoint ruleset at all, which Cloudflare answers with a
 * 404 - that's an empty list, not an error, so [getRuleset] maps it to null.
 */
class RulesetPhaseRepository(private val api: CloudflareApi) {

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

    /** Every ruleset the zone can see. Managed WAF rulesets are identified by kind "managed",
     *  and this is the only place their human-readable names come from - a deployment rule
     *  carries just the id. */
    suspend fun listRulesets(zoneId: String): ApiResult<List<Ruleset>> =
        safeApiCall { api.listRulesets(zoneId) }
}
