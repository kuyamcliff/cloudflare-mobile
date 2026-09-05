package dev.cfmobile.app.data.remote

import dev.cfmobile.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Cloudflare API v4 surface used by this app. Every call returns the raw [CfEnvelope] so
 * repositories can inspect `success` / `errors` even on HTTP 4xx responses (Cloudflare puts
 * the real error message in the body, not just the status line).
 */
interface CloudflareApi {

    @GET("user/tokens/verify")
    suspend fun verifyToken(): Response<CfEnvelope<TokenVerifyResult>>

    /** Used only while adding a new token, before it's saved anywhere, via an explicit
     *  header rather than the app's normal [AuthInterceptor] (which authenticates as the
     *  already-active account). */
    @GET("user/tokens/verify")
    suspend fun verifyTokenWithAuth(
        @Header("Authorization") authorization: String
    ): Response<CfEnvelope<TokenVerifyResult>>

    /** Fallback validity check for account-owned API tokens (the `cfat_`-prefixed kind
     *  created under Account > API Tokens rather than a user's own profile). Those tokens
     *  aren't tied to a user identity at all, so `/user/tokens/verify` always rejects them
     *  with "Invalid API Token" even when the token works fine - listing zones is something
     *  every token this app can use needs to be able to do anyway. */
    @GET("zones")
    suspend fun listZonesWithAuth(
        @Header("Authorization") authorization: String,
        @Query("per_page") perPage: Int = 1
    ): Response<CfEnvelope<List<CfZone>>>

    @GET("user")
    suspend fun getUser(): Response<CfEnvelope<CfUser>>

