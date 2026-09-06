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

/** A redirect rule's static or dynamic target, inside `from_value`. */
@JsonClass(generateAdapter = true)
data class RedirectTargetUrl(
    val value: String? = null,
    val expression: String? = null
)

@JsonClass(generateAdapter = true)
data class RedirectFromValue(
    @Json(name = "status_code") val statusCode: Int = 301,
    @Json(name = "target_url") val targetUrl: RedirectTargetUrl = RedirectTargetUrl(),
    @Json(name = "preserve_query_string") val preserveQueryString: Boolean? = null
)

/** An Origin Rule's replacement origin. Either field may be omitted to leave that half of the
 *  origin as Cloudflare resolved it. */
@JsonClass(generateAdapter = true)
data class RuleOrigin(
    val host: String? = null,
    val port: Int? = null
)

@JsonClass(generateAdapter = true)
data class RuleSni(val value: String = "")

/** A Cache Rule's TTL block. `mode` is Cloudflare's own vocabulary - "respect_origin",
 *  "override_origin", or "bypass_by_default" - and `default` is the override in seconds,
 *  meaningful only in "override_origin". */
@JsonClass(generateAdapter = true)
data class RuleTtl(
    val mode: String = "respect_origin",
    val default: Int? = null
)

/**
 * The action-specific payload of a Rulesets rule. Every rules-engine family in this app writes
 * into the same `action_parameters` object, so one wide type covers them all and Moshi omits
 * whatever a given family leaves null:
 *
 *  - Transform Rules ("rewrite") populate [uri] or [headers]
 *  - Redirect Rules ("redirect") populate [fromValue]
 *  - Origin Rules ("route") populate [origin], [hostHeader], and/or [sni]
 *  - Cache Rules ("set_cache_settings") populate [cache], [edgeTtl], [browserTtl]
 *  - Managed WAF deployments ("execute") populate [id] with the managed ruleset's id
 */
@JsonClass(generateAdapter = true)
data class RuleActionParameters(
    val uri: UriRewrite? = null,
    val headers: Map<String, HeaderModification>? = null,
    @Json(name = "from_value") val fromValue: RedirectFromValue? = null,
    val origin: RuleOrigin? = null,
    @Json(name = "host_header") val hostHeader: String? = null,
    val sni: RuleSni? = null,
    val cache: Boolean? = null,
    @Json(name = "edge_ttl") val edgeTtl: RuleTtl? = null,
    @Json(name = "browser_ttl") val browserTtl: RuleTtl? = null,
    val id: String? = null
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
    @Json(name = "action_parameters") val actionParameters: RuleActionParameters? = null
)

@JsonClass(generateAdapter = true)
data class RulesetRuleWrite(
    val action: String,
    val expression: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val ratelimit: RateLimit? = null,
    @Json(name = "action_parameters") val actionParameters: RuleActionParameters? = null
)

