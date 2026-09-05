package dev.cfmobile.app.ui.workers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.WorkerScript
import dev.cfmobile.app.data.repository.WorkersRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkersUiState(
    val scripts: UiState<List<WorkerScript>> = UiState.Loading,
    val deletingId: String? = null
)

/** List/view/delete only - editing or deploying script code needs an editor and bundler
 *  that don't belong on mobile, see CapabilityRegistry's migrationHint. */
class WorkersViewModel(
    private val accountId: String,
    private val repository: WorkersRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkersUiState())
    val uiState: StateFlow<WorkersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(scripts = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listScripts(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(scripts = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(scripts = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun delete(script: WorkerScript) {
        _uiState.update { it.copy(deletingId = script.id) }
        viewModelScope.launch {
            repository.deleteScript(accountId, script.id)
            _uiState.update { it.copy(deletingId = null) }
            refresh()
        }
    }
}
