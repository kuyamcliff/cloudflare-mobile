package dev.cfmobile.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenVerifyResult(
    val id: String = "",
    val status: String = "",
    @Json(name = "expires_on") val expiresOn: String? = null
)

@JsonClass(generateAdapter = true)
data class CfUser(
    val id: String = "",
    val email: String = "",
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class CfAccount(
    val id: String = "",
    val name: String = ""
)

@JsonClass(generateAdapter = true)
data class CfPlan(
    val id: String = "",
    val name: String = "",
    @Json(name = "is_free") val isFree: Boolean = true
)

@JsonClass(generateAdapter = true)
data class CfZone(
    val id: String = "",
    val name: String = "",
    val status: String = "",
    val paused: Boolean = false,
    @Json(name = "name_servers") val nameServers: List<String> = emptyList(),
    val plan: CfPlan? = null,
    val account: CfAccount? = null
)

/** Union of the `data` object shapes Cloudflare uses for record types that can't be expressed
 *  as a plain `content` string (SRV, URI, TLSA/SMIMEA, NAPTR, SSHFP, CERT). Fields are all
 *  optional and only the ones relevant to the record's `type` are populated - see
 *  dev.cfmobile.app.ui.dns.buildDnsRecordWrite for which fields each type actually uses. */
@JsonClass(generateAdapter = true)
data class DnsRecordData(
    val priority: Int? = null,
    val weight: Int? = null,
    val port: Int? = null,
    val target: String? = null,
    val content: String? = null,
    val usage: Int? = null,
    val selector: Int? = null,
    @Json(name = "matching_type") val matchingType: Int? = null,
    val certificate: String? = null,
    val algorithm: Int? = null,
    val type: Int? = null,
    @Json(name = "key_tag") val keyTag: Int? = null,
    val fingerprint: String? = null,
    val order: Int? = null,
    val preference: Int? = null,
    val flags: String? = null,
    val service: String? = null,
    val regex: String? = null,
    val replacement: String? = null
)

@JsonClass(generateAdapter = true)
data class DnsRecord(
    val id: String = "",
    val type: String = "A",
    val name: String = "",
    val content: String = "",
    val ttl: Int = 1,
    val proxied: Boolean? = null,
    val proxiable: Boolean? = null,
    val priority: Int? = null,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val data: DnsRecordData? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class DnsRecordWrite(
    val type: String,
    val name: String,
    val content: String,
    val ttl: Int,
    val proxied: Boolean? = null,
    val priority: Int? = null,
    val comment: String? = null,
    val data: DnsRecordData? = null
)

@JsonClass(generateAdapter = true)
data class DnsBatchDeleteRef(val id: String)

@JsonClass(generateAdapter = true)
data class DnsBatchRequest(val deletes: List<DnsBatchDeleteRef>)

@JsonClass(generateAdapter = true)
data class DnsBatchResult(
    val deletes: List<DnsRecord>? = null
)

@JsonClass(generateAdapter = true)
data class DnsImportResult(
    @Json(name = "recs_added") val recsAdded: Int = 0,
    @Json(name = "total_records_parsed") val totalRecordsParsed: Int = 0
)

@JsonClass(generateAdapter = true)
data class ZoneSettingString(
    val id: String = "",
    val value: String = "",
    val editable: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ZoneSettingInt(
    val id: String = "",
    val value: Int = 0,
    val editable: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ZoneSettingPatchString(
    val value: String
)

@JsonClass(generateAdapter = true)
data class ZoneSettingPatchInt(
    val value: Int
)

@JsonClass(generateAdapter = true)
data class PurgeCacheRequest(
    @Json(name = "purge_everything") val purgeEverything: Boolean? = null,
    val files: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class FirewallFilter(
    val id: String? = null,
    val expression: String = "",
    val paused: Boolean = false,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class FirewallRule(
    val id: String = "",
    val paused: Boolean = false,
    val description: String? = null,
    val action: String = "block",
    val filter: FirewallFilter? = null
)

@JsonClass(generateAdapter = true)
data class FirewallRuleWrite(
    val filter: FirewallFilterWrite,
    val action: String,
    val description: String? = null,
    val paused: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FirewallFilterWrite(
    val expression: String,
    val paused: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AccessRuleConfiguration(
    val target: String = "ip",
    val value: String = ""
)

@JsonClass(generateAdapter = true)
data class AccessRule(
    val id: String = "",
    val mode: String = "block",
    val notes: String? = null,
    val configuration: AccessRuleConfiguration = AccessRuleConfiguration()
)

@JsonClass(generateAdapter = true)
data class AccessRuleWrite(
    val mode: String,
    val configuration: AccessRuleConfiguration,
    val notes: String? = null
)

/** One rule inside the modern WAF Custom Rules ruleset (Cloudflare's replacement for the
 *  legacy Firewall Rules engine that [FirewallRule] models). */
@JsonClass(generateAdapter = true)
data class RulesetRule(
    val id: String = "",
    val action: String = "block",
    val expression: String = "",
    val description: String? = null,
    val enabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RulesetRuleWrite(
    val action: String,
    val expression: String,
    val description: String? = null,
    val enabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class Ruleset(
    val id: String = "",
    val name: String? = null,
    val phase: String? = null,
    val rules: List<RulesetRule> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RulesetPhaseWrite(val rules: List<RulesetRuleWrite>)

@JsonClass(generateAdapter = true)
data class PageRuleTarget(
    val target: String = "url",
    val constraint: PageRuleConstraint = PageRuleConstraint()
)

@JsonClass(generateAdapter = true)
data class PageRuleConstraint(
    val operator: String = "matches",
    val value: String = ""
)

@JsonClass(generateAdapter = true)
data class PageRuleAction(
    val id: String,
    val value: Any? = null
)

@JsonClass(generateAdapter = true)
data class PageRule(
    val id: String = "",
    val targets: List<PageRuleTarget> = emptyList(),
    val actions: List<PageRuleAction> = emptyList(),
    val priority: Int = 1,
    val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class PageRuleWrite(
    val targets: List<PageRuleTarget>,
    val actions: List<PageRuleAction>,
    val priority: Int = 1,
    val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class AnalyticsDashboard(
    val totals: AnalyticsTotals? = null,
    val since: String? = null,
    val until: String? = null
)

@JsonClass(generateAdapter = true)
data class AnalyticsTotals(
    val requests: AnalyticsMetric? = null,
    val bandwidth: AnalyticsMetric? = null,
    val threats: AnalyticsMetric? = null,
    val uniques: AnalyticsUniques? = null
)

@JsonClass(generateAdapter = true)
data class AnalyticsMetric(
    val all: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class AnalyticsUniques(
    val all: Double = 0.0
)
