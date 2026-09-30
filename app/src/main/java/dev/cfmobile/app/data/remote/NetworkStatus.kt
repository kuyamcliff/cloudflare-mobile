package dev.cfmobile.app.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the app currently knows about its connection to Cloudflare, for the small global
 *  status indicator. Derived from real responses only: nothing here is polled. */
data class NetworkSnapshot(
    val rateLimitedUntilMillis: Long = 0,
    /** Cloudflare's `ratelimit-remaining` header when it sent one. Null means unknown, which
     *  the UI shows as unknown rather than guessing. */
    val rateLimitRemaining: Int? = null,
    val lastStatusCode: Int? = null,
    val lastLatencyMillis: Long? = null,
    val consecutiveServerErrors: Int = 0,
    val lastNetworkFailureAt: Long = 0,
    val requestsThisSession: Int = 0
)

enum class ApiHealth { OK, RATE_LIMITED, POSSIBLE_API_ISSUES, UNREACHABLE }

class NetworkStatus(private val clock: () -> Long = System::currentTimeMillis) {
    private val _snapshot = MutableStateFlow(NetworkSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    fun onResponse(code: Int, latencyMillis: Long, remaining: Int?) = _snapshot.update {
        it.copy(
            lastStatusCode = code,
            lastLatencyMillis = latencyMillis,
            rateLimitRemaining = remaining ?: it.rateLimitRemaining,
            consecutiveServerErrors = if (code >= 500) it.consecutiveServerErrors + 1 else 0,
            lastNetworkFailureAt = 0,
            requestsThisSession = it.requestsThisSession + 1
        )
    }

    fun onRateLimited(retryAfterMillis: Long) = _snapshot.update {
        it.copy(rateLimitedUntilMillis = maxOf(it.rateLimitedUntilMillis, clock() + retryAfterMillis), rateLimitRemaining = 0)
    }

    fun onNetworkFailure() = _snapshot.update { it.copy(lastNetworkFailureAt = clock()) }

    fun health(snapshot: NetworkSnapshot = _snapshot.value): ApiHealth = when {
        snapshot.lastNetworkFailureAt > 0 -> ApiHealth.UNREACHABLE
        snapshot.rateLimitedUntilMillis > clock() -> ApiHealth.RATE_LIMITED
        snapshot.consecutiveServerErrors >= 2 -> ApiHealth.POSSIBLE_API_ISSUES
        else -> ApiHealth.OK
    }
}
