package dev.cfmobile.app.data.remote

import dev.cfmobile.app.data.remote.dto.*
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
}
