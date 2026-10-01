package dev.cfmobile.app.core.tokens

import dev.cfmobile.app.data.remote.dto.ApiToken
import dev.cfmobile.app.data.remote.dto.PermissionGroup
import dev.cfmobile.app.data.remote.dto.PermissionGroupRef
import dev.cfmobile.app.data.remote.dto.TokenCondition
import dev.cfmobile.app.data.remote.dto.TokenIpList
import dev.cfmobile.app.data.remote.dto.TokenPolicy
import dev.cfmobile.app.data.remote.dto.TokenWrite
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Which resource family a permission group applies to, from its Cloudflare scope string. */
enum class PermissionKind(val label: String) { USER("User"), ACCOUNT("Account"), ZONE("Zone"), OTHER("Other") }

fun PermissionGroup.kind(): PermissionKind = when {
    scopes.any { it == "com.cloudflare.api.account.zone" } -> PermissionKind.ZONE
    scopes.any { it == "com.cloudflare.api.account" } -> PermissionKind.ACCOUNT
    scopes.any { it == "com.cloudflare.api.user" } -> PermissionKind.USER
    else -> PermissionKind.OTHER
}

sealed class AccountSelection {
    data object All : AccountSelection()
    data class Specific(val ids: Set<String>) : AccountSelection()
}

sealed class ZoneSelection {
    data object All : ZoneSelection()
    data class AllInAccount(val accountId: String) : ZoneSelection()
    data class Specific(val ids: Set<String>) : ZoneSelection()
}

enum class ExpiryChoice(val label: String, val days: Long?) {
    NONE("No expiration", null), D7("7 days", 7), D30("30 days", 30), D90("90 days", 90), Y1("1 year", 365)
}

data class TokenDraft(
    val name: String = "",
    val selected: List<PermissionGroup> = emptyList(),
    val accounts: AccountSelection = AccountSelection.All,
    val zones: ZoneSelection = ZoneSelection.All,
    val allowedIps: String = "",
    val deniedIps: String = "",
    val expiry: ExpiryChoice = ExpiryChoice.D90,
    val notBefore: String = ""
)

/**
 * Turns the editor's choices into Cloudflare's policy shape. Kept pure so every resource
 * combination is unit tested; no UI state or network here. Only permission group IDs are
 * sent, since names are display data that Cloudflare can change cosmetically.
 */
object TokenPolicyBuilder {
    private const val ACCOUNT = "com.cloudflare.api.account."
    private const val ZONE = "com.cloudflare.api.account.zone."
    private const val USER = "com.cloudflare.api.user."

    /** Returns validation problems, empty when the draft can be submitted. */
    fun validate(draft: TokenDraft, isAccountOwned: Boolean): List<String> {
        val problems = mutableListOf<String>()
        if (draft.name.isBlank()) problems += "Enter a token name"
        if (draft.selected.isEmpty()) problems += "Add at least one permission"
        if (isAccountOwned && draft.selected.any { it.kind() == PermissionKind.USER }) problems += "Account-owned tokens cannot hold user permissions"
        if (draft.accounts is AccountSelection.Specific && (draft.accounts as AccountSelection.Specific).ids.isEmpty() &&
            draft.selected.any { it.kind() == PermissionKind.ACCOUNT }
        ) problems += "Choose at least one account"
        if (draft.zones is ZoneSelection.Specific && (draft.zones as ZoneSelection.Specific).ids.isEmpty() &&
            draft.selected.any { it.kind() == PermissionKind.ZONE }
        ) problems += "Choose at least one zone"
        (parseIps(draft.allowedIps) + parseIps(draft.deniedIps)).filterNot(::isValidCidr).forEach { problems += "Not a valid IP or CIDR: $it" }
        if (draft.notBefore.isNotBlank() && parseInstant(draft.notBefore) == null) problems += "Start time must be an ISO date like 2026-10-01 or 2026-10-01T09:00:00Z"
        return problems
    }

