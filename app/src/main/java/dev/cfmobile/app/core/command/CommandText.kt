package dev.cfmobile.app.core.command

/**
 * Tokenizing for typed commands. Everything here is deterministic and runs on the device: a
 * command is matched against recipes, screens and the operation registry without leaving it.
 */
object CommandText {

    data class Token(
        /** As typed, with surrounding punctuation trimmed. */
        val raw: String,
        /** Lowercased, synonym-mapped and singularized. */
        val canon: String,
        val index: Int
    )

    data class Parsed(
        val input: String,
        val tokens: List<Token>,
        /** Text inside double or typographic quotes, in order. */
        val quoted: List<String>,
        /** `name=value` pairs, applied verbatim to path, query or body fields. */
        val assignments: List<Pair<String, String>>
    ) {
        val canonSet: Set<String> = tokens.map { it.canon }.toSet()
        fun has(word: String) = word in canonSet
    }

    val STOP = setOf(
        "the", "a", "an", "to", "for", "on", "in", "of", "my", "please", "me", "with", "at", "from",
        "and", "this", "that", "it", "is", "be", "i", "want", "would", "like", "can", "you", "could",
        "should", "need", "into", "by", "as", "via", "using", "named", "called", "now", "some", "our",
        "turn", "put", "just", "quickly", "here", "there", "its", "every"
    )

    /** Words that only say "take me there"; they never count as search terms. */
    val NAV = setOf("open", "go", "goto", "navigate", "jump", "manage", "configure")

    private val SYNONYMS = mapOf(
        "add" to "create", "new" to "create", "make" to "create", "setup" to "create", "register" to "create",
        "provision" to "create", "spin" to "create", "generate" to "create", "issue" to "create",
        "remove" to "delete", "rm" to "delete", "del" to "delete", "destroy" to "delete", "drop" to "delete", "erase" to "delete",
        "show" to "list", "get" to "list", "view" to "list", "see" to "list", "display" to "list", "ls" to "list",
        "browse" to "list", "fetch" to "list", "read" to "list", "inspect" to "list", "check" to "list",
        "edit" to "update", "change" to "update", "modify" to "update", "set" to "update", "rename" to "update", "switch" to "update",
        "clear" to "purge", "flush" to "purge", "bust" to "purge", "invalidate" to "purge",
        "enable" to "on", "activate" to "on", "true" to "on", "yes" to "on", "start" to "on",
        "disable" to "off", "deactivate" to "off", "false" to "off", "no" to "off", "stop" to "off",
        "site" to "zone", "domain" to "zone", "website" to "zone",
        "bucket" to "bucket", "buckets" to "bucket",
        "kv" to "kv", "namespace" to "namespace",
        "db" to "database", "sqlite" to "database",
        "script" to "worker", "function" to "worker", "serverless" to "worker",
        "cert" to "certificate", "certs" to "certificate", "tls" to "ssl",
        "firewall" to "firewall", "waf" to "waf",
        "dev" to "development", "devmode" to "development",
        "token" to "token", "key" to "key",
        "cache" to "cache", "cached" to "cache", "caching" to "cache",
        "whitelist" to "allow", "permit" to "allow", "allowlist" to "allow", "deny" to "block", "ban" to "block",
        "stats" to "analytics", "statistic" to "analytics", "traffic" to "analytics", "metrics" to "analytics",
        "logs" to "log", "audit" to "audit",
        "mail" to "email", "e-mail" to "email",
        "user" to "member", "users" to "member", "people" to "member", "team" to "member",
        "redirects" to "redirect", "forwarding" to "redirect",
        "ns" to "nameserver", "nameservers" to "nameserver"
    )

    private val KEEP_PLURAL = setOf(
        "dns", "https", "status", "analytics", "access", "address", "alias", "settings", "pages", "radar",
        "ips", "this", "always", "rules", "ssl", "tls", "class", "process", "express", "series", "workers"
    )

    private val QUOTE = Regex("[\"“”]([^\"“”]*)[\"“”]|'([^']{2,})'")
    private val ASSIGN = Regex("(?<![\\w.])([A-Za-z_][\\w.\\-]*)=(\"[^\"]*\"|\\S+)")

