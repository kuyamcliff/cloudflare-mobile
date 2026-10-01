package dev.cfmobile.app.ui.tokens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.api.EndpointRegistry
import dev.cfmobile.app.core.capabilities.Capability
import dev.cfmobile.app.core.capabilities.CapabilityRegistry
import dev.cfmobile.app.core.capabilities.CapabilityRepository
import dev.cfmobile.app.core.capabilities.CapabilityState
import dev.cfmobile.app.core.capabilities.CapabilitySummary
import dev.cfmobile.app.core.capabilities.CapabilityUiState
import dev.cfmobile.app.core.capabilities.PolicySource
import dev.cfmobile.app.data.local.WorkingContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeatureAccess(val capability: Capability, val state: CapabilityState, val canWrite: Boolean?, val permissions: List<String>)

data class TokenAccessUiState(
    val discovery: CapabilityUiState = CapabilityUiState(),
    val summary: CapabilitySummary? = null,
    val features: List<FeatureAccess> = emptyList(),
    val query: String = ""
) {
    val availableProducts: Int get() = features.filter { it.state == CapabilityState.AVAILABLE_READ || it.state == CapabilityState.AVAILABLE_WRITE }.map { it.capability.product }.distinct().size
}

class TokenAccessViewModel(
    private val capabilities: CapabilityRepository,
    private val registryProvider: suspend () -> EndpointRegistry,
    private val workingContext: () -> WorkingContext
) : ViewModel() {
    private val _uiState = MutableStateFlow(TokenAccessUiState())
    val uiState: StateFlow<TokenAccessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val registry = registryProvider()
            capabilities.state.collect { capState ->
                val ctx = workingContext()
                val features = CapabilityRegistry.implemented().map { cap ->
                    val zoneScoped = cap.zoneRoute != null
                    val state = capabilities.stateFor(cap.id, accountId = ctx.account?.id, zoneId = if (zoneScoped) ctx.zone?.id else null, zoneAccountId = ctx.zone?.parentId)
                    val write = capabilities.canWrite(cap.id, accountId = ctx.account?.id, zoneId = if (zoneScoped) ctx.zone?.id else null, zoneAccountId = ctx.zone?.parentId)
                    FeatureAccess(cap, state, write, capabilities.endpointFor(cap.id)?.permissions?.distinct().orEmpty())
                }
                _uiState.update {
                    it.copy(discovery = capState, features = features, summary = capState.capabilities?.summary(registry))
                }
            }
        }
    }

    fun refresh() { viewModelScope.launch { capabilities.discover() } }
    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }
    fun policiesKnown(): Boolean = _uiState.value.discovery.capabilities?.source == PolicySource.TOKEN_POLICIES
}
