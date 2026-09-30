package dev.cfmobile.app.core.capabilities

import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.data.remote.dto.TokenPolicy

/** Spec 5: more granular than a boolean. */
enum class CapabilityState {
    UNKNOWN,
    AVAILABLE_READ,
    AVAILABLE_WRITE,
    TOKEN_RESTRICTED,
    PLAN_RESTRICTED,
    API_UNSUPPORTED
}

val CapabilityState.isAvailable: Boolean get() = this == CapabilityState.AVAILABLE_READ || this == CapabilityState.AVAILABLE_WRITE

/** Where the engine learned what it knows about the token. */
enum class PolicySource {
    /** Cloudflare returned the token's policies: evaluation is exact for listed permissions. */
    TOKEN_POLICIES,
    /** Policies were not readable (the token lacks API Tokens Read). Evaluation relies on
     *  responses observed while using the app. */
    OBSERVED_ONLY
}

enum class TokenKind { USER, ACCOUNT }

/** One allow or deny grant flattened out of a policy, with the resources it applies to. */
data class Grant(
    val permissionId: String,
    val permissionName: String,
    val allow: Boolean,
    val resources: ResourceScope
)

/**
 * Which accounts and zones a policy covers. Built from Cloudflare's resource map:
 * `com.cloudflare.api.account.*`, `com.cloudflare.api.account.<id>`,
 * `com.cloudflare.api.account.zone.*`, `com.cloudflare.api.account.zone.<id>`, and an
 * account key mapping to a nested zone map for "all zones in this account".
 */
data class ResourceScope(
    val allAccounts: Boolean = false,
    val accounts: Set<String> = emptySet(),
    val allZones: Boolean = false,
    val zones: Set<String> = emptySet(),
    /** Accounts whose every zone is covered. */
    val zonesOfAccounts: Set<String> = emptySet(),
    val user: Boolean = false
) {
    fun coversAccount(accountId: String?): Boolean =
        allAccounts || (accountId != null && accountId in accounts) || user

    fun coversZone(zoneId: String?, zoneAccountId: String?): Boolean =
        allZones || (zoneId != null && zoneId in zones) ||
            (zoneAccountId != null && zoneAccountId in zonesOfAccounts) ||
            // An account-scoped grant on "all accounts" also reaches zone resources in them.
            allAccounts

    companion object {
        private const val ACCOUNT = "com.cloudflare.api.account."
        private const val ZONE = "com.cloudflare.api.account.zone."
        private const val USER = "com.cloudflare.api.user"

        fun from(resources: Map<String, Any>): ResourceScope {
            var scope = ResourceScope()
            for ((key, value) in resources) {
                scope = when {
                    key == "$ZONE*" -> scope.copy(allZones = true)
                    key.startsWith(ZONE) -> scope.copy(zones = scope.zones + key.removePrefix(ZONE))
                    key == "$ACCOUNT*" -> {
                        if (value is Map<*, *>) scope.copy(allZones = true, allAccounts = true) else scope.copy(allAccounts = true)
                    }
                    key.startsWith(ACCOUNT) -> {
                        val id = key.removePrefix(ACCOUNT)
                        if (value is Map<*, *>) {
                            val nested = value.keys.map { it.toString() }
                            val next = scope.copy(accounts = scope.accounts + id)
                            if (nested.any { it == "$ZONE*" }) next.copy(zonesOfAccounts = next.zonesOfAccounts + id)
                            else next.copy(zones = next.zones + nested.filter { it.startsWith(ZONE) }.map { it.removePrefix(ZONE) })
                        } else {
                            scope.copy(accounts = scope.accounts + id)
                        }
                    }
                    key.startsWith(USER) -> scope.copy(user = true)
                    else -> scope
                }
            }
            return scope
        }
    }
}

data class TokenIdentity(
    val tokenId: String?,
    val kind: TokenKind,
    val status: String?,
    val expiresOn: String?,
    /** For account-owned tokens: the account that owns it. */
    val ownerAccountId: String?
)

/**
 * The capability graph for one profile (spec 4, 5, 310). Pure and immutable: the
 * [CapabilityRepository] builds a new instance whenever it learns something.
 */
