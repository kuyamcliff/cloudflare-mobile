package dev.cfmobile.app.core.command

/** Where a slot's value is written: a path placeholder, a query parameter, or a body field
 *  (dotted, with numeric segments for array items: `matchers.0.value`). */
enum class Target { PATH, QUERY, BODY }

sealed class SlotKind {
    /** An IPv4/IPv6 address or CIDR range. */
    data object Ip : SlotKind()
    data object Email : SlotKind()
    data object Url : SlotKind()
    /** A hostname with at least one dot that is not itself a zone the user owns. */
    data object Host : SlotKind()
    data object Number : SlotKind()
    /** A country named in words or as a two-letter code. */
    data object Country : SlotKind()
    /** The first quoted string. */
    data object Quoted : SlotKind()
    /** The next unclaimed word. */
    data object Free : SlotKind()
    /** Everything after the recipe's phrase, as typed (a prompt, a query). */
    data object Rest : SlotKind()
    /** "on"/"off" style words; [on] and [off] are the values written. */
    data class OnOff(val on: Any = "on", val off: Any = "off", val default: Any? = on) : SlotKind()
    /** One of [values], matched case-insensitively (single letters must be typed uppercase).
     *  [aliases] maps extra words to a value. */
    data class Choice(val values: List<String>, val aliases: Map<String, String> = emptyMap(), val default: String? = null) : SlotKind()
    /** A number typed right after [keyword] ("ttl 300"). */
    data class After(val keyword: String) : SlotKind()
    /** Sets [value] when any of [words] is present. */
    data class Flag(val words: Set<String>, val value: Any) : SlotKind()
}

data class Slot(
    val target: Target,
    val name: String,
    val kind: SlotKind,
    /** Writes the value wrapped in a list. */
    val asList: Boolean = false,
    /** Expands a short name to a name under the zone (`www` -> `www.example.com`). */
    val inZone: Boolean = false
)

/**
 * A common task expressed as an API operation plus how to fill it from words. Recipes never
 * run anything: they prefill the same review form every operation uses.
 */
data class Recipe(
    val id: String,
    val title: String,
    val method: String,
    val path: String,
    /** Each phrase is matched as a set of canonical words, order-free. */
    val phrases: List<String>,
    val fixedPath: Map<String, String> = emptyMap(),
    val fixedQuery: Map<String, String> = emptyMap(),
    val fixedBody: Map<String, Any?> = emptyMap(),
    val slots: List<Slot> = emptyList(),
    /** A short line shown under the title in results. */
    val hint: String? = null,
    /** Words that, when present, rule this recipe out ("off" for a recipe that only turns on). */
    val excludes: Set<String> = emptySet()
) {
    val isRead: Boolean get() = method == "GET"
    val needsZone: Boolean get() = path.contains("{zone_id}")
}

private fun body(name: String, kind: SlotKind, asList: Boolean = false, inZone: Boolean = false) = Slot(Target.BODY, name, kind, asList, inZone)
private fun path(name: String, kind: SlotKind) = Slot(Target.PATH, name, kind)
private fun query(name: String, kind: SlotKind) = Slot(Target.QUERY, name, kind)

private val DNS_TYPES = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA", "PTR", "HTTPS", "SVCB", "LOC", "CERT", "DNSKEY", "DS", "NAPTR", "SMIMEA", "SSHFP", "TLSA", "URI")

private fun setting(
    id: String,
    title: String,
    setting: String,
    phrases: List<String>,
    kind: SlotKind = SlotKind.OnOff(),
    hint: String? = null
) = Recipe(
    id = "setting.$id", title = title, method = "PATCH", path = "zones/{zone_id}/settings/{setting_id}",
    phrases = phrases, fixedPath = mapOf("setting_id" to setting), slots = listOf(body("value", kind)), hint = hint
)

object Recipes {

