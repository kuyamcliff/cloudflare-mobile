package dev.cfmobile.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.capabilities.Capability
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityScope
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.data.local.ContextStore
import dev.cfmobile.app.data.local.WorkingContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResourceItem(val capability: Capability, val state: CapabilityState, val readOnly: Boolean, val route: String?)

data class ResourceGroup(val title: String, val items: List<ResourceItem>)

data class ResourcesUiState(
    val context: WorkingContext = WorkingContext(),
    val zoneGroups: List<ResourceGroup> = emptyList(),
    val accountGroups: List<ResourceGroup> = emptyList(),
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    val query: String = ""
)

class ResourcesViewModel(
    private val contextStore: ContextStore,
    private val capabilities: CapabilityRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResourcesUiState())
    val uiState: StateFlow<ResourcesUiState> = _uiState.asStateFlow()
    private val options = MutableStateFlow(false to "")

    init {
        viewModelScope.launch {
            combine(contextStore.state, capabilities.state, options) { ctx, _, opt -> ctx to opt }.collect { (ctx, opt) ->
                val (showHidden, query) = opt
                var hidden = 0
                suspend fun build(scope: CapabilityScope): List<ResourceGroup> {
                    val zone = ctx.zone
                    val account = ctx.account?.id
                    return CapabilityRegistry.implementedForScope(scope).mapNotNull { cap ->
                        val zoneScoped = scope == CapabilityScope.ZONE
                        val route = if (zoneScoped) zone?.let { cap.zoneRoute?.invoke(it.id, it.name) } else account?.let { cap.accountRoute?.invoke(it) }
                        if (route == null) return@mapNotNull null
                        val state = capabilities.stateFor(cap.id, accountId = if (zoneScoped) zone?.parentId else account, zoneId = if (zoneScoped) zone?.id else null, zoneAccountId = zone?.parentId)
                        if (state == CapabilityState.TOKEN_RESTRICTED) hidden++
                        if (state == CapabilityState.TOKEN_RESTRICTED && !showHidden) return@mapNotNull null
                        if (query.isNotBlank() && !(cap.displayName.contains(query, true) || cap.product.contains(query, true) || cap.description.contains(query, true))) return@mapNotNull null
                        val write = capabilities.canWrite(cap.id, accountId = account, zoneId = if (zoneScoped) zone?.id else null, zoneAccountId = zone?.parentId)
                        ResourceItem(cap, state, readOnly = write == false && state.let { it == CapabilityState.AVAILABLE_READ }, route = route)
                    }.groupBy { it.capability.product }.map { (k, v) -> ResourceGroup(k, v) }
                }
                val zoneGroups = build(CapabilityScope.ZONE)
                val accountGroups = build(CapabilityScope.ACCOUNT)
                _uiState.update { it.copy(context = ctx, zoneGroups = zoneGroups, accountGroups = accountGroups, hiddenCount = hidden, showHidden = showHidden, query = query) }
            }
        }
    }

    fun setShowHidden(v: Boolean) = options.update { v to it.second }
    fun setQuery(q: String) {
        _uiState.update { it.copy(query = q) }
        options.update { it.first to q }
    }
}