data class TokenCapabilities(
    val identity: TokenIdentity?,
    val source: PolicySource,
    val grants: List<Grant>,
    /** Operation ids observed to succeed (2xx) or be denied (403) with this token. */
    val observedAllowed: Set<String> = emptySet(),
    val observedDenied: Set<String> = emptySet(),
    val discoveredAt: Long = 0
) {
    private val allowedNames: Set<String> = grants.filter { it.allow }.map { normalize(it.permissionName) }.toSet()

    val grantedPermissionNames: List<String> get() = grants.filter { it.allow }.map { it.permissionName }.distinct().sorted()

    /**
     * Evaluates one operation. [accountId]/[zoneId] narrow the answer to a concrete resource
     * when known, using the policy's resource scope.
     */
    fun evaluate(endpoint: EndpointDef, accountId: String? = null, zoneId: String? = null, zoneAccountId: String? = null): CapabilityState {
        if (endpoint.id in observedDenied) return CapabilityState.TOKEN_RESTRICTED
        if (endpoint.id in observedAllowed) return if (endpoint.isMutation) CapabilityState.AVAILABLE_WRITE else CapabilityState.AVAILABLE_READ
        if (source != PolicySource.TOKEN_POLICIES || endpoint.permissions.isEmpty()) return CapabilityState.UNKNOWN

        val accepted = endpoint.permissions.map(::normalize).toSet()
        val matching = grants.filter { normalize(it.permissionName) in accepted }
        if (matching.isEmpty()) return CapabilityState.TOKEN_RESTRICTED

        fun covers(g: Grant) = when {
            zoneId != null -> g.resources.coversZone(zoneId, zoneAccountId)
            accountId != null -> g.resources.coversAccount(accountId) || g.resources.zonesOfAccounts.contains(accountId) ||
                g.resources.allZones
            else -> true
        }
        val relevant = matching.filter(::covers)
        if (relevant.any { !it.allow }) return CapabilityState.TOKEN_RESTRICTED
        if (relevant.none { it.allow }) return CapabilityState.TOKEN_RESTRICTED
        val writes = relevant.any { it.allow && isWriteName(it.permissionName) }
        return if (writes) CapabilityState.AVAILABLE_WRITE else CapabilityState.AVAILABLE_READ
    }

    fun withObservation(endpointId: String, allowed: Boolean): TokenCapabilities =
        if (allowed) copy(observedAllowed = observedAllowed + endpointId, observedDenied = observedDenied - endpointId)
        else copy(observedDenied = observedDenied + endpointId, observedAllowed = observedAllowed - endpointId)

    /** Counts for the profile summary (spec 259). Not a score. */
    fun summary(registry: EndpointRegistry): CapabilitySummary {
        if (source != PolicySource.TOKEN_POLICIES) return CapabilitySummary(0, 0, 0, known = false)
        val granted = grants.filter { it.allow }.map { it.permissionName }.distinct()
        val writes = granted.count(::isWriteName)
        val restricted = registry.permissionNames.map(::normalize).distinct().count { it !in allowedNames }
        return CapabilitySummary(read = granted.size - writes, write = writes, restrictedPermissionFamilies = restricted, known = true)
    }

    companion object {
        fun normalize(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

        fun isWriteName(name: String): Boolean {
            val n = normalize(name)
            return n.endsWith(" write") || n.endsWith(" edit") || n.endsWith(" admin") || n.endsWith(" revoke") ||
                n == "cache purge" || n.endsWith(":edit")
        }

        fun fromPolicies(identity: TokenIdentity?, policies: List<TokenPolicy>, now: Long): TokenCapabilities {
            val grants = policies.flatMap { policy ->
                val scope = ResourceScope.from(policy.resources)
                policy.permissionGroups.map { pg ->
                    Grant(pg.id, pg.name ?: pg.id, allow = !policy.effect.equals("deny", true), resources = scope)
                }
            }
            return TokenCapabilities(identity, PolicySource.TOKEN_POLICIES, grants, discoveredAt = now)
        }

        fun observedOnly(identity: TokenIdentity?, now: Long) =
            TokenCapabilities(identity, PolicySource.OBSERVED_ONLY, emptyList(), discoveredAt = now)
    }
}

data class CapabilitySummary(val read: Int, val write: Int, val restrictedPermissionFamilies: Int, val known: Boolean)