    fun canonical(word: String): String {
        val w = word.lowercase().trim()
        SYNONYMS[w]?.let { return it }
        val singular = when {
            w in KEEP_PLURAL -> w
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
            else -> w
        }
        return SYNONYMS[singular] ?: singular
    }

    fun parse(input: String): Parsed {
        val quoted = QUOTE.findAll(input).map { (it.groups[1] ?: it.groups[2])!!.value }.toList()
        var rest = QUOTE.replace(input, " ")
        val assignments = ASSIGN.findAll(rest).map { it.groupValues[1] to it.groupValues[2].trim('"') }.toList()
        rest = ASSIGN.replace(rest, " ")
        val tokens = rest.split(Regex("[\\s,;]+"))
            .map { it.trim('?', '!', '(', ')', '[', ']', '{', '}', ':') }
            .map { it.trimEnd('.') }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, raw -> Token(raw, canonical(raw), i) }
        return Parsed(input, tokens, quoted, assignments)
    }

    /** Canonical words of a phrase, minus stopwords. */
    fun phraseWords(phrase: String): List<String> =
        phrase.split(' ').filter { it.isNotBlank() }.map { canonical(it) }.filter { it !in STOP }

    val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")
    val IPV4_CIDR = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}/(3[0-2]|[12]?\\d)$")
    val IPV6 = Regex("^[0-9A-Fa-f:]{2,39}$")
    val IPV6_CIDR = Regex("^[0-9A-Fa-f:]{2,39}/\\d{1,3}$")
    val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")
    val URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    val HOST = Regex("^(?=.{1,253}$)([A-Za-z0-9_*]([A-Za-z0-9-_]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$")
    val NUMBER = Regex("^-?\\d+(\\.\\d+)?$")

    fun isIp(s: String) = IPV4.matches(s) || (IPV6.matches(s) && s.count { it == ':' } >= 2)
    fun isCidr(s: String) = IPV4_CIDR.matches(s) || (IPV6_CIDR.matches(s) && s.count { it == ':' } >= 2)

    /** Country names people type, mapped to the ISO codes Cloudflare expects. */
    val COUNTRIES: Map<String, String> = mapOf(
        "russia" to "RU", "china" to "CN", "united states" to "US", "usa" to "US", "us" to "US", "america" to "US",
        "united kingdom" to "GB", "uk" to "GB", "britain" to "GB", "england" to "GB", "germany" to "DE", "france" to "FR",
        "india" to "IN", "brazil" to "BR", "canada" to "CA", "australia" to "AU", "japan" to "JP", "korea" to "KR",
        "south korea" to "KR", "north korea" to "KP", "iran" to "IR", "iraq" to "IQ", "ukraine" to "UA", "belarus" to "BY",
        "netherlands" to "NL", "spain" to "ES", "italy" to "IT", "mexico" to "MX", "indonesia" to "ID", "vietnam" to "VN",
        "turkey" to "TR", "pakistan" to "PK", "nigeria" to "NG", "philippines" to "PH", "singapore" to "SG", "hong kong" to "HK",
        "taiwan" to "TW", "thailand" to "TH", "poland" to "PL", "romania" to "RO", "sweden" to "SE", "norway" to "NO",
        "finland" to "FI", "denmark" to "DK", "ireland" to "IE", "israel" to "IL", "egypt" to "EG", "south africa" to "ZA",
        "argentina" to "AR", "colombia" to "CO", "chile" to "CL", "peru" to "PE", "saudi arabia" to "SA", "uae" to "AE",
        "bangladesh" to "BD", "malaysia" to "MY", "new zealand" to "NZ", "switzerland" to "CH", "austria" to "AT",
        "belgium" to "BE", "portugal" to "PT", "greece" to "GR", "czechia" to "CZ", "hungary" to "HU", "kazakhstan" to "KZ",
        "venezuela" to "VE", "cuba" to "CU", "syria" to "SY", "tor" to "T1"
    )
}