@JsonClass(generateAdapter = true)
data class Ruleset(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    /** "managed" for a Cloudflare-maintained ruleset, "zone"/"root" for one the account owns. */
    val kind: String? = null,
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

@JsonClass(generateAdapter = true)
data class KvKey(
    val name: String = "",
    val expiration: Long? = null,
    val metadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class D1QueryRequest(
    val sql: String
)

@JsonClass(generateAdapter = true)
data class D1QueryMeta(
    val duration: Double? = null,
    @Json(name = "rows_read") val rowsRead: Long? = null,
    @Json(name = "rows_written") val rowsWritten: Long? = null,
    @Json(name = "changed_db") val changedDb: Boolean? = null
)

/** One statement's outcome. `results` is null or empty for statements that don't return rows
 *  (INSERT, CREATE TABLE, ...), which is a success, not an error. */
@JsonClass(generateAdapter = true)
data class D1QueryResult(
    val success: Boolean = false,
    val results: List<Map<String, Any?>>? = null,
    val meta: D1QueryMeta? = null
)

@JsonClass(generateAdapter = true)
data class WorkerSchedule(
    val cron: String = "",
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkerSchedules(
    val schedules: List<WorkerSchedule> = emptyList()
)

@JsonClass(generateAdapter = true)
data class WorkerRoute(
    val id: String = "",
    val pattern: String = "",
    val script: String? = null
)

/** Cloudflare treats an empty `script` as "no worker runs here", which is how a route is used
 *  to carve an exception out of a broader pattern. */
@JsonClass(generateAdapter = true)
data class WorkerRouteWrite(
    val pattern: String,
    val script: String
)

@JsonClass(generateAdapter = true)
data class D1Database(
    val uuid: String = "",
    val name: String = "",
    val version: String? = null,
    @Json(name = "num_tables") val numTables: Int? = null,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class D1DatabaseCreate(
    val name: String
)

@JsonClass(generateAdapter = true)
data class WorkerScript(
    val id: String = "",
    val etag: String? = null,
    val handlers: List<String>? = null,
    @Json(name = "usage_model") val usageModel: String? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayRule(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val enabled: Boolean = true,
    val action: String = "",
    val traffic: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayRuleCreate(
    val name: String,
    val action: String,
    val traffic: String,
    /** Which Gateway engine evaluates the policy: "dns", "http", or "l4". */
    val filters: List<String> = listOf("dns"),
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class DnssecStatus(
    val status: String? = null,
    val algorithm: String? = null,
    val digest: String? = null,
    @Json(name = "digest_algorithm") val digestAlgorithm: String? = null,
    @Json(name = "digest_type") val digestType: String? = null,
    val ds: String? = null,
    val flags: Int? = null,
    @Json(name = "key_tag") val keyTag: Int? = null,
    @Json(name = "key_type") val keyType: String? = null,
    @Json(name = "public_key") val publicKey: String? = null
)

@JsonClass(generateAdapter = true)
data class DnssecUpdate(
    val status: String
)

@JsonClass(generateAdapter = true)
data class CustomHostnameSsl(
    val status: String? = null,
    val method: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class CustomHostname(
    val id: String = "",
    val hostname: String = "",
    val status: String? = null,
    val ssl: CustomHostnameSsl? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CustomHostnameCreate(
    val hostname: String,
    val ssl: CustomHostnameSsl
)

@JsonClass(generateAdapter = true)
data class CertificatePack(
    val id: String = "",
    val type: String? = null,
    val status: String? = null,
    val hosts: List<String> = emptyList(),
    @Json(name = "certificate_authority") val certificateAuthority: String? = null,
    @Json(name = "validity_days") val validityDays: Int? = null
)

@JsonClass(generateAdapter = true)
data class WaitingRoom(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val path: String? = null,
    val suspended: Boolean = false,
    @Json(name = "new_users_per_minute") val newUsersPerMinute: Int? = null,
    @Json(name = "total_active_users") val totalActiveUsers: Int? = null,
    @Json(name = "queue_all") val queueAll: Boolean? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class WaitingRoomCreate(
    val name: String,
    val host: String,
    val path: String,
    @Json(name = "new_users_per_minute") val newUsersPerMinute: Int,
    @Json(name = "total_active_users") val totalActiveUsers: Int
)

@JsonClass(generateAdapter = true)
data class HealthCheck(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val type: String? = null,
    val status: String? = null,
    val description: String? = null,
    val suspended: Boolean = false,
    val interval: Int? = null,
    val retries: Int? = null,
    val timeout: Int? = null,
    @Json(name = "failure_reason") val failureReason: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthCheckCreate(
    val name: String,
    val address: String,
    val type: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class EmailRoutingSettings(
    val enabled: Boolean = false,
    val name: String? = null,
    val status: String? = null,
    val created: String? = null
)

@JsonClass(generateAdapter = true)
data class EmailRoutingMatcher(
    val type: String = "literal",
    val field: String? = "to",
    val value: String? = null
)

@JsonClass(generateAdapter = true)
data class EmailRoutingAction(
    val type: String = "forward",
    val value: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EmailRoutingRule(
    val tag: String = "",
    val name: String? = null,
    val enabled: Boolean = true,
    val priority: Int? = null,
    val matchers: List<EmailRoutingMatcher> = emptyList(),
    val actions: List<EmailRoutingAction> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EmailRoutingRuleCreate(
    val name: String,
    val enabled: Boolean,
    val matchers: List<EmailRoutingMatcher>,
    val actions: List<EmailRoutingAction>
)

@JsonClass(generateAdapter = true)
data class SpectrumDns(
    val type: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class SpectrumApp(
    val id: String = "",
    val protocol: String? = null,
    val dns: SpectrumDns? = null,
    @Json(name = "origin_direct") val originDirect: List<String>? = null,
    @Json(name = "traffic_type") val trafficType: String? = null,
    @Json(name = "ip_firewall") val ipFirewall: Boolean? = null,
    @Json(name = "created_on") val createdOn: String? = null
)

@JsonClass(generateAdapter = true)
data class MagicGreTunnel(
    val id: String = "",
    val name: String = "",
    @Json(name = "cloudflare_gre_endpoint") val cloudflareEndpoint: String? = null,
    @Json(name = "customer_gre_endpoint") val customerEndpoint: String? = null,
    @Json(name = "interface_address") val interfaceAddress: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class MagicGreTunnelList(
    @Json(name = "gre_tunnels") val greTunnels: List<MagicGreTunnel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MagicIpsecTunnel(
    val id: String = "",
    val name: String = "",
    @Json(name = "cloudflare_endpoint") val cloudflareEndpoint: String? = null,
    @Json(name = "customer_endpoint") val customerEndpoint: String? = null,
    @Json(name = "interface_address") val interfaceAddress: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class MagicIpsecTunnelList(
    @Json(name = "ipsec_tunnels") val ipsecTunnels: List<MagicIpsecTunnel> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MagicRoute(
    val id: String = "",
    val prefix: String = "",
    val nexthop: String? = null,
    val priority: Int? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class MagicRouteList(
    val routes: List<MagicRoute> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SubscriptionProduct(
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class SubscriptionRatePlan(
    val id: String? = null,
    @Json(name = "public_name") val publicName: String? = null,
    val currency: String? = null
)

@JsonClass(generateAdapter = true)
data class AccountSubscription(
    val id: String = "",
    val state: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val frequency: String? = null,
    val product: SubscriptionProduct? = null,
    @Json(name = "rate_plan") val ratePlan: SubscriptionRatePlan? = null,
    @Json(name = "current_period_end") val currentPeriodEnd: String? = null
)

@JsonClass(generateAdapter = true)
data class ScreenshotRequest(
    val url: String
)

/** One WAF/firewall event from the firewallEventsAdaptive GraphQL dataset. Every field is
 *  optional because which columns a plan may query varies. */
@JsonClass(generateAdapter = true)
data class FirewallEvent(
    val datetime: String? = null,
    val action: String? = null,
    val source: String? = null,
    val clientIP: String? = null,
    val clientCountryName: String? = null,
    val clientAsn: String? = null,
    val clientRequestHTTPHost: String? = null,
    val clientRequestPath: String? = null,
    val clientRequestHTTPMethodName: String? = null,
    val userAgent: String? = null,
    val ruleId: String? = null
)

@JsonClass(generateAdapter = true)
data class FirewallEventsZone(
    val firewallEventsAdaptive: List<FirewallEvent> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FirewallEventsViewer(
    val zones: List<FirewallEventsZone> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FirewallEventsData(
    val viewer: FirewallEventsViewer? = null
)

@JsonClass(generateAdapter = true)
data class PageShieldSettings(
    val enabled: Boolean = false,
    @Json(name = "use_cloudflare_reporting_endpoint") val useCloudflareReportingEndpoint: Boolean? = null,
    @Json(name = "use_connection_url_path") val useConnectionUrlPath: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class PageShieldSettingsUpdate(
    val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class PageShieldScript(
    val id: String = "",
    val url: String? = null,
    val host: String? = null,
    val status: String? = null,
    @Json(name = "first_seen_at") val firstSeenAt: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null,
    @Json(name = "js_integrity_score") val jsIntegrityScore: Int? = null
)

@JsonClass(generateAdapter = true)
data class PageShieldConnection(
    val id: String = "",
    val url: String? = null,
    val host: String? = null,
    @Json(name = "first_seen_at") val firstSeenAt: String? = null,
    @Json(name = "last_seen_at") val lastSeenAt: String? = null
)

@JsonClass(generateAdapter = true)
data class DdosRule(
    val id: String = "",
    val description: String? = null,
    val action: String? = null,
    val enabled: Boolean = true,
    val expression: String? = null
)

@JsonClass(generateAdapter = true)
data class DdosRuleset(
    val id: String = "",
    val name: String? = null,
    val description: String? = null,
    val phase: String? = null,
    @Json(name = "last_updated") val lastUpdated: String? = null,
    val rules: List<DdosRule> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ApiOperation(
    @Json(name = "operation_id") val operationId: String = "",
    val method: String? = null,
    val host: String? = null,
    val endpoint: String? = null,
    @Json(name = "last_updated") val lastUpdated: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamVideoStatus(
    val state: String? = null,
    @Json(name = "errorReasonText") val errorReasonText: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamVideo(
    val uid: String = "",
    val status: StreamVideoStatus? = null,
    /** Free-form user metadata; Cloudflare puts the display name under "name". Values are
     *  typed [Any] because callers can store arbitrary JSON here. */
    val meta: Map<String, Any?>? = null,
    val created: String? = null,
    val duration: Double? = null,
    val size: Long? = null,
    val thumbnail: String? = null,
    val preview: String? = null,
    @Json(name = "readyToStream") val readyToStream: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CfImage(
    val id: String = "",
    val filename: String? = null,
    val uploaded: String? = null,
    @Json(name = "requireSignedURLs") val requireSignedUrls: Boolean? = null,
    val variants: List<String>? = null
)

/** Images' list response nests the array under "images", the same way R2 nests buckets. */
@JsonClass(generateAdapter = true)
data class ImagesListResult(
    val images: List<CfImage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ImagesCount(
    val allowed: Long? = null,
    val current: Long? = null
)

@JsonClass(generateAdapter = true)
data class ImagesStats(
    val count: ImagesCount? = null
)

@JsonClass(generateAdapter = true)
data class TurnstileWidget(
    val sitekey: String = "",
    val name: String = "",
    val domains: List<String> = emptyList(),
    val mode: String? = null,
    @Json(name = "created_on") val createdOn: String? = null
)

@JsonClass(generateAdapter = true)
data class TurnstileWidgetCreate(
    val name: String,
    val domains: List<String>,
    val mode: String
)

@JsonClass(generateAdapter = true)
data class LogpushJob(
    val id: Long = 0,
    val name: String? = null,
    val dataset: String? = null,
    val enabled: Boolean = false,
    @Json(name = "destination_conf") val destinationConf: String? = null,
    @Json(name = "last_complete") val lastComplete: String? = null,
    @Json(name = "last_error") val lastError: String? = null,
    @Json(name = "error_message") val errorMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class LogpushJobUpdate(
    val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class AiModelTask(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class AiModel(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val task: AiModelTask? = null
)

@JsonClass(generateAdapter = true)
data class DeviceUser(
    val email: String? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class EnrolledDevice(
    val id: String = "",
    val name: String? = null,
    @Json(name = "device_type") val deviceType: String? = null,
    val version: String? = null,
    @Json(name = "last_seen") val lastSeen: String? = null,
    val user: DeviceUser? = null
)

@JsonClass(generateAdapter = true)
data class PostureRule(
    val id: String = "",
    val name: String = "",
    val type: String? = null,
    val description: String? = null,
    val schedule: String? = null
)

@JsonClass(generateAdapter = true)
data class CfQueue(
    @Json(name = "queue_id") val queueId: String = "",
    @Json(name = "queue_name") val queueName: String = "",
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "producers_total_count") val producersCount: Int? = null,
    @Json(name = "consumers_total_count") val consumersCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class QueueCreate(
    @Json(name = "queue_name") val queueName: String
)

@JsonClass(generateAdapter = true)
data class DurableObjectNamespace(
    val id: String = "",
    val name: String = "",
    val script: String? = null,
    @Json(name = "class") val className: String? = null,
    @Json(name = "use_sqlite") val useSqlite: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class CfWorkflow(
    val id: String = "",
    val name: String = "",
    @Json(name = "class_name") val className: String? = null,
    @Json(name = "script_name") val scriptName: String? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class WorkflowInstance(
    val id: String = "",
    val status: String? = null,
    @Json(name = "version_id") val versionId: String? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "started_on") val startedOn: String? = null,
    @Json(name = "ended_on") val endedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class HyperdriveOrigin(
    val host: String? = null,
    val port: Int? = null,
    val database: String? = null,
    val scheme: String? = null,
    val user: String? = null
)

@JsonClass(generateAdapter = true)
data class HyperdriveCaching(
    val disabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class HyperdriveConfig(
    val id: String = "",
    val name: String = "",
    val origin: HyperdriveOrigin? = null,
    val caching: HyperdriveCaching? = null
)

@JsonClass(generateAdapter = true)
data class VectorizeIndexConfig(
    val dimensions: Int = 0,
    val metric: String = ""
)

@JsonClass(generateAdapter = true)
data class VectorizeIndex(
    val name: String = "",
    val description: String? = null,
    val config: VectorizeIndexConfig? = null,
    @Json(name = "created_on") val createdOn: String? = null
)

@JsonClass(generateAdapter = true)
data class VectorizeIndexCreate(
    val name: String,
    val config: VectorizeIndexConfig,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class CfTunnel(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    @Json(name = "tun_type") val tunType: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

/** Creates a remotely-managed tunnel (config_src "cloudflare") rather than a locally-managed
 *  one, so no tunnel_secret needs to be generated on-device - see CapabilityRegistry's
 *  migrationHint: this only registers the tunnel, running it still needs the cloudflared
 *  daemon elsewhere. */
@JsonClass(generateAdapter = true)
data class TunnelCreate(
    val name: String,
    @Json(name = "config_src") val configSrc: String = "cloudflare"
)

@JsonClass(generateAdapter = true)
data class PagesProject(
    val name: String = "",
    val subdomain: String? = null,
    val domains: List<String>? = null,
    @Json(name = "production_branch") val productionBranch: String? = null,
    @Json(name = "created_on") val createdOn: String? = null
)

@JsonClass(generateAdapter = true)
data class PagesDeploymentStage(
    val name: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class PagesDeploymentTriggerMetadata(
    val branch: String? = null,
    @Json(name = "commit_hash") val commitHash: String? = null,
    @Json(name = "commit_message") val commitMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class PagesDeploymentTrigger(
    val metadata: PagesDeploymentTriggerMetadata? = null
)

@JsonClass(generateAdapter = true)
data class AccessApplication(
    val id: String = "",
    val name: String = "",
    val domain: String = "",
    val aud: String? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AccessApplicationCreate(
    val name: String,
    val domain: String,
    val type: String = "self_hosted",
    @Json(name = "session_duration") val sessionDuration: String = "24h"
)

/** Cloudflare's Access policy "include" entries are a discriminated union in the real API
 *  (one populated key per object, e.g. {"email_domain":{"domain":"..."}} or
 *  {"email":{"email":"..."}}) - modeled here as one class with nullable branches, relying on
 *  Moshi's default of omitting null fields when writing JSON, rather than a custom adapter. */
@JsonClass(generateAdapter = true)
data class AccessPolicyIncludeRule(
    @Json(name = "email_domain") val emailDomain: AccessEmailDomainRule? = null,
    val email: AccessEmailRule? = null
)

@JsonClass(generateAdapter = true)
data class AccessEmailDomainRule(val domain: String)

@JsonClass(generateAdapter = true)
data class AccessEmailRule(val email: String)

/** An Access identity provider. Cloudflare's own one-time PIN provider has type "onetimepin"
 *  and no configuration; every other type carries provider credentials this app never reads. */
@JsonClass(generateAdapter = true)
data class AccessIdentityProvider(
    val id: String = "",
    val name: String = "",
    val type: String = ""
)

@JsonClass(generateAdapter = true)
data class AccessIdentityProviderCreate(
    val name: String,
    val type: String,
    /** Empty for one-time PIN, which is the only type this app can create. */
    val config: Map<String, String> = emptyMap()
)

/**
 * An Access service token. [clientSecret] is returned only in the response that creates the
 * token - Cloudflare never sends it again, and it is not stored anywhere by this app.
 */
@JsonClass(generateAdapter = true)
data class AccessServiceToken(
    val id: String = "",
    val name: String = "",
    @Json(name = "client_id") val clientId: String? = null,
    @Json(name = "client_secret") val clientSecret: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class AccessServiceTokenCreate(val name: String)

@JsonClass(generateAdapter = true)
data class GatewayListItem(val value: String = "")

/** A Zero Trust list. [count] is how many items it holds; the items themselves are a separate
 *  request. */
@JsonClass(generateAdapter = true)
data class GatewayList(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val type: String = "",
    val count: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class GatewayListCreate(
    val name: String,
    val type: String,
    val description: String? = null,
    val items: List<GatewayListItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AccessPolicyCreate(
    val name: String,
    val decision: String,
    val include: List<AccessPolicyIncludeRule>
)

@JsonClass(generateAdapter = true)
data class PagesDeployment(
    val id: String = "",
    val environment: String? = null,
    val url: String? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "latest_stage") val latestStage: PagesDeploymentStage? = null,
    @Json(name = "deployment_trigger") val deploymentTrigger: PagesDeploymentTrigger? = null
)

// ---- Account-level products: API tokens, notifications, bulk redirects, registrar, RUM ----

/** An API token as the tokens list reports it. The token's own value is never returned by any
 *  list or read endpoint - only by the call that creates or rolls it, which this app doesn't
 *  make. */
@JsonClass(generateAdapter = true)
data class ApiToken(
    val id: String = "",
    val name: String = "",
    val status: String? = null,
    @Json(name = "issued_on") val issuedOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null,
    @Json(name = "expires_on") val expiresOn: String? = null,
    @Json(name = "last_used_on") val lastUsedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationMechanismTarget(
    val id: String? = null,
    val name: String? = null
)

/** Where a notification policy sends: email addresses, webhooks, and PagerDuty services, each
 *  keyed by channel. */
@JsonClass(generateAdapter = true)
data class NotificationMechanisms(
    val email: List<NotificationMechanismTarget>? = null,
    val webhooks: List<NotificationMechanismTarget>? = null,
    val pagerduty: List<NotificationMechanismTarget>? = null
)

@JsonClass(generateAdapter = true)
data class NotificationPolicy(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val enabled: Boolean = true,
    @Json(name = "alert_type") val alertType: String? = null,
    val mechanisms: NotificationMechanisms? = null,
    val created: String? = null,
    val modified: String? = null
)

/** Only the fields this app changes; Cloudflare merges a PATCH into the stored policy. */
@JsonClass(generateAdapter = true)
data class NotificationPolicyUpdate(val enabled: Boolean)

/** An account-level Rules List. Bulk Redirects are the "redirect" kind; the same endpoint also
 *  serves IP and hostname lists used by WAF rules. */
@JsonClass(generateAdapter = true)
data class RulesList(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val kind: String = "",
    @Json(name = "num_items") val numItems: Int? = null,
    @Json(name = "created_on") val createdOn: String? = null,
    @Json(name = "modified_on") val modifiedOn: String? = null
)

@JsonClass(generateAdapter = true)
data class RulesListCreate(
    val name: String,
    val kind: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class BulkRedirect(
    @Json(name = "source_url") val sourceUrl: String = "",
    @Json(name = "target_url") val targetUrl: String = "",
    @Json(name = "status_code") val statusCode: Int? = null,
    @Json(name = "preserve_query_string") val preserveQueryString: Boolean? = null,
    @Json(name = "subpath_matching") val subpathMatching: Boolean? = null
)

/** One entry in a Rules List. Which field is populated depends on the list's kind. */
@JsonClass(generateAdapter = true)
data class RulesListItem(
    val id: String = "",
    val ip: String? = null,
    val hostname: RulesListHostname? = null,
    val redirect: BulkRedirect? = null,
    val comment: String? = null
)

@JsonClass(generateAdapter = true)
data class RulesListHostname(@Json(name = "url_hostname") val urlHostname: String = "")

/** A domain registered through Cloudflare Registrar. */
@JsonClass(generateAdapter = true)
data class RegistrarDomain(
    val id: String = "",
    val name: String = "",
    @Json(name = "available") val available: Boolean? = null,
    @Json(name = "auto_renew") val autoRenew: Boolean? = null,
    val locked: Boolean? = null,
    @Json(name = "current_registrar") val currentRegistrar: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "registry_statuses") val registryStatuses: String? = null,
    @Json(name = "transfer_in") val transferIn: RegistrarTransfer? = null
)

@JsonClass(generateAdapter = true)
data class RegistrarTransfer(
    @Json(name = "unlock_domain") val unlockDomain: String? = null,
    @Json(name = "approve_transfer") val approveTransfer: String? = null,
    @Json(name = "accept_foa") val acceptFoa: String? = null,
    @Json(name = "enter_auth_code") val enterAuthCode: String? = null
)

/** A Web Analytics (RUM) site. [snippet] is the JavaScript tag to paste into a page. */
@JsonClass(generateAdapter = true)
data class RumSite(
    @Json(name = "site_tag") val siteTag: String = "",
    @Json(name = "site_token") val siteToken: String? = null,
    val snippet: String? = null,
    @Json(name = "auto_install") val autoInstall: Boolean? = null,
    val created: String? = null,
    val ruleset: RumRuleset? = null
)

@JsonClass(generateAdapter = true)
data class RumRuleset(
    val id: String? = null,
    @Json(name = "zone_name") val zoneName: String? = null,
    val enabled: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class RumSiteCreate(
    val host: String,
    @Json(name = "auto_install") val autoInstall: Boolean = true
)

