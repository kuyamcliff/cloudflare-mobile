package dev.cfmobile.app.ui.zonesettings

/**
 * A zone setting declared as data rather than code.
 *
 * Cloudflare exposes dozens of zone settings that are identical over the wire - GET/PATCH a
 * string value at `/zones/{id}/settings/{settingId}`. The older screens modelled each one as
 * an enum constant plus a branch in an exhaustive `when`, so adding a single setting touched
 * several files and could break an unrelated screen's compile. Declaring them makes adding a
 * setting a one-line change.
 */
sealed interface ZoneSettingSpec {
    val id: String
    val title: String
    val subtitle: String?

    /** An on/off setting. Cloudflare represents these as the strings "on" and "off". */
    data class Toggle(
        override val id: String,
        override val title: String,
        override val subtitle: String? = null
    ) : ZoneSettingSpec

    /** A setting with a fixed set of allowed values, rendered as a dropdown. */
    data class Options(
        override val id: String,
        override val title: String,
        /** value to display label, in the order Cloudflare documents them. */
        val options: List<Pair<String, String>>,
        override val subtitle: String? = null
    ) : ZoneSettingSpec
}

/** Cloudflare's on/off settings are strings, not booleans. */
fun String?.isSettingOn(): Boolean = this == "on"

fun Boolean.toSettingValue(): String = if (this) "on" else "off"

/**
 * The settings families this app exposes, grouped the way Cloudflare's own dashboard groups
 * them so someone who knows the dashboard can find them here.
 */
object ZoneSettingGroups {

    val SPEED: List<ZoneSettingSpec> = listOf(
        ZoneSettingSpec.Options(
            id = "polish",
            title = "Polish",
            subtitle = "Compress images at the edge",
            options = listOf("off" to "Off", "lossless" to "Lossless", "lossy" to "Lossy")
        ),
        ZoneSettingSpec.Toggle("brotli", "Brotli", "Compress text responses with Brotli"),
        ZoneSettingSpec.Toggle("early_hints", "Early Hints", "Send 103 responses so browsers preload sooner"),
        ZoneSettingSpec.Toggle("rocket_loader", "Rocket Loader", "Defer JavaScript so pages paint faster"),
        ZoneSettingSpec.Toggle("http2", "HTTP/2", "Serve traffic over HTTP/2"),
        ZoneSettingSpec.Toggle("http3", "HTTP/3 (QUIC)", "Serve traffic over HTTP/3"),
        ZoneSettingSpec.Toggle("0rtt", "0-RTT Connection Resumption", "Faster repeat connections, at some replay risk"),
        ZoneSettingSpec.Toggle("mirage", "Mirage", "Optimise images for slow mobile connections")
    )

    val NETWORK: List<ZoneSettingSpec> = listOf(
        ZoneSettingSpec.Toggle("websockets", "WebSockets", "Allow WebSocket connections through Cloudflare"),
        ZoneSettingSpec.Toggle("ip_geolocation", "IP Geolocation", "Send the visitor's country to your origin"),
        ZoneSettingSpec.Toggle("ipv6", "IPv6 Compatibility", "Serve your site over IPv6"),
        ZoneSettingSpec.Toggle("pseudo_ipv4", "Pseudo IPv4", "Add a pseudo IPv4 header for IPv6 visitors"),
        ZoneSettingSpec.Toggle("opportunistic_onion", "Onion Routing", "Offer Tor users an onion service"),
        ZoneSettingSpec.Toggle("true_client_ip_header", "True-Client-IP Header", "Send the visitor IP as True-Client-IP"),
        ZoneSettingSpec.Toggle("grpc", "gRPC", "Proxy gRPC traffic")
    )

    val SCRAPE_SHIELD: List<ZoneSettingSpec> = listOf(
        ZoneSettingSpec.Toggle("email_obfuscation", "Email Address Obfuscation", "Hide email addresses from scrapers"),
        ZoneSettingSpec.Toggle("hotlink_protection", "Hotlink Protection", "Block other sites embedding your images"),
        ZoneSettingSpec.Toggle("server_side_exclude", "Server-side Excludes", "Hide sensitive content from suspicious visitors")
    )

    val CACHE_BEHAVIOUR: List<ZoneSettingSpec> = listOf(
        ZoneSettingSpec.Toggle("always_online", "Always Online", "Serve a cached copy when your origin is down"),
        ZoneSettingSpec.Toggle("crawler_hints", "Crawler Hints", "Tell search engines when content changed"),
        ZoneSettingSpec.Toggle("sort_query_string_for_cache", "Query String Sort", "Treat reordered query strings as one cached object"),
        ZoneSettingSpec.Toggle("browser_check", "Browser Integrity Check", "Block requests with suspicious headers")
    )
}
