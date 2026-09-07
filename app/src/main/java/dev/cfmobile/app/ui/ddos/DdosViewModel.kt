package dev.cfmobile.app.ui.ddos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DdosRuleset
import dev.cfmobile.app.data.repository.DdosRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DdosUiState(
    val ruleset: UiState<DdosRuleset?> = UiState.Loading,
    val isRefreshing: Boolean = false
)

class DdosViewModel(
    private val zoneId: String,
    private val repository: DdosRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DdosUiState())
    val uiState: StateFlow<DdosUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(ruleset = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.getEntrypoint(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(ruleset = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure ->
                    // A zone with no ddos_l7 entrypoint ruleset answers 404. That means "no
                    // overrides configured", not an error - Cloudflare's L7 DDoS protection is
                    // on regardless - so render it as an empty state instead of a failure.
                    if (result.httpCode == 404) {
                        _uiState.update { it.copy(ruleset = UiState.Data(null), isRefreshing = false) }
                    } else {
                        _uiState.update {
                            it.copy(ruleset = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                        }
                    }
            }
        }
    }
}
