package dev.cfmobile.app.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val ZONES = "zones"
    const val SETTINGS = "settings"
    const val SECURITY = "security"
    const val ZONE_MENU = "zone/{zoneId}/{zoneName}"
    const val ACCOUNT_MEMBERS = "account/{accountId}/members"
    const val AUDIT_LOGS = "account/{accountId}/auditlogs"
    const val LOAD_BALANCING = "account/{accountId}/loadbalancing"
    const val R2 = "account/{accountId}/r2"
    const val KV = "account/{accountId}/kv"
    const val D1 = "account/{accountId}/d1"
    const val WORKERS = "account/{accountId}/workers"
    const val DNS = "zone/{zoneId}/{zoneName}/dns"
    const val SSL = "zone/{zoneId}/{zoneName}/ssl"
    const val FIREWALL = "zone/{zoneId}/{zoneName}/firewall"
    const val WAF = "zone/{zoneId}/{zoneName}/waf"
    const val RATE_LIMITING = "zone/{zoneId}/{zoneName}/ratelimit"
    const val TRANSFORM_RULES = "zone/{zoneId}/{zoneName}/transformrules"
    const val PAGE_RULES = "zone/{zoneId}/{zoneName}/pagerules"
    const val CACHING = "zone/{zoneId}/{zoneName}/caching"
    const val ANALYTICS = "zone/{zoneId}/{zoneName}/analytics"
    const val BOT_MANAGEMENT = "zone/{zoneId}/{zoneName}/botmanagement"

    /** Zone names (domains) only ever contain URL-safe characters, but this encodes anyway
     *  rather than assuming - a nav route argument is still a URL path segment. */
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    fun decodeZoneName(raw: String): String = URLDecoder.decode(raw, "UTF-8")

    fun zoneMenu(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}"
    fun accountMembers(accountId: String) = "account/$accountId/members"
    fun auditLogs(accountId: String) = "account/$accountId/auditlogs"
    fun loadBalancing(accountId: String) = "account/$accountId/loadbalancing"
    fun r2(accountId: String) = "account/$accountId/r2"
    fun kv(accountId: String) = "account/$accountId/kv"
    fun d1(accountId: String) = "account/$accountId/d1"
    fun workers(accountId: String) = "account/$accountId/workers"
    fun dns(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/dns"
    fun ssl(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/ssl"
    fun firewall(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/firewall"
    fun waf(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/waf"
    fun rateLimiting(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/ratelimit"
    fun transformRules(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/transformrules"
    fun pageRules(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/pagerules"
    fun caching(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/caching"
    fun analytics(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/analytics"
    fun botManagement(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/botmanagement"
}
