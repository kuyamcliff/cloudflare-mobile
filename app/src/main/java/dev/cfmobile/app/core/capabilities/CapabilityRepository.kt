package dev.cfmobile.app.core.capabilities

import android.content.SharedPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.RequestRecord
import dev.cfmobile.app.data.remote.dto.TokenPolicy
import dev.cfmobile.app.data.remote.safeApiCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Discovery progress, for the onboarding and profile screens (spec 248, 249). */
enum class DiscoveryPhase { IDLE, VERIFYING, READING_POLICIES, DONE, FAILED }

data class CapabilityUiState(
    val profileId: String? = null,
    val capabilities: TokenCapabilities? = null,
    val phase: DiscoveryPhase = DiscoveryPhase.IDLE,
    val error: String? = null
)

/**
 * Central capability engine (spec 203, 310). Feature screens never guess permissions: they
 * ask this repository, which combines the token's policies (when Cloudflare lets the token
 * read them) with what the app has observed, and re-evaluates after any 403 (spec 199).
 *
 * Discovery only ever makes safe GET calls (spec 317, 318).
 */
class CapabilityRepository(
    private val api: CloudflareApi,
    private val registryProvider: suspend () -> EndpointRegistry,
    private val cache: SharedPreferences,
    private val scope: CoroutineScope,
    private val activeProfileId: () -> String?,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow(CapabilityUiState())
    val state: StateFlow<CapabilityUiState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var rediscoveryJob: Job? = null
    private var lastForbiddenRefresh = 0L

    /** Loads cached knowledge for the active profile immediately, then refreshes. */
    fun onProfileActivated() {
        val profile = activeProfileId() ?: run { _state.value = CapabilityUiState(); return }
        _state.value = CapabilityUiState(profileId = profile, capabilities = readCache(profile), phase = DiscoveryPhase.IDLE)
        scope.launch { discover() }
    }

    suspend fun discover(): CapabilityUiState = mutex.withLock {
        val profile = activeProfileId() ?: return CapabilityUiState()
        _state.update { it.copy(profileId = profile, phase = DiscoveryPhase.VERIFYING, error = null) }

        val identity = identify()
        if (identity == null) {
            // Neither verify endpoint accepted the token. Keep whatever was cached: an expired
            // or revoked token is reported by the 401 handling, not by wiping knowledge.
            _state.update { it.copy(phase = DiscoveryPhase.FAILED, error = "Cloudflare did not verify this token.") }
            return _state.value
        }

        _state.update { it.copy(phase = DiscoveryPhase.READING_POLICIES) }
        val policies = readPolicies(identity)
        val previous = _state.value.capabilities
        val next = if (policies != null) {
            TokenCapabilities.fromPolicies(identity, policies, clock())
        } else {
            TokenCapabilities.observedOnly(identity, clock())
        }.copy(
            observedAllowed = previous?.observedAllowed.orEmpty(),
            // A fresh policy read supersedes earlier 403 observations for listed permissions.
            observedDenied = if (policies != null) emptySet() else previous?.observedDenied.orEmpty()
        )
        writeCache(profile, next, policies)
        _state.value = CapabilityUiState(profile, next, DiscoveryPhase.DONE)
        return _state.value
    }

    private suspend fun identify(): TokenIdentity? {
        when (val user = safeApiCall { api.verifyToken() }) {
            is ApiResult.Success -> return TokenIdentity(user.data.id, TokenKind.USER, user.data.status, user.data.expiresOn, null)
            is ApiResult.Failure -> Unit
        }
        // Account-owned tokens are verified per account.
        val accounts = (safeApiCall { api.listAccounts(perPage = 10) } as? ApiResult.Success)?.data.orEmpty()
        for (account in accounts.take(5)) {
            val result = safeApiCall { api.verifyAccountToken(account.id) }
            if (result is ApiResult.Success) {
                return TokenIdentity(result.data.id, TokenKind.ACCOUNT, result.data.status, result.data.expiresOn, account.id)
            }
        }
        return null
    }

    private suspend fun readPolicies(identity: TokenIdentity): List<TokenPolicy>? {
        val tokenId = identity.tokenId ?: return null
        val result = when (identity.kind) {
            TokenKind.USER -> safeApiCall { api.getApiToken(tokenId) }
            TokenKind.ACCOUNT -> {
                val owner = identity.ownerAccountId ?: return null
                safeApiCall { api.getAccountApiToken(owner, tokenId) }
            }
        }
        return (result as? ApiResult.Success)?.data?.policies
    }

    /** Fed by the network layer for every API response (spec 199, 205). */
    fun onRequestObserved(record: RequestRecord) {
        val code = record.statusCode ?: return
        if (code != 403 && code !in 200..299) return
        scope.launch {
            val endpoint = registryProvider().match(record.method, record.path) ?: return@launch
            _state.update { s -> s.copy(capabilities = s.capabilities?.withObservation(endpoint.id, allowed = code != 403)) }
            if (code == 403) scheduleRediscovery()
        }
    }

    /** At most one re-evaluation per minute, however many 403s arrive (no retry loops). */
    private fun scheduleRediscovery() {
        val now = clock()
        if (now - lastForbiddenRefresh < 60_000 || rediscoveryJob?.isActive == true) return
        lastForbiddenRefresh = now
        rediscoveryJob = scope.launch { discover() }
    }

    suspend fun endpointFor(capabilityId: String): EndpointDef? {
        val probe = CapabilityProbes.byCapabilityId[capabilityId] ?: return null
        return registryProvider().endpoints.firstOrNull { it.method == probe.method && it.path == probe.path }
    }

    /** State of a native screen for a concrete account or zone. */
    suspend fun stateFor(capabilityId: String, accountId: String? = null, zoneId: String? = null, zoneAccountId: String? = null): CapabilityState {
        val caps = _state.value.capabilities ?: return CapabilityState.UNKNOWN
        val endpoint = endpointFor(capabilityId) ?: return CapabilityState.UNKNOWN
        return caps.evaluate(endpoint, accountId, zoneId, zoneAccountId)
    }

    /**
     * Whether writes in this screen's area are allowed: evaluates the schema's own mutation
     * operations on the probe's path. Null when unknown.
     */
    suspend fun canWrite(capabilityId: String, accountId: String? = null, zoneId: String? = null, zoneAccountId: String? = null): Boolean? {
        val caps = _state.value.capabilities ?: return null
        if (caps.source != PolicySource.TOKEN_POLICIES) return null
        val probe = CapabilityProbes.byCapabilityId[capabilityId] ?: return null
        val mutations = registryProvider().endpoints.filter {
            it.isMutation && (it.path == probe.path || it.path.startsWith(probe.path + "/"))
        }
        if (mutations.isEmpty()) return null
        return mutations.any { caps.evaluate(it, accountId, zoneId, zoneAccountId).isAvailable }
    }

    fun clear() {
        _state.value = CapabilityUiState()
    }

    fun forgetProfile(profileId: String) {
        cache.edit().remove(key(profileId)).apply()
    }

    // ---- cache: permission metadata only, never a secret (spec 205) ----

    @JsonClass(generateAdapter = true)
    internal data class CachedCapabilities(
        val tokenId: String?,
        val kind: String,
        val status: String?,
        val expiresOn: String?,
        val ownerAccountId: String?,
        val policies: List<TokenPolicy>?,
        val discoveredAt: Long
    )

    private val adapter = Moshi.Builder()
        .add(Any::class.java, dev.cfmobile.app.data.remote.AnyJsonAdapter())
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
        .adapter(CachedCapabilities::class.java)

    private fun key(profile: String) = "caps_$profile"

    private fun writeCache(profile: String, caps: TokenCapabilities, policies: List<TokenPolicy>?) {
        val id = caps.identity
        val json = adapter.toJson(
            CachedCapabilities(id?.tokenId, id?.kind?.name ?: TokenKind.USER.name, id?.status, id?.expiresOn, id?.ownerAccountId, policies, caps.discoveredAt)
        )
        cache.edit().putString(key(profile), json).apply()
    }

    private fun readCache(profile: String): TokenCapabilities? {
        val raw = cache.getString(key(profile), null) ?: return null
        val cached = runCatching { adapter.fromJson(raw) }.getOrNull() ?: return null
        val identity = TokenIdentity(cached.tokenId, runCatching { TokenKind.valueOf(cached.kind) }.getOrDefault(TokenKind.USER), cached.status, cached.expiresOn, cached.ownerAccountId)
        return if (cached.policies != null) TokenCapabilities.fromPolicies(identity, cached.policies, cached.discoveredAt)
        else TokenCapabilities.observedOnly(identity, cached.discoveredAt)
    }
}