    fun build(draft: TokenDraft, ownerAccountId: String?, userId: String?, now: Instant = Instant.now()): TokenWrite {
        val byKind = draft.selected.groupBy { it.kind() }
        val policies = mutableListOf<TokenPolicy>()
        byKind[PermissionKind.ACCOUNT]?.let { groups ->
            policies += TokenPolicy(effect = "allow", resources = accountResources(draft.accounts, ownerAccountId), permissionGroups = groups.map { PermissionGroupRef(it.id) })
        }
        byKind[PermissionKind.ZONE]?.let { groups ->
            policies += TokenPolicy(effect = "allow", resources = zoneResources(draft.zones, ownerAccountId), permissionGroups = groups.map { PermissionGroupRef(it.id) })
        }
        byKind[PermissionKind.USER]?.let { groups ->
            if (userId != null) policies += TokenPolicy(effect = "allow", resources = mapOf("$USER$userId" to "*"), permissionGroups = groups.map { PermissionGroupRef(it.id) })
        }
        byKind[PermissionKind.OTHER]?.let { groups ->
            policies += TokenPolicy(effect = "allow", resources = accountResources(draft.accounts, ownerAccountId), permissionGroups = groups.map { PermissionGroupRef(it.id) })
        }
        return TokenWrite(
            name = draft.name.trim(),
            policies = policies,
            notBefore = draft.notBefore.takeIf { it.isNotBlank() }?.let { parseInstant(it)?.toString() },
            expiresOn = draft.expiry.days?.let { now.plus(it, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString() },
            condition = condition(draft.allowedIps, draft.deniedIps)
        )
    }

    /** Update keeps the token's existing policies untouched and changes only what the edit
     *  screen offers, so a policy the editor cannot represent is never rewritten. */
    fun buildUpdate(existing: ApiToken, name: String, active: Boolean, expiresOn: String?, allowedIps: String, deniedIps: String): TokenWrite =
        TokenWrite(
            name = name.trim(),
            policies = existing.policies.orEmpty(),
            notBefore = existing.notBefore,
            expiresOn = expiresOn,
            condition = condition(allowedIps, deniedIps),
            status = if (active) "active" else "disabled"
        )

    private fun accountResources(selection: AccountSelection, owner: String?): Map<String, Any> = when {
        owner != null -> mapOf("$ACCOUNT$owner" to "*")
        selection is AccountSelection.Specific -> selection.ids.associate { "$ACCOUNT$it" to "*" }
        else -> mapOf("$ACCOUNT*" to "*")
    }

    private fun zoneResources(selection: ZoneSelection, owner: String?): Map<String, Any> = when (selection) {
        is ZoneSelection.Specific -> selection.ids.associate { "$ZONE$it" to "*" }
        is ZoneSelection.AllInAccount -> mapOf("$ACCOUNT${selection.accountId}" to mapOf("$ZONE*" to "*"))
        ZoneSelection.All -> if (owner != null) mapOf("$ACCOUNT$owner" to mapOf("$ZONE*" to "*")) else mapOf("$ZONE*" to "*")
    }

    private fun condition(allowed: String, denied: String): TokenCondition? {
        val a = parseIps(allowed)
        val d = parseIps(denied)
        if (a.isEmpty() && d.isEmpty()) return null
        return TokenCondition(TokenIpList(allowed = a.ifEmpty { null }, denied = d.ifEmpty { null }))
    }

    fun parseIps(raw: String): List<String> = raw.split(',', '\n', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun isValidCidr(value: String): Boolean {
        val ip = value.substringBefore('/')
        val prefix = value.substringAfter('/', "")
        val v4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$").matches(ip)
        val v6 = !v4 && ip.contains(':') && Regex("^[0-9a-fA-F:.]+$").matches(ip) && ip.count { it == ':' } in 2..7
        if (!v4 && !v6) return false
        if (prefix.isEmpty()) return true
        val n = prefix.toIntOrNull() ?: return false
        return if (v4) n in 0..32 else n in 0..128
    }

    fun parseInstant(raw: String): Instant? = try {
        if (raw.length == 10) Instant.parse("${raw}T00:00:00Z") else Instant.parse(raw)
    } catch (e: DateTimeParseException) {
        null
    }
}

/**
 * Local, documented-attribute observations about a token (spec 57). Deliberately worded as
 * facts ("No expiration configured"), never as a verdict that a token is unsafe.
 */
object TokenRisk {
    data class Flag(val label: String, val detail: String)

    fun flags(token: ApiToken, now: Instant = Instant.now()): List<Flag> {
        val out = mutableListOf<Flag>()
        val expires = token.expiresOn?.let { runCatching { Instant.parse(it) }.getOrNull() }
        when {
            expires == null -> out += Flag("No expiration configured", "The token works until it is revoked.")
            expires.isBefore(now) -> out += Flag("Expired", "Expired ${token.expiresOn}.")
            expires.isBefore(now.plus(7, ChronoUnit.DAYS)) -> out += Flag("Expires soon", "Expires ${token.expiresOn}.")
            expires.isAfter(now.plus(365, ChronoUnit.DAYS)) -> out += Flag("Long-lived", "Valid for more than a year.")
        }
        val allowed = token.condition?.requestIp?.allowed.orEmpty()
        if (allowed.isEmpty()) out += Flag("No IP restriction", "Usable from any client IP address.")
        val policies = token.policies.orEmpty().filter { !it.effect.equals("deny", true) }
        val keys = policies.flatMap { p -> p.resources.keys + p.resources.values.filterIsInstance<Map<*, *>>().flatMap { it.keys.map(Any?::toString) } }
        if (keys.any { it == "com.cloudflare.api.account.*" }) out += Flag("Broad account scope", "Applies to every account the owner can access.")
        if (keys.any { it == "com.cloudflare.api.account.zone.*" }) out += Flag("All zones", "Applies to every zone in scope, including zones added later.")
        val names = policies.flatMap { it.permissionGroups }.mapNotNull { it.name?.lowercase() }
        if (names.any { it == "api tokens write" || it == "account api tokens write" }) out += Flag("Can create tokens", "Can create and edit other API tokens.")
        val lastUsed = token.lastUsedOn?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (lastUsed != null && lastUsed.isBefore(now.minus(90, ChronoUnit.DAYS))) out += Flag("Unused for 90+ days", "Last used ${token.lastUsedOn}.")
        return out
    }
}

/**
 * Starting points for new tokens (spec 10, 147). Names are resolved against the live
 * permission catalog at runtime; any that Cloudflare no longer offers are reported, never
 * silently dropped or guessed.
 */
object TokenTemplates {
    data class Template(val id: String, val title: String, val permissionNames: List<String>, val readAll: Boolean = false)

    val all = listOf(
        Template("dns", "Edit zone DNS", listOf("DNS Write", "Zone Read")),
        Template("analytics", "Read analytics and logs", listOf("Analytics Read", "Account Analytics Read", "Logs Read")),
        Template("workers", "Edit Cloudflare Workers", listOf(
            "Workers Scripts Write", "Workers KV Storage Write", "Workers R2 Storage Write", "Workers Routes Write",
            "Account Settings Read", "User Details Read", "Zone Read"
        )),
        Template("storage", "R2 storage", listOf("Workers R2 Storage Write")),
        Template("lb", "Edit load balancing", listOf("Load Balancers Write", "Load Balancing: Monitors and Pools Write", "Zone Read")),
        Template("billing", "Read billing", listOf("Billing Read")),
        Template("tokens", "Create additional tokens", listOf("API Tokens Write")),
        Template("readall", "Read all resources", emptyList(), readAll = true)
    )

    fun resolve(template: Template, catalog: List<PermissionGroup>, isAccountOwned: Boolean): Pair<List<PermissionGroup>, List<String>> {
        val usable = if (isAccountOwned) catalog.filter { it.kind() != PermissionKind.USER } else catalog
        if (template.readAll) return usable.filter { it.name.endsWith(" Read") } to emptyList()
        val found = template.permissionNames.mapNotNull { n -> usable.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        val missing = template.permissionNames.filter { n -> found.none { it.name.equals(n, ignoreCase = true) } }
        return found to missing
    }
}
