package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.PageRule
import dev.cfmobile.app.data.remote.dto.PageRuleAction
import dev.cfmobile.app.data.remote.dto.PageRuleConstraint
import dev.cfmobile.app.data.remote.dto.PageRuleTarget
import dev.cfmobile.app.data.remote.dto.PageRuleWrite
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

class PageRulesRepository(private val api: CloudflareApi) {

    suspend fun listRules(zoneId: String): ApiResult<List<PageRule>> =
        safeApiCall { api.listPageRules(zoneId) }

    suspend fun createRule(zoneId: String, urlPattern: String, actionId: String, actionValue: Any?, priority: Int): ApiResult<PageRule> =
        safeApiCall {
            api.createPageRule(
                zoneId,
                PageRuleWrite(
                    targets = listOf(PageRuleTarget(constraint = PageRuleConstraint(value = urlPattern))),
                    actions = listOf(PageRuleAction(id = actionId, value = actionValue)),
                    priority = priority
                )
            )
        }

    suspend fun setStatus(zoneId: String, rule: PageRule, active: Boolean): ApiResult<PageRule> =
        safeApiCall {
            api.updatePageRule(
                zoneId,
                rule.id,
                PageRuleWrite(targets = rule.targets, actions = rule.actions, priority = rule.priority, status = if (active) "active" else "disabled")
            )
        }

    suspend fun deleteRule(zoneId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deletePageRule(zoneId, ruleId) }
}
