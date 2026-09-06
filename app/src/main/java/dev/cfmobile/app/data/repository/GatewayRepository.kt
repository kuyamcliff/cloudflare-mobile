package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.GatewayList
import dev.cfmobile.app.data.remote.dto.GatewayListCreate
import dev.cfmobile.app.data.remote.dto.GatewayListItem
import dev.cfmobile.app.data.remote.dto.GatewayRule
import dev.cfmobile.app.data.remote.dto.GatewayRuleCreate
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Zero Trust Gateway policies (DNS, HTTP, and network) plus the lists a policy can match
 *  against. Richer Wirefilter expressions - categories, identity, device posture - and rule
 *  ordering aren't implemented here, see CapabilityRegistry's migrationHint. */
class GatewayRepository(private val api: CloudflareApi) {

    suspend fun listRules(accountId: String): ApiResult<List<GatewayRule>> =
        safeApiCall { api.listGatewayRules(accountId) }

    suspend fun createRule(accountId: String, rule: GatewayRuleCreate): ApiResult<GatewayRule> =
        safeApiCall { api.createGatewayRule(accountId, rule) }

    suspend fun deleteRule(accountId: String, ruleId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteGatewayRule(accountId, ruleId) }

    suspend fun listLists(accountId: String): ApiResult<List<GatewayList>> =
        safeApiCall { api.listGatewayLists(accountId) }

    /** A list's items are a separate request - the list itself only reports how many it holds. */
    suspend fun listItems(accountId: String, listId: String): ApiResult<List<GatewayListItem>> =
        safeApiCall { api.listGatewayListItems(accountId, listId) }

    suspend fun createList(accountId: String, list: GatewayListCreate): ApiResult<GatewayList> =
        safeApiCall { api.createGatewayList(accountId, list) }

    suspend fun deleteList(accountId: String, listId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteGatewayList(accountId, listId) }
}
