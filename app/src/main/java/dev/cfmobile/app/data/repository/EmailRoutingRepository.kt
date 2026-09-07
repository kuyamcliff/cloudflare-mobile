package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.EmailCatchAll
import dev.cfmobile.app.data.remote.dto.EmailCatchAllAction
import dev.cfmobile.app.data.remote.dto.EmailCatchAllWrite
import dev.cfmobile.app.data.remote.dto.EmailDestinationAddress
import dev.cfmobile.app.data.remote.dto.EmailDestinationCreate
import dev.cfmobile.app.data.remote.dto.EmailRoutingAction
import dev.cfmobile.app.data.remote.dto.EmailRoutingMatcher
import dev.cfmobile.app.data.remote.dto.EmailRoutingRule
import dev.cfmobile.app.data.remote.dto.EmailRoutingRuleCreate
import dev.cfmobile.app.data.remote.dto.EmailRoutingSettings
import dev.cfmobile.app.data.remote.safeApiCall
import dev.cfmobile.app.data.remote.safeApiCallUnit

/** Email Routing status and forwarding rules for a zone. Destination addresses live at the
 *  account level and have to be verified by clicking a link in an email, which this app can't
 *  do - so a rule here forwards to an address you have already verified. */
class EmailRoutingRepository(private val api: CloudflareApi) {

    suspend fun getSettings(zoneId: String): ApiResult<EmailRoutingSettings> =
        safeApiCall { api.getEmailRoutingSettings(zoneId) }

    suspend fun listRules(zoneId: String): ApiResult<List<EmailRoutingRule>> =
        safeApiCall { api.listEmailRoutingRules(zoneId) }

    suspend fun createForwardRule(
        zoneId: String,
        name: String,
        fromAddress: String,
        toAddress: String
    ): ApiResult<EmailRoutingRule> = safeApiCall {
        api.createEmailRoutingRule(
            zoneId,
            EmailRoutingRuleCreate(
                name = name,
                enabled = true,
                matchers = listOf(EmailRoutingMatcher(type = "literal", field = "to", value = fromAddress)),
                actions = listOf(EmailRoutingAction(type = "forward", value = listOf(toAddress)))
            )
        )
    }

    suspend fun deleteRule(zoneId: String, ruleTag: String): ApiResult<Unit> =
        when (val result = safeApiCall { api.deleteEmailRoutingRule(zoneId, ruleTag) }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }

    /** Destination addresses are account-scoped: one verified address can receive mail routed
     *  from any zone on the account. */
    suspend fun listDestinations(accountId: String): ApiResult<List<EmailDestinationAddress>> =
        safeApiCall { api.listEmailDestinations(accountId) }

    suspend fun createDestination(accountId: String, email: String): ApiResult<EmailDestinationAddress> =
        safeApiCall { api.createEmailDestination(accountId, EmailDestinationCreate(email)) }

    suspend fun deleteDestination(accountId: String, addressTag: String): ApiResult<Unit> =
        safeApiCallUnit { api.deleteEmailDestination(accountId, addressTag) }

    suspend fun getCatchAll(zoneId: String): ApiResult<EmailCatchAll> =
        safeApiCall { api.getEmailCatchAll(zoneId) }

    /** Forwarding to an address, or dropping the mail when [forwardTo] is empty. */
    suspend fun setCatchAll(zoneId: String, enabled: Boolean, forwardTo: List<String>): ApiResult<EmailCatchAll> =
        safeApiCall {
            api.updateEmailCatchAll(
                zoneId,
                EmailCatchAllWrite(
                    enabled = enabled,
                    actions = listOf(
                        if (forwardTo.isEmpty()) {
                            EmailCatchAllAction(type = "drop", value = emptyList())
                        } else {
                            EmailCatchAllAction(type = "forward", value = forwardTo)
                        }
                    )
                )
            )
        }
}
