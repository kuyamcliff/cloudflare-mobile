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
    val deployments: UiState<List<PagesDeployment>>? = null,
    /** Non-null while a deploy or retry is in flight, so the sheet can disable both buttons. */
    val deployingProject: String? = null,
    val deployError: String? = null,
    val deployMessage: String? = null
)

/** Projects, deployment history, and re-deploying. Editing a project's build configuration
 *  isn't implemented - see CapabilityRegistry's migrationHint. */
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

    fun closeDeployments() = _uiState.update {
        it.copy(selectedProjectName = null, deployments = null, deployError = null, deployMessage = null)
    }

    /** Rebuilds the project's production branch from its connected source. Cloudflare queues
     *  the build, so the new deployment shows up in the refreshed history as "queued" rather
     *  than finished. */
    fun deploy() {
        val projectName = _uiState.value.selectedProjectName ?: return
        startDeploy(projectName) { repository.createDeployment(accountId, projectName) }
    }

    /** Re-runs one failed deployment rather than building the branch afresh. */
    fun retry(deployment: PagesDeployment) {
        val projectName = _uiState.value.selectedProjectName ?: return
        startDeploy(projectName) { repository.retryDeployment(accountId, projectName, deployment.id) }
    }

    private fun startDeploy(projectName: String, request: suspend () -> ApiResult<PagesDeployment>) {
        _uiState.update { it.copy(deployingProject = projectName, deployError = null, deployMessage = null) }
        viewModelScope.launch {
            when (val result = request()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(deployingProject = null, deployMessage = "Deployment queued", deployError = null)
                    }
                    reloadDeployments(projectName)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(deployingProject = null, deployError = result.message)
                }
            }
        }
    }

    private suspend fun reloadDeployments(projectName: String) {
        when (val result = repository.listDeployments(accountId, projectName)) {
            is ApiResult.Success -> _uiState.update {
                // Guard against the sheet having been closed or switched while the deploy ran.
                if (it.selectedProjectName != projectName) it else it.copy(deployments = UiState.Data(result.data))
            }
            is ApiResult.Failure -> _uiState.update {
                if (it.selectedProjectName != projectName) it
                else it.copy(deployments = UiState.Error(ErrorClassifier.classify(result)))
            }
        }
    }
}
