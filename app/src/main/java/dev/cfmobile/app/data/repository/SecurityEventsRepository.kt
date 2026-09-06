package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.GraphQlRequest
import dev.cfmobile.app.data.remote.dto.FirewallEvent
import dev.cfmobile.app.data.remote.safeGraphQlCall
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Security events come from Cloudflare's GraphQL analytics API (firewallEventsAdaptive);
 * the old REST firewall/events endpoint is gone. Retention and which fields a query may select
 * both depend on the zone's plan, so an empty list here is a legitimate answer rather than a
 * bug - see CapabilityRegistry's migrationHint.
 */
class SecurityEventsRepository(private val api: CloudflareApi) {

    suspend fun listEvents(
        zoneId: String,
        sinceHours: Long = 24,
        limit: Int = 100
    ): ApiResult<List<FirewallEvent>> {
        val until = Instant.now()
        val since = until.minus(sinceHours, ChronoUnit.HOURS)
        val request = GraphQlRequest(
            query = FIREWALL_EVENTS_QUERY,
            variables = mapOf(
                "zoneTag" to zoneId,
                "since" to since.toString(),
                "until" to until.toString(),
                "limit" to limit
            )
        )
        return when (val result = safeGraphQlCall { api.queryFirewallEvents(request) }) {
            is ApiResult.Success ->
                ApiResult.Success(result.data.viewer?.zones?.firstOrNull()?.firewallEventsAdaptive.orEmpty())
            is ApiResult.Failure -> result
        }
    }

    private companion object {
        const val FIREWALL_EVENTS_QUERY = """
            query FirewallEvents(${'$'}zoneTag: String!, ${'$'}since: Time!, ${'$'}until: Time!, ${'$'}limit: Int!) {
              viewer {
                zones(filter: { zoneTag: ${'$'}zoneTag }) {
                  firewallEventsAdaptive(
                    filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                    limit: ${'$'}limit
                    orderBy: [datetime_DESC]
                  ) {
                    datetime
                    action
                    source
                    clientIP
                    clientCountryName
                    clientAsn
                    clientRequestHTTPHost
                    clientRequestPath
                    clientRequestHTTPMethodName
                    userAgent
                    ruleId
                  }
                }
              }
            }
        """
    }
}
