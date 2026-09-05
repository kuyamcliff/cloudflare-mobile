package dev.cfmobile.app.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.core.errors.ErrorClassifier
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.PagesDeployment
import dev.cfmobile.app.data.remote.dto.PagesProject
import dev.cfmobile.app.data.repository.PagesRepository
import dev.cfmobile.app.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagesUiState(
    val projects: UiState<List<PagesProject>> = UiState.Loading,
    val selectedProjectName: String? = null,
    val deployments: UiState<List<PagesDeployment>>? = null
)

/** Read-mostly (PRD scope trim): list projects and view deployment history only - triggering
 *  a new deployment or editing project/build config isn't implemented, see
 *  CapabilityRegistry's migrationHint. */
class PagesViewModel(
    private val accountId: String,
    private val repository: PagesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PagesUiState())
    val uiState: StateFlow<PagesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(projects = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listProjects(accountId)) {
                is ApiResult.Success -> _uiState.update { it.copy(projects = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(projects = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun selectProject(project: PagesProject) {
        _uiState.update { it.copy(selectedProjectName = project.name, deployments = UiState.Loading) }
        viewModelScope.launch {
            when (val result = repository.listDeployments(accountId, project.name)) {
                is ApiResult.Success -> _uiState.update { it.copy(deployments = UiState.Data(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(deployments = UiState.Error(ErrorClassifier.classify(result))) }
            }
        }
    }

    fun closeDeployments() = _uiState.update { it.copy(selectedProjectName = null, deployments = null) }
}
