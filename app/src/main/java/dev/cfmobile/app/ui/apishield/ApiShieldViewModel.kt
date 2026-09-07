package dev.cfmobile.app.ui.apishield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.ApiOperation
import dev.cfmobile.app.data.repository.ApiShieldRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiShieldUiState(
    val operations: UiState<List<ApiOperation>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** "GET /api/users/{id}" - the shape people recognise an endpoint by. */
fun operationLabel(operation: ApiOperation): String =
    listOfNotNull(operation.method, operation.endpoint).joinToString(" ").ifBlank { operation.operationId }

class ApiShieldViewModel(
    private val zoneId: String,
    private val repository: ApiShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiShieldUiState())
    val uiState: StateFlow<ApiShieldUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(operations = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listOperations(zoneId)) {
                is ApiResult.Success -> _uiState.update { it.copy(operations = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(operations = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