    @GET("accounts")
    suspend fun listAccounts(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<CfAccount>>>

    @GET("zones")
    suspend fun listZones(
        @Query("account.id") accountId: String? = null,
        @Query("name") name: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<CfZone>>>

    @GET("zones/{zoneId}")
    suspend fun getZone(@Path("zoneId") zoneId: String): Response<CfEnvelope<CfZone>>

    // ---- DNS records ----

    @GET("zones/{zoneId}/dns_records")
    suspend fun listDnsRecords(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 100
    ): Response<CfEnvelope<List<DnsRecord>>>

    @POST("zones/{zoneId}/dns_records")
    suspend fun createDnsRecord(
        @Path("zoneId") zoneId: String,
        @Body record: DnsRecordWrite
    ): Response<CfEnvelope<DnsRecord>>

    @PUT("zones/{zoneId}/dns_records/{recordId}")
    suspend fun updateDnsRecord(
        @Path("zoneId") zoneId: String,
        @Path("recordId") recordId: String,
        @Body record: DnsRecordWrite
    ): Response<CfEnvelope<DnsRecord>>

    @DELETE("zones/{zoneId}/dns_records/{recordId}")
    suspend fun deleteDnsRecord(
        @Path("zoneId") zoneId: String,
        @Path("recordId") recordId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @POST("zones/{zoneId}/dns_records/batch")
    suspend fun batchDnsRecords(
        @Path("zoneId") zoneId: String,
        @Body batch: DnsBatchRequest
    ): Response<CfEnvelope<DnsBatchResult>>

    /** Returns a raw BIND zone file, not a [CfEnvelope] - Cloudflare's export endpoint is the
     *  one DNS record response that isn't JSON. */
    @GET("zones/{zoneId}/dns_records/export")
    suspend fun exportDnsRecords(@Path("zoneId") zoneId: String): Response<ResponseBody>

    @Multipart
    @POST("zones/{zoneId}/dns_records/import")
    suspend fun importDnsRecords(
        @Path("zoneId") zoneId: String,
        @Part file: MultipartBody.Part,
        @Part proxied: MultipartBody.Part
    ): Response<CfEnvelope<DnsImportResult>>

    // ---- Zone settings (string-valued) ----

    @GET("zones/{zoneId}/settings/{setting}")
    suspend fun getStringSetting(
        @Path("zoneId") zoneId: String,
        @Path("setting") setting: String
    ): Response<CfEnvelope<ZoneSettingString>>

    @PATCH("zones/{zoneId}/settings/{setting}")
    suspend fun patchStringSetting(
        @Path("zoneId") zoneId: String,
        @Path("setting") setting: String,
        @Body body: ZoneSettingPatchString
    ): Response<CfEnvelope<ZoneSettingString>>

    @GET("zones/{zoneId}/settings/{setting}")
    suspend fun getIntSetting(
        @Path("zoneId") zoneId: String,
        @Path("setting") setting: String
    ): Response<CfEnvelope<ZoneSettingInt>>

    @PATCH("zones/{zoneId}/settings/{setting}")
    suspend fun patchIntSetting(
        @Path("zoneId") zoneId: String,
        @Path("setting") setting: String,
        @Body body: ZoneSettingPatchInt
    ): Response<CfEnvelope<ZoneSettingInt>>

    // ---- Cache purge ----

    @POST("zones/{zoneId}/purge_cache")
    suspend fun purgeCache(
        @Path("zoneId") zoneId: String,
        @Body body: PurgeCacheRequest
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Firewall rules ----

    @GET("zones/{zoneId}/firewall/rules")
    suspend fun listFirewallRules(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<FirewallRule>>>

    @POST("zones/{zoneId}/firewall/rules")
    suspend fun createFirewallRule(
        @Path("zoneId") zoneId: String,
        @Body rules: List<FirewallRuleWrite>
    ): Response<CfEnvelope<List<FirewallRule>>>

    @DELETE("zones/{zoneId}/firewall/rules/{ruleId}")
    suspend fun deleteFirewallRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- IP Access rules ----

    @GET("zones/{zoneId}/firewall/access_rules/rules")
    suspend fun listAccessRules(
        @Path("zoneId") zoneId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<AccessRule>>>

    @POST("zones/{zoneId}/firewall/access_rules/rules")
    suspend fun createAccessRule(
        @Path("zoneId") zoneId: String,
        @Body rule: AccessRuleWrite
    ): Response<CfEnvelope<AccessRule>>

    @DELETE("zones/{zoneId}/firewall/access_rules/rules/{ruleId}")
    suspend fun deleteAccessRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Rulesets (phase entrypoints) - backs both WAF Custom Rules and Rate Limiting Rules,
    // which Cloudflare models as two rule phases ("http_request_firewall_custom" and
    // "http_ratelimit") of the same underlying Rulesets engine. ----

    /** 404s when the zone has never had a rule created for this phase - callers treat that as
     *  "no rules yet," not a failure (see WafRepository/RateLimitRepository.getRuleset). */
    @GET("zones/{zoneId}/rulesets/phases/{phase}/entrypoint")
    suspend fun getPhaseRuleset(@Path("zoneId") zoneId: String, @Path("phase") phase: String): Response<CfEnvelope<Ruleset>>

    /** Creates the phase entrypoint ruleset (if it doesn't exist) or replaces it entirely -
     *  only used to add the zone's very first rule in this phase; afterwards rules are added
     *  one at a time via [addRulesetRule] so existing rules are never clobbered. */
    @PUT("zones/{zoneId}/rulesets/phases/{phase}/entrypoint")
    suspend fun putPhaseRuleset(
        @Path("zoneId") zoneId: String,
        @Path("phase") phase: String,
        @Body body: RulesetPhaseWrite
    ): Response<CfEnvelope<Ruleset>>

    @POST("zones/{zoneId}/rulesets/{rulesetId}/rules")
    suspend fun addRulesetRule(
        @Path("zoneId") zoneId: String,
        @Path("rulesetId") rulesetId: String,
        @Body rule: RulesetRuleWrite
    ): Response<CfEnvelope<Ruleset>>

    @PATCH("zones/{zoneId}/rulesets/{rulesetId}/rules/{ruleId}")
    suspend fun updateRulesetRule(
        @Path("zoneId") zoneId: String,
        @Path("rulesetId") rulesetId: String,
        @Path("ruleId") ruleId: String,
        @Body rule: RulesetRuleWrite
    ): Response<CfEnvelope<Ruleset>>

    @DELETE("zones/{zoneId}/rulesets/{rulesetId}/rules/{ruleId}")
    suspend fun deleteRulesetRule(
        @Path("zoneId") zoneId: String,
        @Path("rulesetId") rulesetId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Ruleset>>

    // ---- Page rules ----

    @GET("zones/{zoneId}/pagerules")
    suspend fun listPageRules(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<List<PageRule>>>

    @POST("zones/{zoneId}/pagerules")
    suspend fun createPageRule(
        @Path("zoneId") zoneId: String,
        @Body rule: PageRuleWrite
    ): Response<CfEnvelope<PageRule>>

    @PUT("zones/{zoneId}/pagerules/{ruleId}")
    suspend fun updatePageRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String,
        @Body rule: PageRuleWrite
    ): Response<CfEnvelope<PageRule>>

    @DELETE("zones/{zoneId}/pagerules/{ruleId}")
    suspend fun deletePageRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Analytics ----

    @GET("zones/{zoneId}/analytics/dashboard")
    suspend fun getAnalyticsDashboard(
        @Path("zoneId") zoneId: String,
        @Query("since") since: String,
        @Query("until") until: String
    ): Response<CfEnvelope<AnalyticsDashboard>>

    // ---- Account members ----

    @GET("accounts/{accountId}/roles")
    suspend fun listAccountRoles(@Path("accountId") accountId: String): Response<CfEnvelope<List<AccountRole>>>

    @GET("accounts/{accountId}/members")
    suspend fun listAccountMembers(
        @Path("accountId") accountId: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<AccountMember>>>

    @POST("accounts/{accountId}/members")
    suspend fun inviteAccountMember(
        @Path("accountId") accountId: String,
        @Body invite: AccountMemberInvite
    ): Response<CfEnvelope<AccountMember>>

    @DELETE("accounts/{accountId}/members/{memberId}")
    suspend fun removeAccountMember(
        @Path("accountId") accountId: String,
        @Path("memberId") memberId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Audit logs ----

    @GET("accounts/{accountId}/audit_logs")
    suspend fun listAuditLogs(
        @Path("accountId") accountId: String,
        @Query("since") since: String? = null,
        @Query("before") before: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): Response<CfEnvelope<List<AuditLogEntry>>>

    // ---- Load Balancing: pools (account-level) and load balancers (zone-level) ----

    @GET("accounts/{accountId}/load_balancers/pools")
    suspend fun listLoadBalancerPools(@Path("accountId") accountId: String): Response<CfEnvelope<List<LoadBalancerPool>>>

    @POST("accounts/{accountId}/load_balancers/pools")
    suspend fun createLoadBalancerPool(
        @Path("accountId") accountId: String,
        @Body pool: LoadBalancerPoolWrite
    ): Response<CfEnvelope<LoadBalancerPool>>

    @DELETE("accounts/{accountId}/load_balancers/pools/{poolId}")
    suspend fun deleteLoadBalancerPool(
        @Path("accountId") accountId: String,
        @Path("poolId") poolId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("zones/{zoneId}/load_balancers")
    suspend fun listLoadBalancers(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<LoadBalancer>>>

    @POST("zones/{zoneId}/load_balancers")
    suspend fun createLoadBalancer(
        @Path("zoneId") zoneId: String,
        @Body loadBalancer: LoadBalancerWrite
    ): Response<CfEnvelope<LoadBalancer>>

    @DELETE("zones/{zoneId}/load_balancers/{loadBalancerId}")
    suspend fun deleteLoadBalancer(
        @Path("zoneId") zoneId: String,
        @Path("loadBalancerId") loadBalancerId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- R2 (bucket management only - no object/file browsing) ----

    @GET("accounts/{accountId}/r2/buckets")
    suspend fun listR2Buckets(@Path("accountId") accountId: String): Response<CfEnvelope<R2BucketListResult>>

    @POST("accounts/{accountId}/r2/buckets")
    suspend fun createR2Bucket(
        @Path("accountId") accountId: String,
        @Body bucket: R2BucketCreate
    ): Response<CfEnvelope<R2Bucket>>

    @DELETE("accounts/{accountId}/r2/buckets/{bucketName}")
    suspend fun deleteR2Bucket(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Workers KV (namespace management only - no per-key browsing/editing) ----

    @GET("accounts/{accountId}/storage/kv/namespaces")
    suspend fun listKvNamespaces(@Path("accountId") accountId: String): Response<CfEnvelope<List<KvNamespace>>>

    @POST("accounts/{accountId}/storage/kv/namespaces")
    suspend fun createKvNamespace(
        @Path("accountId") accountId: String,
        @Body namespace: KvNamespaceCreate
    ): Response<CfEnvelope<KvNamespace>>

    @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}")
    suspend fun deleteKvNamespace(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- D1 (database management only - no query execution) ----

    @GET("accounts/{accountId}/d1/database")
    suspend fun listD1Databases(@Path("accountId") accountId: String): Response<CfEnvelope<List<D1Database>>>

    @POST("accounts/{accountId}/d1/database")
    suspend fun createD1Database(
        @Path("accountId") accountId: String,
        @Body database: D1DatabaseCreate
    ): Response<CfEnvelope<D1Database>>

    @DELETE("accounts/{accountId}/d1/database/{databaseId}")
    suspend fun deleteD1Database(
        @Path("accountId") accountId: String,
        @Path("databaseId") databaseId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Workers (script list/view/delete only - no code editing/deployment) ----

    @GET("accounts/{accountId}/workers/scripts")
    suspend fun listWorkerScripts(@Path("accountId") accountId: String): Response<CfEnvelope<List<WorkerScript>>>

    @DELETE("accounts/{accountId}/workers/scripts/{scriptName}")
    suspend fun deleteWorkerScript(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Pages (list + deployment history only - read-mostly, no new deployments) ----

    @GET("accounts/{accountId}/pages/projects")
    suspend fun listPagesProjects(@Path("accountId") accountId: String): Response<CfEnvelope<List<PagesProject>>>

    @GET("accounts/{accountId}/pages/projects/{projectName}/deployments")
    suspend fun listPagesDeployments(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): Response<CfEnvelope<List<PagesDeployment>>>

    // ---- Zero Trust Access (applications + one inline policy per app - common cases only) ----

    @GET("accounts/{accountId}/access/apps")
    suspend fun listAccessApplications(@Path("accountId") accountId: String): Response<CfEnvelope<List<AccessApplication>>>

    @POST("accounts/{accountId}/access/apps")
    suspend fun createAccessApplication(
        @Path("accountId") accountId: String,
        @Body application: AccessApplicationCreate
    ): Response<CfEnvelope<AccessApplication>>

    @DELETE("accounts/{accountId}/access/apps/{appId}")
    suspend fun deleteAccessApplication(
        @Path("accountId") accountId: String,
        @Path("appId") appId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @POST("accounts/{accountId}/access/apps/{appId}/policies")
    suspend fun createAccessPolicy(
        @Path("accountId") accountId: String,
        @Path("appId") appId: String,
        @Body policy: AccessPolicyCreate
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Zero Trust Gateway (DNS policies - block/allow by domain, common case only) ----

    @GET("accounts/{accountId}/gateway/rules")
    suspend fun listGatewayRules(@Path("accountId") accountId: String): Response<CfEnvelope<List<GatewayRule>>>

    @POST("accounts/{accountId}/gateway/rules")
    suspend fun createGatewayRule(
        @Path("accountId") accountId: String,
        @Body rule: GatewayRuleCreate
    ): Response<CfEnvelope<GatewayRule>>

    @DELETE("accounts/{accountId}/gateway/rules/{ruleId}")
    suspend fun deleteGatewayRule(
        @Path("accountId") accountId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Map<String, String>>>
}
