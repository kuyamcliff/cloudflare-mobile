package dev.cfmobile.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.EndpointDef
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.data.local.AccountSummary
import dev.cfmobile.app.data.local.ContextStore
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.CfAccount
import dev.cfmobile.app.data.remote.dto.CfZone
import dev.cfmobile.app.data.repository.AccountsRepository
import dev.cfmobile.app.data.repository.AuthRepository
import dev.cfmobile.app.data.repository.ZonesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeatureHit(val title: String, val subtitle: String, val route: String)

data class SearchUiState(
    val query: String = "",
    val features: List<FeatureHit> = emptyList(),
    val zones: List<CfZone> = emptyList(),
    val zonesLoading: Boolean = false,
    val accounts: List<CfAccount> = emptyList(),
    val profiles: List<AccountSummary> = emptyList(),
    val endpoints: List<EndpointDef> = emptyList()
)

/**
 * Search everywhere (spec 14). Local sources answer instantly; zone search runs server-side,
 * debounced, and is cancelled by the next keystroke. Supports `type:` prefixes such as
 * `zone:example` or `api:dns` to search one source (spec 237).
 */
class SearchViewModel(
    private val zonesRepository: ZonesRepository,
    private val accountsRepository: AccountsRepository,
    private val auth: AuthRepository,
    private val contextStore: ContextStore,
    private val registryProvider: suspend () -> EndpointRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private var remoteJob: Job? = null
    private var accountsCache: List<CfAccount>? = null

    fun setQuery(raw: String) {
        _uiState.update { it.copy(query = raw) }
        val (type, q) = parse(raw)
        remoteJob?.cancel()
        if (q.isBlank()) {
            _uiState.update { SearchUiState(query = raw) }
            return
        }
        val ctx = contextStore.state.value
        val features = if (type == null || type == "feature") CapabilityRegistry.implemented().filter {
            it.displayName.contains(q, true) || it.product.contains(q, true) || it.description.contains(q, true)
        }.mapNotNull { cap ->
            val route = cap.zoneRoute?.let { r -> ctx.zone?.let { z -> r(z.id, z.name) } } ?: cap.accountRoute?.let { r -> ctx.account?.let { a -> r(a.id) } }
            route?.let { FeatureHit(cap.displayName, if (cap.zoneRoute != null) "${ctx.zone?.name}" else "${ctx.account?.name}", it) }
        }.take(8) else emptyList()
        val profiles = if (type == null || type == "profile") auth.savedAccounts.filter { it.label.contains(q, true) } else emptyList()
        _uiState.update { it.copy(features = features, profiles = profiles) }

        remoteJob = viewModelScope.launch {
            if (type == null || type == "api") {
                val eps = registryProvider().search(q, 15)
                _uiState.update { it.copy(endpoints = eps) }
            } else _uiState.update { it.copy(endpoints = emptyList()) }
            if (type == null || type == "account") {
                val accounts = accountsCache ?: (accountsRepository.listAccounts() as? ApiResult.Success)?.data?.also { accountsCache = it }.orEmpty()
                _uiState.update { it.copy(accounts = accounts.filter { a -> a.name.contains(q, true) || a.id.startsWith(q) }.take(8)) }
            } else _uiState.update { it.copy(accounts = emptyList()) }
            if (type == null || type == "zone") {
                delay(300)
                _uiState.update { it.copy(zonesLoading = true) }
                val zones = (zonesRepository.listZones(search = q, maxPages = 1) as? ApiResult.Success)?.data.orEmpty()
                _uiState.update { it.copy(zones = zones.take(20), zonesLoading = false) }
            } else _uiState.update { it.copy(zones = emptyList()) }
        }
    }

    fun selectZone(zone: CfZone) = contextStore.selectZone(dev.cfmobile.app.data.local.NamedRef(zone.id, zone.name, zone.account?.id))
    fun selectAccount(a: CfAccount) = contextStore.selectAccount(dev.cfmobile.app.data.local.NamedRef(a.id, a.name))
    fun switchProfile(id: String) = auth.switchTo(id)

    companion object {
        private val TYPES = setOf("zone", "api", "account", "feature", "profile")
        fun parse(raw: String): Pair<String?, String> {
            val t = raw.trim()
            val prefix = t.substringBefore(':', "").lowercase()
            return if (prefix in TYPES) prefix to t.substringAfter(':').trim() else null to t
        }
    }
}
