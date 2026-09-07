package dev.cfmobile.app.data.remote

import dev.cfmobile.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

    /** Every ruleset visible to the zone, managed ones included. Used to put a name to the
     *  managed ruleset ids that a deployment rule references. */
    @GET("zones/{zoneId}/rulesets")
    suspend fun listRulesets(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<Ruleset>>>

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

    @GET("accounts/{accountId}/load_balancers/monitors")
    suspend fun listLoadBalancerMonitors(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<LoadBalancerMonitor>>>

    @POST("accounts/{accountId}/load_balancers/monitors")
    suspend fun createLoadBalancerMonitor(
        @Path("accountId") accountId: String,
        @Body monitor: LoadBalancerMonitorWrite
    ): Response<CfEnvelope<LoadBalancerMonitor>>

    @DELETE("accounts/{accountId}/load_balancers/monitors/{monitorId}")
    suspend fun deleteLoadBalancerMonitor(
        @Path("accountId") accountId: String,
        @Path("monitorId") monitorId: String
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

    @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/keys")
    suspend fun listKvKeys(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String
    ): Response<CfEnvelope<List<KvKey>>>

    /** Values are returned as-is (text or bytes), not wrapped in the JSON envelope. */
    @GET("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
    suspend fun getKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("keyName") keyName: String
    ): Response<ResponseBody>

    @PUT("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
    suspend fun putKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("keyName") keyName: String,
        @Body value: RequestBody
    ): Response<CfEnvelope<Map<String, String>>>

    @DELETE("accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{keyName}")
    suspend fun deleteKvValue(
        @Path("accountId") accountId: String,
        @Path("namespaceId") namespaceId: String,
        @Path("keyName") keyName: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- D1 (databases plus a SQL console) ----

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

    @POST("accounts/{accountId}/d1/database/{databaseId}/query")
    suspend fun queryD1Database(
        @Path("accountId") accountId: String,
        @Path("databaseId") databaseId: String,
        @Body request: D1QueryRequest
    ): Response<CfEnvelope<List<D1QueryResult>>>

    // ---- Workers (list/inspect/delete scripts, zone routes - no code editing/deployment) ----

    @GET("accounts/{accountId}/workers/scripts")
    suspend fun listWorkerScripts(@Path("accountId") accountId: String): Response<CfEnvelope<List<WorkerScript>>>

    @DELETE("accounts/{accountId}/workers/scripts/{scriptName}")
    suspend fun deleteWorkerScript(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<CfEnvelope<Map<String, String>>>

    /** Returns the deployed script itself - JavaScript for a service-worker script, or a
     *  multipart body for a module worker - so it isn't the JSON envelope. */
    @GET("accounts/{accountId}/workers/scripts/{scriptName}")
    suspend fun getWorkerScriptContent(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<ResponseBody>

    @GET("accounts/{accountId}/workers/scripts/{scriptName}/schedules")
    suspend fun getWorkerSchedules(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<CfEnvelope<WorkerSchedules>>

    @GET("zones/{zoneId}/workers/routes")
    suspend fun listWorkerRoutes(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<WorkerRoute>>>

    @POST("zones/{zoneId}/workers/routes")
    suspend fun createWorkerRoute(
        @Path("zoneId") zoneId: String,
        @Body route: WorkerRouteWrite
    ): Response<CfEnvelope<WorkerRoute>>

    @PUT("zones/{zoneId}/workers/routes/{routeId}")
    suspend fun updateWorkerRoute(
        @Path("zoneId") zoneId: String,
        @Path("routeId") routeId: String,
        @Body route: WorkerRouteWrite
    ): Response<CfEnvelope<WorkerRoute>>

    @DELETE("zones/{zoneId}/workers/routes/{routeId}")
    suspend fun deleteWorkerRoute(
        @Path("zoneId") zoneId: String,
        @Path("routeId") routeId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Pages (projects, deployment history, redeploy/retry - no build config editing) ----

    @GET("accounts/{accountId}/pages/projects")
    suspend fun listPagesProjects(@Path("accountId") accountId: String): Response<CfEnvelope<List<PagesProject>>>

    @GET("accounts/{accountId}/pages/projects/{projectName}/deployments")
    suspend fun listPagesDeployments(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): Response<CfEnvelope<List<PagesDeployment>>>

    @POST("accounts/{accountId}/pages/projects/{projectName}/deployments")
    suspend fun createPagesDeployment(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): Response<CfEnvelope<PagesDeployment>>

    @POST("accounts/{accountId}/pages/projects/{projectName}/deployments/{deploymentId}/retry")
    suspend fun retryPagesDeployment(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Path("deploymentId") deploymentId: String
    ): Response<CfEnvelope<PagesDeployment>>

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

    @GET("accounts/{accountId}/access/identity_providers")
    suspend fun listAccessIdentityProviders(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AccessIdentityProvider>>>

    @POST("accounts/{accountId}/access/identity_providers")
    suspend fun createAccessIdentityProvider(
        @Path("accountId") accountId: String,
        @Body provider: AccessIdentityProviderCreate
    ): Response<CfEnvelope<AccessIdentityProvider>>

    @DELETE("accounts/{accountId}/access/identity_providers/{providerId}")
    suspend fun deleteAccessIdentityProvider(
        @Path("accountId") accountId: String,
        @Path("providerId") providerId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/access/service_tokens")
    suspend fun listAccessServiceTokens(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AccessServiceToken>>>

    /** The only response that ever carries the token's client secret. */
    @POST("accounts/{accountId}/access/service_tokens")
    suspend fun createAccessServiceToken(
        @Path("accountId") accountId: String,
        @Body token: AccessServiceTokenCreate
    ): Response<CfEnvelope<AccessServiceToken>>

    @DELETE("accounts/{accountId}/access/service_tokens/{tokenId}")
    suspend fun deleteAccessServiceToken(
        @Path("accountId") accountId: String,
        @Path("tokenId") tokenId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Zero Trust Gateway (DNS, HTTP, and network policies plus lists) ----

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

    @GET("accounts/{accountId}/gateway/lists")
    suspend fun listGatewayLists(@Path("accountId") accountId: String): Response<CfEnvelope<List<GatewayList>>>

    @GET("accounts/{accountId}/gateway/lists/{listId}/items")
    suspend fun listGatewayListItems(
        @Path("accountId") accountId: String,
        @Path("listId") listId: String
    ): Response<CfEnvelope<List<GatewayListItem>>>

    @POST("accounts/{accountId}/gateway/lists")
    suspend fun createGatewayList(
        @Path("accountId") accountId: String,
        @Body list: GatewayListCreate
    ): Response<CfEnvelope<GatewayList>>

    @DELETE("accounts/{accountId}/gateway/lists/{listId}")
    suspend fun deleteGatewayList(
        @Path("accountId") accountId: String,
        @Path("listId") listId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Zero Trust Tunnels (list/create/delete only - running one needs cloudflared) ----

    @GET("accounts/{accountId}/cfd_tunnel")
    suspend fun listTunnels(@Path("accountId") accountId: String): Response<CfEnvelope<List<CfTunnel>>>

    @POST("accounts/{accountId}/cfd_tunnel")
    suspend fun createTunnel(
        @Path("accountId") accountId: String,
        @Body tunnel: TunnelCreate
    ): Response<CfEnvelope<CfTunnel>>

    @DELETE("accounts/{accountId}/cfd_tunnel/{tunnelId}")
    suspend fun deleteTunnel(
        @Path("accountId") accountId: String,
        @Path("tunnelId") tunnelId: String
    ): Response<CfEnvelope<CfTunnel>>

    // ---- Queues ----

    @GET("accounts/{accountId}/queues")
    suspend fun listQueues(@Path("accountId") accountId: String): Response<CfEnvelope<List<CfQueue>>>

    @POST("accounts/{accountId}/queues")
    suspend fun createQueue(
        @Path("accountId") accountId: String,
        @Body queue: QueueCreate
    ): Response<CfEnvelope<CfQueue>>

    @DELETE("accounts/{accountId}/queues/{queueId}")
    suspend fun deleteQueue(
        @Path("accountId") accountId: String,
        @Path("queueId") queueId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Durable Objects (read-only: namespaces come from Worker deployments) ----

    @GET("accounts/{accountId}/workers/durable_objects/namespaces")
    suspend fun listDurableObjectNamespaces(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<DurableObjectNamespace>>>

    // ---- Workflows ----

    @GET("accounts/{accountId}/workflows")
    suspend fun listWorkflows(@Path("accountId") accountId: String): Response<CfEnvelope<List<CfWorkflow>>>

    /** Starts a new run of a Workflow. */
    @POST("accounts/{accountId}/workflows/{workflowName}/instances")
    suspend fun createWorkflowInstance(
        @Path("accountId") accountId: String,
        @Path("workflowName") workflowName: String
    ): Response<CfEnvelope<WorkflowInstance>>

    /** Cloudflare takes the verb - "terminate", "pause", "resume" - as a status. */
    @PATCH("accounts/{accountId}/workflows/{workflowName}/instances/{instanceId}/status")
    suspend fun updateWorkflowInstanceStatus(
        @Path("accountId") accountId: String,
        @Path("workflowName") workflowName: String,
        @Path("instanceId") instanceId: String,
        @Body status: WorkflowInstanceStatusWrite
    ): Response<CfEnvelope<WorkflowInstance>>

    @GET("accounts/{accountId}/workflows/{workflowName}/instances")
    suspend fun listWorkflowInstances(
        @Path("accountId") accountId: String,
        @Path("workflowName") workflowName: String
    ): Response<CfEnvelope<List<WorkflowInstance>>>

    // ---- Hyperdrive (list/delete only - creating needs a database password) ----

    @GET("accounts/{accountId}/hyperdrive/configs")
    suspend fun listHyperdriveConfigs(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<HyperdriveConfig>>>

    @DELETE("accounts/{accountId}/hyperdrive/configs/{configId}")
    suspend fun deleteHyperdriveConfig(
        @Path("accountId") accountId: String,
        @Path("configId") configId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Vectorize ----

    @GET("accounts/{accountId}/vectorize/v2/indexes")
    suspend fun listVectorizeIndexes(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<VectorizeIndex>>>

    @POST("accounts/{accountId}/vectorize/v2/indexes")
    suspend fun createVectorizeIndex(
        @Path("accountId") accountId: String,
        @Body index: VectorizeIndexCreate
    ): Response<CfEnvelope<VectorizeIndex>>

    @DELETE("accounts/{accountId}/vectorize/v2/indexes/{indexName}")
    suspend fun deleteVectorizeIndex(
        @Path("accountId") accountId: String,
        @Path("indexName") indexName: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Stream (list/view/delete - uploading video from a phone isn't implemented) ----

    @GET("accounts/{accountId}/stream")
    suspend fun listStreamVideos(@Path("accountId") accountId: String): Response<CfEnvelope<List<StreamVideo>>>

    /** Basic (non-resumable) upload: the whole file in one multipart request. Cloudflare caps
     *  this at 200 MB - anything larger needs the tus resumable protocol, which this app
     *  doesn't implement. */
    @Multipart
    @POST("accounts/{accountId}/stream")
    suspend fun uploadStreamVideo(
        @Path("accountId") accountId: String,
        @Part file: MultipartBody.Part
    ): Response<CfEnvelope<StreamVideo>>

    @DELETE("accounts/{accountId}/stream/{videoId}")
    suspend fun deleteStreamVideo(
        @Path("accountId") accountId: String,
        @Path("videoId") videoId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Images (list/delete + usage stats) ----

    @GET("accounts/{accountId}/images/v1")
    suspend fun listImages(@Path("accountId") accountId: String): Response<CfEnvelope<ImagesListResult>>

    @Multipart
    @POST("accounts/{accountId}/images/v1")
    suspend fun uploadImage(
        @Path("accountId") accountId: String,
        @Part file: MultipartBody.Part
    ): Response<CfEnvelope<CfImage>>

    @GET("accounts/{accountId}/images/v1/stats")
    suspend fun getImagesStats(@Path("accountId") accountId: String): Response<CfEnvelope<ImagesStats>>

    @DELETE("accounts/{accountId}/images/v1/{imageId}")
    suspend fun deleteImage(
        @Path("accountId") accountId: String,
        @Path("imageId") imageId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Turnstile ----

    @GET("accounts/{accountId}/challenges/widgets")
    suspend fun listTurnstileWidgets(@Path("accountId") accountId: String): Response<CfEnvelope<List<TurnstileWidget>>>

    @POST("accounts/{accountId}/challenges/widgets")
    suspend fun createTurnstileWidget(
        @Path("accountId") accountId: String,
        @Body widget: TurnstileWidgetCreate
    ): Response<CfEnvelope<TurnstileWidget>>

    @DELETE("accounts/{accountId}/challenges/widgets/{sitekey}")
    suspend fun deleteTurnstileWidget(
        @Path("accountId") accountId: String,
        @Path("sitekey") sitekey: String
    ): Response<CfEnvelope<TurnstileWidget>>

    // ---- Logpush (list/enable/delete - creating a job carries destination credentials) ----

    @GET("accounts/{accountId}/logpush/jobs")
    suspend fun listLogpushJobs(@Path("accountId") accountId: String): Response<CfEnvelope<List<LogpushJob>>>

    @PUT("accounts/{accountId}/logpush/jobs/{jobId}")
    suspend fun updateLogpushJob(
        @Path("accountId") accountId: String,
        @Path("jobId") jobId: Long,
        @Body update: LogpushJobUpdate
    ): Response<CfEnvelope<LogpushJob>>

    @DELETE("accounts/{accountId}/logpush/jobs/{jobId}")
    suspend fun deleteLogpushJob(
        @Path("accountId") accountId: String,
        @Path("jobId") jobId: Long
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Workers AI (read-only model catalog) ----

    @GET("accounts/{accountId}/ai/models/search")
    suspend fun searchAiModels(@Path("accountId") accountId: String): Response<CfEnvelope<List<AiModel>>>

    // ---- Zero Trust devices and posture rules (inventory plus device revoke) ----

    @GET("accounts/{accountId}/devices")
    suspend fun listEnrolledDevices(@Path("accountId") accountId: String): Response<CfEnvelope<List<EnrolledDevice>>>

    /** Revokes the device's Zero Trust registration; the user has to re-enrol to reconnect.
     *  Cloudflare takes a bare array of device ids. */
    @POST("accounts/{accountId}/devices/revoke")
    suspend fun revokeDevices(
        @Path("accountId") accountId: String,
        @Body deviceIds: List<String>
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/devices/posture")
    suspend fun listPostureRules(@Path("accountId") accountId: String): Response<CfEnvelope<List<PostureRule>>>

    // ---- Security Events (GraphQL analytics, not the REST envelope) ----

    @POST("graphql")
    suspend fun queryFirewallEvents(
        @Body request: GraphQlRequest
    ): Response<GraphQlResponse<FirewallEventsData>>

    // ---- Page Shield ----

    @GET("zones/{zoneId}/page_shield")
    suspend fun getPageShieldSettings(@Path("zoneId") zoneId: String): Response<CfEnvelope<PageShieldSettings>>

    @PUT("zones/{zoneId}/page_shield")
    suspend fun updatePageShieldSettings(
        @Path("zoneId") zoneId: String,
        @Body settings: PageShieldSettingsUpdate
    ): Response<CfEnvelope<PageShieldSettings>>

    @GET("zones/{zoneId}/page_shield/scripts")
    suspend fun listPageShieldScripts(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<PageShieldScript>>>

    @GET("zones/{zoneId}/page_shield/connections")
    suspend fun listPageShieldConnections(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<PageShieldConnection>>>

    @GET("zones/{zoneId}/page_shield/policies")
    suspend fun listPageShieldPolicies(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<List<PageShieldPolicy>>>

    @POST("zones/{zoneId}/page_shield/policies")
    suspend fun createPageShieldPolicy(
        @Path("zoneId") zoneId: String,
        @Body policy: PageShieldPolicyWrite
    ): Response<CfEnvelope<PageShieldPolicy>>

    @PUT("zones/{zoneId}/page_shield/policies/{policyId}")
    suspend fun updatePageShieldPolicy(
        @Path("zoneId") zoneId: String,
        @Path("policyId") policyId: String,
        @Body policy: PageShieldPolicyWrite
    ): Response<CfEnvelope<PageShieldPolicy>>

    @DELETE("zones/{zoneId}/page_shield/policies/{policyId}")
    suspend fun deletePageShieldPolicy(
        @Path("zoneId") zoneId: String,
        @Path("policyId") policyId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Bot management (Super Bot Fight Mode and its free-tier sibling) ----

    @GET("zones/{zoneId}/bot_management")
    suspend fun getBotManagement(@Path("zoneId") zoneId: String): Response<CfEnvelope<BotManagementConfig>>

    @PUT("zones/{zoneId}/bot_management")
    suspend fun updateBotManagement(
        @Path("zoneId") zoneId: String,
        @Body config: BotManagementConfig
    ): Response<CfEnvelope<BotManagementConfig>>

    // ---- DDoS protection (read-only managed ruleset) ----

    @GET("zones/{zoneId}/rulesets/phases/ddos_l7/entrypoint")
    suspend fun getDdosEntrypoint(@Path("zoneId") zoneId: String): Response<CfEnvelope<DdosRuleset>>

    // ---- API Shield (read-only discovered operations) ----

    @GET("zones/{zoneId}/api_gateway/operations")
    suspend fun listApiOperations(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<ApiOperation>>>

    // ---- DNSSEC ----

    @GET("zones/{zoneId}/dnssec")
    suspend fun getDnssec(@Path("zoneId") zoneId: String): Response<CfEnvelope<DnssecStatus>>

    @PATCH("zones/{zoneId}/dnssec")
    suspend fun updateDnssec(
        @Path("zoneId") zoneId: String,
        @Body update: DnssecUpdate
    ): Response<CfEnvelope<DnssecStatus>>

    // ---- Custom hostnames (SSL for SaaS) ----

    @GET("zones/{zoneId}/custom_hostnames")
    suspend fun listCustomHostnames(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<CustomHostname>>>

    @POST("zones/{zoneId}/custom_hostnames")
    suspend fun createCustomHostname(
        @Path("zoneId") zoneId: String,
        @Body hostname: CustomHostnameCreate
    ): Response<CfEnvelope<CustomHostname>>

    @DELETE("zones/{zoneId}/custom_hostnames/{hostnameId}")
    suspend fun deleteCustomHostname(
        @Path("zoneId") zoneId: String,
        @Path("hostnameId") hostnameId: String
    ): Response<CfEnvelope<CustomHostname>>

    // ---- Edge certificates (read-only) ----

    @GET("zones/{zoneId}/ssl/certificate_packs")
    suspend fun listCertificatePacks(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<CertificatePack>>>

    // ---- Waiting Room ----

    @GET("zones/{zoneId}/waiting_rooms")
    suspend fun listWaitingRooms(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<WaitingRoom>>>

    @POST("zones/{zoneId}/waiting_rooms")
    suspend fun createWaitingRoom(
        @Path("zoneId") zoneId: String,
        @Body room: WaitingRoomCreate
    ): Response<CfEnvelope<WaitingRoom>>

    @DELETE("zones/{zoneId}/waiting_rooms/{roomId}")
    suspend fun deleteWaitingRoom(
        @Path("zoneId") zoneId: String,
        @Path("roomId") roomId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Standalone health checks ----

    @GET("zones/{zoneId}/healthchecks")
    suspend fun listHealthChecks(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<HealthCheck>>>

    @POST("zones/{zoneId}/healthchecks")
    suspend fun createHealthCheck(
        @Path("zoneId") zoneId: String,
        @Body check: HealthCheckCreate
    ): Response<CfEnvelope<HealthCheck>>

    @DELETE("zones/{zoneId}/healthchecks/{checkId}")
    suspend fun deleteHealthCheck(
        @Path("zoneId") zoneId: String,
        @Path("checkId") checkId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Email Routing ----

    @GET("zones/{zoneId}/email/routing")
    suspend fun getEmailRoutingSettings(@Path("zoneId") zoneId: String): Response<CfEnvelope<EmailRoutingSettings>>

    @GET("zones/{zoneId}/email/routing/rules")
    suspend fun listEmailRoutingRules(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<EmailRoutingRule>>>

    @POST("zones/{zoneId}/email/routing/rules")
    suspend fun createEmailRoutingRule(
        @Path("zoneId") zoneId: String,
        @Body rule: EmailRoutingRuleCreate
    ): Response<CfEnvelope<EmailRoutingRule>>

    @GET("accounts/{accountId}/email/routing/addresses")
    suspend fun listEmailDestinations(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<EmailDestinationAddress>>>

    /** Cloudflare emails the address a verification link; until it's clicked, mail isn't
     *  delivered there. */
    @POST("accounts/{accountId}/email/routing/addresses")
    suspend fun createEmailDestination(
        @Path("accountId") accountId: String,
        @Body address: EmailDestinationCreate
    ): Response<CfEnvelope<EmailDestinationAddress>>

    @DELETE("accounts/{accountId}/email/routing/addresses/{addressTag}")
    suspend fun deleteEmailDestination(
        @Path("accountId") accountId: String,
        @Path("addressTag") addressTag: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("zones/{zoneId}/email/routing/rules/catch_all")
    suspend fun getEmailCatchAll(@Path("zoneId") zoneId: String): Response<CfEnvelope<EmailCatchAll>>

    @PUT("zones/{zoneId}/email/routing/rules/catch_all")
    suspend fun updateEmailCatchAll(
        @Path("zoneId") zoneId: String,
        @Body catchAll: EmailCatchAllWrite
    ): Response<CfEnvelope<EmailCatchAll>>

    @DELETE("zones/{zoneId}/email/routing/rules/{ruleId}")
    suspend fun deleteEmailRoutingRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<EmailRoutingRule>>

    // ---- Spectrum (read-only list plus delete) ----

    @GET("zones/{zoneId}/spectrum/apps")
    suspend fun listSpectrumApps(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<SpectrumApp>>>

    @DELETE("zones/{zoneId}/spectrum/apps/{appId}")
    suspend fun deleteSpectrumApp(
        @Path("zoneId") zoneId: String,
        @Path("appId") appId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Magic WAN / Transit (read-only inventory) ----

    @GET("accounts/{accountId}/magic/gre_tunnels")
    suspend fun listMagicGreTunnels(@Path("accountId") accountId: String): Response<CfEnvelope<MagicGreTunnelList>>

    @GET("accounts/{accountId}/magic/ipsec_tunnels")
    suspend fun listMagicIpsecTunnels(@Path("accountId") accountId: String): Response<CfEnvelope<MagicIpsecTunnelList>>

    @GET("accounts/{accountId}/magic/routes")
    suspend fun listMagicRoutes(@Path("accountId") accountId: String): Response<CfEnvelope<MagicRouteList>>

    // ---- Billing (read-only: this app never changes payment details) ----

    @GET("accounts/{accountId}/subscriptions")
    suspend fun listSubscriptions(@Path("accountId") accountId: String): Response<CfEnvelope<List<AccountSubscription>>>

    // ---- Browser Rendering (returns image bytes, not the JSON envelope) ----

    @POST("accounts/{accountId}/browser-rendering/screenshot")
    suspend fun renderScreenshot(
        @Path("accountId") accountId: String,
        @Body request: ScreenshotRequest
    ): Response<ResponseBody>

    // ---- API tokens (metadata only, never a token's value) ----

    /** Cloudflare returns only metadata here - the token value itself is returned exactly once,
     *  by the call that creates or rolls it, which this app doesn't make. */
    @GET("user/tokens")
    suspend fun listApiTokens(): Response<CfEnvelope<List<ApiToken>>>

    @DELETE("user/tokens/{tokenId}")
    suspend fun deleteApiToken(@Path("tokenId") tokenId: String): Response<CfEnvelope<Map<String, String>>>

    /** Account-owned tokens (the `cfat_` kind) are a separate collection from a user's own -
     *  they belong to the account and survive the person who made them leaving it. */
    @GET("accounts/{accountId}/tokens")
    suspend fun listAccountApiTokens(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<ApiToken>>>

    @DELETE("accounts/{accountId}/tokens/{tokenId}")
    suspend fun deleteAccountApiToken(
        @Path("accountId") accountId: String,
        @Path("tokenId") tokenId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Notifications (alerting policies) ----

    @GET("accounts/{accountId}/alerting/v3/policies")
    suspend fun listNotificationPolicies(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<NotificationPolicy>>>

    @PATCH("accounts/{accountId}/alerting/v3/policies/{policyId}")
    suspend fun updateNotificationPolicy(
        @Path("accountId") accountId: String,
        @Path("policyId") policyId: String,
        @Body update: NotificationPolicyUpdate
    ): Response<CfEnvelope<NotificationPolicy>>

    @DELETE("accounts/{accountId}/alerting/v3/policies/{policyId}")
    suspend fun deleteNotificationPolicy(
        @Path("accountId") accountId: String,
        @Path("policyId") policyId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Account Rules Lists (Bulk Redirects are the "redirect" kind) ----

    @GET("accounts/{accountId}/rules/lists")
    suspend fun listRulesLists(@Path("accountId") accountId: String): Response<CfEnvelope<List<RulesList>>>

    @GET("accounts/{accountId}/rules/lists/{listId}/items")
    suspend fun listRulesListItems(
        @Path("accountId") accountId: String,
        @Path("listId") listId: String
    ): Response<CfEnvelope<List<RulesListItem>>>

    @POST("accounts/{accountId}/rules/lists")
    suspend fun createRulesList(
        @Path("accountId") accountId: String,
        @Body list: RulesListCreate
    ): Response<CfEnvelope<RulesList>>

    @DELETE("accounts/{accountId}/rules/lists/{listId}")
    suspend fun deleteRulesList(
        @Path("accountId") accountId: String,
        @Path("listId") listId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Registrar (read-only: renewals and transfers cost money) ----

    @GET("accounts/{accountId}/registrar/domains")
    suspend fun listRegistrarDomains(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<RegistrarDomain>>>

    // ---- Web Analytics (RUM sites) ----

    @GET("accounts/{accountId}/rum/site_info/list")
    suspend fun listRumSites(@Path("accountId") accountId: String): Response<CfEnvelope<List<RumSite>>>

    @POST("accounts/{accountId}/rum/site_info")
    suspend fun createRumSite(
        @Path("accountId") accountId: String,
        @Body site: RumSiteCreate
    ): Response<CfEnvelope<RumSite>>

    @DELETE("accounts/{accountId}/rum/site_info/{siteTag}")
    suspend fun deleteRumSite(
        @Path("accountId") accountId: String,
        @Path("siteTag") siteTag: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Snippets (list, read source, delete; plus the rules that run them) ----

    @GET("zones/{zoneId}/snippets")
    suspend fun listSnippets(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<Snippet>>>

    /** Returns the snippet's JavaScript, as a file rather than as the JSON envelope. */
    @GET("zones/{zoneId}/snippets/{snippetName}/content")
    suspend fun getSnippetContent(
        @Path("zoneId") zoneId: String,
        @Path("snippetName") snippetName: String
    ): Response<ResponseBody>

    @DELETE("zones/{zoneId}/snippets/{snippetName}")
    suspend fun deleteSnippet(
        @Path("zoneId") zoneId: String,
        @Path("snippetName") snippetName: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("zones/{zoneId}/snippets/snippet_rules")
    suspend fun listSnippetRules(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<SnippetRule>>>

    /** Replaces the zone's whole snippet-rule list; Cloudflare has no per-rule endpoint here. */
    @PUT("zones/{zoneId}/snippets/snippet_rules")
    suspend fun putSnippetRules(
        @Path("zoneId") zoneId: String,
        @Body rules: SnippetRulesWrite
    ): Response<CfEnvelope<List<SnippetRule>>>

    // ---- Cloud Connector ----

    @GET("zones/{zoneId}/cloud_connector/rules")
    suspend fun listCloudConnectorRules(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<List<CloudConnectorRule>>>

    /** Also a whole-list replace - the same read-modify-write shape as snippet rules. */
    @PUT("zones/{zoneId}/cloud_connector/rules")
    suspend fun putCloudConnectorRules(
        @Path("zoneId") zoneId: String,
        @Body rules: List<CloudConnectorRule>
    ): Response<CfEnvelope<List<CloudConnectorRule>>>

    // ---- Custom error pages ----

    @GET("zones/{zoneId}/custom_pages")
    suspend fun listCustomPages(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<CustomPage>>>

    /** Takes a pre-encoded body: reverting a page means sending `"url": null` explicitly, and
     *  Moshi drops null fields by default (see CustomPagesRepository). */
    @PUT("zones/{zoneId}/custom_pages/{pageId}")
    suspend fun updateCustomPage(
        @Path("zoneId") zoneId: String,
        @Path("pageId") pageId: String,
        @Body page: RequestBody
    ): Response<CfEnvelope<CustomPage>>

    // ---- Zaraz (read-only) ----

    @GET("zones/{zoneId}/settings/zaraz/config")
    suspend fun getZarazConfig(@Path("zoneId") zoneId: String): Response<CfEnvelope<ZarazConfig>>

    // ---- Zone Lockdown and User Agent Blocking (the legacy firewall products) ----

    @GET("zones/{zoneId}/firewall/lockdowns")
    suspend fun listZoneLockdowns(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<ZoneLockdown>>>

    @POST("zones/{zoneId}/firewall/lockdowns")
    suspend fun createZoneLockdown(
        @Path("zoneId") zoneId: String,
        @Body lockdown: ZoneLockdownWrite
    ): Response<CfEnvelope<ZoneLockdown>>

    @PUT("zones/{zoneId}/firewall/lockdowns/{lockdownId}")
    suspend fun updateZoneLockdown(
        @Path("zoneId") zoneId: String,
        @Path("lockdownId") lockdownId: String,
        @Body lockdown: ZoneLockdownWrite
    ): Response<CfEnvelope<ZoneLockdown>>

    @DELETE("zones/{zoneId}/firewall/lockdowns/{lockdownId}")
    suspend fun deleteZoneLockdown(
        @Path("zoneId") zoneId: String,
        @Path("lockdownId") lockdownId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("zones/{zoneId}/firewall/ua_rules")
    suspend fun listUserAgentRules(@Path("zoneId") zoneId: String): Response<CfEnvelope<List<UserAgentRule>>>

    @POST("zones/{zoneId}/firewall/ua_rules")
    suspend fun createUserAgentRule(
        @Path("zoneId") zoneId: String,
        @Body rule: UserAgentRuleWrite
    ): Response<CfEnvelope<UserAgentRule>>

    @PUT("zones/{zoneId}/firewall/ua_rules/{ruleId}")
    suspend fun updateUserAgentRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String,
        @Body rule: UserAgentRuleWrite
    ): Response<CfEnvelope<UserAgentRule>>

    @DELETE("zones/{zoneId}/firewall/ua_rules/{ruleId}")
    suspend fun deleteUserAgentRule(
        @Path("zoneId") zoneId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- mTLS, origin pulls, Total TLS ----

    @GET("zones/{zoneId}/client_certificates")
    suspend fun listClientCertificates(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<List<ClientCertificate>>>

    /** Revokes a client certificate. Cloudflare keeps the record with a revoked status rather
     *  than removing it. */
    @DELETE("zones/{zoneId}/client_certificates/{certificateId}")
    suspend fun revokeClientCertificate(
        @Path("zoneId") zoneId: String,
        @Path("certificateId") certificateId: String
    ): Response<CfEnvelope<ClientCertificate>>

    @GET("zones/{zoneId}/origin_tls_client_auth/settings")
    suspend fun getOriginTlsClientAuth(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<OriginTlsClientAuthSettings>>

    @PUT("zones/{zoneId}/origin_tls_client_auth/settings")
    suspend fun updateOriginTlsClientAuth(
        @Path("zoneId") zoneId: String,
        @Body settings: OriginTlsClientAuthSettings
    ): Response<CfEnvelope<OriginTlsClientAuthSettings>>

    @GET("zones/{zoneId}/acm/total_tls")
    suspend fun getTotalTls(@Path("zoneId") zoneId: String): Response<CfEnvelope<TotalTlsSettings>>

    @POST("zones/{zoneId}/acm/total_tls")
    suspend fun updateTotalTls(
        @Path("zoneId") zoneId: String,
        @Body settings: TotalTlsWrite
    ): Response<CfEnvelope<TotalTlsSettings>>

    // ---- Custom nameservers and zone holds ----

    @GET("accounts/{accountId}/custom_ns")
    suspend fun listAccountCustomNameservers(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<CustomNameserver>>>

    @GET("zones/{zoneId}/custom_ns")
    suspend fun getZoneCustomNameservers(
        @Path("zoneId") zoneId: String
    ): Response<CfEnvelope<ZoneCustomNameservers>>

    @PUT("zones/{zoneId}/custom_ns")
    suspend fun updateZoneCustomNameservers(
        @Path("zoneId") zoneId: String,
        @Body settings: ZoneCustomNameserversWrite
    ): Response<CfEnvelope<ZoneCustomNameservers>>

    @GET("zones/{zoneId}/hold")
    suspend fun getZoneHold(@Path("zoneId") zoneId: String): Response<CfEnvelope<ZoneHold>>

    @POST("zones/{zoneId}/hold")
    suspend fun createZoneHold(
        @Path("zoneId") zoneId: String,
        @Query("include_subdomains") includeSubdomains: Boolean
    ): Response<CfEnvelope<ZoneHold>>

    @DELETE("zones/{zoneId}/hold")
    suspend fun removeZoneHold(@Path("zoneId") zoneId: String): Response<CfEnvelope<ZoneHold>>

    // ---- Argo, tiered cache, cache reserve, managed transforms, URL normalization ----

    @GET("zones/{zoneId}/argo/smart_routing")
    suspend fun getArgoSmartRouting(@Path("zoneId") zoneId: String): Response<CfEnvelope<ArgoSetting>>

    @PATCH("zones/{zoneId}/argo/smart_routing")
    suspend fun updateArgoSmartRouting(
        @Path("zoneId") zoneId: String,
        @Body setting: ArgoSettingWrite
    ): Response<CfEnvelope<ArgoSetting>>

    @GET("zones/{zoneId}/argo/tiered_caching")
    suspend fun getTieredCaching(@Path("zoneId") zoneId: String): Response<CfEnvelope<ArgoSetting>>

    @PATCH("zones/{zoneId}/argo/tiered_caching")
    suspend fun updateTieredCaching(
        @Path("zoneId") zoneId: String,
        @Body setting: ArgoSettingWrite
    ): Response<CfEnvelope<ArgoSetting>>

    @GET("zones/{zoneId}/cache/cache_reserve")
    suspend fun getCacheReserve(@Path("zoneId") zoneId: String): Response<CfEnvelope<CacheSetting>>

    @PATCH("zones/{zoneId}/cache/cache_reserve")
    suspend fun updateCacheReserve(
        @Path("zoneId") zoneId: String,
        @Body setting: CacheSettingWrite
    ): Response<CfEnvelope<CacheSetting>>

    @GET("zones/{zoneId}/cache/regional_tiered_cache")
    suspend fun getRegionalTieredCache(@Path("zoneId") zoneId: String): Response<CfEnvelope<CacheSetting>>

    @PATCH("zones/{zoneId}/cache/regional_tiered_cache")
    suspend fun updateRegionalTieredCache(
        @Path("zoneId") zoneId: String,
        @Body setting: CacheSettingWrite
    ): Response<CfEnvelope<CacheSetting>>

    @GET("zones/{zoneId}/cache/tiered_cache_smart_topology_enable")
    suspend fun getSmartTieredCache(@Path("zoneId") zoneId: String): Response<CfEnvelope<CacheSetting>>

    @PATCH("zones/{zoneId}/cache/tiered_cache_smart_topology_enable")
    suspend fun updateSmartTieredCache(
        @Path("zoneId") zoneId: String,
        @Body setting: CacheSettingWrite
    ): Response<CfEnvelope<CacheSetting>>

    @GET("zones/{zoneId}/managed_headers")
    suspend fun getManagedHeaders(@Path("zoneId") zoneId: String): Response<CfEnvelope<ManagedHeaders>>

    /** Cloudflare replaces both header lists on write, so the caller sends the whole document. */
    @PATCH("zones/{zoneId}/managed_headers")
    suspend fun updateManagedHeaders(
        @Path("zoneId") zoneId: String,
        @Body headers: ManagedHeaders
    ): Response<CfEnvelope<ManagedHeaders>>

    @GET("zones/{zoneId}/url_normalization")
    suspend fun getUrlNormalization(@Path("zoneId") zoneId: String): Response<CfEnvelope<UrlNormalization>>

    @PUT("zones/{zoneId}/url_normalization")
    suspend fun updateUrlNormalization(
        @Path("zoneId") zoneId: String,
        @Body settings: UrlNormalizationWrite
    ): Response<CfEnvelope<UrlNormalization>>

    // ---- R2 bucket configuration (the control plane; objects need the S3 data plane) ----

    @GET("accounts/{accountId}/r2/buckets/{bucketName}/cors")
    suspend fun getR2Cors(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<R2CorsRules>>

    @DELETE("accounts/{accountId}/r2/buckets/{bucketName}/cors")
    suspend fun deleteR2Cors(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/r2/buckets/{bucketName}/lifecycle")
    suspend fun getR2Lifecycle(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<R2LifecycleRules>>

    @GET("accounts/{accountId}/r2/buckets/{bucketName}/domains/custom")
    suspend fun listR2CustomDomains(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<R2CustomDomains>>

    @DELETE("accounts/{accountId}/r2/buckets/{bucketName}/domains/custom/{domain}")
    suspend fun deleteR2CustomDomain(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String,
        @Path("domain") domain: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/r2/buckets/{bucketName}/domains/managed")
    suspend fun getR2ManagedDomain(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String
    ): Response<CfEnvelope<R2ManagedDomain>>

    /** Turning the r2.dev URL on makes every object in the bucket publicly readable. */
    @PUT("accounts/{accountId}/r2/buckets/{bucketName}/domains/managed")
    suspend fun updateR2ManagedDomain(
        @Path("accountId") accountId: String,
        @Path("bucketName") bucketName: String,
        @Body settings: R2ManagedDomainWrite
    ): Response<CfEnvelope<R2ManagedDomain>>

    // ---- Worker secrets, custom domains, and deployments ----

    /** Names and types only - Cloudflare never returns a secret's value. */
    @GET("accounts/{accountId}/workers/scripts/{scriptName}/secrets")
    suspend fun listWorkerSecrets(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<CfEnvelope<List<WorkerSecret>>>

    @PUT("accounts/{accountId}/workers/scripts/{scriptName}/secrets")
    suspend fun putWorkerSecret(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Body secret: WorkerSecretWrite
    ): Response<CfEnvelope<WorkerSecret>>

    @DELETE("accounts/{accountId}/workers/scripts/{scriptName}/secrets/{secretName}")
    suspend fun deleteWorkerSecret(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String,
        @Path("secretName") secretName: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/workers/domains")
    suspend fun listWorkerDomains(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<WorkerDomain>>>

    @PUT("accounts/{accountId}/workers/domains")
    suspend fun attachWorkerDomain(
        @Path("accountId") accountId: String,
        @Body domain: WorkerDomainWrite
    ): Response<CfEnvelope<WorkerDomain>>

    @DELETE("accounts/{accountId}/workers/domains/{domainId}")
    suspend fun detachWorkerDomain(
        @Path("accountId") accountId: String,
        @Path("domainId") domainId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/workers/scripts/{scriptName}/deployments")
    suspend fun listWorkerDeployments(
        @Path("accountId") accountId: String,
        @Path("scriptName") scriptName: String
    ): Response<CfEnvelope<WorkerDeployments>>

    // ---- Pages custom domains and queue consumers ----

    @GET("accounts/{accountId}/pages/projects/{projectName}/domains")
    suspend fun listPagesDomains(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String
    ): Response<CfEnvelope<List<PagesDomain>>>

    @POST("accounts/{accountId}/pages/projects/{projectName}/domains")
    suspend fun addPagesDomain(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Body domain: PagesDomainCreate
    ): Response<CfEnvelope<PagesDomain>>

    @DELETE("accounts/{accountId}/pages/projects/{projectName}/domains/{domainName}")
    suspend fun deletePagesDomain(
        @Path("accountId") accountId: String,
        @Path("projectName") projectName: String,
        @Path("domainName") domainName: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/queues/{queueId}/consumers")
    suspend fun listQueueConsumers(
        @Path("accountId") accountId: String,
        @Path("queueId") queueId: String
    ): Response<CfEnvelope<List<QueueConsumer>>>

    @DELETE("accounts/{accountId}/queues/{queueId}/consumers/{consumerId}")
    suspend fun deleteQueueConsumer(
        @Path("accountId") accountId: String,
        @Path("queueId") queueId: String,
        @Path("consumerId") consumerId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Access groups, mTLS roots, bookmarks, tags ----

    @GET("accounts/{accountId}/access/groups")
    suspend fun listAccessGroups(@Path("accountId") accountId: String): Response<CfEnvelope<List<AccessGroup>>>

    @POST("accounts/{accountId}/access/groups")
    suspend fun createAccessGroup(
        @Path("accountId") accountId: String,
        @Body group: AccessGroupWrite
    ): Response<CfEnvelope<AccessGroup>>

    @DELETE("accounts/{accountId}/access/groups/{groupId}")
    suspend fun deleteAccessGroup(
        @Path("accountId") accountId: String,
        @Path("groupId") groupId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/access/certificates")
    suspend fun listAccessMtlsCertificates(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AccessMtlsCertificate>>>

    @DELETE("accounts/{accountId}/access/certificates/{certificateId}")
    suspend fun deleteAccessMtlsCertificate(
        @Path("accountId") accountId: String,
        @Path("certificateId") certificateId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/access/bookmarks")
    suspend fun listAccessBookmarks(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AccessBookmark>>>

    @POST("accounts/{accountId}/access/bookmarks")
    suspend fun createAccessBookmark(
        @Path("accountId") accountId: String,
        @Body bookmark: AccessBookmarkWrite
    ): Response<CfEnvelope<AccessBookmark>>

    @DELETE("accounts/{accountId}/access/bookmarks/{bookmarkId}")
    suspend fun deleteAccessBookmark(
        @Path("accountId") accountId: String,
        @Path("bookmarkId") bookmarkId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/access/tags")
    suspend fun listAccessTags(@Path("accountId") accountId: String): Response<CfEnvelope<List<AccessTag>>>

    @POST("accounts/{accountId}/access/tags")
    suspend fun createAccessTag(
        @Path("accountId") accountId: String,
        @Body tag: AccessTagWrite
    ): Response<CfEnvelope<AccessTag>>

    @DELETE("accounts/{accountId}/access/tags/{tagName}")
    suspend fun deleteAccessTag(
        @Path("accountId") accountId: String,
        @Path("tagName") tagName: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Gateway locations, WARP profiles, tunnel routing ----

    @GET("accounts/{accountId}/gateway/locations")
    suspend fun listGatewayLocations(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<GatewayLocation>>>

    @POST("accounts/{accountId}/gateway/locations")
    suspend fun createGatewayLocation(
        @Path("accountId") accountId: String,
        @Body location: GatewayLocationWrite
    ): Response<CfEnvelope<GatewayLocation>>

    @DELETE("accounts/{accountId}/gateway/locations/{locationId}")
    suspend fun deleteGatewayLocation(
        @Path("accountId") accountId: String,
        @Path("locationId") locationId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/devices/policies")
    suspend fun listDeviceSettingsPolicies(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<DeviceSettingsPolicy>>>

    @GET("accounts/{accountId}/teamnet/routes")
    suspend fun listTunnelRoutes(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<TunnelRoute>>>

    @POST("accounts/{accountId}/teamnet/routes")
    suspend fun createTunnelRoute(
        @Path("accountId") accountId: String,
        @Body route: TunnelRouteWrite
    ): Response<CfEnvelope<TunnelRoute>>

    @DELETE("accounts/{accountId}/teamnet/routes/{routeId}")
    suspend fun deleteTunnelRoute(
        @Path("accountId") accountId: String,
        @Path("routeId") routeId: String
    ): Response<CfEnvelope<TunnelRoute>>

    @GET("accounts/{accountId}/teamnet/virtual_networks")
    suspend fun listVirtualNetworks(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<VirtualNetwork>>>

    @POST("accounts/{accountId}/teamnet/virtual_networks")
    suspend fun createVirtualNetwork(
        @Path("accountId") accountId: String,
        @Body network: VirtualNetworkWrite
    ): Response<CfEnvelope<VirtualNetwork>>

    @DELETE("accounts/{accountId}/teamnet/virtual_networks/{networkId}")
    suspend fun deleteVirtualNetwork(
        @Path("accountId") accountId: String,
        @Path("networkId") networkId: String
    ): Response<CfEnvelope<VirtualNetwork>>

    // ---- AI Gateway, Calls, Pipelines, Secrets Store ----

    @GET("accounts/{accountId}/ai-gateway/gateways")
    suspend fun listAiGateways(@Path("accountId") accountId: String): Response<CfEnvelope<List<AiGateway>>>

    @POST("accounts/{accountId}/ai-gateway/gateways")
    suspend fun createAiGateway(
        @Path("accountId") accountId: String,
        @Body gateway: AiGatewayWrite
    ): Response<CfEnvelope<AiGateway>>

    @DELETE("accounts/{accountId}/ai-gateway/gateways/{gatewayId}")
    suspend fun deleteAiGateway(
        @Path("accountId") accountId: String,
        @Path("gatewayId") gatewayId: String
    ): Response<CfEnvelope<AiGateway>>

    @GET("accounts/{accountId}/calls/apps")
    suspend fun listCallsApps(@Path("accountId") accountId: String): Response<CfEnvelope<List<CallsApp>>>

    /** The only response carrying the app's secret. */
    @POST("accounts/{accountId}/calls/apps")
    suspend fun createCallsApp(
        @Path("accountId") accountId: String,
        @Body app: CallsAppWrite
    ): Response<CfEnvelope<CallsApp>>

    @DELETE("accounts/{accountId}/calls/apps/{appId}")
    suspend fun deleteCallsApp(
        @Path("accountId") accountId: String,
        @Path("appId") appId: String
    ): Response<CfEnvelope<CallsApp>>

    @GET("accounts/{accountId}/pipelines")
    suspend fun listPipelines(@Path("accountId") accountId: String): Response<CfEnvelope<List<Pipeline>>>

    @DELETE("accounts/{accountId}/pipelines/{pipelineName}")
    suspend fun deletePipeline(
        @Path("accountId") accountId: String,
        @Path("pipelineName") pipelineName: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/secrets_store/stores")
    suspend fun listSecretStores(@Path("accountId") accountId: String): Response<CfEnvelope<List<SecretStore>>>

    @POST("accounts/{accountId}/secrets_store/stores")
    suspend fun createSecretStore(
        @Path("accountId") accountId: String,
        @Body store: SecretStoreWrite
    ): Response<CfEnvelope<List<SecretStore>>>

    @DELETE("accounts/{accountId}/secrets_store/stores/{storeId}")
    suspend fun deleteSecretStore(
        @Path("accountId") accountId: String,
        @Path("storeId") storeId: String
    ): Response<CfEnvelope<Map<String, String>>>

    /** Names and metadata only - a stored secret's value is never returned. */
    @GET("accounts/{accountId}/secrets_store/stores/{storeId}/secrets")
    suspend fun listStoredSecrets(
        @Path("accountId") accountId: String,
        @Path("storeId") storeId: String
    ): Response<CfEnvelope<List<StoredSecret>>>

    @DELETE("accounts/{accountId}/secrets_store/stores/{storeId}/secrets/{secretId}")
    suspend fun deleteStoredSecret(
        @Path("accountId") accountId: String,
        @Path("storeId") storeId: String,
        @Path("secretId") secretId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Addressing: BYOIP prefixes and address maps ----

    @GET("accounts/{accountId}/addressing/prefixes")
    suspend fun listAddressingPrefixes(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AddressingPrefix>>>

    @PATCH("accounts/{accountId}/addressing/prefixes/{prefixId}")
    suspend fun updatePrefixDescription(
        @Path("accountId") accountId: String,
        @Path("prefixId") prefixId: String,
        @Body body: PrefixDescriptionWrite
    ): Response<CfEnvelope<AddressingPrefix>>

    /** Advertisement lives on its own sub-resource rather than on the prefix, because turning
     *  it on or off is what actually moves traffic. */
    @GET("accounts/{accountId}/addressing/prefixes/{prefixId}/bgp/status")
    suspend fun getPrefixBgpStatus(
        @Path("accountId") accountId: String,
        @Path("prefixId") prefixId: String
    ): Response<CfEnvelope<PrefixBgpStatus>>

    @PATCH("accounts/{accountId}/addressing/prefixes/{prefixId}/bgp/status")
    suspend fun setPrefixBgpStatus(
        @Path("accountId") accountId: String,
        @Path("prefixId") prefixId: String,
        @Body body: PrefixBgpStatusWrite
    ): Response<CfEnvelope<PrefixBgpStatus>>

    @GET("accounts/{accountId}/addressing/address_maps")
    suspend fun listAddressMaps(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<AddressMap>>>

    /** The list omits ips and memberships; only the single-map read carries them. */
    @GET("accounts/{accountId}/addressing/address_maps/{addressMapId}")
    suspend fun getAddressMap(
        @Path("accountId") accountId: String,
        @Path("addressMapId") addressMapId: String
    ): Response<CfEnvelope<AddressMap>>

    @POST("accounts/{accountId}/addressing/address_maps")
    suspend fun createAddressMap(
        @Path("accountId") accountId: String,
        @Body body: AddressMapWrite
    ): Response<CfEnvelope<AddressMap>>

    @PATCH("accounts/{accountId}/addressing/address_maps/{addressMapId}")
    suspend fun updateAddressMap(
        @Path("accountId") accountId: String,
        @Path("addressMapId") addressMapId: String,
        @Body body: AddressMapUpdate
    ): Response<CfEnvelope<AddressMap>>

    @DELETE("accounts/{accountId}/addressing/address_maps/{addressMapId}")
    suspend fun deleteAddressMap(
        @Path("accountId") accountId: String,
        @Path("addressMapId") addressMapId: String
    ): Response<CfEnvelope<Map<String, String>>>

    // ---- Magic WAN sites, static routes, and Magic Firewall ----

    @GET("accounts/{accountId}/magic/sites")
    suspend fun listMagicSites(@Path("accountId") accountId: String): Response<CfEnvelope<List<MagicSite>>>

    @GET("accounts/{accountId}/magic/sites/{siteId}/lans")
    suspend fun listMagicSiteLans(
        @Path("accountId") accountId: String,
        @Path("siteId") siteId: String
    ): Response<CfEnvelope<MagicSiteLanList>>

    @GET("accounts/{accountId}/magic/sites/{siteId}/wans")
    suspend fun listMagicSiteWans(
        @Path("accountId") accountId: String,
        @Path("siteId") siteId: String
    ): Response<CfEnvelope<MagicSiteWanList>>

    /** Answers with the routes it created, since one call can add several. */
    @POST("accounts/{accountId}/magic/routes")
    suspend fun createMagicRoute(
        @Path("accountId") accountId: String,
        @Body route: MagicRouteWrite
    ): Response<CfEnvelope<MagicRouteWriteResult>>

    @DELETE("accounts/{accountId}/magic/routes/{routeId}")
    suspend fun deleteMagicRoute(
        @Path("accountId") accountId: String,
        @Path("routeId") routeId: String
    ): Response<CfEnvelope<MagicRouteDeleteResult>>

    /** Magic Firewall is the Rulesets engine at account scope, phase "magic_transit".
     *  404s until the account has its first rule, the same as a zone phase does. */
    @GET("accounts/{accountId}/rulesets/phases/{phase}/entrypoint")
    suspend fun getAccountPhaseRuleset(
        @Path("accountId") accountId: String,
        @Path("phase") phase: String
    ): Response<CfEnvelope<Ruleset>>

    @PUT("accounts/{accountId}/rulesets/phases/{phase}/entrypoint")
    suspend fun putAccountPhaseRuleset(
        @Path("accountId") accountId: String,
        @Path("phase") phase: String,
        @Body body: RulesetPhaseWrite
    ): Response<CfEnvelope<Ruleset>>

    @POST("accounts/{accountId}/rulesets/{rulesetId}/rules")
    suspend fun addAccountRulesetRule(
        @Path("accountId") accountId: String,
        @Path("rulesetId") rulesetId: String,
        @Body rule: RulesetRuleWrite
    ): Response<CfEnvelope<Ruleset>>

    @PATCH("accounts/{accountId}/rulesets/{rulesetId}/rules/{ruleId}")
    suspend fun updateAccountRulesetRule(
        @Path("accountId") accountId: String,
        @Path("rulesetId") rulesetId: String,
        @Path("ruleId") ruleId: String,
        @Body rule: RulesetRuleWrite
    ): Response<CfEnvelope<Ruleset>>

    @DELETE("accounts/{accountId}/rulesets/{rulesetId}/rules/{ruleId}")
    suspend fun deleteAccountRulesetRule(
        @Path("accountId") accountId: String,
        @Path("rulesetId") rulesetId: String,
        @Path("ruleId") ruleId: String
    ): Response<CfEnvelope<Ruleset>>

    // ---- DNS Firewall and notification destinations ----

    @GET("accounts/{accountId}/dns_firewall")
    suspend fun listDnsFirewallClusters(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<DnsFirewallCluster>>>

    @POST("accounts/{accountId}/dns_firewall")
    suspend fun createDnsFirewallCluster(
        @Path("accountId") accountId: String,
        @Body cluster: DnsFirewallClusterWrite
    ): Response<CfEnvelope<DnsFirewallCluster>>

    @DELETE("accounts/{accountId}/dns_firewall/{clusterId}")
    suspend fun deleteDnsFirewallCluster(
        @Path("accountId") accountId: String,
        @Path("clusterId") clusterId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/alerting/v3/destinations/webhooks")
    suspend fun listNotificationWebhooks(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<NotificationWebhook>>>

    @POST("accounts/{accountId}/alerting/v3/destinations/webhooks")
    suspend fun createNotificationWebhook(
        @Path("accountId") accountId: String,
        @Body webhook: NotificationWebhookWrite
    ): Response<CfEnvelope<NotificationWebhook>>

    @DELETE("accounts/{accountId}/alerting/v3/destinations/webhooks/{webhookId}")
    suspend fun deleteNotificationWebhook(
        @Path("accountId") accountId: String,
        @Path("webhookId") webhookId: String
    ): Response<CfEnvelope<Map<String, String>>>

    @GET("accounts/{accountId}/alerting/v3/history")
    suspend fun listNotificationHistory(
        @Path("accountId") accountId: String
    ): Response<CfEnvelope<List<NotificationHistoryEntry>>>
}
