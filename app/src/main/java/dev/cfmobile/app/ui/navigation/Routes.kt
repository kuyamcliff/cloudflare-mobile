package dev.cfmobile.app.ui.navigation

object Routes {
    const val LOGIN = "login"
    const val ZONES = "zones"
    const val SETTINGS = "settings"
    const val SECURITY = "security"
    const val ZONE_MENU = "zone/{zoneId}/{zoneName}"
    const val DNS = "zone/{zoneId}/dns"
    const val SSL = "zone/{zoneId}/ssl"
    const val FIREWALL = "zone/{zoneId}/firewall"
    const val PAGE_RULES = "zone/{zoneId}/pagerules"
    const val CACHING = "zone/{zoneId}/caching"
    const val ANALYTICS = "zone/{zoneId}/analytics"

    fun zoneMenu(zoneId: String, zoneName: String) = "zone/$zoneId/$zoneName"
    fun dns(zoneId: String) = "zone/$zoneId/dns"
    fun ssl(zoneId: String) = "zone/$zoneId/ssl"
    fun firewall(zoneId: String) = "zone/$zoneId/firewall"
    fun pageRules(zoneId: String) = "zone/$zoneId/pagerules"
    fun caching(zoneId: String) = "zone/$zoneId/caching"
    fun analytics(zoneId: String) = "zone/$zoneId/analytics"
}
