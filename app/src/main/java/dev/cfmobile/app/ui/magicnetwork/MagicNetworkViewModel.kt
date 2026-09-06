package dev.cfmobile.app.ui.magicnetwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.MagicGreTunnel
import dev.cfmobile.app.data.remote.dto.MagicIpsecTunnel
import dev.cfmobile.app.data.remote.dto.MagicRoute
import dev.cfmobile.app.data.repository.MagicNetworkRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MagicTab { GRE, IPSEC, ROUTES }

data class MagicNetworkUiState(
    val tab: MagicTab = MagicTab.GRE,
    val greTunnels: UiState<List<MagicGreTunnel>> = UiState.Loading,
    val ipsecTunnels: UiState<List<MagicIpsecTunnel>> = UiState.Loading,
    val routes: UiState<List<MagicRoute>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** "203.0.113.1 → 198.51.100.1" for a tunnel's two endpoints. */
fun tunnelEndpointsLabel(cloudflareEndpoint: String?, customerEndpoint: String?): String? = when {
    cloudflareEndpoint != null && customerEndpoint != null -> "$cloudflareEndpoint → $customerEndpoint"
    cloudflareEndpoint != null -> cloudflareEndpoint
    else -> customerEndpoint
}

class MagicNetworkViewModel(
    private val accountId: String,
    private val repository: MagicNetworkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MagicNetworkUiState())
    val uiState: StateFlow<MagicNetworkUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun selectTab(tab: MagicTab) = _uiState.update { it.copy(tab = tab) }

    fun refresh() = load(isRefresh = true)

    /** All three lists load sequentially in one coroutine, for the same reason as elsewhere:
     *  independent launches would race at the HTTP layer with nothing gained. */
    private fun load(isRefresh: Boolean) {
        _uiState.update {
            if (isRefresh) it.copy(isRefreshing = true)
            else it.copy(greTunnels = UiState.Loading, ipsecTunnels = UiState.Loading, routes = UiState.Loading)
        }
        viewModelScope.launch {
            when (val result = repository.listGreTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(greTunnels = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(greTunnels = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listIpsecTunnels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(ipsecTunnels = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(ipsecTunnels = UiState.Error(ErrorClassifier.classify(result))) }
            }
            when (val result = repository.listRoutes(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(routes = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(routes = UiState.Error(ErrorClassifier.classify(result))) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
