package dev.cfmobile.app.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DnsFirewallCluster
import dev.cfmobile.app.data.repository.DnsFirewallRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DnsFirewallFormState(
    val name: String = "",
    val upstreamIps: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

data class DnsFirewallUiState(
    val clusters: UiState<List<DnsFirewallCluster>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val form: DnsFirewallFormState? = null,
    val deletingId: String? = null,
    val error: String? = null
)

/** Cloudflare accepts IPv4 and IPv6 upstreams; this only checks the shape enough to catch a
 *  hostname or a typo before the round trip. */
private val IPV4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")

fun looksLikeIpAddress(value: String): Boolean {
    if (IPV4.matches(value)) return value.split('.').all { (it.toIntOrNull() ?: 256) <= 255 }
    // Anything with a colon and only hex digits is an IPv6 address as far as this check goes.
    return value.contains(':') && value.all { it.isDigit() || it in "abcdefABCDEF:" }
}

fun parseUpstreamIps(raw: String): List<String> =
    raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

fun validateDnsFirewallForm(form: DnsFirewallFormState): String? {
    if (form.name.isBlank()) return "Cluster name is required"
    val ips = parseUpstreamIps(form.upstreamIps)
    if (ips.isEmpty()) return "At least one upstream nameserver is required"
    val invalid = ips.firstOrNull { !looksLikeIpAddress(it) }
    if (invalid != null) return "\"$invalid\" isn't an IP address - DNS Firewall takes addresses, not hostnames"
    return null
}

/** "2 upstreams · cache 60-900s" - how the cluster is configured, at a glance. */
fun clusterSummary(cluster: DnsFirewallCluster): String {
    val upstreams = cluster.upstreamIps?.size ?: 0
    val cache = listOfNotNull(cluster.minimumCacheTtl, cluster.maximumCacheTtl)
    return listOfNotNull(
        "$upstreams upstream${if (upstreams == 1) "" else "s"}",
        if (cache.size == 2) "cache ${cache[0]}-${cache[1]}s" else null,
        cluster.rateLimit?.takeIf { it > 0 }?.let { "$it qps limit" }
    ).joinToString(" · ")
}

/** DNS Firewall clusters: Cloudflare's resolvers in front of your own authoritative servers.
 *  Unrelated to the per-zone DNS the rest of the app manages. */
class DnsFirewallViewModel(
    private val accountId: String,
    private val repository: DnsFirewallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DnsFirewallUiState())
    val uiState: StateFlow<DnsFirewallUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(clusters = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listClusters(accountId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(clusters = UiState.Data(result.data), isRefreshing = false)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(clusters = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun openForm() = _uiState.update { it.copy(form = DnsFirewallFormState()) }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun updateForm(transform: (DnsFirewallFormState) -> DnsFirewallFormState) =
        _uiState.update { state -> state.form?.let { state.copy(form = transform(it)) } ?: state }

    fun save() {
        val form = _uiState.value.form ?: return
        val validationError = validateDnsFirewallForm(form)
        if (validationError != null) {
            updateForm { it.copy(error = validationError) }
            return
        }
        updateForm { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = repository.createCluster(
                accountId,
                form.name.trim(),
                parseUpstreamIps(form.upstreamIps)
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(form = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> updateForm { it.copy(isSaving = false, error = result.message) }
            }
        }
    }

    fun delete(cluster: DnsFirewallCluster) {
        _uiState.update { it.copy(deletingId = cluster.id, error = null) }
        viewModelScope.launch {
            when (val result = repository.deleteCluster(accountId, cluster.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null) }
                    load(isRefresh = true)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(deletingId = null, error = result.message) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