    val all: List<Recipe> = listOf(
        // DNS
        Recipe(
            "dns.create", "Add DNS record", "POST", "zones/{zone_id}/dns_records",
            listOf("create record", "create dns record", "create dns", "point to", "create subdomain"),
            fixedBody = mapOf("ttl" to 1),
            slots = listOf(
                body("type", SlotKind.Choice(DNS_TYPES, mapOf("ipv4" to "A", "ipv6" to "AAAA", "alias" to "CNAME", "mail" to "MX", "text" to "TXT"))),
                body("content", SlotKind.Ip),
                body("content", SlotKind.Quoted),
                body("name", SlotKind.Free, inZone = true),
                body("content", SlotKind.Free),
                body("proxied", SlotKind.Flag(setOf("proxied", "proxy", "orange"), true)),
                body("proxied", SlotKind.Flag(setOf("unproxied", "grey", "gray"), false)),
                body("ttl", SlotKind.After("ttl")),
                body("priority", SlotKind.After("priority"))
            ),
            hint = "Type, name and value, e.g. A www 192.0.2.1"
        ),
        Recipe("dns.list", "List DNS records", "GET", "zones/{zone_id}/dns_records", listOf("list record", "list dns", "dns record", "list dns record"),
            slots = listOf(query("name", SlotKind.Host), query("type", SlotKind.Choice(DNS_TYPES)))),
        Recipe("dns.update", "Edit DNS record", "PATCH", "zones/{zone_id}/dns_records/{dns_record_id}", listOf("update record", "update dns record", "update dns"),
            slots = listOf(body("content", SlotKind.Ip), body("content", SlotKind.Quoted), body("proxied", SlotKind.Flag(setOf("proxied", "proxy"), true)))),
        Recipe("dns.delete", "Delete DNS record", "DELETE", "zones/{zone_id}/dns_records/{dns_record_id}", listOf("delete record", "delete dns record", "delete dns")),
        Recipe("dns.export", "Export DNS zone file", "GET", "zones/{zone_id}/dns_records/export", listOf("export dns", "export record", "zone file", "bind file")),
        Recipe("dnssec.status", "DNSSEC status", "GET", "zones/{zone_id}/dnssec", listOf("dnssec")),

        // Cache
        Recipe("cache.purge_all", "Purge everything", "POST", "zones/{zone_id}/purge_cache", listOf("purge cache", "purge everything", "purge all", "purge", "purge zone"),
            fixedBody = mapOf("purge_everything" to true), hint = "Removes every cached file for the zone", excludes = setOf("url", "file", "host", "tag", "prefix")),
        Recipe("cache.purge_urls", "Purge URLs", "POST", "zones/{zone_id}/purge_cache", listOf("purge url", "purge file", "purge page"),
            slots = listOf(body("files", SlotKind.Url, asList = true))),
        Recipe("cache.purge_hosts", "Purge hostnames", "POST", "zones/{zone_id}/purge_cache", listOf("purge host", "purge hostname", "purge subdomain"),
            slots = listOf(body("hosts", SlotKind.Host, asList = true))),
        Recipe("cache.purge_tags", "Purge cache tags", "POST", "zones/{zone_id}/purge_cache", listOf("purge tag"),
            slots = listOf(body("tags", SlotKind.Free, asList = true))),

        // Zone settings
        setting("dev_mode", "Development mode", "development_mode", listOf("development mode", "development"), hint = "Bypasses the cache for 3 hours"),
        setting("under_attack", "I'm Under Attack mode", "security_level", listOf("under attack", "attack mode", "im under attack"),
            SlotKind.OnOff(on = "under_attack", off = "medium")),
        setting("security_level", "Security level", "security_level", listOf("security level"),
            SlotKind.Choice(listOf("off", "essentially_off", "low", "medium", "high", "under_attack"))),
        setting("ssl", "SSL/TLS encryption mode", "ssl", listOf("ssl mode", "ssl", "encryption mode", "encryption"),
            SlotKind.Choice(listOf("off", "flexible", "full", "strict"), mapOf("strict" to "strict", "full_strict" to "strict"))),
        setting("always_https", "Always Use HTTPS", "always_use_https", listOf("always https", "always use https", "force https", "redirect https")),
        setting("https_rewrites", "Automatic HTTPS Rewrites", "automatic_https_rewrites", listOf("https rewrite", "automatic https rewrite")),
        setting("min_tls", "Minimum TLS version", "min_tls_version", listOf("minimum ssl version", "min ssl", "minimum ssl", "ssl version"),
            SlotKind.Choice(listOf("1.0", "1.1", "1.2", "1.3"))),
        setting("tls13", "TLS 1.3", "tls_1_3", listOf("ssl 1.3"), SlotKind.Choice(listOf("on", "off", "zrt"), mapOf("on" to "on", "off" to "off"), default = "on")),
        setting("rocket_loader", "Rocket Loader", "rocket_loader", listOf("rocket loader", "rocket")),
        setting("cache_level", "Cache level", "cache_level", listOf("cache level"), SlotKind.Choice(listOf("aggressive", "basic", "simplified"))),
        setting("browser_ttl", "Browser cache TTL", "browser_cache_ttl", listOf("browser cache ttl", "browser ttl"), SlotKind.Number),
        setting("always_online", "Always Online", "always_online", listOf("always online")),
        setting("ipv6", "IPv6 compatibility", "ipv6", listOf("ipv6")),
        setting("websockets", "WebSockets", "websockets", listOf("websocket", "web socket")),
        setting("http3", "HTTP/3 (QUIC)", "http3", listOf("http3", "quic", "http 3")),
        setting("zero_rtt", "0-RTT connection resumption", "0rtt", listOf("0rtt", "0-rtt", "zero rtt")),
        setting("email_obfuscation", "Email address obfuscation", "email_obfuscation", listOf("email obfuscation")),
        setting("hotlink", "Hotlink protection", "hotlink_protection", listOf("hotlink", "hotlink protection")),
        setting("browser_check", "Browser integrity check", "browser_check", listOf("browser integrity", "browser check")),
        setting("opportunistic", "Opportunistic encryption", "opportunistic_encryption", listOf("opportunistic encryption")),
        setting("early_hints", "Early Hints", "early_hints", listOf("early hint")),
        setting("brotli", "Brotli compression", "brotli", listOf("brotli")),
        setting("challenge_ttl", "Challenge passage", "challenge_ttl", listOf("challenge passage", "challenge ttl"), SlotKind.Number),
        Recipe("settings.list", "All zone settings", "GET", "zones/{zone_id}/settings", listOf("zone setting", "list setting", "setting")),

        // Security
        Recipe(
            "firewall.access_rule", "Block, challenge or allow a visitor", "POST", "zones/{zone_id}/firewall/access_rules/rules",
            listOf("block", "block ip", "block country", "challenge ip", "challenge country", "allow ip", "allow country"),
            fixedBody = mapOf("mode" to "block", "configuration" to mapOf("target" to "ip", "value" to "")),
            slots = listOf(
                body("mode", SlotKind.Choice(listOf("block", "challenge", "whitelist", "js_challenge", "managed_challenge"), mapOf("allow" to "whitelist", "challenge" to "managed_challenge"))),
                body("configuration.value", SlotKind.Ip),
                body("configuration.value", SlotKind.Country),
                body("notes", SlotKind.Quoted)
            ),
            hint = "An IP, a CIDR range or a country"
        ),
        Recipe("firewall.access_rules", "IP access rules", "GET", "zones/{zone_id}/firewall/access_rules/rules", listOf("list access rule", "access rule", "blocked ip", "ip rule")),
        Recipe("rulesets.list", "Rulesets", "GET", "zones/{zone_id}/rulesets", listOf("ruleset", "list ruleset")),
        Recipe("security.events", "Security events (last 24h)", "GET", "zones/{zone_id}/security-center/insights", listOf("security insight", "security center")),

        // Zone lifecycle
        Recipe("zone.create", "Add a zone", "POST", "zones", listOf("create zone", "onboard zone"),
            fixedBody = mapOf("type" to "full", "account" to mapOf("id" to "{account_id}")),
            slots = listOf(body("name", SlotKind.Host))),
        Recipe("zone.details", "Zone details and nameservers", "GET", "zones/{zone_id}", listOf("zone detail", "zone info", "nameserver", "zone status")),
        Recipe("zone.pause", "Pause Cloudflare on zone", "PATCH", "zones/{zone_id}", listOf("pause zone", "pause cloudflare", "pause"),
            fixedBody = mapOf("paused" to true), excludes = setOf("unpause", "resume")),
        Recipe("zone.resume", "Resume Cloudflare on zone", "PATCH", "zones/{zone_id}", listOf("unpause zone", "resume zone", "unpause", "resume cloudflare"),
            fixedBody = mapOf("paused" to false)),
        Recipe("zone.delete", "Delete zone", "DELETE", "zones/{zone_id}", listOf("delete zone")),
        Recipe("zone.activation_check", "Re-check nameservers", "PUT", "zones/{zone_id}/activation_check", listOf("activation check", "recheck nameserver", "check activation")),
        Recipe("zones.list", "All zones", "GET", "zones", listOf("list zone", "all zone"), fixedQuery = mapOf("per_page" to "50")),

        // Storage and databases
        Recipe("kv.create", "Create KV namespace", "POST", "accounts/{account_id}/storage/kv/namespaces", listOf("create kv", "create kv namespace", "create namespace"),
            slots = listOf(body("title", SlotKind.Quoted), body("title", SlotKind.Free))),
        Recipe("kv.list", "KV namespaces", "GET", "accounts/{account_id}/storage/kv/namespaces", listOf("list kv", "kv namespace", "list namespace")),
        Recipe("d1.create", "Create D1 database", "POST", "accounts/{account_id}/d1/database", listOf("create d1", "create database", "create d1 database"),
            slots = listOf(body("name", SlotKind.Quoted), body("name", SlotKind.Free))),
        Recipe("d1.list", "D1 databases", "GET", "accounts/{account_id}/d1/database", listOf("list d1", "list database", "d1 database")),
        Recipe("r2.create", "Create R2 bucket", "POST", "accounts/{account_id}/r2/buckets", listOf("create bucket", "create r2", "create r2 bucket"),
            slots = listOf(body("name", SlotKind.Quoted), body("name", SlotKind.Free))),
        Recipe("r2.list", "R2 buckets", "GET", "accounts/{account_id}/r2/buckets", listOf("list bucket", "list r2", "r2 bucket")),
        Recipe("queues.create", "Create queue", "POST", "accounts/{account_id}/queues", listOf("create queue"),
            slots = listOf(body("queue_name", SlotKind.Quoted), body("queue_name", SlotKind.Free))),
        Recipe("queues.list", "Queues", "GET", "accounts/{account_id}/queues", listOf("list queue", "queue")),
        Recipe("hyperdrive.list", "Hyperdrive configs", "GET", "accounts/{account_id}/hyperdrive/configs", listOf("hyperdrive", "list hyperdrive")),
        Recipe("vectorize.list", "Vectorize indexes", "GET", "accounts/{account_id}/vectorize/v2/indexes", listOf("vectorize", "vector index", "list vector")),

        // Compute
        Recipe("workers.list", "Workers", "GET", "accounts/{account_id}/workers/scripts", listOf("list worker", "worker")),
        Recipe("workers.delete", "Delete Worker", "DELETE", "accounts/{account_id}/workers/scripts/{script_name}", listOf("delete worker"),
            slots = listOf(path("script_name", SlotKind.Free))),
        Recipe("workers.subdomain", "workers.dev subdomain", "GET", "accounts/{account_id}/workers/subdomain", listOf("worker subdomain", "workers.dev")),
        Recipe("worker_routes.list", "Worker routes", "GET", "zones/{zone_id}/workers/routes", listOf("worker route", "list route")),
        Recipe("pages.list", "Pages projects", "GET", "accounts/{account_id}/pages/projects", listOf("pages project", "list pages", "pages")),
        Recipe("ai.run", "Ask Workers AI", "POST", "accounts/{account_id}/ai/run/{model_name}", listOf("ask ai", "run ai", "workers ai", "ai prompt"),
            fixedPath = mapOf("model_name" to "@cf/meta/llama-3.3-70b-instruct-fp8-fast"),
            slots = listOf(body("prompt", SlotKind.Rest)), hint = "Runs a prompt on your account's Workers AI"),
        Recipe("ai.models", "Workers AI models", "GET", "accounts/{account_id}/ai/models/search", listOf("ai model", "list model")),

        // Networking and Zero Trust
        Recipe("tunnels.create", "Create tunnel", "POST", "accounts/{account_id}/cfd_tunnel", listOf("create tunnel"),
            fixedBody = mapOf("config_src" to "cloudflare"), slots = listOf(body("name", SlotKind.Quoted), body("name", SlotKind.Free))),
        Recipe("tunnels.list", "Tunnels", "GET", "accounts/{account_id}/cfd_tunnel", listOf("list tunnel", "tunnel"), fixedQuery = mapOf("is_deleted" to "false")),
        Recipe("access.apps", "Access applications", "GET", "accounts/{account_id}/access/apps", listOf("access app", "access application", "zero trust app")),
        Recipe("access.service_token", "Create service token", "POST", "accounts/{account_id}/access/service_tokens", listOf("create service token"),
            slots = listOf(body("name", SlotKind.Quoted), body("name", SlotKind.Free))),
        Recipe("gateway.rules", "Gateway policies", "GET", "accounts/{account_id}/gateway/rules", listOf("gateway rule", "gateway policy", "gateway")),
        Recipe("devices.list", "Devices", "GET", "accounts/{account_id}/devices/physical-devices", listOf("list device", "device", "warp device")),
        Recipe("lists.create", "Create IP list", "POST", "accounts/{account_id}/rules/lists", listOf("create list", "create ip list"),
            fixedBody = mapOf("kind" to "ip"),
            slots = listOf(body("kind", SlotKind.Choice(listOf("ip", "redirect", "hostname", "asn"))), body("name", SlotKind.Quoted), body("name", SlotKind.Free))),
        Recipe("turnstile.create", "Create Turnstile widget", "POST", "accounts/{account_id}/challenges/widgets", listOf("create turnstile", "create widget", "create captcha"),
            fixedBody = mapOf("mode" to "managed"),
            slots = listOf(
                body("mode", SlotKind.Choice(listOf("managed", "non-interactive", "invisible"))),
                body("domains", SlotKind.Host, asList = true),
                body("name", SlotKind.Quoted), body("name", SlotKind.Free)
            )),
        Recipe("turnstile.list", "Turnstile widgets", "GET", "accounts/{account_id}/challenges/widgets", listOf("turnstile", "captcha", "list widget")),

        // Email
        Recipe(
            "email.forward", "Forward an address", "POST", "zones/{zone_id}/email/routing/rules", listOf("forward email", "email forward", "forward", "create email rule"),
            fixedBody = mapOf(
                "enabled" to true,
                "matchers" to listOf(mapOf("type" to "literal", "field" to "to", "value" to "")),
                "actions" to listOf(mapOf("type" to "forward", "value" to listOf("")))
            ),
            slots = listOf(body("matchers.0.value", SlotKind.Email), body("actions.0.value.0", SlotKind.Email)),
            hint = "e.g. forward hi@example.com to me@gmail.com"
        ),
        Recipe("email.rules", "Email routing rules", "GET", "zones/{zone_id}/email/routing/rules", listOf("email rule", "email routing", "list email")),
        Recipe("email.address", "Add destination address", "POST", "accounts/{account_id}/email/routing/addresses", listOf("create destination", "verify email", "create email address"),
            slots = listOf(body("email", SlotKind.Email))),

        // Account
        Recipe("members.list", "Account members", "GET", "accounts/{account_id}/members", listOf("list member", "member")),
        Recipe("members.invite", "Invite member", "POST", "accounts/{account_id}/members", listOf("invite", "invite member", "create member"),
            slots = listOf(body("email", SlotKind.Email))),
        Recipe("audit.list", "Audit log", "GET", "accounts/{account_id}/audit_logs", listOf("audit log", "audit", "who changed")),
        Recipe("billing.profile", "Billing profile", "GET", "accounts/{account_id}/billing/profile", listOf("billing", "billing profile", "invoice")),
        Recipe("subscriptions.list", "Subscriptions", "GET", "accounts/{account_id}/subscriptions", listOf("subscription", "plan")),
        Recipe("notifications.list", "Notification policies", "GET", "accounts/{account_id}/alerting/v3/policies", listOf("notification", "alert")),
        Recipe("token.verify", "Verify this token", "GET", "user/tokens/verify", listOf("verify token", "token status", "whoami", "who am i")),

        // Tools
        Recipe("trace", "Trace a request", "POST", "accounts/{account_id}/request-tracer/trace", listOf("trace", "trace request", "trace url"),
            fixedBody = mapOf("method" to "GET"), slots = listOf(body("url", SlotKind.Url))),
        Recipe("urlscan", "Scan a URL", "POST", "accounts/{account_id}/urlscanner/v2/scan", listOf("scan url", "url scan", "scan"),
            slots = listOf(body("url", SlotKind.Url), body("url", SlotKind.Host))),
        Recipe("whois", "WHOIS lookup", "GET", "accounts/{account_id}/intel/whois", listOf("whois", "who owns"),
            slots = listOf(query("domain", SlotKind.Host))),
        Recipe("domain.intel", "Domain intelligence", "GET", "accounts/{account_id}/intel/domain", listOf("domain intel", "domain reputation", "intel domain"),
            slots = listOf(query("domain", SlotKind.Host))),
        Recipe("ip.intel", "IP intelligence", "GET", "accounts/{account_id}/intel/ip", listOf("ip intel", "ip reputation", "lookup ip", "intel ip"),
            slots = listOf(query("ipv4", SlotKind.Ip))),
        Recipe("cf.ips", "Cloudflare IP ranges", "GET", "ips", listOf("cloudflare ip", "ip range", "cloudflare range"))
    )

    fun byId(id: String): Recipe? = all.firstOrNull { it.id == id }
}
