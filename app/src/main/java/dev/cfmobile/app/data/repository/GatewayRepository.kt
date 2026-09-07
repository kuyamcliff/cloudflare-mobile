package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.GatewayList
import dev.cfmobile.app.data.remote.dto.GatewayLocation
import dev.cfmobile.app.data.remote.dto.GatewayLocationNetwork
import dev.cfmobile.app.data.remote.dto.GatewayLocationWrite
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

    /** DNS locations: the resolver addresses a network's queries arrive on. */
    suspend fun listLocations(accountId: String): ApiResult<List<GatewayLocation>> =
        safeApiCall { api.listGatewayLocations(accountId) }

    suspend fun createLocation(
        accountId: String,
        name: String,
        clientDefault: Boolean,
        networks: List<GatewayLocationNetwork>
    ): ApiResult<GatewayLocation> =
        safeApiCall {
            api.createGatewayLocation(
                accountId,
                GatewayLocationWrite(name = name, clientDefault = clientDefault, networks = networks)
            )
        }

    suspend fun deleteLocation(accountId: String, locationId: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteGatewayLocation(accountId, locationId) }
}
