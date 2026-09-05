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

/** The rate-limiting-specific half of a Rulesets rule - present only for rules in the
 *  "http_ratelimit" phase (PRD §9: threshold-based Rate Limiting). */
@JsonClass(generateAdapter = true)
data class RateLimit(
    val characteristics: List<String> = listOf("ip.src"),
    val period: Int = 60,
    @Json(name = "requests_per_period") val requestsPerPeriod: Int = 100,
    @Json(name = "mitigation_timeout") val mitigationTimeout: Int? = null
)

/** One side of a URI rewrite (path or query) - exactly one of [value] (static) or
 *  [expression] (dynamic) is set, matching Cloudflare's "rewrite" action schema for the
 *  "http_request_transform" phase (PRD §9: URL Rewrite Rules). */
@JsonClass(generateAdapter = true)
data class UriRewritePart(
    val value: String? = null,
    val expression: String? = null
)

@JsonClass(generateAdapter = true)
data class UriRewrite(
    val path: UriRewritePart? = null,
    val query: UriRewritePart? = null
)

/** One header change inside a "rewrite" action's `headers` map - keyed by header name.
 *  `operation` is "set" or "remove"; "set" carries exactly one of [value] (static) or
 *  [expression] (dynamic), "remove" carries neither (PRD §9: Request/Response Header
 *  Transform Rules, phases "http_request_late_transform" / "http_response_headers_transform"). */
@JsonClass(generateAdapter = true)
data class HeaderModification(
    val operation: String,
    val value: String? = null,
    val expression: String? = null
)

/** The action-specific payload of a "rewrite" rule. Which of [uri]/[headers] is populated
 *  depends on which phase the rule lives in - URL Rewrite rules set [uri], Request/Response
 *  Header Transform rules set [headers]. */
@JsonClass(generateAdapter = true)
data class TransformActionParameters(
    val uri: UriRewrite? = null,
    val headers: Map<String, HeaderModification>? = null
)

/** One rule inside a Rulesets phase entrypoint. What it does is entirely determined by
 *  which phase its ruleset belongs to plus [action]: a WAF Custom Rule (the modern
 *  replacement for the legacy Firewall Rules engine [FirewallRule] models), a Rate Limiting
 *  rule when [ratelimit] is present, or a Transform Rule (URL Rewrite / header modification)
 *  when [actionParameters] is present. */
@JsonClass(generateAdapter = true)
data class RulesetRule(
    val id: String = "",
    val action: String = "block",
    val expression: String = "",
    val description: String? = null,
    val enabled: Boolean = true,
    val ratelimit: RateLimit? = null,
    @Json(name = "action_parameters") val actionParameters: TransformActionParameters? = null
)

@JsonClass(generateAdapter = true)
data class RulesetRuleWrite(
    val action: String,
    val expression: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val ratelimit: RateLimit? = null,
    @Json(name = "action_parameters") val actionParameters: TransformActionParameters? = null
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

@JsonClass(generateAdapter = true)
data class AccountRole(
    val id: String = "",
    val name: String = "",
    val description: String = ""
)

@JsonClass(generateAdapter = true)
data class AccountMemberUser(
    val id: String = "",
    val email: String = "",
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null
)

@JsonClass(generateAdapter = true)
data class AccountMember(
    val id: String = "",
    val user: AccountMemberUser = AccountMemberUser(),
    val status: String = "",
    val roles: List<AccountRole> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AccountMemberInvite(
    val email: String,
    val roles: List<String>
)

@JsonClass(generateAdapter = true)
data class AuditLogActor(
    val id: String? = null,
    val email: String? = null,
    val ip: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class AuditLogAction(
    val type: String? = null,
    val result: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class AuditLogResource(
    val id: String? = null,
    val type: String? = null,
    val product: String? = null
)

@JsonClass(generateAdapter = true)
data class AuditLogEntry(
    val id: String = "",
    val action: AuditLogAction? = null,
    val actor: AuditLogActor? = null,
    val resource: AuditLogResource? = null,
    @Json(name = "when") val occurredAt: String? = null,
    @Json(name = "newValue") val newValue: String? = null,
    @Json(name = "oldValue") val oldValue: String? = null
)

@JsonClass(generateAdapter = true)
data class LoadBalancerOrigin(
    val name: String = "",
    val address: String = "",
    val enabled: Boolean = true,
    val weight: Double = 1.0
)

/** Pools and their origins are account-level, shared across every zone's load balancers -
 *  unlike [LoadBalancer] itself, which is zone-scoped. Health check monitors aren't modeled
 *  here (see LoadBalancingRepository); a pool works without one, just with no automatic
 *  failover based on health. */
@JsonClass(generateAdapter = true)
data class LoadBalancerPool(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val monitor: String? = null,
    val origins: List<LoadBalancerOrigin> = emptyList(),
    @Json(name = "minimum_origins") val minimumOrigins: Int = 1
)

@JsonClass(generateAdapter = true)
data class LoadBalancerPoolWrite(
    val name: String,
    val enabled: Boolean = true,
    val origins: List<LoadBalancerOrigin>,
    @Json(name = "minimum_origins") val minimumOrigins: Int = 1
)

@JsonClass(generateAdapter = true)
data class LoadBalancer(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val proxied: Boolean = true,
    @Json(name = "default_pools") val defaultPools: List<String> = emptyList(),
    @Json(name = "fallback_pool") val fallbackPool: String? = null,
    val ttl: Int? = null
)

@JsonClass(generateAdapter = true)
data class LoadBalancerWrite(
    val name: String,
    val enabled: Boolean = true,
    val proxied: Boolean = true,
    @Json(name = "default_pools") val defaultPools: List<String>,
    @Json(name = "fallback_pool") val fallbackPool: String,
    val ttl: Int = 30
)

@JsonClass(generateAdapter = true)
data class R2Bucket(
    val name: String = "",
    @Json(name = "creation_date") val creationDate: String? = null
)

/** R2's list-buckets response nests the array under "buckets" rather than returning it as
 *  `result` directly - unlike almost every other Cloudflare v4 list endpoint this app calls. */
@JsonClass(generateAdapter = true)
data class R2BucketListResult(
    val buckets: List<R2Bucket> = emptyList()
)

@JsonClass(generateAdapter = true)
data class R2BucketCreate(
    val name: String
)

@JsonClass(generateAdapter = true)
data class KvNamespace(
    val id: String = "",
    val title: String = ""
)

@JsonClass(generateAdapter = true)
data class KvNamespaceCreate(
    val title: String
)
