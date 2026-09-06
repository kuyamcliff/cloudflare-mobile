package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.RulesList
import dev.cfmobile.app.data.remote.dto.RulesListCreate
import dev.cfmobile.app.data.remote.dto.RulesListItem
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/**
 * Account-level Rules Lists. Bulk Redirects live in a list of kind "redirect"; the same
 * endpoint also serves the IP and hostname lists that WAF rules match against, so the caller
 * filters by kind rather than this repository hiding the others.
 */
class BulkRedirectsRepository(private val api: CloudflareApi) {

    suspend fun listLists(accountId: String): ApiResult<List<RulesList>> =
        safeApiCall { api.listRulesLists(accountId) }

    suspend fun listItems(accountId: String, listId: String): ApiResult<List<RulesListItem>> =
        safeApiCall { api.listRulesListItems(accountId, listId) }

    suspend fun createList(accountId: String, name: String, kind: String, description: String?): ApiResult<RulesList> =
        safeApiCall { api.createRulesList(accountId, RulesListCreate(name = name, kind = kind, description = description)) }

    suspend fun deleteList(accountId: String, listId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteRulesList(accountId, listId) }
}
