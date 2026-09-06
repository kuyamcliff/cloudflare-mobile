package dev.cfmobile.app.ui.workersai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.AiModel
import dev.cfmobile.app.data.repository.WorkersAiRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkersAiUiState(
    val models: UiState<List<AiModel>> = UiState.Loading,
    val isRefreshing: Boolean = false
)

/** Model ids look like "@cf/meta/llama-3-8b-instruct"; the trailing segment is what people
 *  actually recognise, so lead with that and keep the full id available underneath. */
fun aiModelShortName(model: AiModel): String =
    model.name.substringAfterLast('/').takeIf { it.isNotBlank() } ?: model.id

class WorkersAiViewModel(
    private val accountId: String,
    private val repository: WorkersAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkersAiUiState())
    val uiState: StateFlow<WorkersAiUiState> = _uiState.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        _uiState.update { if (isRefresh) it.copy(isRefreshing = true) else it.copy(models = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listModels(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(models = UiState.Data(result.data), isRefreshing = false) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(models = UiState.Error(ErrorClassifier.classify(result)), isRefreshing = false)
                }
            }
        }
    }
}
