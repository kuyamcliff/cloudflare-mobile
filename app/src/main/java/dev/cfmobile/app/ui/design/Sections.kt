package dev.cfmobile.app.ui.design

/** Groups the capability registry's product labels into a short, stable set of sections. */
object Sections {
    private val ORDER = listOf(
        "DNS", "SSL/TLS", "Security", "Rules", "Speed and caching", "Traffic", "Network", "Email",
        "Developer platform", "Storage and media", "Zero Trust", "Analytics and logs", "Account"
    )

    fun of(product: String, id: String = ""): String = when {
        id == "email_routing" -> "Email"
        product == "DNS" -> "DNS"
        product == "SSL/TLS" -> "SSL/TLS"
        product in setOf("Firewall", "Security", "WAF", "Rate Limiting") -> "Security"
        product in setOf("Rules", "Transform Rules", "Page Rules") -> "Rules"
        product in setOf("Caching", "Speed") -> "Speed and caching"
        product == "Traffic" -> "Traffic"
        product == "Network" -> "Network"
        product == "Develop" -> "Developer platform"
        product == "Storage & Media" -> "Storage and media"
        product == "Zero Trust" -> "Zero Trust"
        product in setOf("Analytics", "Analytics & Logs", "Audit", "Diagnostics") -> "Analytics and logs"
        else -> "Account"
    }

    fun rank(section: String): Int = ORDER.indexOf(section).let { if (it < 0) ORDER.size else it }
}
