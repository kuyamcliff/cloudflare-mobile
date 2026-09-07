package dev.cfmobile.app.ui.durableobjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.DurableObjectNamespace
import dev.cfmobile.app.data.repository.DurableObjectsRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DurableObjectsUiState(
    val namespaces: UiState<List<DurableObjectNamespace>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** Read-only: namespaces appear and disappear by deploying Workers that declare them. */
class DurableObjectsViewModel(
    private val accountId: String,
    private val repository: DurableObjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DurableObjectsUiState())
    val uiState: StateFlow<DurableObjectsUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(namespaces = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listNamespaces(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(namespaces = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(namespaces = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
