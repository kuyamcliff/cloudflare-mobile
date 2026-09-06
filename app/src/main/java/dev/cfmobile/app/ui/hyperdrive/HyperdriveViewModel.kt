package dev.cfmobile.app.ui.hyperdrive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.HyperdriveConfig
import dev.cfmobile.app.data.repository.HyperdriveRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HyperdriveUiState(
    val configs: UiState<List<HyperdriveConfig>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val deletingId: String? = null
)

/** Renders an origin as user@host:port/database, skipping whatever Cloudflare didn't return.
 *  The password is never part of the read payload, so there's nothing sensitive to redact. */
fun hyperdriveOriginLabel(config: HyperdriveConfig): String? {
    val origin = config.origin ?: return null
    val host = origin.host ?: return null
    val hostPort = origin.port?.let { "$host:$it" } ?: host
    val withUser = origin.user?.let { "$it@$hostPort" } ?: hostPort
    return origin.database?.let { "$withUser/$it" } ?: withUser
}

class HyperdriveViewModel(
    private val accountId: String,
    private val repository: HyperdriveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HyperdriveUiState())
    val uiState: StateFlow<HyperdriveUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(configs = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listConfigs(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(configs = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(configs = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }

    fun delete(config: HyperdriveConfig) {
        _uiState.update { it.copy(deletingId = config.id) }
        viewModelScope.launch {
            repository.deleteConfig(accountId, config.id)
            _uiState.update { it.copy(deletingId = null) }
            load(isRefresh = true)
        }
    }
}
