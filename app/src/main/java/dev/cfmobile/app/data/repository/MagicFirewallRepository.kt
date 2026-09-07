package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.Ruleset
import dev.cfmobile.app.data.remote.dto.RulesetPhaseWrite
import dev.cfmobile.app.data.remote.dto.RulesetRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall

/**
 * Magic Firewall: the packet filter in front of Magic Transit traffic. It is the same Rulesets
 * engine the zone screens use, at account scope and in the "magic_transit" phase, so this
 * mirrors [RulesetPhaseRepository] rather than inventing a second shape. As there, an account
 * with no rule yet has no entrypoint ruleset at all and Cloudflare answers 404, which is an
 * empty list rather than an error.
 */
class MagicFirewallRepository(private val api: CloudflareApi) {

    suspend fun getRuleset(accountId: String): ApiResult<Ruleset?> =
        when (val result = safeApiCall { api.getAccountPhaseRuleset(accountId, PHASE) }) {
            is ApiResult.Success -> ApiResult.Success(result.data)
            is ApiResult.Failure -> if (result.httpCode == 404) ApiResult.Success(null) else result
        }

    suspend fun addRule(accountId: String, existingRulesetId: String?, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        if (existingRulesetId != null) {
            safeApiCall { api.addAccountRulesetRule(accountId, existingRulesetId, rule) }
        } else {
            safeApiCall { api.putAccountPhaseRuleset(accountId, PHASE, RulesetPhaseWrite(rules = listOf(rule))) }
        }

    suspend fun updateRule(accountId: String, rulesetId: String, ruleId: String, rule: RulesetRuleWrite): ApiResult<Ruleset> =
        safeApiCall { api.updateAccountRulesetRule(accountId, rulesetId, ruleId, rule) }

    suspend fun deleteRule(accountId: String, rulesetId: String, ruleId: String): ApiResult<Ruleset> =
        safeApiCall { api.deleteAccountRulesetRule(accountId, rulesetId, ruleId) }

    private companion object {
        const val PHASE = "magic_transit"
    }
}
