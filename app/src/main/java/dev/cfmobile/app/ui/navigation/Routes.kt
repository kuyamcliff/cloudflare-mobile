package dev.cfmobile.app.ui.navigation

import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val LOGIN = "login"
    const val ZONES = "zones"
    const val SETTINGS = "settings"
    const val SECURITY = "security"
    const val ZONE_MENU = "zone/{zoneId}/{zoneName}"
    const val DNS = "zone/{zoneId}/{zoneName}/dns"
    const val SSL = "zone/{zoneId}/{zoneName}/ssl"
    const val FIREWALL = "zone/{zoneId}/{zoneName}/firewall"
    const val WAF = "zone/{zoneId}/{zoneName}/waf"
    const val PAGE_RULES = "zone/{zoneId}/{zoneName}/pagerules"
    const val CACHING = "zone/{zoneId}/{zoneName}/caching"
    const val ANALYTICS = "zone/{zoneId}/{zoneName}/analytics"

    /** Zone names (domains) only ever contain URL-safe characters, but this encodes anyway
     *  rather than assuming - a nav route argument is still a URL path segment. */
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    fun decodeZoneName(raw: String): String = URLDecoder.decode(raw, "UTF-8")

    fun zoneMenu(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}"
    fun dns(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/dns"
    fun ssl(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/ssl"
    fun firewall(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/firewall"
    fun waf(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/waf"
    fun pageRules(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/pagerules"
    fun caching(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/caching"
    fun analytics(zoneId: String, zoneName: String) = "zone/$zoneId/${encode(zoneName)}/analytics"
}
